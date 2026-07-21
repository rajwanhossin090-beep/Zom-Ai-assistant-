package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SendAndArchive
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.AssistantStatus
import com.example.live.AssistantState
import com.example.live.LiveSessionManager
import com.example.service.BackgroundAudioService
import com.example.ui.components.MjOrbVisualizer
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanLight
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow

data class ToolQuickAction(
    val title: String,
    val iconName: String,
    val prompt: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MJMainScreen(
    liveSessionManager: LiveSessionManager?,
    onOpenPermissionsOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fallbackFlow = remember { MutableStateFlow(AssistantState()) }
    val assistantState by (liveSessionManager?.state ?: fallbackFlow).collectAsState()
    val isServiceRunning by BackgroundAudioService.isRunning.collectAsState()

    var textInput by remember { mutableStateOf("") }

    val quickActions = remember {
        listOf(
            ToolQuickAction("YouTube", "youtube", "Open YouTube for me"),
            ToolQuickAction("Instagram", "instagram", "Launch Instagram"),
            ToolQuickAction("Calculator", "calc", "Open the Calculator app"),
            ToolQuickAction("Call Alex", "call", "Search contacts and call Alex"),
            ToolQuickAction("WhatsApp", "whatsapp", "Send WhatsApp message to Sarah saying Hey darling!"),
            ToolQuickAction("Send Email", "email", "Send email to john@example.com subject Meeting body Hey John let us catch up")
        )
    }

    fun toggleBackgroundService(enable: Boolean) {
        val intent = Intent(context, BackgroundAudioService::class.java).apply {
            action = if (enable) BackgroundAudioService.ACTION_START_SERVICE else BackgroundAudioService.ACTION_STOP_SERVICE
        }
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("mj_main_screen"),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, NeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_mj_avatar_1784653974274),
                                    contentDescription = "MJ Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(NeonCyan)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "MJ LIVE • V3.1",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = if (assistantState.isConnected) "Gemini 1.5 Live Connected" else "Connecting...",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenPermissionsOnboarding,
                        modifier = Modifier.testTag("permissions_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Permissions",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Pills Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator pill
                val (statusText, statusColor) = when (assistantState.status) {
                    AssistantStatus.IDLE -> "IDLE (Standing by)" to NeonCyanLight
                    AssistantStatus.LISTENING -> "LISTENING..." to GlowGreen
                    AssistantStatus.THINKING -> "THINKING..." to NeonMagenta
                    AssistantStatus.SPEAKING -> "SPEAKING..." to NeonViolet
                }

                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                // Background Service Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Background Service",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { toggleBackgroundService(it) },
                        modifier = Modifier.testTag("background_service_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = GlowGreen,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Central Animated Orb Visualizer with Atmospheric Glow
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Background atmospheric radial glow
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan.copy(alpha = 0.15f), Color.Transparent),
                            center = center,
                            radius = size.minDimension / 1.5f
                        ),
                        radius = size.minDimension / 1.5f,
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonViolet.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(center.x, center.y + 40f),
                            radius = size.minDimension / 1.8f
                        ),
                        radius = size.minDimension / 1.8f,
                        center = Offset(center.x, center.y + 40f)
                    )
                }

                MjOrbVisualizer(
                    status = assistantState.status,
                    micAmplitude = assistantState.micAmplitude,
                    speakerAmplitude = assistantState.speakerAmplitude,
                    modifier = Modifier.size(240.dp),
                    onOrbClick = {
                        if (assistantState.isRecording) {
                            liveSessionManager?.stopListening()
                        } else if (assistantState.status == AssistantStatus.SPEAKING) {
                            liveSessionManager?.stopSpeaking()
                        } else {
                            liveSessionManager?.startListening()
                        }
                    }
                )
            }

            // Error banner if any
            assistantState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonMagenta.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonMagenta)
                ) {
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Sassy MJ Speech / Transcript Response Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("mj_transcript_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.5f), NeonMagenta.copy(alpha = 0.5f)))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MJ's Persona Voice",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )

                        assistantState.lastExecutedTool?.let { tool ->
                            Box(
                                modifier = Modifier
                                    .background(NeonMagenta.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Tool: $tool",
                                    fontSize = 10.sp,
                                    color = NeonMagenta,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "\"${assistantState.lastTranscript}\"",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Tool Action Chips
            Text(
                text = "Quick Voice Command Tools",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickActions) { action ->
                    Card(
                        modifier = Modifier
                            .clickable {
                                liveSessionManager?.sendTextQuery(action.prompt)
                            }
                            .testTag("quick_action_${action.iconName}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = action.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Mic / Text Input Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Talk or type to MJ...", fontSize = 13.sp, color = TextSecondary) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("text_query_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    trailingIcon = {
                        if (textInput.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        liveSessionManager?.sendTextQuery(textInput)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier.testTag("send_text_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = NeonCyan
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(10.dp))

                FloatingActionButton(
                    onClick = {
                        if (assistantState.isRecording) {
                            liveSessionManager?.stopListening()
                        } else {
                            liveSessionManager?.startListening()
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("mic_toggle_fab"),
                    shape = CircleShape,
                    containerColor = if (assistantState.isRecording) NeonMagenta else NeonCyan
                ) {
                    Icon(
                        imageVector = if (assistantState.isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Microphone Toggle",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
