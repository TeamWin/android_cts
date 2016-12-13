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
package android.app.usage.cts.preconditions.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import com.android.compatibility.common.preconditions.TelephonyHelper;

/**
 * A test to verify that device-side preconditions are met for CTS
 */
public class AppUsagePreparerAppTest extends AndroidTestCase {
    private static final String TAG = "AppUsagePreparerAppTest";

    /**
     * Test if device has cellular data connectivity.
     * @throws Exception
     */
    public void testCellularConnectivity() throws Exception {
        PackageManager pm = this.getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return; // do not test for cellular data without telephony feature
        }

        assertTrue("Device must have cellular data connectivity in order to run CTS."
            + " Please make sure you have added a SIM card with data plan to your phone,"
            + " have enabled data over cellular and in case of dual SIM devices,"
            + " have selected the right SIM for data connection.",
                TelephonyHelper.hasCellcularData(this.getContext()));
    }
}
