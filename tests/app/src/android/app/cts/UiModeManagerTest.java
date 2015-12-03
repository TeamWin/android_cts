/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.app.cts;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.test.AndroidTestCase;
import android.util.Log;

public class UiModeManagerTest extends AndroidTestCase {
    private static final String TAG = "UiModeManagerTest";

    private UiModeManager mUiModeManager;
    private boolean mIsAuto = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUiModeManager = (UiModeManager) getContext().getSystemService(Context.UI_MODE_SERVICE);
        assertNotNull(mUiModeManager);
        mIsAuto = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);
    }

    public void testCarMode() throws Exception {
        if (mIsAuto) {
            Log.i(TAG, "testCarMode automotive");
            doTestCarModeAutomotive();
        } else {
            Log.i(TAG, "testCarMode generic");
            doTestCarModeGeneric();
        }
    }

    private void doTestCarModeAutomotive() throws Exception {
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        assertTrue(mUiModeManager.isUiModeLocked());
        doTestLockedUiMode();
        assertTrue(mUiModeManager.isNightModeLocked());
        doTestLockedNightMode();
    }

    private void doTestCarModeGeneric() throws Exception {
        if (mUiModeManager.isUiModeLocked()) {
            doTestLockedUiMode();
        } else {
            doTestUnlockedUiMode();
        }
        if (mUiModeManager.isNightModeLocked()) {
            doTestLockedNightMode();
        } else {
            doTestUnlockedNightMode();
        }
    }

    private void doTestLockedUiMode() throws Exception {
        mUiModeManager.enableCarMode(0);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        mUiModeManager.disableCarMode(0);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
    }

    private void doTestUnlockedUiMode() throws Exception {
        mUiModeManager.enableCarMode(0);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        mUiModeManager.disableCarMode(0);
        assertNotSame(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
    }

    private void doTestLockedNightMode() throws Exception {
        int currentMode = mUiModeManager.getNightMode();
        if (currentMode == UiModeManager.MODE_NIGHT_YES) {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
            assertEquals(currentMode, mUiModeManager.getNightMode());
        } else {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
            assertEquals(currentMode, mUiModeManager.getNightMode());
        }
    }

    private void doTestUnlockedNightMode() throws Exception {
        // day night mode should be settable regardless of car mode.
        mUiModeManager.enableCarMode(0);
        doTestAllNightModes();
        mUiModeManager.disableCarMode(0);
        doTestAllNightModes();
    }

    private void doTestAllNightModes() {
        assertNightModeChange(UiModeManager.MODE_NIGHT_AUTO);
        assertNightModeChange(UiModeManager.MODE_NIGHT_YES);
        assertNightModeChange(UiModeManager.MODE_NIGHT_NO);
    }

    private void assertNightModeChange(int mode) {
        mUiModeManager.setNightMode(mode);
        assertEquals(mode, mUiModeManager.getNightMode());
    }
}
