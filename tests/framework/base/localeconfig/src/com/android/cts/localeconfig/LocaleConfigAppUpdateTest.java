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

package com.android.cts.localeconfig;

import static android.localeconfig.cts.util.LocaleConstants.APP_CREATION_INFO_PROVIDER_ACTION;
import static android.localeconfig.cts.util.LocaleConstants.EXTRA_QUERY_LOCALES;
import static android.localeconfig.cts.util.LocaleConstants.EXTRA_SET_LOCALES;
import static android.server.wm.CliIntentExtra.extraString;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.LocaleConfig;
import android.app.LocaleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.LocaleList;
import android.server.wm.ActivityManagerTestBase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link com.android.server.locales.LocaleManagerBackupHelper#onPackageUpdateFinished()}.
 *
 * Build/Install/Run: atest LocaleConfigAppUpdateTest
 */
@RunWith(AndroidJUnit4.class)
public class LocaleConfigAppUpdateTest extends ActivityManagerTestBase {
    private static final String APK_PATH = "/data/local/tmp/cts/localeconfig/";
    private static final String APK_WITH_LOCALECONFIG = APK_PATH + "ApkWithLocaleConfig.apk";
    private static final String APK_WITHOUT_LOCALECONFIG = APK_PATH + "ApkWithoutLocaleConfig.apk";
    private static final String APK_REMOVEAPPLOCALES_INLOCALECONFIG =
            APK_PATH + "ApkRemoveAppLocalesInLocaleConfig.apk";
    private static final String TEST_PACKAGE = "com.android.cts.localeconfiginorout";
    private static final LocaleList EMPTY_LOCALES = LocaleList.getEmptyLocaleList();
    private static Context sContext;
    private static LocaleManager sLocaleManager;

    /* Receiver to listen to the broadcast in the calling (instrumentation) app. */
    private BlockingBroadcastReceiver mAppSpecificLocaleChangeBroadcastReceiver;
    /* Receiver to listen to the response from the app's activity. */
    private BlockingBroadcastReceiver mAppCreationInfoProvider;

    @BeforeClass
    public static void setUpClass() {
        sContext = InstrumentationRegistry.getTargetContext();
        sLocaleManager = sContext.getSystemService(LocaleManager.class);
    }

    @Before
    public void setUp() throws Exception {
        mAppSpecificLocaleChangeBroadcastReceiver = new BlockingBroadcastReceiver();
        mAppCreationInfoProvider = new BlockingBroadcastReceiver();
        sContext.registerReceiver(mAppSpecificLocaleChangeBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));
        sContext.registerReceiver(mAppCreationInfoProvider,
                new IntentFilter(APP_CREATION_INFO_PROVIDER_ACTION),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        resetReceivers();
    }

    @After
    public void tearDown() throws Exception {
        uninstall(TEST_PACKAGE);
        unRegisterReceiver(mAppSpecificLocaleChangeBroadcastReceiver);
        unRegisterReceiver(mAppCreationInfoProvider);
    }

    private void unRegisterReceiver(BlockingBroadcastReceiver receiver) {
        if (receiver != null) {
            sContext.unregisterReceiver(receiver);
            receiver = null;
        }
    }

    /**
     * Resets the countdown latch in all the receivers.
     */
    private void resetReceivers() {
        mAppSpecificLocaleChangeBroadcastReceiver.reset();
        mAppCreationInfoProvider.reset();
    }

    /**
     * Tests the scenario where the per-app locales are kept after the app upgrades.
     *
     * <p>If the per-app locales are set by a delegate selector and the same LocaleConfig is still
     * existed after the app upgrades, the per-app locales should be kept without clearing.
     */
    @Test
    public void testUpgradedApk_KeepLocaleConfig_keepAppLocales() throws Exception {
        install(APK_WITH_LOCALECONFIG);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        runWithShellPermissionIdentity(
                () -> sLocaleManager.setApplicationLocales(TEST_PACKAGE, expectedLocales),
                Manifest.permission.CHANGE_CONFIGURATION);

        install(APK_WITH_LOCALECONFIG);
        // Tell the app to fetch locales
        launchActivity(new ComponentName(TEST_PACKAGE, TEST_PACKAGE + ".MainActivity"),
                extraString(EXTRA_QUERY_LOCALES, TEST_PACKAGE));
        assertTrue(mAppCreationInfoProvider.await());
        mAppCreationInfoProvider.assertReceivedBroadcastContains(TEST_PACKAGE, expectedLocales);
    }

