/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <android/log.h>

#include "OboeRecorder.h"

#include "AudioSink.h"

static const char * const TAG = "OboeRecorder(native)";

using namespace oboe;

constexpr int32_t kBufferSizeInBursts = 2; // Use 2 bursts as the buffer size (double buffer)

OboeRecorder::OboeRecorder(AudioSink* sink, int32_t subtype)
        : Recorder(sink, subtype),
          mInputPreset(-1)
{}

//
// State
//
//TODO move code that is common to OboeRecorder/OboePlayer into OboeStream
bool OboeRecorder::setupStream(int32_t channelCount, int32_t sampleRate, int32_t routeDeviceId)
{
    std::lock_guard<std::mutex> lock(mStreamLock);

    bool success = false;
    if (mAudioStream == nullptr) {
        Result result = Result::ErrorInternal;

        mChannelCount = channelCount;
        mSampleRate = sampleRate;
        mRouteDeviceId = routeDeviceId;

        // Create an audio stream
        AudioStreamBuilder builder;
        builder.setChannelCount(mChannelCount);
        builder.setSampleRate(mSampleRate);
        builder.setCallback(this);
        if (mInputPreset != -1) {
            builder.setInputPreset((enum InputPreset)mInputPreset);
        }
        builder.setPerformanceMode(PerformanceMode::LowLatency);
        // builder.setPerformanceMode(PerformanceMode::None);
        builder.setSharingMode(SharingMode::Exclusive);
        builder.setSampleRateConversionQuality(SampleRateConversionQuality::Medium);
        builder.setDirection(Direction::Input);

        if (mRouteDeviceId != -1) {
            builder.setDeviceId(mRouteDeviceId);
        }

        if (mSubtype == SUB_TYPE_OBOE_AAUDIO) {
            builder.setAudioApi(AudioApi::AAudio);
        } else if (mSubtype == SUB_TYPE_OBOE_OPENSL_ES) {
            builder.setAudioApi(AudioApi::OpenSLES);
        }

        // Result result = builder.openManagedStream(mAudioStream);
        result = builder.openStream(mAudioStream);
        if (result != Result::OK){
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    TAG,
                    "openStream failed. Error: %s", convertToText(result));
            goto lbl_exit;
        }

        success = true;

        // Reduce stream latency by setting the buffer size to a multiple of the burst size
        // Note: this will fail with ErrorUnimplemented if we are using a callback with OpenSL ES
        // See oboe::AudioStreamBuffered::setBufferSizeInFrames
        // This doesn't affect the success of opening the stream.
        mBufferSizeInFrames = mAudioStream->getFramesPerBurst()/* * kBufferSizeInBursts*/;
        result = mAudioStream->setBufferSizeInFrames(mBufferSizeInFrames);
        if (result != Result::OK) {
            __android_log_print(
                    ANDROID_LOG_WARN,
                    TAG,
                    "setBufferSizeInFrames failed. Error: %s", convertToText(result));
        }
    }

lbl_exit:
    return success;
}

void OboeRecorder::teardownStream() {
    __android_log_print(ANDROID_LOG_INFO, TAG, "teardownStream()");

    std::lock_guard<std::mutex> lock(mStreamLock);
    teardownStream_l();
}

void OboeRecorder::teardownStream_l() {
    // tear down the player
    if (mAudioStream != nullptr) {
        mAudioStream->stop();
        mAudioStream->close();
        mAudioStream = nullptr;
    }
}

bool OboeRecorder::startStream() {
    __android_log_print(ANDROID_LOG_INFO, TAG, "startStream()");

    // Don't cover up (potential) bugs in AAudio
    oboe::OboeGlobals::setWorkaroundsEnabled(false);

    std::lock_guard<std::mutex> lock(mStreamLock);
    Result result = Result::ErrorInternal;
    if (mAudioStream != nullptr) {
        mAudioSink->init(mBufferSizeInFrames, mChannelCount);

        result = mAudioStream->requestStart();
        if (result != Result::OK){
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    TAG,
                    "requestStart failed. Error: %s", convertToText(result));

            teardownStream_l();
            goto lbl_exit;
        }

        mAudioSink->start();

        mStreamStarted = true;
    }

