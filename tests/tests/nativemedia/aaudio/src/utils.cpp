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

StreamBuilderHelper::StreamBuilderHelper(aaudio_sharing_mode_t requestedSharingMode)
        : mRequested{48000, 2, AAUDIO_FORMAT_PCM_I16, requestedSharingMode},
          mActual{0, 0, AAUDIO_FORMAT_INVALID, -1}, mFramesPerBurst{-1},
          mBuilder{nullptr}, mStream{nullptr} {
}

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
    AAudioStreamBuilder_setDirection(mBuilder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(mBuilder, mRequested.sampleRate);
    AAudioStreamBuilder_setSamplesPerFrame(mBuilder, mRequested.samplesPerFrame);
    AAudioStreamBuilder_setFormat(mBuilder, mRequested.dataFormat);
    AAudioStreamBuilder_setSharingMode(mBuilder, mRequested.sharingMode);
    AAudioStreamBuilder_setBufferCapacityInFrames(mBuilder, 2000);
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
    ASSERT_EQ(AAUDIO_DIRECTION_OUTPUT, AAudioStream_getDirection(mStream));

    // Check to see what kind of stream we actually got.
    mActual.sampleRate = AAudioStream_getSampleRate(mStream);
    ASSERT_GE(mActual.sampleRate, 44100);
    ASSERT_LE(mActual.sampleRate, 96000); // TODO what is min/max?

    mActual.samplesPerFrame = AAudioStream_getSamplesPerFrame(mStream);
    ASSERT_GE(mActual.samplesPerFrame, 1);
    ASSERT_LE(mActual.samplesPerFrame, 16); // TODO what is min/max?

    mActual.dataFormat = AAudioStream_getFormat(mStream);
    // Asserted by the client code.

    mFramesPerBurst = AAudioStream_getFramesPerBurst(mStream);
    ASSERT_GE(mFramesPerBurst, 16);
    ASSERT_LE(mFramesPerBurst, 3072); // on some devices, it can be 2052

    int32_t actualBufferSize = AAudioStream_getBufferSizeInFrames(mStream);
    ASSERT_GT(actualBufferSize, 0);
    ASSERT_GT(AAudioStream_setBufferSizeInFrames(mStream, actualBufferSize), 0);

    *success = true;
}

void StreamBuilderHelper::streamCommand(
        StreamCommand cmd, aaudio_stream_state_t fromState, aaudio_stream_state_t toState) {
    ASSERT_EQ(AAUDIO_OK, cmd(mStream));
    aaudio_stream_state_t state = AAUDIO_STREAM_STATE_UNINITIALIZED;
    ASSERT_EQ(AAUDIO_OK,
            AAudioStream_waitForStateChange(mStream, fromState, &state, DEFAULT_STATE_TIMEOUT));
    ASSERT_EQ(toState, state);
}

void StreamBuilderHelper::close() {
    if (mBuilder != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_delete(mBuilder));
    }
    if (mStream != nullptr) {
        ASSERT_EQ(AAUDIO_OK, AAudioStream_close(mStream));
    }
}
