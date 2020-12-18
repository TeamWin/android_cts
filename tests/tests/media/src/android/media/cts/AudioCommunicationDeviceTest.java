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

package android.media.cts;

import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.Executors;

public class AudioCommunicationDeviceTest extends CtsAndroidTestCase {
    private final static String TAG = "AudioCommunicationDeviceTest";

    private AudioManager mAudioManager;

    private static final int[] VALID_COMMUNICATION_DEVICE_TYPES = {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_AUX_LINE
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAudioManager = getInstrumentation().getContext().getSystemService(AudioManager.class);
    }

    private boolean isValidCommunicationDevice(AudioDeviceInfo device) {
        for (int type : VALID_COMMUNICATION_DEVICE_TYPES) {
            if (device.getType() == type) {
                return true;
            }
        }
        return false;
    }

    public void testSetValidDeviceForCommunication() {
        if (!isValidPlatform("testSetValidDeviceForCommunication")) return;

        AudioDeviceInfo commDevice = null;
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (!isValidCommunicationDevice(device)) {
                continue;
            }
            try {
                mAudioManager.setDeviceForCommunication(device);
                try {
                    commDevice = mAudioManager.getDeviceForCommunication();
                } catch (Exception e) {
                    fail("getDeviceForCommunication failed with exception: " + e);
                }
                if (commDevice == null || commDevice.getType() != device.getType()) {
                    fail("setDeviceForCommunication failed, expected device: "
                            + device.getType() + " but got: "
                            + ((commDevice == null)
                                ? AudioDeviceInfo.TYPE_UNKNOWN : commDevice.getType()));
                }
            } catch (Exception e) {
                fail("setDeviceForCommunication failed with exception: " + e);
            }
        }

        try {
            mAudioManager.clearDeviceForCommunication();
        } catch (Exception e) {
            fail("clearDeviceForCommunication failed with exception: " + e);
        }
        try {
            commDevice = mAudioManager.getDeviceForCommunication();
        } catch (Exception e) {
            fail("getDeviceForCommunication failed with exception: " + e);
        }
        if (commDevice != null) {
            fail("clearDeviceForCommunication failed, expected device null but got: "
                    + commDevice.getType());
        }
    }

    public void testSetInvalidDeviceForCommunication() {
        if (!isValidPlatform("testSetInvalidDeviceForCommunication")) return;

        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (isValidCommunicationDevice(device)) {
                continue;
            }
            try {
                mAudioManager.setDeviceForCommunication(device);
                fail("setDeviceForCommunication should fail for device: " + device.getType());
            } catch (Exception e) {
            }
        }
    }

    static class MyOnCommunicationDeviceChangedListener implements
            AudioManager.OnCommunicationDeviceChangedListener {

        private final Object mCbLock = new Object();
        @GuardedBy("mCbLock")
        private boolean mCalled;
        @GuardedBy("mCbLock")
        private AudioDeviceInfo mDevice;

        private static final int LISTENER_WAIT_TIMEOUT_MS = 3000;
        void reset() {
            synchronized (mCbLock) {
                mCalled = false;
                mDevice = null;
            }
        }

        AudioDeviceInfo waitForDeviceUpdate() {
            synchronized (mCbLock) {
                while (!mCalled) {
                    try {
                        mCbLock.wait(LISTENER_WAIT_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                    }
                }
                return mDevice;
            }
        }

        AudioDeviceInfo getDevice() {
            synchronized (mCbLock) {
                return mDevice;
            }
        }

        MyOnCommunicationDeviceChangedListener() {
            reset();
        }

        @Override
        public void onCommunicationDeviceChanged(AudioDeviceInfo device) {
            synchronized (mCbLock) {
                mCalled = true;
                mDevice = device;
                mCbLock.notifyAll();
            }
        }
    }

    public void testDeviceForCommunicationListener() {
        if (!isValidPlatform("testDeviceForCommunicationListener")) return;

        MyOnCommunicationDeviceChangedListener listener =
                new MyOnCommunicationDeviceChangedListener();

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(null, listener);
            fail("addOnCommunicationDeviceChangedListener should fail with null executor");
        } catch (Exception e) {
        }

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(
                    Executors.newSingleThreadExecutor(), null);
            fail("addOnCommunicationDeviceChangedListener should fail with null listener");
        } catch (Exception e) {
        }

        try {
            mAudioManager.removeOnCommunicationDeviceChangedListener(null);
            fail("removeOnCommunicationDeviceChangedListener should fail with null listener");
        } catch (Exception e) {
        }

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(
                Executors.newSingleThreadExecutor(), listener);
        } catch (Exception e) {
            fail("addOnCommunicationDeviceChangedListener failed with exception: "
                    + e);
        }

        try {
            mAudioManager.addOnCommunicationDeviceChangedListener(
                Executors.newSingleThreadExecutor(), listener);
            fail("addOnCommunicationDeviceChangedListener succeeded for same listener");
        } catch (Exception e) {
        }

        AudioDeviceInfo originalDevice = mAudioManager.getDeviceForCommunication();
        AudioDeviceInfo requestedDevice = null;
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (!isValidCommunicationDevice(device)) {
                continue;
            }
            if (originalDevice == null || device.getType() != originalDevice.getType()) {
                requestedDevice = device;
                break;
            }
        }
        if (requestedDevice == null) {
            Log.i(TAG,"Skipping end of testDeviceForCommunicationListener test,"
                    +" no valid decice to test");
            return;
        }
        mAudioManager.setDeviceForCommunication(requestedDevice);
        AudioDeviceInfo listenerDevice = listener.waitForDeviceUpdate();
        if (listenerDevice == null || listenerDevice.getType() != requestedDevice.getType()) {
            fail("listener and setter device mismatch, expected device: "
                    + requestedDevice.getType() + " but got: "
                    + ((listenerDevice == null)
                        ? AudioDeviceInfo.TYPE_UNKNOWN : listenerDevice.getType()));
        }
        AudioDeviceInfo getterDevice = mAudioManager.getDeviceForCommunication();
        if (getterDevice == null || getterDevice.getType() != listenerDevice.getType()) {
            fail("listener and getter device mismatch, expected device: "
                    + listenerDevice.getType() + " but got: "
                    + ((getterDevice == null)
                        ? AudioDeviceInfo.TYPE_UNKNOWN : getterDevice.getType()));
        }

        listener.reset();

        if (originalDevice == null) {
            mAudioManager.clearDeviceForCommunication();
        } else {
            mAudioManager.setDeviceForCommunication(originalDevice);
        }
        listenerDevice = listener.waitForDeviceUpdate();
        if (originalDevice == null) {
            if (listenerDevice != null) {
                fail("setDeviceForCommunication failed, expected null device but got: "
                        + listenerDevice.getType());
            }
        } else {
            if (listenerDevice == null || listenerDevice.getType() != originalDevice.getType()) {
                fail("communication device listener failed on clear, expected device: "
                        + originalDevice.getType() + " but got: "
                        + ((listenerDevice == null)
                            ? AudioDeviceInfo.TYPE_UNKNOWN : listenerDevice.getType()));
            }
        }

        try {
            mAudioManager.removeOnCommunicationDeviceChangedListener(listener);
        } catch (Exception e) {
            fail("removeOnCommunicationDeviceChangedListener failed with exception: "
                    + e);
        }
    }

    private boolean isValidPlatform(String testName) {
        if (!(getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT) &&
                !getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY))) {
            Log.i(TAG,"Skipping test " + testName + " : device has no audio output or is a TV.");
            return false;
        }
        return true;
    }
}
