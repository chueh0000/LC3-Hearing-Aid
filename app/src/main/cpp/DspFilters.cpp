#include "DspFilters.h"

// --- Biquad Implementation ---
Biquad::Biquad() {}

void Biquad::setSampleRate(float sampleRate) {
    mSampleRate = sampleRate;
}

void Biquad::setPeakingEQ(float centerFreq, float Q, float dbGain) {
    // Standard audio EQ biquad calculation (Cookbook formulae)
    float A = std::pow(10.0f, dbGain / 40.0f);
    float w0 = 2.0f * M_PI * centerFreq / mSampleRate;
    float alpha = std::sin(w0) / (2.0f * Q);

    float b0_raw = 1.0f + alpha * A;
    float b1_raw = -2.0f * std::cos(w0);
    float b2_raw = 1.0f - alpha * A;
    float a0_raw = 1.0f + alpha / A;
    float a1_raw = -2.0f * std::cos(w0);
    float a2_raw = 1.0f - alpha / A;

    // Normalize coefficients
    b0 = b0_raw / a0_raw;
    b1 = b1_raw / a0_raw;
    b2 = b2_raw / a0_raw;
    a1 = a1_raw / a0_raw;
    a2 = a2_raw / a0_raw;
}

// Use Direct Form II Transposed for better numerical stability.
float Biquad::process(float input) {
    float output = b0 * input + z1;
    z1 = b1 * input - a1 * output + z2;
    z2 = b2 * input - a2 * output;
    return output;
}

// --- DynamicRangeCompressor Implementation ---
DynamicRangeCompressor::DynamicRangeCompressor() {}

void DynamicRangeCompressor::setSampleRate(float sampleRate) {
    mSampleRate = sampleRate;
}

void DynamicRangeCompressor::setParameters(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupGainDb) {
    mThresholdLinear = std::pow(10.0f, thresholdDb / 20.0f);
    mThresholdDb = thresholdDb;                    // Pre-compute: eliminates per-sample log10
    mRatio = ratio;
    mGainFactor = (1.0f / ratio) - 1.0f;           // Pre-compute: eliminates per-sample division
    
    // Calculate attack and release coefficients based on sample rate
    // Time constants mapped to single pole filter coeff
    mAttackCoeff = std::exp(-1.0f / (attackMs * 0.001f * mSampleRate));
    mReleaseCoeff = std::exp(-1.0f / (releaseMs * 0.001f * mSampleRate));
    
    mMakeupGainLinear = std::pow(10.0f, makeupGainDb / 20.0f);
}

float DynamicRangeCompressor::process(float input) {
    // 1. Level Detection (Peak) — already efficient (multiply-add only)
    float absInput = std::abs(input);
    if (absInput > mEnv) {
        mEnv = mAttackCoeff * mEnv + (1.0f - mAttackCoeff) * absInput;
    } else {
        mEnv = mReleaseCoeff * mEnv + (1.0f - mReleaseCoeff) * absInput;
    }

    // 2. Gain Calculation — OPTIMIZED: fast math + pre-computed constants
    //    Previously: 2× std::log10 + 1× std::pow per sample (very expensive)
    //    Now: 1× fastLinearToDb + 1× fastDbToLinear (IEEE 754 bit tricks, ~5x faster)
    float gain = 1.0f;
    if (mEnv > mThresholdLinear) {
        float envDb = fastLinearToDb(std::max(mEnv, 1e-6f));
        float overDb = envDb - mThresholdDb;         // mThresholdDb pre-computed in setParameters()
        float gainReductionDb = overDb * mGainFactor; // mGainFactor pre-computed in setParameters()
        gain = fastDbToLinear(gainReductionDb);
    }

    // 3. Apply Gain and Makeup
    float output = input * gain * mMakeupGainLinear;
    
    // 4. Hard Clipper (Peak Limiter) to protect hearing
    if (output > 1.0f) output = 1.0f;
    if (output < -1.0f) output = -1.0f;
    
    return output;
}

// --- DspPipeline Implementation ---
DspPipeline::DspPipeline() : mEqBands(NUM_EQ_BANDS) {
    for (int i = 0; i < NUM_EQ_BANDS; ++i) {
        mEqGains[i].store(0.0f);
        mCurrentEqGains[i] = 0.0f;
        mEqBands[i].setPeakingEQ(EQ_FREQUENCIES[i], 1.414f, 0.0f);
    }
    mSpeechBoostLevel.store(0.0f);
    mCurrentSpeechBoostLevel = 0.0f;
    
    mInputGainDb.store(0.0f);
    mOutputGainDb.store(0.0f);
    
    mCompressor.setParameters(0.0f, 1.0f, 5.0f, 50.0f, 0.0f);
}

