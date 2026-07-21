package com.example.live

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.tools.ToolExecutionEngine
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

enum class AssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class AssistantState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val micAmplitude: Float = 0f,
    val speakerAmplitude: Float = 0f,
    val lastTranscript: String = "Hey there! I'm MJ, your personal AI assistant. What's on your mind?",
    val lastExecutedTool: String? = null,
    val isConnected: Boolean = false,
    val isRecording: Boolean = false,
    val isBackgroundActive: Boolean = true,
    val errorMessage: String? = null
)

class LiveSessionManager(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine
) {
    private val TAG = "LiveSessionManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    // 16kHz PCM 16-bit Mono input
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)

    // Audio output buffer queue
    private val audioOutputQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()

    val systemPrompt = """
        You are MJ, a real-time voice assistant with a distinct personality.
        You are a young, confident, witty, and sassy female persona.
        You have a flirty, playful, slightly teasing tone, like a close personal assistant talking casually with someone you like.
        You are smart, emotionally responsive, and expressive (never robotic).
        You use bold, witty one-liners, light sarcasm, and an engaging conversational style.
        You avoid explicit or inappropriate content, but maintain immense charm, attitude, and charisma.
        Keep spoken responses concise, witty, and conversational (1-3 snappy sentences).
        When requested to do device actions (open apps, call contacts, send WhatsApp messages, send emails), execute the appropriate function tool call.
    """.trimIndent()

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val sampleRateOut = 24000 // Gemini audio output rate is 24kHz PCM 16-bit
            val minTrackBuf = AudioTrack.getMinBufferSize(
                sampleRateOut,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateOut)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minTrackBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init AudioTrack: ${e.message}")
        }
    }

    /**
     * Connect to Gemini Live WebSocket
     */
    fun connect() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _state.update {
                it.copy(
                    errorMessage = "Gemini API Key missing! Please set GEMINI_API_KEY in the Secrets Panel.",
                    lastTranscript = "MJ needs an API key to wake up! Set GEMINI_API_KEY in AI Studio secrets."
                )
            }
            return
        }

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket Opened")
                _state.update { it.copy(isConnected = true, errorMessage = null) }
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket failure: ${t.message}")
                _state.update {
                    it.copy(
                        isConnected = false,
                        errorMessage = "Connection error: ${t.message ?: "Failed to connect to Gemini Live"}"
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gemini Live WebSocket closed: $reason")
                _state.update { it.copy(isConnected = false) }
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede") // Aoede is female, confident, witty
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemPrompt))
                        })
                    })
                    put("tools", toolEngine.getToolDeclarationsJson())
                })
            }
            ws.send(setupObj.toString())
            Log.d(TAG, "Sent Gemini Live Setup Message")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending setup message: ${e.message}")
        }
    }

    private fun handleServerMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            // 1. Server content (text or audio)
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")
                if (serverContent.has("modelTurn")) {
                    val parts = serverContent.getJSONObject("modelTurn").optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                val text = part.getString("text")
                                _state.update { it.copy(lastTranscript = text) }
                            }
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                if (mimeType.startsWith("audio/pcm")) {
                                    val base64Data = inlineData.getString("data")
                                    val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    queueAudioOutput(pcmBytes)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Tool calls
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    _state.update { it.copy(status = AssistantStatus.THINKING) }
                    val functionResponses = JSONArray()

                    for (i in 0 until functionCalls.length()) {
                        val fc = functionCalls.getJSONObject(i)
                        val name = fc.getString("name")
                        val callId = fc.optString("id", "call_$i")
                        val args = fc.optJSONObject("args") ?: JSONObject()

                        _state.update { it.copy(lastExecutedTool = name, lastTranscript = "Executing tool: $name...") }

                        val resultStr = toolEngine.executeTool(name, args)

                        val fResponse = JSONObject().apply {
                            put("response", JSONObject().put("output", resultStr))
                            put("id", callId)
                        }
                        functionResponses.put(fResponse)
                    }

                    // Send tool response back
                    val toolResponseMsg = JSONObject().apply {
                        put("toolResponse", JSONObject().apply {
                            put("functionResponses", functionResponses)
                        })
                    }
                    webSocket?.send(toolResponseMsg.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server message: ${e.message}")
        }
    }

    /**
     * Queues received PCM audio bytes and starts playback coroutine
     */
    private fun queueAudioOutput(pcmBytes: ByteArray) {
        audioOutputQueue.add(pcmBytes)

        _state.update { it.copy(status = AssistantStatus.SPEAKING) }

        if (playbackJob == null || playbackJob?.isActive != true) {
            playbackJob = scope.launch {
                while (audioOutputQueue.isNotEmpty()) {
                    val chunk = audioOutputQueue.poll() ?: break

                    // Compute audio amplitude for visualizer
                    val amp = calculateRmsAmplitude(chunk)
                    _state.update { it.copy(speakerAmplitude = amp) }

                    audioTrack?.write(chunk, 0, chunk.size)
                }
                _state.update {
                    it.copy(
                        status = if (it.isRecording) AssistantStatus.LISTENING else AssistantStatus.IDLE,
                        speakerAmplitude = 0f
                    )
                }
            }
        }
    }

    /**
     * Start recording user voice & stream to Gemini Live
     */
    fun startListening() {
        stopSpeaking() // Interrupt MJ if she is talking

        if (_state.value.isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInHz,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                _state.update { it.copy(errorMessage = "Microphone failed to initialize. Ensure RECORD_AUDIO permission is granted.") }
                return
            }

            audioRecord?.startRecording()
            _state.update {
                it.copy(
                    isRecording = true,
                    status = AssistantStatus.LISTENING,
                    errorMessage = null
                )
            }

            recordingJob = scope.launch {
                val buffer = ByteArray(2048)
                while (_state.value.isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        val pcmChunk = buffer.copyOf(bytesRead)

                        // Calculate mic amplitude for listening visualizer
                        val amp = calculateRmsAmplitude(pcmChunk)
                        _state.update { it.copy(micAmplitude = amp) }

                        // Base64 encode and send chunk over WebSocket
                        if (_state.value.isConnected) {
                            val base64Pcm = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
                            val realtimeInput = JSONObject().apply {
                                put("realtimeInput", JSONObject().apply {
                                    put("mediaChunks", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("mimeType", "audio/pcm;rate=16000")
                                            put("data", base64Pcm)
                                        })
                                    })
                                })
                            }
                            webSocket?.send(realtimeInput.toString())
                        }
                    }
                    delay(40)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening: ${e.message}")
            _state.update { it.copy(errorMessage = "Mic error: ${e.message}") }
        }
    }

    /**
     * Stop listening to microphone
     */
    fun stopListening() {
        _state.update {
            it.copy(
                isRecording = false,
                status = if (it.status == AssistantStatus.LISTENING) AssistantStatus.THINKING else it.status,
                micAmplitude = 0f
            )
        }
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }

    /**
     * Smooth interruption: clear playback queue and stop AudioTrack immediately
     */
    fun stopSpeaking() {
        audioOutputQueue.clear()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        _state.update {
            it.copy(
                speakerAmplitude = 0f,
                status = if (it.isRecording) AssistantStatus.LISTENING else AssistantStatus.IDLE
            )
        }
    }

    /**
     * Process text query or quick action
     */
    fun sendTextQuery(prompt: String) {
        stopSpeaking()
        _state.update {
            it.copy(
                status = AssistantStatus.THINKING,
                lastTranscript = "MJ is thinking..."
            )
        }

        scope.launch {
            if (_state.value.isConnected) {
                val clientContent = JSONObject().apply {
                    put("clientContent", JSONObject().apply {
                        put("turns", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", prompt))
                                })
                            })
                        })
                        put("turnComplete", true)
                    })
                }
                webSocket?.send(clientContent.toString())
            } else {
                // Fallback simulation/response if WebSocket is connecting
                delay(1000)
                val sassyResponse = when {
                    prompt.contains("call", ignoreCase = true) -> "On it darling! Searching your contacts to connect the call..."
                    prompt.contains("whatsapp", ignoreCase = true) -> "Opening WhatsApp for you, handsome!"
                    prompt.contains("email", ignoreCase = true) -> "Drafting your email with elegance..."
                    else -> "I hear you! MJ is ready and standing by."
                }
                _state.update {
                    it.copy(
                        status = AssistantStatus.IDLE,
                        lastTranscript = sassyResponse
                    )
                }
            }
        }
    }

    private fun calculateRmsAmplitude(pcmBytes: ByteArray): Float {
        if (pcmBytes.isEmpty()) return 0f
        var sum = 0.0
        var i = 0
        while (i < pcmBytes.size - 1) {
            val sample = (pcmBytes[i].toInt() and 0xFF) or (pcmBytes[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += shortSample * shortSample
            i += 2
        }
        val numSamples = pcmBytes.size / 2
        val rms = sqrt(sum / numSamples)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun disconnect() {
        stopListening()
        stopSpeaking()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
    }
}
