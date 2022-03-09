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

package android.localemanager.cts;

import static android.localemanager.cts.util.LocaleConstants.DEFAULT_APP_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.DEFAULT_SYSTEM_LOCALES;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.LocaleManager;
import android.content.Context;
import android.os.LocaleList;
import android.server.wm.ActivityManagerTestBase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link android.app.LocaleManager} API(s) related to system locales.
 *
 * Build/Install/Run: atest CtsLocaleManagerTestCases
 */
@RunWith(AndroidJUnit4.class)
public class LocaleManagerSystemLocaleTest extends ActivityManagerTestBase {
    private Context mContext;
    private LocaleManager mLocaleManager;

    /* System locales that were set on the device prior to running tests */
    private LocaleList mPreviousSystemLocales;

    @Before
    public void setUp() throws Exception {
        // Unlocks the device if locked, since we have tests where the app/activity needs
        // to be in the foreground/background.
        super.setUp();

        mContext = InstrumentationRegistry.getTargetContext();
        mLocaleManager = mContext.getSystemService(LocaleManager.class);

        // Set custom system locales for these tests.
        // Store the existing system locales and reset back to it in tearDown.
        mPreviousSystemLocales = mLocaleManager.getSystemLocales();
        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setSystemLocales(DEFAULT_SYSTEM_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION, Manifest.permission.WRITE_SETTINGS);

        // Reset locales for the calling app.
        mLocaleManager.setApplicationLocales(LocaleList.getEmptyLocaleList());
    }

    @After
    public void tearDown() throws Exception {
        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setSystemLocales(mPreviousSystemLocales),
                Manifest.permission.CHANGE_CONFIGURATION, Manifest.permission.WRITE_SETTINGS);
    }

    @Test
    public void testGetSystemLocales_noAppLocaleSet_returnsCorrectList()
            throws Exception {
        assertEquals(DEFAULT_SYSTEM_LOCALES, mLocaleManager.getSystemLocales());
    }

    @Test
    public void testGetSystemLocales_appLocaleSet_returnsCorrectList()
            throws Exception {
        mLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);
        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);

        // ensure that getSystemLocales still returns the system locales
        assertEquals(DEFAULT_SYSTEM_LOCALES, mLocaleManager.getSystemLocales());
    }


    /**
     * Verifies that the locales are correctly set for calling(instrumentation) app
     * by fetching the locales of the app with a binder call.
     */
    private void assertLocalesCorrectlySetForCallingApp(LocaleList expectedLocales) {
        assertEquals(expectedLocales, mLocaleManager.getApplicationLocales());
    }

}
