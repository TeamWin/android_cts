/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hdmicec.app;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

/**
 * A simple app that captures the key press events and logs them.
 */
public class HdmiCecAudioManager extends Activity {

    private static final String TAG = HdmiCecAudioManager.class.getSimpleName();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if ("android.hdmicec.app.MUTE".equals(getIntent().getAction())) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE, 0);
        } else if ("android.hdmicec.app.UNMUTE".equals(getIntent().getAction())) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE, 0);
        } else if ("android.hdmicec.app.MUTE_STATUS".equals(getIntent().getAction())) {
            if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                Log.i(TAG, "Device muted.");
            } else {
                Log.i(TAG, "Device not muted.");
            }
        } else if ("android.hdmicec.app.SET_VOLUME".equals(getIntent().getAction())) {
            int percentVolume = getIntent().getIntExtra("volumePercent", 50);
            int minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int volume = minVolume + ((maxVolume - minVolume) * percentVolume / 100);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
            Log.i(TAG, "Set volume to " + volume + " (" + percentVolume + "%)");
        } else {
            Log.i(TAG, "Unknown intent");
        }
        finishAndRemoveTask();
    }
}

