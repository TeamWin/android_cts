/*
 * Copyright 2016 The Android Open Source Project
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

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <gtest/gtest.h>

#include "test_aaudio.h"
#include "utils.h"

// Test Writing to an AAudioStream
static void runtest_aaudio_stream(aaudio_sharing_mode_t requestedSharingMode) {
    StreamBuilderHelper helper{requestedSharingMode};

    int writeLoops = 0;

    int32_t framesWritten = 0;
    int64_t framesTotal = 0;
    int64_t aaudioFramesRead = 0;
    int64_t aaudioFramesRead1 = 0;
    int64_t aaudioFramesRead2 = 0;
    int64_t aaudioFramesWritten = 0;

    int64_t timeoutNanos;

    aaudio_stream_state_t state = AAUDIO_STREAM_STATE_UNINITIALIZED;

    helper.initBuilder();

    bool success = false;
    helper.createAndVerifyStream(&success);
    if (!success) return;

    // TODO test this on full build
    // ASSERT_NE(AAUDIO_DEVICE_UNSPECIFIED, AAudioStream_getDeviceId(aaudioStream));

    // Allocate a buffer for the audio data.
    // TODO handle possibility of other data formats
    ASSERT_TRUE(helper.actual().dataFormat == AAUDIO_FORMAT_PCM_I16);
    size_t dataSizeSamples = helper.framesPerBurst() * helper.actual().samplesPerFrame;
    int16_t *data = (int16_t *) calloc(dataSizeSamples, sizeof(int16_t));
    ASSERT_TRUE(nullptr != data);

    // Prime the buffer.
    timeoutNanos = 0;
    do {
        framesWritten = AAudioStream_write(
                helper.stream(), data, helper.framesPerBurst(), timeoutNanos);
        // There should be some room for priming the buffer.
        framesTotal += framesWritten;
        ASSERT_GE(framesWritten, 0);
        ASSERT_LE(framesWritten, helper.framesPerBurst());
    } while (framesWritten > 0);
    ASSERT_TRUE(framesTotal > 0);

    // Start/write/pause more than once to see if it fails after the first time.
    // Write some data and measure the rate to see if the timing is OK.
    for (int numLoops = 0; numLoops < 2; numLoops++) {
        helper.startStream();

        // Write some data while we are running. Read counter should be advancing.
        writeLoops = 1 * helper.actual().sampleRate / helper.framesPerBurst(); // 1 second
        ASSERT_LT(2, writeLoops); // detect absurdly high framesPerBurst
        timeoutNanos = 100 * (NANOS_PER_SECOND * helper.framesPerBurst() /
                helper.actual().sampleRate); // N bursts
        framesWritten = 1;
        aaudioFramesRead = AAudioStream_getFramesRead(helper.stream());
        aaudioFramesRead1 = aaudioFramesRead;
        int64_t beginTime = getNanoseconds(CLOCK_MONOTONIC);
        do {
            framesWritten = AAudioStream_write(
                    helper.stream(), data, helper.framesPerBurst(), timeoutNanos);
            ASSERT_EQ(framesWritten, helper.framesPerBurst());

            framesTotal += framesWritten;
            aaudioFramesWritten = AAudioStream_getFramesWritten(helper.stream());
            EXPECT_EQ(framesTotal, aaudioFramesWritten);

            // Try to get a more accurate measure of the sample rate.
            if (beginTime == 0) {
                aaudioFramesRead = AAudioStream_getFramesRead(helper.stream());
                if (aaudioFramesRead > aaudioFramesRead1) { // is read pointer advancing
                    beginTime = getNanoseconds(CLOCK_MONOTONIC);
                    aaudioFramesRead1 = aaudioFramesRead;
                }
            }
        } while (framesWritten > 0 && writeLoops-- > 0);

        aaudioFramesRead2 = AAudioStream_getFramesRead(helper.stream());
        int64_t endTime = getNanoseconds(CLOCK_MONOTONIC);
        ASSERT_GT(aaudioFramesRead2, 0);
        EXPECT_GT(aaudioFramesRead2, aaudioFramesRead1);


        // TODO why is AudioTrack path so inaccurate?
        const double rateTolerance = 200.0; // arbitrary tolerance for sample rate
        if (requestedSharingMode != AAUDIO_SHARING_MODE_SHARED) {
            // Calculate approximate sample rate and compare with stream rate.
            double seconds = (endTime - beginTime) / (double) NANOS_PER_SECOND;
            double measuredRate = (aaudioFramesRead2 - aaudioFramesRead1) / seconds;
            ASSERT_NEAR(helper.actual().sampleRate, measuredRate, rateTolerance);
        }

        helper.pauseStream();
    }

    // Make sure the read counter is not advancing when we are paused.
    aaudioFramesRead = AAudioStream_getFramesRead(helper.stream());
    ASSERT_GE(aaudioFramesRead, aaudioFramesRead2); // monotonic increase

    // Use this to sleep by waiting for a state that won't happen.
    timeoutNanos = 100 * NANOS_PER_MILLISECOND;
    AAudioStream_waitForStateChange(helper.stream(), AAUDIO_STREAM_STATE_OPEN, &state, timeoutNanos);
    aaudioFramesRead2 = AAudioStream_getFramesRead(helper.stream());
    EXPECT_EQ(aaudioFramesRead, aaudioFramesRead2);

    // ------------------- TEST FLUSH -----------------
    // Prime the buffer.
    timeoutNanos = 0;
    writeLoops = 1000;
    do {
        framesWritten = AAudioStream_write(
                helper.stream(), data, helper.framesPerBurst(), timeoutNanos);
        framesTotal += framesWritten;
    } while (framesWritten > 0 && writeLoops-- > 0);
    EXPECT_EQ(0, framesWritten);

    helper.flushStream();

    // After a flush, the read counter should be caught up with the write counter.
    aaudioFramesWritten = AAudioStream_getFramesWritten(helper.stream());
    EXPECT_EQ(framesTotal, aaudioFramesWritten);
    aaudioFramesRead = AAudioStream_getFramesRead(helper.stream());
    EXPECT_EQ(aaudioFramesRead, aaudioFramesWritten);

    sleep(1); // FIXME - The write returns 0 if we remove this sleep! Why?

    // The buffer should be empty after a flush so we should be able to write.
    framesWritten = AAudioStream_write(
            helper.stream(), data, helper.framesPerBurst(), timeoutNanos);
    // There should be some room for priming the buffer.
    ASSERT_GT(framesWritten, 0);
    ASSERT_LE(framesWritten, helper.framesPerBurst());

    free(data);
}

// Test Writing to an AAudioStream using SHARED mode.
TEST(test_aaudio, aaudio_stream_shared) {
    runtest_aaudio_stream(AAUDIO_SHARING_MODE_SHARED);
}

// Test Writing to an AAudioStream using EXCLUSIVE sharing mode. It may fail gracefully.
TEST(test_aaudio, aaudio_stream_exclusive) {
    runtest_aaudio_stream(AAUDIO_SHARING_MODE_EXCLUSIVE);
}

int main(int argc, char **argv) {
    testing::InitGoogleTest(&argc, argv);

    return RUN_ALL_TESTS();
}
