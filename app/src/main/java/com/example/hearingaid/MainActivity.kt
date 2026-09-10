package com.example.hearingaid

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.hearingaid.ui.HearingAidScreen

class MainActivity : ComponentActivity() {

    private var audioService: AudioProcessingService? = null
    private var isBound by mutableStateOf(false)
    private var hasPermissions by mutableStateOf(false)
    
    private lateinit var settingsManager: SettingsManager
    private val validator by lazy { HardwareValidator(this) }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioProcessingService.LocalBinder
            audioService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            audioService = null
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.entries.all { it.value }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        checkPermissions()
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLeAudioReady = validator.isLeAudioActive()
                    val isEngineRunning by AudioProcessingService.isRunningFlow.collectAsState()
                    val isExclusiveModeActive by AudioProcessingService.isExclusiveModeActiveFlow.collectAsState()
                    
                    val initialEqGains = FloatArray(6) { i -> settingsManager.getEqGain(i) }
                    
                    HearingAidScreen(
                        isEngineRunning = isEngineRunning,
                        isExclusiveModeActive = isExclusiveModeActive,
                        hasPermissions = hasPermissions,
                        isLeAudioActive = isLeAudioReady,
                        initialBoostLevel = settingsManager.getSpeechBoost(),
                        initialInputGain = settingsManager.getInputGain(),
                        initialOutputGain = settingsManager.getOutputGain(),
                        initialDuckAudio = settingsManager.getDuckAudio(),
                        initialLowLatencyMode = settingsManager.getLowLatencyMode(),
                        initialEqGains = initialEqGains,
                        onToggleEngine = {
                            if (AudioProcessingService.isRunningFlow.value) {
                                stopAudioService()
                            } else {
                                startAudioService()
                            }
                        },
                        onEqChanged = { bandIndex, gain ->
                            audioService?.setEqGain(bandIndex, gain)
                        },
                        onEqChangedFinished = { bandIndex, gain ->
                            settingsManager.setEqGain(bandIndex, gain)
                        },
                        onBoostChanged = { boostLevel ->
                            audioService?.setSpeechBoost(boostLevel)
                        },
                        onBoostChangedFinished = { boostLevel -> 
                            settingsManager.setSpeechBoost(boostLevel)
                        },
                        onInputGainChanged = { gain ->
                            audioService?.setInputGain(gain)
                        },
                        onInputGainChangedFinished = { gain -> 
                            settingsManager.setInputGain(gain)
                        },
                        onOutputGainChanged = { gain ->
                            audioService?.setOutputGain(gain)
                        },
                        onOutputGainChangedFinished = { gain -> 
                            settingsManager.setOutputGain(gain)
                        },
                        onDuckAudioChanged = { duck ->
                            settingsManager.setDuckAudio(duck)
                            audioService?.setDuckAudio(duck)
                        },
                        onLowLatencyModeChanged = { enabled ->
                            settingsManager.setLowLatencyMode(enabled)
                            audioService?.setLowLatencyMode(enabled)
                        },
                        onResetDefaults = {
                            settingsManager.resetToDefaults()
                            audioService?.setInputGain(0f)
                            audioService?.setOutputGain(0f)
                            audioService?.setSpeechBoost(0f)
                            audioService?.setDuckAudio(false)
                            for (i in 0 until 6) {
                                audioService?.setEqGain(i, 0f)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioProcessingService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            hasPermissions = true
        } else {
            requestPermissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioProcessingService::class.java).apply {
            action = AudioProcessingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopAudioService() {
        val intent = Intent(this, AudioProcessingService::class.java).apply {
            action = AudioProcessingService.ACTION_STOP
        }
        startService(intent)
    }
}
