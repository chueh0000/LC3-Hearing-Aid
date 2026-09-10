#ifndef DSPFILTERS_H
#define DSPFILTERS_H

#include <atomic>
#include <vector>
#include <cmath>
#include <cstdint>

// --- Fast math approximations for real-time audio DSP ---
// IEEE 754 float bit tricks: ~5x faster than std::log/pow with
// sufficient accuracy for audio gain computation (~0.1 dB error).

inline float fastLog2(float x) {
    union { float f; int32_t i; } u = {x};
    return (float)(u.i - 1064866805) * 8.2629582881927490e-8f;
}

inline float fastPow2(float x) {
    union { float f; int32_t i; } u;
    u.i = (int32_t)(x * 12102203.0f + 1064866805.0f);
    return u.f;
}

// 20 * log10(x) via fast log2: 20/log2(10) ≈ 6.0206
inline float fastLinearToDb(float x) {
    return 6.0206f * fastLog2(x);
}

// 10^(x/20) via fast pow2: 1/(20*log10(2)) ≈ 0.16610
inline float fastDbToLinear(float db) {
    return fastPow2(db * 0.16609640474f);
}

class Biquad {
public:
    Biquad();
    void setSampleRate(float sampleRate);
    // Configure as a peaking EQ filter
    void setPeakingEQ(float centerFreq, float Q, float dbGain);
    float process(float input);

private:
    float mSampleRate = 48000.0f;
    float a0 = 1.0f, a1 = 0.0f, a2 = 0.0f;
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float z1 = 0.0f, z2 = 0.0f;
};

class DynamicRangeCompressor {
public:
    DynamicRangeCompressor();
    void setSampleRate(float sampleRate);
    void setParameters(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupGainDb);
    float process(float input);

private:
    float mSampleRate = 48000.0f;
    float mThresholdLinear = 1.0f;
    float mThresholdDb = 0.0f;     // Pre-computed: avoids per-sample log10
    float mRatio = 1.0f;
    float mGainFactor = 0.0f;      // Pre-computed: (1/ratio - 1)
    float mAttackCoeff = 0.0f;
    float mReleaseCoeff = 0.0f;
    float mMakeupGainLinear = 1.0f;
    float mEnv = 0.0f; // Envelope tracker
};

class DspPipeline {
public:
    DspPipeline();
    void setSampleRate(float sampleRate);
    void process(float* audioData, int numFrames, int numChannels);

    // Thread-safe atomic parameter updates
    void setEqGain(int bandIndex, float dbGain);
    void setSpeechBoost(float boostLevel); // 0.0 to 1.0
    void setInputGain(float dbGain);
    void setOutputGain(float dbGain);

private:
    float mSampleRate = 48000.0f;
    
    // 6-Band EQ: 250, 500, 1k, 2k, 4k, 8k Hz
    static constexpr int NUM_EQ_BANDS = 6;
    static constexpr float EQ_FREQUENCIES[NUM_EQ_BANDS] = {250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f};
    
    std::vector<Biquad> mEqBands;
    std::atomic<float> mEqGains[NUM_EQ_BANDS];

    DynamicRangeCompressor mCompressor;
    std::atomic<float> mSpeechBoostLevel;
    std::atomic<float> mInputGainDb;
    std::atomic<float> mOutputGainDb;
    
    void updateInternalParameters();
    
    // We keep track of the current values to know when to re-calculate biquad coefficients
    float mCurrentEqGains[NUM_EQ_BANDS];
    float mCurrentSpeechBoostLevel;
    
    // Target and Current Linear Gains for Parameter Smoothing
    float mTargetInputGainLinear = 1.0f;
    float mCurrentInputGainLinear = 1.0f;
    
    float mTargetOutputGainLinear = 1.0f;
    float mCurrentOutputGainLinear = 1.0f;
    
    // Lowpass filter coefficient for gain smoothing (e.g., 50ms time constant)
    float mGainSmoothingCoeff = 0.0f;
};

#endif // DSPFILTERS_H
