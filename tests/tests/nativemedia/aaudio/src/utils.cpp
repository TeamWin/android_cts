/*
 * Copyright 2017 The Android Open Source Project
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

#define LOG_TAG "AAudioTest"

#include <sys/types.h>
#include <unistd.h>

#include <android/log.h>
#include <gtest/gtest.h>

#include "test_aaudio.h"
#include "utils.h"

int64_t getNanoseconds(clockid_t clockId) {
    struct timespec time;
    int result = clock_gettime(clockId, &time);
    if (result < 0) {
        return -errno;
    }
    return (time.tv_sec * NANOS_PER_SECOND) + time.tv_nsec;
}

const char* sharingModeToString(aaudio_sharing_mode_t mode) {
    switch (mode) {
        case AAUDIO_SHARING_MODE_SHARED: return "SHARED";
        case AAUDIO_SHARING_MODE_EXCLUSIVE: return "EXCLUSIVE";
    }
    return "UNKNOWN";
}


StreamBuilderHelper::StreamBuilderHelper(
        aaudio_direction_t direction, int32_t sampleRate,
        int32_t samplesPerFrame, aaudio_audio_format_t dataFormat,
        aaudio_sharing_mode_t sharingMode)
        : mDirection{direction},
          mRequested{sampleRate, samplesPerFrame, dataFormat, sharingMode},
          mActual{0, 0, AAUDIO_FORMAT_INVALID, -1}, mFramesPerBurst{-1},
          mBuilder{nullptr}, mStream{nullptr} {}

StreamBuilderHelper::~StreamBuilderHelper() {
    close();
}

void StreamBuilderHelper::initBuilder() {
    // Use an AAudioStreamBuilder to define the stream.
    aaudio_result_t result = AAudio_createStreamBuilder(&mBuilder);
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_TRUE(mBuilder != nullptr);

    // Request stream properties.
    AAudioStreamBuilder_setDeviceId(mBuilder, AAUDIO_DEVICE_UNSPECIFIED);
    AAudioStreamBuilder_setDirection(mBuilder, mDirection);
    AAudioStreamBuilder_setSampleRate(mBuilder, mRequested.sampleRate);
    AAudioStreamBuilder_setSamplesPerFrame(mBuilder, mRequested.samplesPerFrame);
    AAudioStreamBuilder_setFormat(mBuilder, mRequested.dataFormat);
    AAudioStreamBuilder_setSharingMode(mBuilder, mRequested.sharingMode);
}

// Needs to be a 'void' function due to ASSERT requirements.
void StreamBuilderHelper::createAndVerifyStream(bool *success) {
    *success = false;

    aaudio_result_t result = AAudioStreamBuilder_openStream(mBuilder, &mStream);
    if (mRequested.sharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE && result != AAUDIO_OK) {
        return;
    }
    ASSERT_EQ(AAUDIO_OK, result);
    ASSERT_TRUE(mStream != nullptr);
    ASSERT_EQ(AAUDIO_STREAM_STATE_OPEN, AAudioStream_getState(mStream));
    ASSERT_EQ(mDirection, AAudioStream_getDirection(mStream));

    // Check to see what kind of stream we actually got.
    mActual.sampleRate = AAudioStream_getSampleRate(mStream);
    ASSERT_GE(mActual.sampleRate, 44100);
    ASSERT_LE(mActual.sampleRate, 96000); // TODO what is min/max?

    mActual.samplesPerFrame = AAudioStream_getSamplesPerFrame(mStream);
    ASSERT_GE(mActual.samplesPerFrame, 1);
    ASSERT_LE(mActual.samplesPerFrame, 16); // TODO what is min/max?

    mActual.dataFormat = AAudioStream_getFormat(mStream);
    ASSERT_EQ(AAUDIO_FORMAT_PCM_I16, mActual.dataFormat);

    mFramesPerBurst = AAudioStream_getFramesPerBurst(mStream);
    ASSERT_GE(mFramesPerBurst, 16);
    ASSERT_LE(mFramesPerBurst, 3072); // on some devices, it can be 2052

    int32_t actualBufferSize = AAudioStream_getBufferSizeInFrames(mStream);
    ASSERT_GT(actualBufferSize, 0);
    ASSERT_GT(AAudioStream_setBufferSizeInFrames(mStream, actualBufferSize), 0);

    *success = true;
}

void StreamBuilderHelper::close() {
    if (mBuilder != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(mBuilder));
    }
    if (mStream != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStream_close(mStream));
    }
}

void StreamBuilderHelper::streamCommand(
        StreamCommand cmd, aaudio_stream_state_t fromState, aaudio_stream_state_t toState) {
    ASSERT_EQ(AAUDIO_OK, cmd(mStream));
    aaudio_stream_state_t state = AAUDIO_STREAM_STATE_UNINITIALIZED;
    ASSERT_EQ(AAUDIO_OK,
            AAudioStream_waitForStateChange(mStream, fromState, &state, DEFAULT_STATE_TIMEOUT));
    ASSERT_EQ(toState, state);
}


InputStreamBuilderHelper::InputStreamBuilderHelper(aaudio_sharing_mode_t requestedSharingMode)
        : StreamBuilderHelper{AAUDIO_DIRECTION_INPUT,
            48000, 2, AAUDIO_FORMAT_PCM_I16, requestedSharingMode} {}

// Native apps don't have permissions, thus recording can
// only be tested when running as root.
static bool canTestRecording() {
    static const bool runningAsRoot = getuid() == 0;
    return runningAsRoot;
}

void InputStreamBuilderHelper::createAndVerifyStream(bool *success) {
    if (!canTestRecording()) {
        __android_log_write(ANDROID_LOG_WARN, LOG_TAG, "No permissions to run recording tests");
        *success = false;
    } else {
        StreamBuilderHelper::createAndVerifyStream(success);
    }
}


OutputStreamBuilderHelper::OutputStreamBuilderHelper(aaudio_sharing_mode_t requestedSharingMode)
        : StreamBuilderHelper{AAUDIO_DIRECTION_OUTPUT,
            48000, 2, AAUDIO_FORMAT_PCM_I16, requestedSharingMode} {}

void OutputStreamBuilderHelper::initBuilder() {
    StreamBuilderHelper::initBuilder();
    AAudioStreamBuilder_setBufferCapacityInFrames(mBuilder, 2000);
}
