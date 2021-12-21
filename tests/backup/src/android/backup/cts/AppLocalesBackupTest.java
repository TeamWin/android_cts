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

package android.backup.cts;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.Manifest;
import android.app.LocaleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.LocaleList;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull
public class AppLocalesBackupTest extends BaseBackupCtsTest {
    private static final String APK_PATH = "/data/local/tmp/cts/backup/";
    private static final String TEST_APP_APK_1 = APK_PATH + "CtsAppLocalesBackupApp1.apk";
    private static final String TEST_APP_PACKAGE_1 =
            "android.cts.backup.applocalesbackupapp1";

    private static final String TEST_APP_APK_2 = APK_PATH + "CtsAppLocalesBackupApp2.apk";
    private static final String TEST_APP_PACKAGE_2 =
            "android.cts.backup.applocalesbackupapp2";
    private static final String SYSTEM_PACKAGE = "android";

    private static final LocaleList DEFAULT_LOCALES_1 = LocaleList.forLanguageTags("hi-IN,de-DE");
    private static final LocaleList DEFAULT_LOCALES_2 = LocaleList.forLanguageTags("fr-CA");
    private static final LocaleList EMPTY_LOCALES = LocaleList.getEmptyLocaleList();

    // An identifier for the backup dataset. Since we're using localtransport, it's set to "1".
    private static final String RESTORE_TOKEN = "1";

    private Context mContext;
    private LocaleManager mLocaleManager;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mContext = InstrumentationRegistry.getTargetContext();
        mLocaleManager = mContext.getSystemService(LocaleManager.class);

