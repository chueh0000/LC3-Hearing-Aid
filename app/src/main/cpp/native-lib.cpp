#include <jni.h>
#include <string>
#include "AudioEngine.h"

// Global instance of the audio engine
AudioEngine *engine = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_hearingaid_AudioProcessingService_startEngine(JNIEnv *env, jobject /* this */, jboolean exclusive) {
    if (engine == nullptr) {
        engine = new AudioEngine();
    }
    engine->setExclusiveMode(exclusive == JNI_TRUE);
    return engine->startEngine() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_hearingaid_AudioProcessingService_stopEngine(JNIEnv *env, jobject /* this */) {
    if (engine != nullptr) {
        engine->stopEngine();
        delete engine;
        engine = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_hearingaid_AudioProcessingService_isExclusiveModeActive(JNIEnv *env, jobject /* this */) {
    if (engine != nullptr) {
        return engine->isExclusiveModeActive() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}



extern "C" JNIEXPORT void JNICALL
Java_com_example_hearingaid_AudioProcessingService_setEqGain(JNIEnv *env, jobject /* this */, jint bandIndex, jfloat dbGain) {
    if (engine != nullptr) {
        engine->getDspPipeline().setEqGain(bandIndex, dbGain);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_hearingaid_AudioProcessingService_setSpeechBoost(JNIEnv *env, jobject /* this */, jfloat boostLevel) {
    if (engine != nullptr) {
        engine->getDspPipeline().setSpeechBoost(boostLevel);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_hearingaid_AudioProcessingService_setInputGain(JNIEnv *env, jobject /* this */, jfloat dbGain) {
    if (engine != nullptr) {
        engine->getDspPipeline().setInputGain(dbGain);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_hearingaid_AudioProcessingService_setOutputGain(JNIEnv *env, jobject /* this */, jfloat dbGain) {
    if (engine != nullptr) {
        engine->getDspPipeline().setOutputGain(dbGain);
    }
}
