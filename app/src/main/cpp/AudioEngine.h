#ifndef AUDIOENGINE_H
#define AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <atomic>
#include <vector>
#include <chrono>
#include "DspFilters.h"

#if __ANDROID_API__ >= 33
#include <android/performance_hint.h>
#endif

// Lightweight ADPF helper — reports DSP workload duration so the OS
// keeps the audio thread on a performant CPU core at adequate frequency.
class AdpfHelper {
public:
    void open(int32_t threadId, int64_t targetDurationNanos) {
#if __ANDROID_API__ >= 33
        APerformanceHintManager* manager = APerformanceHint_getManager();
        if (manager) {
            mSession = APerformanceHint_createSession(
                manager, &threadId, 1, targetDurationNanos);
        }
#endif
    }

    void reportActualDuration(int64_t actualDurationNanos) {
#if __ANDROID_API__ >= 33
        if (mSession) {
            APerformanceHint_reportActualWorkDuration(mSession, actualDurationNanos);
        }
#endif
    }

    void close() {
#if __ANDROID_API__ >= 33
        if (mSession) {
            APerformanceHint_closeSession(mSession);
            mSession = nullptr;
        }
#endif
    }

    ~AdpfHelper() { close(); }

private:
#if __ANDROID_API__ >= 33
    APerformanceHintSession* mSession = nullptr;
#endif
};

class AudioEngine : public oboe::FullDuplexStream {
public:
    AudioEngine();
    ~AudioEngine();

    bool startEngine();
    void stopEngine();
    
    void setExclusiveMode(bool exclusive);
    bool isExclusiveModeActive();

    // oboe::FullDuplexStream override — called when both input and output
    // buffers are ready, with safe clock-drift synchronization handled by Oboe.
    oboe::DataCallbackResult onBothStreamsReady(
            const void *inputData, int numInputFrames,
            void *outputData, int numOutputFrames) override;

    // Direct access to the DSP pipeline
    DspPipeline& getDspPipeline() { return mDspPipeline; }

private:
    std::shared_ptr<oboe::AudioStream> mRecordingStream;
    std::shared_ptr<oboe::AudioStream> mPlaybackStream;
    
    DspPipeline mDspPipeline;
    
    bool mExclusiveMode = false;
    
    // ADPF integration for CPU performance hints
    AdpfHelper mAdpfHelper;
    bool mAdpfInitialized = false;
};

#endif // AUDIOENGINE_H
