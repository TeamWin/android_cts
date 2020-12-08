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

#include "OboePlayer.h"
#include "WaveTableSource.h"

#include "AudioSource.h"

static const char * const TAG = "OboePlayer(native)";

using namespace oboe;

constexpr int32_t kBufferSizeInBursts = 2; // Use 2 bursts as the buffer size (double buffer)

OboePlayer::OboePlayer(AudioSource* source, int subtype)
 : Player(source, subtype)
{}

DataCallbackResult OboePlayer::onAudioReady(AudioStream *oboeStream, void *audioData,
                                            int32_t numFrames) {
    StreamState streamState = oboeStream->getState();
    if (streamState != StreamState::Open && streamState != StreamState::Started) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "  streamState:%d", streamState);
    }
    if (streamState == StreamState::Disconnected) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "  streamState::Disconnected");
    }

    // memset(audioData, 0, numFrames * mChannelCount * sizeof(float));

    // Pull the data here!
    int numFramesRead = mAudioSource->pull((float*)audioData, numFrames, mChannelCount);
    // may need to handle 0-filling if numFramesRead < numFrames

    return numFramesRead != 0 ? DataCallbackResult::Continue : DataCallbackResult::Stop;
}

void OboePlayer::onErrorAfterClose(AudioStream *oboeStream, Result error) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "==== onErrorAfterClose() error:%d", error);

    startStream();
}

void OboePlayer::onErrorBeforeClose(AudioStream *, Result error) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "==== onErrorBeforeClose() error:%d", error);
}

//TODO move code that is common to OboeRecorder/OboePlayer into OboeStream
bool OboePlayer::setupStream(int32_t channelCount, int32_t sampleRate, int32_t routeDeviceId) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "setupStream()");

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
        builder.setPerformanceMode(PerformanceMode::LowLatency);
        builder.setSharingMode(SharingMode::Exclusive);
        builder.setDirection(Direction::Output);
        switch (mSubtype) {
        case SUB_TYPE_OBOE_AAUDIO:
            builder.setAudioApi(AudioApi::AAudio);
            break;

        case SUB_TYPE_OBOE_OPENSL_ES:
            builder.setAudioApi(AudioApi::OpenSLES);
            break;

        default:
            success = false;
            goto lbl_exit;
        }

        if (mRouteDeviceId != ROUTING_DEVICE_NONE) {
            builder.setDeviceId(mRouteDeviceId);
        }

        mAudioSource->init(getNumBufferFrames() , mChannelCount);

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
        result = mAudioStream->setBufferSizeInFrames(
                mAudioStream->getFramesPerBurst() * kBufferSizeInBursts);
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

void OboePlayer::teardownStream() {
    __android_log_print(ANDROID_LOG_INFO, TAG, "teardownStream()");

    std::lock_guard<std::mutex> lock(mStreamLock);
    teardownStream_l();
}

void OboePlayer::teardownStream_l() {
    // tear down the player
    if (mAudioStream != nullptr) {
        mAudioStream->stop();
        mAudioStream->close();
        mAudioStream = nullptr;
    }
}

bool OboePlayer::startStream() {
    __android_log_print(ANDROID_LOG_INFO, TAG, "startStream()");

    // Don't cover up (potential) bugs in AAudio
    oboe::OboeGlobals::setWorkaroundsEnabled(false);

    std::lock_guard<std::mutex> lock(mStreamLock);

    Result result = Result::ErrorInternal;
    if (mAudioStream != nullptr) {
        result = mAudioStream->requestStart();
        if (result != Result::OK){
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    TAG,
                    "requestStart failed. Error: %s", convertToText(result));

            // clean up
            teardownStream_l();

            goto lbl_exit;
        }

        mStreamStarted = result == Result::OK;
    }

lbl_exit:
    return mStreamStarted;
}

void OboePlayer::stopStream() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    if (mAudioStream != nullptr) {
        mAudioStream->stop();
    }
    mStreamStarted = false;
}

//
// JNI functions
//
#include <jni.h>

#include <android/log.h>

extern "C" {
JNIEXPORT JNICALL jlong
Java_org_hyphonate_megaaudio_player_OboePlayer_allocNativePlayer(
    JNIEnv *env, jobject thiz, jlong native_audio_source, jint playerSubtype) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "teardownStream()");

    return (jlong)new OboePlayer((AudioSource*)native_audio_source, playerSubtype);
}

JNIEXPORT jboolean JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_setupStreamN(
        JNIEnv *env, jobject thiz, jlong native_player,
        jint channel_count, jint sample_rate, jint routeDeviceId) {
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Java_org_hyphonate_megaaudio_playerOboePlayer_startStreamN()");

    OboePlayer* player = (OboePlayer*)native_player;
    return player->setupStream(channel_count, sample_rate, routeDeviceId);
}

JNIEXPORT void JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_teardownStreamN(
        JNIEnv *env, jobject thiz, jlong native_player) {
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Java_org_hyphonate_megaaudio_player_OboePlayer_teardownStreamN()");

}

JNIEXPORT JNICALL jboolean Java_org_hyphonate_megaaudio_player_OboePlayer_startStreamN(
        JNIEnv *env, jobject thiz, jlong native_player, jint playerSubtype) {
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Java_org_hyphonate_megaaudio_playerOboePlayer_startStreamN()");

    return ((OboePlayer*)(native_player))->startStream();
}

JNIEXPORT JNICALL jboolean
Java_org_hyphonate_megaaudio_player_OboePlayer_stopN(JNIEnv *env, jobject thiz, jlong native_player) {
    ((OboePlayer*)(native_player))->teardownStream();
    return true;
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_player_OboePlayer_getBufferFrameCountN(JNIEnv *env, jobject thiz,  jlong native_player) {
    return ((OboePlayer*)(native_player))->getNumBufferFrames();
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_getRoutedDeviceIdN(JNIEnv *env, jobject thiz, jlong native_player) {
    return ((OboePlayer*)(native_player))->getRoutedDeviceId();
}

} // extern "C"
