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

#ifndef MEGA_RECORDER_OBOERECORDER_H
#define MEGA_RECORDER_OBOERECORDER_H

#include <mutex>

#include <oboe/Oboe.h>

#include "Recorder.h"

class OboeRecorder: public oboe::AudioStreamCallback, public Recorder {
public:
    OboeRecorder(AudioSink* sink, int32_t recorderSubtype);
    virtual ~OboeRecorder() {}

    // Inherited from oboe::AudioStreamCallback
    virtual oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int numFrames) override;
//    virtual void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {}
//    virtual void onErrorBeforeClose(oboe::AudioStream * oboeStream, oboe::Result error) override {}

    // Inherited from Recorder
    //
    // State
    //
    virtual bool isRecording() override { return mStreamStarted; }

    virtual bool setupStream(int32_t channelCount, int32_t sampleRate, int32_t routeDeviceId) override;
    virtual void teardownStream() override;

    virtual bool startStream() override;
    virtual void stopStream() override;

    void setInputPreset(int inputPreset) { mInputPreset = inputPreset; }

private:
    int32_t mInputPreset;
    void teardownStream_l();
};

#endif // MEGA_RECORDER_OBOERECORDER_H
