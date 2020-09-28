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
package org.hyphonate.megaaudio.recorder.sinks;

import org.hyphonate.megaaudio.recorder.AudioSink;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;

public class AppCallbackAudioSinkProvider implements AudioSinkProvider {
    private AppCallback mCallbackObj;
    private long mOboeSinkObj;

    public AppCallbackAudioSinkProvider(AppCallback callback) {
        mCallbackObj = callback;
    }

    public AudioSink getJavaSink() {
        return new AppCallbackAudioSink(mCallbackObj);
    }

    @Override
    public long getOboeSink() {
        return mOboeSinkObj = getOboeSinkN(mCallbackObj);
    }

    private native long getOboeSinkN(AppCallback callbackObj);

    public void releaseJNIResources() {
        releaseJNIResourcesN(mOboeSinkObj);
    }

    private native void releaseJNIResourcesN(long oboeSink);
}
