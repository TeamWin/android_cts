/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.hardware.fingerprint.cts;

import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.test.AndroidTestCase;

public class FingerprintFeatureCompatTest extends AndroidTestCase {

    @Presubmit
    public void test_bothFeaturesDeclared() {
        /**
         * Android biometric features are now all under the "android.hardware.biometrics" namespace.
         * For backwards compatibility, both {@link PackageManager.FEATURE_FINGERPRINT} and
         * {@link android.content.pm.PackageManager.FEATURE_FINGERPRINT_PRE_29} must be declared
         * by any device supporting fingerprint.
         */
        final PackageManager pm = getContext().getPackageManager();
        final boolean hasNewFeature = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        final boolean hasOldFeature =
                pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT_PRE_29);

        if (hasNewFeature || hasOldFeature) {
            assertTrue(hasNewFeature && hasOldFeature);
        }
    }
}
