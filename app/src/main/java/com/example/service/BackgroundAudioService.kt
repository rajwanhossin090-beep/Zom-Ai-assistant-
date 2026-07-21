package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.live.LiveSessionManager
import com.example.tools.ToolExecutionEngine
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Foreground Service that keeps MJ Voice Assistant alive in the background
 * and monitors for local wake-word energy / "MJ" speech trigger.
 */
class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "MJ_VOICE_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_WAKE_MJ = "ACTION_WAKE_MJ"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _wakeWordTriggered = MutableStateFlow(false)
        val wakeWordTriggered: StateFlow<Boolean> = _wakeWordTriggered.asStateFlow()

        fun resetWakeWordTrigger() {
            _wakeWordTriggered.value = false
        }
    }

    private val TAG = "BackgroundAudioService"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val binder = LocalBinder()

    var liveSessionManager: LiveSessionManager? = null
        private set

    private var wakeWordRecordingJob: Job? = null
    private var isWakeWordListening = false

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundAudioService = this@BackgroundAudioService
    }

    override fun onCreate() {
        super.onCreate()
        val toolEngine = ToolExecutionEngine(applicationContext)
        liveSessionManager = LiveSessionManager(applicationContext, toolEngine)
        liveSessionManager?.connect()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_WAKE_MJ -> {
                _wakeWordTriggered.value = true
                liveSessionManager?.startListening()
            }
            else -> {
                startForegroundServiceWithNotification()
                startLocalWakeWordDetector()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        _isRunning.value = true

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MJ Assistant Active")
            .setContentText("Listening for 'MJ' in background • Double tap to open")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Lightweight local wake-word/speech detector on AudioRecord.
     * When speech energy threshold is breached in background, triggers MJ awakening!
     */
    private fun startLocalWakeWordDetector() {
        if (isWakeWordListening) return
        isWakeWordListening = true

        wakeWordRecordingJob = scope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            var localRecord: AudioRecord? = null
            try {
                localRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBuf * 2
                )

                if (localRecord.state == AudioRecord.STATE_INITIALIZED) {
                    localRecord.startRecording()
                    val buffer = ShortArray(1024)

                    while (isWakeWordListening) {
                        // Only listen for wake word if live session isn't already actively streaming voice
                        if (liveSessionManager?.state?.value?.isRecording != true) {
                            val read = localRecord.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                var sum = 0.0
                                for (i in 0 until read) {
                                    sum += buffer[i] * buffer[i]
                                }
                                val rms = sqrt(sum / read)
                                val amplitude = rms / 32768.0

                                // Energy spike threshold corresponding to user saying "MJ!"
                                if (amplitude > 0.15) {
                                    Log.d(TAG, "Wake word / Speech energy spike detected! Triggering MJ...")
                                    _wakeWordTriggered.value = true
                                    liveSessionManager?.startListening()
                                    delay(3000) // Cooldown to avoid duplicate triggers
                                }
                            }
                        }
                        delay(50)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wake-word detector error: ${e.message}")
            } finally {
                try {
                    localRecord?.stop()
                    localRecord?.release()
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun stopForegroundService() {
        isWakeWordListening = false
        wakeWordRecordingJob?.cancel()
        liveSessionManager?.disconnect()
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MJ Voice Assistant Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps MJ Assistant active for voice commands and device tools"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {

        isWakeWordListening = false
        wakeWordRecordingJob?.cancel()
        liveSessionManager?.disconnect()
        _isRunning.value = false
        super.onDestroy()
    }
}
