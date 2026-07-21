package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.live.LiveSessionManager
import com.example.service.BackgroundAudioService
import com.example.tools.ToolExecutionEngine
import com.example.ui.screens.MJMainScreen
import com.example.ui.screens.PermissionsOnboardingScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var backgroundService: BackgroundAudioService? = null
    private var isBound = false
    private var localLiveSessionManager: LiveSessionManager? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BackgroundAudioService.LocalBinder
            backgroundService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            backgroundService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create fallback LiveSessionManager
        val toolEngine = ToolExecutionEngine(applicationContext)
        localLiveSessionManager = LiveSessionManager(applicationContext, toolEngine)
        localLiveSessionManager?.connect()

        // Auto-start foreground service if RECORD_AUDIO granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioService()
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    var showOnboarding by remember {
                        mutableStateOf(!hasCorePermissions())
                    }

                    DisposableEffect(Unit) {
                        val intent = Intent(this@MainActivity, BackgroundAudioService::class.java)
                        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                        onDispose {
                            if (isBound) {
                                unbindService(serviceConnection)
                                isBound = false
                            }
                        }
                    }

                    val activeManager = backgroundService?.liveSessionManager ?: localLiveSessionManager

                    if (showOnboarding) {
                        PermissionsOnboardingScreen(
                            onContinue = {
                                showOnboarding = false
                                startAudioService()
                            }
                        )
                    } else {
                        MJMainScreen(
                            liveSessionManager = activeManager,
                            onOpenPermissionsOnboarding = {
                                showOnboarding = true
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, BackgroundAudioService::class.java).apply {
            action = BackgroundAudioService.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hasCorePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        localLiveSessionManager?.disconnect()
        super.onDestroy()
    }
}
