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

package android.permission.cts.sdk28;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RequestLocation {

    private TelephonyManager mTelephonyManager;
    private boolean mHasTelephony;

    @Before
    public void setUp() throws Exception {
        mHasTelephony = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        mTelephonyManager = (TelephonyManager) getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        assertNotNull(mTelephonyManager);
    }

    /**
     * Verify that a SecurityException is thrown when an app targeting SDK 28
     * lacks the coarse location permission.
     */
    @Test
    public void testGetNeighboringCellInfo() {
        if (!mHasTelephony) return;
        try {
            mTelephonyManager.getNeighboringCellInfo();
            fail("No Exception thrown for getNeighboringCellInfo without permission!");
        } catch (SecurityException expected) {
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }
}
