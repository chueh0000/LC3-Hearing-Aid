package com.example.hearingaid

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "HearingAidSettings"

        private const val KEY_INPUT_GAIN = "input_gain"
        private const val KEY_OUTPUT_GAIN = "output_gain"
        private const val KEY_SPEECH_BOOST = "speech_boost"
        private const val KEY_DUCK_AUDIO = "duck_audio"
        private const val KEY_LOW_LATENCY_MODE = "low_latency_mode"

        // Flattened EQ Band Keys
        private val KEY_EQ_BANDS = arrayOf(
            "eq_250",
            "eq_500",
            "eq_1k",
            "eq_2k",
            "eq_4k",
            "eq_8k"
        )
    }

    // --- Getters ---
    
    fun getInputGain(): Float = prefs.getFloat(KEY_INPUT_GAIN, 0.0f)
    
    fun getOutputGain(): Float = prefs.getFloat(KEY_OUTPUT_GAIN, 0.0f)
    
    fun getSpeechBoost(): Float = prefs.getFloat(KEY_SPEECH_BOOST, 0.0f)
    
    fun getDuckAudio(): Boolean = prefs.getBoolean(KEY_DUCK_AUDIO, false)
    
    fun getLowLatencyMode(): Boolean = prefs.getBoolean(KEY_LOW_LATENCY_MODE, false)
    
    fun getEqGain(bandIndex: Int): Float {
        if (bandIndex in KEY_EQ_BANDS.indices) {
            return prefs.getFloat(KEY_EQ_BANDS[bandIndex], 0.0f)
        }
        return 0.0f
    }

    // --- Setters (Asynchronous using apply()) ---
    
    fun setInputGain(gain: Float) {
        prefs.edit().putFloat(KEY_INPUT_GAIN, gain).apply()
    }

    fun setOutputGain(gain: Float) {
        prefs.edit().putFloat(KEY_OUTPUT_GAIN, gain).apply()
    }

    fun setSpeechBoost(boost: Float) {
        prefs.edit().putFloat(KEY_SPEECH_BOOST, boost).apply()
    }

    fun setDuckAudio(duck: Boolean) {
        prefs.edit().putBoolean(KEY_DUCK_AUDIO, duck).apply()
    }

    fun setLowLatencyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_LATENCY_MODE, enabled).apply()
    }

    fun setEqGain(bandIndex: Int, gain: Float) {
        if (bandIndex in KEY_EQ_BANDS.indices) {
            prefs.edit().putFloat(KEY_EQ_BANDS[bandIndex], gain).apply()
        }
    }

    // --- Reset ---
    
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