lbl_exit:
    return result == Result::OK;
}

void OboeRecorder::stopStream() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    if (mAudioStream != nullptr) {
        mAudioStream->stop();
        mAudioSink->stop();
        mStreamStarted = false;
    }
}

oboe::DataCallbackResult OboeRecorder::onAudioReady(
        oboe::AudioStream *audioStream, void *audioData, int numFrames) {
    mAudioSink->push((float*)audioData, numFrames, mChannelCount);
    return oboe::DataCallbackResult::Continue;
}

#include <jni.h>

extern "C" {
JNIEXPORT jlong JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_allocNativeRecorder(JNIEnv *env, jobject thiz, jlong native_audio_sink, jint recorderSubtype) {
    OboeRecorder* recorder = new OboeRecorder((AudioSink*)native_audio_sink, recorderSubtype);
    return (jlong)recorder;
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getBufferFrameCountN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->getNumBufferFrames();
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_setInputPresetN(JNIEnv *env, jobject thiz,
                                                                   jlong native_recorder,
                                                                   jint input_preset) {
    ((OboeRecorder*)native_recorder)->setInputPreset(input_preset);
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_setupStreamN(JNIEnv *env, jobject thiz,
                                                                   jlong native_recorder,
                                                                   jint channel_count,
                                                                   jint sample_rate,
                                                                   jint route_device_id) {
    return ((OboeRecorder*)native_recorder)->setupStream(channel_count, sample_rate, route_device_id);
}

JNIEXPORT void JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_teardownStreamN(
    JNIEnv *env, jobject thiz, jlong native_recorder) {
    ((OboeRecorder*)native_recorder)->teardownStream();
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_startStreamN(JNIEnv *env, jobject thiz,
                                                              jlong native_recorder,
                                                              jint recorder_subtype) {
    return ((OboeRecorder*)native_recorder)->startStream();
}

JNIEXPORT jboolean JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_stopN(JNIEnv *env, jobject thiz,
                                                       jlong native_recorder) {
    ((OboeRecorder*)native_recorder)->stopStream();
    return true;
}

//JNIEXPORT void JNICALL
//Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getDataBufferN(JNIEnv *env, jobject thiz,
//                                                                jlong native_recorder,
//                                                                jfloatArray buffer) {
//    // TODO: implement getDataBuffer()
//    // __android_log_print(ANDROID_LOG_INFO, TAG,"getDataBuffer");
//
//    OboeRecorder* nativeRecorder = ((OboeRecorder*)native_recorder);
//
//    // in progress...
////    float* dataBuffer = nativeRecorder->GetRecordBuffer();
////    if (dataBuffer != 0) {
////        int numBufferSamples = sNativeRecorder->GetNumBufferSamples();
////        jEnv->SetFloatArrayRegion(j_data, 0, numBufferSamples, dataBuffer);
////        __android_log_print(ANDROID_LOG_INFO, TAG, "AudioRecorder_GetBufferData() ...");
////    }
//}

JNIEXPORT jboolean JNICALL
        Java_org_hyphonate_megaaudio_recorder_OboeRecorder_isRecordingN(
                JNIEnv *env, jobject thiz, jlong native_recorder) {
    OboeRecorder* nativeRecorder = ((OboeRecorder*)native_recorder);
    return nativeRecorder->isRecording();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getNumBufferFramesN(
        JNIEnv *env, jobject thiz, jlong native_recorder) {
    OboeRecorder* nativeRecorder = ((OboeRecorder*)native_recorder);
    return nativeRecorder->getNumBufferFrames();
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_recorder_OboeRecorder_getRoutedDeviceIdN(JNIEnv *env, jobject thiz, jlong native_recorder) {
    return ((OboeRecorder*)native_recorder)->getRoutedDeviceId();
}

}   // extern "C"
