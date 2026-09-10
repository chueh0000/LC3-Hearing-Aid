#include "AudioEngine.h"
#include <android/log.h>
#include <unistd.h>
#include <algorithm>

#define TAG "HearingAid-AudioEngine"

AudioEngine::AudioEngine() {
    // Cushion of 1 burst balances low latency and glitch prevention for hearing aids.
    // This tells FullDuplexStream to keep a minimal buffer between input read/write
    // cursors to safely handle clock drift between mic and headset hardware.
    setNumInputBurstsCushion(1);
}

AudioEngine::~AudioEngine() {
    stopEngine();
}

void AudioEngine::setExclusiveMode(bool exclusive) {
    mExclusiveMode = exclusive;
}

bool AudioEngine::isExclusiveModeActive() {
    if (mPlaybackStream) {
        return mPlaybackStream->getSharingMode() == oboe::SharingMode::Exclusive;
    }
    return false;
}

bool AudioEngine::startEngine() {
    // --- 1. Open Output stream first (determines sample rate) ---
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(mExclusiveMode
                ? oboe::SharingMode::Exclusive   // Bypass system mixer — lowest latency
                : oboe::SharingMode::Shared)     // Allow background audio mixing
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setDataCallback(this);              // FullDuplexStream handles the output callback

    oboe::Result result = outBuilder.openStream(mPlaybackStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "Failed to open playback stream: %s", oboe::convertToText(result));
        return false;
    }

    // Log the actual sharing mode granted (Exclusive may fall back to Shared)
    bool gotExclusive = mPlaybackStream->getSharingMode() == oboe::SharingMode::Exclusive;
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Output stream opened — Sharing: %s, PerformanceMode: LowLatency",
        gotExclusive ? "Exclusive" : "Shared");

    // Dynamic Sample Rate Binding — use the output stream's native rate
    int32_t sampleRate = mPlaybackStream->getSampleRate();
    __android_log_print(ANDROID_LOG_INFO, TAG, "Sample rate: %d Hz", sampleRate);
    mDspPipeline.setSampleRate((float)sampleRate);

    // Buffer tuning: 2 × burst size is the theoretical minimum for stable double-buffering
    int32_t burstSize = mPlaybackStream->getFramesPerBurst();
    mPlaybackStream->setBufferSizeInFrames(burstSize * 2);
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Output buffer: %d frames (burst: %d, ratio: 2x)",
        mPlaybackStream->getBufferSizeInFrames(), burstSize);

    // 2. Open Input stream (NO callback — FullDuplexStream reads synchronously)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive) // Always try exclusive for mic
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setInputPreset(oboe::InputPreset::VoicePerformance)
            ->setSampleRate(sampleRate)
            // Oboe docs: set input capacity to 2× output to handle clock drift
            ->setBufferCapacityInFrames(mPlaybackStream->getBufferCapacityInFrames() * 2);

    result = inBuilder.openStream(mRecordingStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "Failed to open recording stream: %s", oboe::convertToText(result));
        mPlaybackStream->close();
        return false;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Input stream opened — Sharing: %s",
        mRecordingStream->getSharingMode() == oboe::SharingMode::Exclusive
            ? "Exclusive" : "Shared");

    // 3. Connect streams to FullDuplexStream (use shared_ptr API)
    setSharedOutputStream(mPlaybackStream);
    setSharedInputStream(mRecordingStream);

    // --- 4. Start both streams via FullDuplexStream ---
    result = FullDuplexStream::start();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "Failed to start full-duplex streams: %s", oboe::convertToText(result));
        mPlaybackStream->close();
        mRecordingStream->close();
        return false;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Full-duplex audio engine started successfully");
    return true;
}

void AudioEngine::stopEngine() {
    // Close ADPF session
    mAdpfHelper.close();
    mAdpfInitialized = false;

    // FullDuplexStream::stop() requests stop on both streams
    FullDuplexStream::stop();
    
    if (mRecordingStream) {
        mRecordingStream->close();
        mRecordingStream.reset();
    }
    if (mPlaybackStream) {
        mPlaybackStream->close();
        mPlaybackStream.reset();
    }
}

oboe::DataCallbackResult AudioEngine::onBothStreamsReady(
        const void *inputData, int numInputFrames,
        void *outputData, int numOutputFrames) {

    // Initialize ADPF on first callback — we need the audio thread's ID
    if (!mAdpfInitialized) {
        int32_t tid = gettid();
        int32_t sampleRate = getOutputStream()->getSampleRate();
        int64_t targetNs = (int64_t)numOutputFrames * 1000000000LL / sampleRate;
        mAdpfHelper.open(tid, targetNs);
        mAdpfInitialized = true;
        __android_log_print(ANDROID_LOG_INFO, TAG,
            "ADPF session opened — thread: %d, target: %lld ns", tid, (long long)targetNs);
    }

    auto startTime = std::chrono::steady_clock::now();

    const float *in = static_cast<const float *>(inputData);
    float *out = static_cast<float *>(outputData);

    int framesToProcess = std::min(numInputFrames, numOutputFrames);

    // Copy input directly to output buffer — no ring buffer, zero latency added
    for (int i = 0; i < framesToProcess; ++i) {
        out[i] = in[i];
    }

    // Zero-fill if output needs more frames than input provided (underflow protection)
    for (int i = framesToProcess; i < numOutputFrames; ++i) {
        out[i] = 0.0f;
    }

    // Process through DSP pipeline (in-place on output buffer)
    mDspPipeline.process(out, numOutputFrames, 1);

    // Report actual workload duration to ADPF for CPU frequency scaling
    auto endTime = std::chrono::steady_clock::now();
    int64_t durationNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
        endTime - startTime).count();
    mAdpfHelper.reportActualDuration(durationNs);

    return oboe::DataCallbackResult::Continue;
}
