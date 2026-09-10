package com.example.hearingaid

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

class HardwareValidator(private val context: Context) {

    fun isLeAudioActive(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        
        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET || 
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                return true
            }
        }
        return false
    }
}
