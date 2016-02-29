/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.UserManager;

import java.util.Objects;
import java.util.concurrent.Callable;

public class AudioRestrictionTest extends BaseDeviceAdminTest {

    private AudioManager mAudioManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    private void disallowAdjustVolumeTestInternal(boolean setByAddUserRestiction) throws Exception {
        try {
            // Set volume of ringtone to be 1.
            mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 1, /* flag= */ 0);

            // Disallow adjusting volume.
            mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT,
                    UserManager.DISALLOW_ADJUST_VOLUME);
            waitUntil(true, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return mDevicePolicyManager.isMasterVolumeMuted(ADMIN_RECEIVER_COMPONENT);
                }
            });
            // Verify that volume can't be changed.
            mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, /* flag= */ 0);
            assertEquals(1, mAudioManager.getStreamVolume(AudioManager.STREAM_RING));

            // Allowing adjusting volume.
            mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT,
                    UserManager.DISALLOW_ADJUST_VOLUME);
            waitUntil(false, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return mDevicePolicyManager.isMasterVolumeMuted(ADMIN_RECEIVER_COMPONENT);
                }
            });
            // Verify the volume can be changed now.
            mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE,  /* flag= */ 0);
            waitUntil(2, new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
                }
            });
        } finally {
            // Clear the restriction.
            mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT,
                    UserManager.DISALLOW_ADJUST_VOLUME);
            waitUntil(false, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return mDevicePolicyManager.isMasterVolumeMuted(ADMIN_RECEIVER_COMPONENT);
                }
            });
        }
    }

    public void testDisallowUnmuteMicrophone() throws Exception {
        try {
            mAudioManager.setMicrophoneMute(false);
            assertFalse(mAudioManager.isMicrophoneMute());

            // Disallow the microphone to be unmuted.
            mDevicePolicyManager.addUserRestriction(
                    ADMIN_RECEIVER_COMPONENT, UserManager.DISALLOW_UNMUTE_MICROPHONE);
            waitUntil(true, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return mAudioManager.isMicrophoneMute();
                }
            });
            // Verify that we can't unmute the microphone.
            mAudioManager.setMicrophoneMute(false);
            assertTrue(mAudioManager.isMicrophoneMute());
        } finally {
            // Clear the restriction
            mDevicePolicyManager.clearUserRestriction(
                    ADMIN_RECEIVER_COMPONENT, UserManager.DISALLOW_UNMUTE_MICROPHONE);
            waitUntil(false, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return mAudioManager.isMicrophoneMute();
                }
            });
        }
    }

    private <T> void waitUntil(T expected, Callable<T> c) throws Exception {
        final long start = SystemClock.elapsedRealtime();
        final int TIMEOUT_MS = 5 * 1000;

        T actual;
        while (!Objects.equals(expected, actual = c.call())) {
            if ((SystemClock.elapsedRealtime() - start) >= TIMEOUT_MS) {
                fail(String.format("Timed out waiting the value to change to %s (actual=%s)",
                        expected, actual));
            }
            Thread.sleep(200);
        }
    }
}