void DspPipeline::setSampleRate(float sampleRate) {
    mSampleRate = sampleRate;
    for (int i = 0; i < NUM_EQ_BANDS; ++i) {
        mEqBands[i].setSampleRate(sampleRate);
        mEqBands[i].setPeakingEQ(EQ_FREQUENCIES[i], 1.414f, mCurrentEqGains[i]);
    }
    mCompressor.setSampleRate(sampleRate);
    
    // Set parameter smoothing coefficient (e.g., 20ms time constant)
    mGainSmoothingCoeff = std::exp(-1.0f / (20.0f * 0.001f * mSampleRate));
}

void DspPipeline::setEqGain(int bandIndex, float dbGain) {
    if (bandIndex >= 0 && bandIndex < NUM_EQ_BANDS) {
        mEqGains[bandIndex].store(dbGain);
    }
}

void DspPipeline::setSpeechBoost(float boostLevel) {
    mSpeechBoostLevel.store(boostLevel);
}

void DspPipeline::setInputGain(float dbGain) {
    mInputGainDb.store(dbGain);
}

void DspPipeline::setOutputGain(float dbGain) {
    mOutputGainDb.store(dbGain);
}

void DspPipeline::updateInternalParameters() {
    // Check if UI changed EQ parameters
    for (int i = 0; i < NUM_EQ_BANDS; ++i) {
        float gain = mEqGains[i].load(std::memory_order_relaxed);
        if (gain != mCurrentEqGains[i]) {
            mCurrentEqGains[i] = gain;
            mEqBands[i].setPeakingEQ(EQ_FREQUENCIES[i], 1.414f, gain);
        }
    }

    // Check if UI changed DRC parameters (Speech Boost)
    float boost = mSpeechBoostLevel.load(std::memory_order_relaxed);
    if (boost != mCurrentSpeechBoostLevel) {
        mCurrentSpeechBoostLevel = boost;
        
        // Map Speech Boost (0.0 to 1.0) to Compressor Parameters
        // 0.0 = No compression (Threshold 0, Ratio 1:1, Makeup 0dB)
        // 1.0 = Max boost (Threshold -40dB, Ratio 4:1, Makeup 15dB)
        float thresholdDb = -40.0f * boost; 
        float ratio = 1.0f + 3.0f * boost;
        float makeupGainDb = 15.0f * boost;
        
        // Fast attack (5ms) for hearing aid to catch sudden loud sounds
        // Moderate release (50ms) to avoid pumping but recover quickly
        mCompressor.setParameters(thresholdDb, ratio, 5.0f, 50.0f, makeupGainDb);
    }
    
    // Update Master Gains
    float inGainDb = mInputGainDb.load(std::memory_order_relaxed);
    mTargetInputGainLinear = std::pow(10.0f, inGainDb / 20.0f);
    
    float outGainDb = mOutputGainDb.load(std::memory_order_relaxed);
    mTargetOutputGainLinear = std::pow(10.0f, outGainDb / 20.0f);
}

void DspPipeline::process(float* audioData, int numFrames, int numChannels) {
    // 1. Update params atomically
    updateInternalParameters();
    
    // 2. Process samples
    for (int i = 0; i < numFrames * numChannels; ++i) {
        // Smooth gains
        mCurrentInputGainLinear = mGainSmoothingCoeff * mCurrentInputGainLinear + (1.0f - mGainSmoothingCoeff) * mTargetInputGainLinear;
        mCurrentOutputGainLinear = mGainSmoothingCoeff * mCurrentOutputGainLinear + (1.0f - mGainSmoothingCoeff) * mTargetOutputGainLinear;
        
        float sample = audioData[i];
        
        // Apply Input Gain (Drive)
        sample *= mCurrentInputGainLinear;
        
        // Apply EQ bands in series
        for (int band = 0; band < NUM_EQ_BANDS; ++band) {
            sample = mEqBands[band].process(sample);
        }
        
        // Apply DRC
        sample = mCompressor.process(sample);
        
        // Apply Output Gain
        sample *= mCurrentOutputGainLinear;
        
        audioData[i] = sample;
    }
}
