/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.security.cts;

import android.content.ContentResolver;
import android.content.Context;
import android.os.SystemClock;
import android.platform.test.annotations.SecurityTest;
import android.provider.Settings.System;
import android.test.AndroidTestCase;

import androidx.test.platform.app.InstrumentationRegistry;

@SecurityTest
public class FontScaleSettingsTest extends AndroidTestCase {
    private Context mContext;
    private String mPackageName;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageName = mContext.getPackageName();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "appops set " + mPackageName + " android:write_settings allow");

        // Wait a beat to persist the change
        SystemClock.sleep(500);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "appops set " + mPackageName + " android:write_settings default");
    }

    /**
     * Verifies that the invalid values for the font scale setting is rejected.
     *
     * Prior to fixing bug 156260178, an invalid value could be assigned to the font scale setting,
     * resulting in a bricked device. With the fix, the assignment will be rejected.
     */
    @SecurityTest(minPatchLevel = "2021-02")
    public void testSetInvalidFontScaleValueRejected() {
        final ContentResolver cr = mContext.getContentResolver();
        try {
            System.putFloat(cr, System.FONT_SCALE, Float.MAX_VALUE);
            fail("Should throw");
        } catch (IllegalArgumentException e) {
        }
        try {
            System.putFloat(cr, System.FONT_SCALE, -1f);
            fail("Should throw");
        } catch (IllegalArgumentException e) {
        }
        try {
            System.putFloat(cr, System.FONT_SCALE, 0.1f);
            fail("Should throw");
        } catch (IllegalArgumentException e) {
        }
        try {
            System.putFloat(cr, System.FONT_SCALE, 30.0f);
            fail("Should throw");
        } catch (IllegalArgumentException e) {
        }
    }
}
