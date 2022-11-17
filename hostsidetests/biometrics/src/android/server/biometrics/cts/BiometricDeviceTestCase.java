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

package android.server.biometrics.cts;

import android.cts.statsdatom.lib.DeviceUtils;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

/** Base class for hostside biometrics tests that includes common utility methods. */
abstract class BiometricDeviceTestCase extends DeviceTestCase implements IBuildReceiver {

    private static final String FEATURE_FINGERPRINT = "android.hardware.fingerprint";
    private static final String FEATURE_FACE = "android.hardware.biometrics.face";

    protected IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    /** If any biometric feature is present. */
    protected boolean hasBiometrics() throws Exception {
        return hasFeatureFace() || hasFeatureFingerprint();
    }

    /** {@see PackageManager.FEATURE_FACE}. */
    protected boolean hasFeatureFace() throws Exception {
        return DeviceUtils.hasFeature(getDevice(), FEATURE_FACE);
    }

    /** {@see PackageManager.FEATURE_FINGERPRINT}. */
    protected boolean hasFeatureFingerprint() throws Exception {
        return DeviceUtils.hasFeature(getDevice(), FEATURE_FINGERPRINT);
    }

    /** If there is an AIDL fingerprint HAL on the test device. */
    protected boolean hasAidlFingerprintSensorId() throws Exception {
        final String dumpsys = getDevice().executeShellCommand("dumpsys fingerprint");
        final int indexOfAidlProvider = dumpsys.indexOf(", provider: FingerprintProvider");
        return indexOfAidlProvider > -1;
    }
}
