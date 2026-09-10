package com.example.hearingaid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow

class AudioProcessingService : Service() {

    private val binder = LocalBinder()
    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // JNI Native methods
    private external fun startEngine(exclusive: Boolean): Boolean
    private external fun stopEngine()
    private external fun isExclusiveModeActive(): Boolean
    external fun setEqGain(bandIndex: Int, dbGain: Float)
    external fun setSpeechBoost(boostLevel: Float)
    external fun setInputGain(dbGain: Float)
    external fun setOutputGain(dbGain: Float)

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "HearingAidChannel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        
        // Expose service state to UI
        val isRunningFlow = MutableStateFlow(false)
        val isExclusiveModeActiveFlow = MutableStateFlow(false)
        
        init {
            System.loadLibrary("hearingaid")
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioProcessingService = this@AudioProcessingService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAudioProcessing()
            ACTION_STOP -> stopAudioProcessing()
        }
        return START_STICKY
    }

    private fun startAudioProcessing() {
        if (isRunning) return
        
        // Acquire partial wakelock to prevent CPU from sleeping
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HearingAid::AudioDspWakeLock")
        wakeLock?.acquire()

        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, 
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        val success = startEngine(settingsManager.getLowLatencyMode())
        if (success) {
            isRunning = true
            isRunningFlow.value = true
            isExclusiveModeActiveFlow.value = isExclusiveModeActive()
            pushSettingsToEngine()
            if (settingsManager.getDuckAudio()) {
                requestDucking()
            }
        } else {
            stopSelf()
        }
    }
    
    private fun pushSettingsToEngine() {
        setInputGain(settingsManager.getInputGain())
        setOutputGain(settingsManager.getOutputGain())
        setSpeechBoost(settingsManager.getSpeechBoost())
        for (i in 0 until 6) {
            setEqGain(i, settingsManager.getEqGain(i))
        }
    }

    private fun stopAudioProcessing() {
        
        stopEngine()
        isRunning = false
        isRunningFlow.value = false
        isExclusiveModeActiveFlow.value = false
        
        abandonDucking()

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setDuckAudio(duck: Boolean) {
        if (isRunning) {
            if (duck) {
                requestDucking()
            } else {
                abandonDucking()
            }
        }
    }

    fun setLowLatencyMode(enabled: Boolean) {
        if (isRunning) {
            // Changing sharing mode requires reopening streams — restart the engine
            stopEngine()
            val success = startEngine(enabled)
            if (success) {
                isExclusiveModeActiveFlow.value = isExclusiveModeActive()
                pushSettingsToEngine()
                if (settingsManager.getDuckAudio()) {
                    requestDucking()
                }
            }
        }
    }

    private fun requestDucking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonDucking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus({ })
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Hearing Aid Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AudioProcessingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Hearing Aid Active")
            .setContentText("Processing audio and streaming to LE Audio headset")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // placeholder icon
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        stopAudioProcessing()
        super.onDestroy()
    }
}
