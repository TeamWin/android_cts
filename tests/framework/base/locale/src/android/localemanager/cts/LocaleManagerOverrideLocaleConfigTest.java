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

import static android.localemanager.cts.util.LocaleConstants.EXTRA_QUERY_LOCALECONFIG;
import static android.localemanager.cts.util.LocaleConstants.EXTRA_SET_LOCALECONFIG;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_CREATION_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_MAIN_ACTIVITY;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_PACKAGE;
import static android.server.wm.CliIntentExtra.extraString;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.Context;
import android.content.IntentFilter;
import android.os.LocaleList;
import android.server.wm.ActivityManagerTestBase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tests for {@link android.app.LocaleManager} API(s) related to the override LocaleConfig.
 *
 * Build/Install/Run: atest CtsLocaleManagerTestCases
 */
@RunWith(AndroidJUnit4.class)
public class LocaleManagerOverrideLocaleConfigTest extends ActivityManagerTestBase {
    private static final boolean DEBUG = true;
    private static final String TAG = "LocaleManagerOverrideLocaleConfigTest";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final List<String> RESOURCE_LOCALES = Arrays.asList(
            new String[]{"en-US", "zh-Hant-TW", "pt-PT", "fr-FR", "zh-Hans-SG"});
    public static final LocaleList OVERRIDE_LOCALES =
            LocaleList.forLanguageTags("en-US,fr-FR,zh-Hant-TW");
    private static Context sContext;
    private static Context sTestAppContext;
    private static LocaleManager sLocaleManager;

    /* Receiver to listen to the response from the test app's activity. */
    private BlockingBroadcastReceiver mTestAppCreationInfoProvider;
    private boolean mResetOverride = false;

    @BeforeClass
    public static void setUpClass() {
        sContext = InstrumentationRegistry.getTargetContext();
        sLocaleManager = sContext.getSystemService(LocaleManager.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        sTestAppContext = sContext.createPackageContext(TEST_APP_PACKAGE, 0);
        mTestAppCreationInfoProvider = new BlockingBroadcastReceiver();
        sContext.registerReceiver(mTestAppCreationInfoProvider,
                new IntentFilter(TEST_APP_CREATION_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        mTestAppCreationInfoProvider.reset();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestAppCreationInfoProvider != null) {
            sContext.unregisterReceiver(mTestAppCreationInfoProvider);
            mTestAppCreationInfoProvider = null;
        }
        if (mResetOverride) {
            mResetOverride = false;
            cleanTestAppOverride();
        }
    }

    @Test
    public void testSetOverrideLocaleConfig_overrideByTestApp_getCorrectLocaleConfig()
            throws Exception {
        if (DEBUG) {
            Log.d(TAG, "overrideByTestApp_getCorrectLocaleConfig");
        }
        // Verify where a LocaleConfig of the test app can be read successfully
        final LocaleConfig localeConfig = new LocaleConfig(sTestAppContext);

        assertEquals(RESOURCE_LOCALES.stream().sorted().collect(Collectors.toList()),
                new ArrayList<String>(Arrays.asList(
                        localeConfig.getSupportedLocales().toLanguageTags().split(
                                ","))).stream().sorted().collect(Collectors.toList()));

        // Tell the test app to set the override LocaleConfig
        launchActivity(TEST_APP_MAIN_ACTIVITY,
                extraString(EXTRA_SET_LOCALECONFIG, OVERRIDE_LOCALES.toLanguageTags()));
        mResetOverride = true;

        // Verify the override LocaleConfig is read correctly
        PollingCheck.check("Make sure that the override LocaleConfig is read correctly",
                TIMEOUT,
                () -> OVERRIDE_LOCALES.equals(
                        new LocaleConfig(sTestAppContext).getSupportedLocales()));
    }

    @Test
    public void testGetOverrideLocaleConfig_getOverrideFromTestApp_returnCorrectLocaleConfig()
            throws Exception {
        if (DEBUG) {
            Log.d(TAG, "getOverrideByTestApp_returnCorrectLocaleConfig");
        }
        // Tell the test app to set the override LocaleConfig
        launchActivity(TEST_APP_MAIN_ACTIVITY,
                extraString(EXTRA_SET_LOCALECONFIG, OVERRIDE_LOCALES.toLanguageTags()));
        mResetOverride = true;

        // Verify the override LocaleConfig is read correctly
        PollingCheck.check("Make sure that the override LocaleConfig is read correctly",
                TIMEOUT,
                () -> OVERRIDE_LOCALES.equals(
                        new LocaleConfig(sTestAppContext).getSupportedLocales()));

        // Re-start the test app by starting an activity and check if the override LocaleConfig
        // correctly received by the test app and listen to the broadcast for result from the test
        // app.
        launchActivity(TEST_APP_MAIN_ACTIVITY, extraString(EXTRA_QUERY_LOCALECONFIG, "true"));

        assertTrue(mTestAppCreationInfoProvider.await());
        assertReceivedBroadcastContains(mTestAppCreationInfoProvider, TEST_APP_PACKAGE,
                OVERRIDE_LOCALES);
    }

    @Test
    public void testSetOverrideLocaleConfig_appLocalesNotInOverrideLocaleConfig_clearAppLocales()
            throws Exception {
        if (DEBUG) {
            Log.d(TAG, "appLocalesNotInOverrideLocaleConfig_clearAppLocales");
        }
        // Set the app locales which are not existed in the override LocaleConfig
        runWithShellPermissionIdentity(
                () -> sLocaleManager.setApplicationLocales(TEST_APP_PACKAGE,
                        LocaleList.forLanguageTags("pt-PT")),
                Manifest.permission.CHANGE_CONFIGURATION);

        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE,
                LocaleList.forLanguageTags("pt-PT"));

        // Tell the test app to set the override LocaleConfig
        launchActivity(TEST_APP_MAIN_ACTIVITY,
                extraString(EXTRA_SET_LOCALECONFIG, OVERRIDE_LOCALES.toLanguageTags()));
        mResetOverride = true;

        // Check whether the app locales has been set to follow the system default locales
        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE,
                LocaleList.getEmptyLocaleList());
    }