    /**
     * Tests the scenario where the per-app locales set from the app should be kept regardless of
     * whether there is a LocaleConfig before and after the app upgrades.
     *
     * <p>What we want to avoid is when the per-app locales are set from a delegate selector, and
     * the LocaleConfig is removed after the app upgrades, then previously set per-app locales can't
     * be changed. So if the per-app locales are set from the app in the same scenario, they will
     * still be kept without clearing.
     */
    @Test
    public void testUpgradedApk_setFromApp_KeepAppLocales() throws Exception {
        install(APK_WITHOUT_LOCALECONFIG);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        // Tell the app to set its app-specific locales
        launchActivity(new ComponentName(TEST_PACKAGE, TEST_PACKAGE + ".MainActivity"),
                extraString(EXTRA_SET_LOCALES, "fr"));

        install(APK_WITHOUT_LOCALECONFIG);
        Context appContext = sContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);
        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());

        // Tell the app to fetch locales
        launchActivity(new ComponentName(TEST_PACKAGE, TEST_PACKAGE + ".MainActivity"),
                extraString(EXTRA_QUERY_LOCALES, TEST_PACKAGE));
        assertTrue(mAppCreationInfoProvider.await());
        mAppCreationInfoProvider.assertReceivedBroadcastContains(TEST_PACKAGE, expectedLocales);
    }

    /**
     * Tests the scenario where the per-app locales set from a delegate selector should be cleared
     * when the LocaleConfig is removed after the app upgrades.
     *
     * <p>What we want to avoid is when the per-app locales are set from a delegate selector, and
     * the LocaleConfig is removed after the app upgrades, then previously set per-app locales can't
     * be changed.
     */
    @Test
    public void testUpgradedApk_removeLocaleConfig_ClearAppLocales() throws Exception {
        install(APK_WITH_LOCALECONFIG);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        String[] permissions = new String[]{Manifest.permission.CHANGE_CONFIGURATION,
                Manifest.permission.READ_APP_SPECIFIC_LOCALES};
        runWithShellPermissionIdentity(() -> {
            sLocaleManager.setApplicationLocales(TEST_PACKAGE, expectedLocales);
            mAppSpecificLocaleChangeBroadcastReceiver.await();
        }, permissions);
        mAppSpecificLocaleChangeBroadcastReceiver.assertReceivedBroadcastContains(TEST_PACKAGE,
                expectedLocales);
        mAppSpecificLocaleChangeBroadcastReceiver.reset();

        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        // so that we receive it.
        runWithShellPermissionIdentity(() -> {
            // Installation will clear per-app locale, which internally calls setApplicationLocales
            // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
            install(APK_WITHOUT_LOCALECONFIG);
            mAppSpecificLocaleChangeBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        mAppSpecificLocaleChangeBroadcastReceiver.assertReceivedBroadcastContains(TEST_PACKAGE,
                EMPTY_LOCALES);

        Context appContext = sContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);
        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());
    }

    /**
     * Tests the scenario where the per-app locales set from a delegate selector should be cleared
     * when the locales are removed from the LocaleConfig after the app upgrades.
     *
     * <p>What we want to avoid is when the per-app locales are set from a delegate selector, and
     * the locales are removed from the LocaleConfig after the app upgrades, it would confuse the
     * user since the removed locales are still shown to the user and the they are not present in
     * the delegate selector.
     */
    @Test
    public void testUpgradedApk_removeAppLocalesInLocaleConfig_ClearAppLocales() throws Exception {
        install(APK_WITH_LOCALECONFIG);
        LocaleList expectedLocales = LocaleList.forLanguageTags("fr");
        String[] permissions = new String[]{Manifest.permission.CHANGE_CONFIGURATION,
                Manifest.permission.READ_APP_SPECIFIC_LOCALES};
        runWithShellPermissionIdentity(() -> {
            sLocaleManager.setApplicationLocales(TEST_PACKAGE, expectedLocales);
            mAppSpecificLocaleChangeBroadcastReceiver.await();
        }, permissions);
        mAppSpecificLocaleChangeBroadcastReceiver.assertReceivedBroadcastContains(TEST_PACKAGE,
                expectedLocales);
        mAppSpecificLocaleChangeBroadcastReceiver.reset();

        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        // so that we receive it.
        runWithShellPermissionIdentity(() -> {
            // Installation will clear per-app locale, which internally calls setApplicationLocales
            // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
            install(APK_REMOVEAPPLOCALES_INLOCALECONFIG);
            mAppSpecificLocaleChangeBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        mAppSpecificLocaleChangeBroadcastReceiver.assertReceivedBroadcastContains(TEST_PACKAGE,
                EMPTY_LOCALES);
    }

    private void install(String apk) throws InterruptedException {
        String installResult = ShellUtils.runShellCommand("pm install -r " + apk);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    private void uninstall(String packageName) throws InterruptedException {
        String uninstallResult = ShellUtils.runShellCommand("pm uninstall " + packageName);
        assertThat(uninstallResult.trim()).isEqualTo("Success");
    }

    private static final class BlockingBroadcastReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private String mPackageName;
        private LocaleList mLocales;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(Intent.EXTRA_PACKAGE_NAME)) {
                mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            }
            if (intent.hasExtra(Intent.EXTRA_LOCALE_LIST)) {
                mLocales = intent.getParcelableExtra(Intent.EXTRA_LOCALE_LIST);
            }
            mLatch.countDown();
        }

        public boolean await() throws Exception {
            return mLatch.await(/* timeout= */ 5, TimeUnit.SECONDS);
        }

        public void reset() {
            mLatch = new CountDownLatch(1);
            mPackageName = null;
            mLocales = null;
        }

        /**
         * Verifies that the broadcast received in the relevant apps have the correct information
         * in the intent extras. It verifies the below extras:
         * <ul>
         * <li> {@link Intent#EXTRA_PACKAGE_NAME}
         * <li> {@link Intent#EXTRA_LOCALE_LIST}
         * </ul>
         */
        public void assertReceivedBroadcastContains(String expectedPackageName,
                                                    LocaleList expectedLocales) {
            assertEquals(expectedPackageName, mPackageName);
            assertEquals(expectedLocales, mLocales);
        }
    }
}
