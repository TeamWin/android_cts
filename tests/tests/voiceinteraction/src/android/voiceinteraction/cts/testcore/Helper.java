/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.voiceinteraction.cts.testcore;

import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.provider.DeviceConfig;
import android.system.ErrnoException;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.nio.ByteBuffer;

/**
 * Helper for common functionalities.
 */
public final class Helper {

    public static final String TAG = "VoiceInteractionCtsHelper";

    // The timeout to wait for async result
    public static final long WAIT_TIMEOUT_IN_MS = 10_000;
    public static final String CTS_SERVICE_PACKAGE = "android.voiceinteraction.cts";

    // The id that is used to gate compat change
    public static final long MULTIPLE_ACTIVE_HOTWORD_DETECTORS = 193232191L;

    private static final String INDICATORS_FLAG = "camera_mic_icons_enabled";
    private static final String KEY_FAKE_DATA = "fakeData";
    private static final String VALUE_FAKE_DATA = "fakeData";
    private static final byte[] FAKE_BYTE_ARRAY_DATA = new byte[]{1, 2, 3};

    /**
     * Returns the SharedMemory data that is used for testing.
     */
    public static SharedMemory createFakeSharedMemoryData() {
        try {
            SharedMemory sharedMemory = SharedMemory.create("SharedMemory", 3);
            ByteBuffer byteBuffer = sharedMemory.mapReadWrite();
            byteBuffer.put(FAKE_BYTE_ARRAY_DATA);
            return sharedMemory;
        } catch (ErrnoException e) {
            Log.w(TAG, "createFakeSharedMemoryData ErrnoException : " + e);
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Returns the PersistableBundle data that is used for testing.
     */
    public static PersistableBundle createFakePersistableBundleData() {
        // TODO : Add more data for testing
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putString(KEY_FAKE_DATA, VALUE_FAKE_DATA);
        return persistableBundle;
    }

    /**
     * Returns the camera_mic_icons_enabled value.
     */
    public static String getIndicatorEnabledState() {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            String currentlyEnabled = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    INDICATORS_FLAG);
            Log.v(TAG, "getIndicatorEnabledStateIfNeeded()=" + currentlyEnabled);
            return currentlyEnabled;
        });
    }

    /**
     * Sets the camera_mic_icons_enabled state.
     */
    public static void setIndicatorEnabledState(String shouldEnable) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Log.v(TAG, "setIndicatorEnabledState()=" + shouldEnable);
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, INDICATORS_FLAG, shouldEnable,
                    false);
        });
    }
}