        install(TEST_APP_APK_1);
        install(TEST_APP_APK_2);
    }

    @After
    public void tearDown() throws Exception {
        uninstall(TEST_APP_PACKAGE_1);
        uninstall(TEST_APP_PACKAGE_2);
    }

    /**
     * Tests the scenario where all apps are installed on the device when restore is triggered.
     *
     * <p>In this case, all the apps should have their locales restored as soon as the restore
     * operation finishes. The only condition is that the apps should not have the locales set
     * already before restore.
     */
    public void testBackupRestore_allAppsInstalledNoAppLocalesSet_restoresImmediately()
            throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        resetAppLocales();

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        assertLocalesForApp(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);
    }

    /**
     * Tests the scenario where the user sets the app-locales before the restore could be applied.
     *
     * <p>The locales from the backup data should be ignored in this case.
     */
    public void testBackupRestore_localeAlreadySet_doesNotRestore() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        LocaleList newLocales = LocaleList.forLanguageTags("zh,hi");
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, newLocales);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_2, EMPTY_LOCALES);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // Should restore only for app_2.
        assertLocalesForApp(TEST_APP_PACKAGE_1, newLocales);
        assertLocalesForApp(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);
    }

    /**
     * Tests the scenario when some apps are installed after the restore finishes.
     *
     * <p>More specifically, this tests the lazy restore where the locales are fetched and
     * restored from the stage file if the app is installed within a certain amount of time after
     * the initial restore.
     */
    public void testBackupRestore_appInstalledAfterRestore_doesLazyRestore() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        resetAppLocales();

        uninstall(TEST_APP_PACKAGE_2);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // Locales for App1 should be restored immediately since that's present already.
        assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);

        // This is to ensure there are no lingering broadcasts (could be from the setUp method
        // where we are calling setApplicationLocales).
        AmUtils.waitForBroadcastIdle();

        BlockingBroadcastReceiver appSpecificLocaleBroadcastReceiver =
                new BlockingBroadcastReceiver();
        mContext.registerReceiver(appSpecificLocaleBroadcastReceiver,
                new IntentFilter(Intent.ACTION_APPLICATION_LOCALE_CHANGED));

        // Hold Manifest.permission.READ_APP_SPECIFIC_LOCALES while the broadcast is sent,
        // so that we receive it.
        runWithShellPermissionIdentity(() -> {
            // Installation will trigger lazy restore, which internally calls setApplicationLocales
            // which sends out the ACTION_APPLICATION_LOCALE_CHANGED broadcast.
            install(TEST_APP_APK_2);
            appSpecificLocaleBroadcastReceiver.await();
        }, Manifest.permission.READ_APP_SPECIFIC_LOCALES);

        appSpecificLocaleBroadcastReceiver.assertOneBroadcastReceived();
        appSpecificLocaleBroadcastReceiver.assertReceivedBroadcastContains(TEST_APP_PACKAGE_2,
                DEFAULT_LOCALES_2);

        // Verify that lazy restore occurred upon package install.
        assertLocalesForApp(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);

        // APP2's entry is removed from the stage file after restore so nothing should be restored
        // when APP2 is installed for the second time.
        uninstall(TEST_APP_PACKAGE_2);
        install(TEST_APP_APK_2);
        assertLocalesForApp(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
    }

    /**
     * Tests the scenario when an application is removed from the device.
     *
     * <p>The data for the uninstalled app should be removed from the next backup pass.
     */
    public void testBackupRestore_uninstallApp_deletesDataFromBackup() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupDefaultAppLocales();

        // Uninstall an app and run the backup pass. The locales for the uninstalled app should
        // be removed from the backup.
        uninstall(TEST_APP_PACKAGE_2);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        getBackupUtils().backupNowAndAssertSuccess(SYSTEM_PACKAGE);

        install(TEST_APP_APK_2);
        // Remove app1's locales so that it can be restored.
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, EMPTY_LOCALES);

        getBackupUtils().restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // Restores only app1's locales because app2's data is no longer present in the backup.
        assertLocalesForApp(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        assertLocalesForApp(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
    }

    // TODO(b/210593602): Add a test to check staged data removal after the retention period.

    private void setApplicationLocalesAndVerify(String packageName, LocaleList locales)
            throws Exception {
        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setApplicationLocales(packageName, locales),
                Manifest.permission.CHANGE_CONFIGURATION);
        assertLocalesForApp(packageName, locales);
    }

    /**
     * Verifies that the locales are correctly set for another package
     * by fetching locales of the app with a binder call.
     */
    private void assertLocalesForApp(String packageName,
            LocaleList expectedLocales) throws Exception {
        assertEquals(expectedLocales, getApplicationLocales(packageName));
    }

    private LocaleList getApplicationLocales(String packageName) throws Exception {
        return callWithShellPermissionIdentity(() ->
                        mLocaleManager.getApplicationLocales(packageName),
                Manifest.permission.READ_APP_SPECIFIC_LOCALES);
    }

    private void install(String apk) {
        ShellUtils.runShellCommand("pm install -r " + apk);
    }

    private void uninstall(String packageName) {
        ShellUtils.runShellCommand("pm uninstall " + packageName);
    }

    private void setAndBackupDefaultAppLocales() throws Exception {
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, DEFAULT_LOCALES_1);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_2, DEFAULT_LOCALES_2);
        // Backup the data for SYSTEM_PACKAGE which includes app-locales.
        getBackupUtils().backupNowAndAssertSuccess(SYSTEM_PACKAGE);
    }

    private void resetAppLocales() throws Exception {
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_1, EMPTY_LOCALES);
        setApplicationLocalesAndVerify(TEST_APP_PACKAGE_2, EMPTY_LOCALES);
    }

    private static final class BlockingBroadcastReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private String mPackageName;
        private LocaleList mLocales;
        private int mCalls;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(Intent.EXTRA_PACKAGE_NAME)) {
                mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            }
            if (intent.hasExtra(Intent.EXTRA_LOCALE_LIST)) {
                mLocales = intent.getParcelableExtra(Intent.EXTRA_LOCALE_LIST);
            }
            mCalls += 1;
            mLatch.countDown();
        }

        public void await() throws Exception {
            mLatch.await(/* timeout= */ 5, TimeUnit.SECONDS);
        }

        public void reset() {
            mLatch = new CountDownLatch(1);
            mCalls = 0;
            mPackageName = null;
            mLocales = null;
        }

        public void assertOneBroadcastReceived() {
            assertEquals(1, mCalls);
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