    @Test(expected = SecurityException.class)
    public void testSetOverrideLocaleConfig_overrideByOtherApp_throwsSecurityException()
            throws Exception {
        if (DEBUG) {
            Log.d(TAG, "overrideByOtherApp_throwsSecurityException");
        }
        final LocaleManager localeManager = sTestAppContext.getSystemService(LocaleManager.class);
        final LocaleConfig overrideLocaleConfig = new LocaleConfig(OVERRIDE_LOCALES);
        localeManager.setOverrideLocaleConfig(overrideLocaleConfig);
        fail("Expected SecurityException due to no permission.");
    }

    private void cleanTestAppOverride() throws Exception {
        if (DEBUG) {
            Log.d(TAG, "cleanTestAppOverride");
        }
        // Clean the override LocaleConfig
        launchActivity(TEST_APP_MAIN_ACTIVITY, extraString(EXTRA_SET_LOCALECONFIG, "reset"));

        PollingCheck.check("Make sure there is no oveerride LocaleConfig", TIMEOUT,
                () -> RESOURCE_LOCALES.stream().sorted().collect(Collectors.toList()).equals(
                        new ArrayList<String>(Arrays.asList(new LocaleConfig(
                                sTestAppContext).getSupportedLocales().toLanguageTags().split(
                                ","))).stream().sorted().collect(Collectors.toList())));
    }

    /**
     * Verifies that the locales are correctly set for another package
     * by fetching locales of the app with a binder call.
     */
    private void assertLocalesCorrectlySetForAnotherApp(String packageName,
            LocaleList expectedLocales) throws Exception {
        assertEquals(expectedLocales, getApplicationLocales(packageName));
    }

    private LocaleList getApplicationLocales(String packageName) throws Exception {
        return callWithShellPermissionIdentity(() ->
                        sLocaleManager.getApplicationLocales(packageName),
                Manifest.permission.READ_APP_SPECIFIC_LOCALES);
    }

    /**
     * Verifies that the broadcast received in the relevant apps have the correct information
     * in the intent extras. It verifies the below extras:
     * <ul>
     * <li> {@link Intent#EXTRA_PACKAGE_NAME}
     * <li> {@link Intent#EXTRA_LOCALE_LIST}
     * </ul>
     */
    private void assertReceivedBroadcastContains(BlockingBroadcastReceiver receiver,
            String expectedPackageName, LocaleList expectedLocales) {
        assertEquals(expectedPackageName, receiver.getPackageName());
        assertEquals(expectedLocales, receiver.getLocales());
    }
}
