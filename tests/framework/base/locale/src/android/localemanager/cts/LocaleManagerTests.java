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

package android.localemanager.cts;

import static android.localemanager.cts.util.LocaleConstants.CALLING_PACKAGE;
import static android.localemanager.cts.util.LocaleConstants.DEFAULT_APP_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.DEFAULT_SYSTEM_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.EXTRA_QUERY_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_APP_BROADCAST_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_APP_BROADCAST_RECEIVER;
import static android.localemanager.cts.util.LocaleConstants.INSTALLER_PACKAGE;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_BROADCAST_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_BROADCAST_RECEIVER;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_CONFIG_CHANGED_INFO_PROVIDER_ACTION;
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
import android.app.Activity;
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

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link android.app.LocaleManager} API(s).
 *
 * Build/Install/Run: atest CtsLocaleManagerTestCases
 */
@RunWith(AndroidJUnit4.class)
public class LocaleManagerTests extends ActivityManagerTestBase {
    private Context mContext;
    private LocaleManager mLocaleManager;

    /* System locales that were set on the device prior to running tests */
    private LocaleList mPreviousSystemLocales;

    /* Receiver to listen to the broadcast in the calling (instrumentation) app. */
    private BlockingBroadcastReceiver mCallingAppBroadcastReceiver;

    /* Receiver to listen to the response from the test app's broadcast receiver. */
    private BlockingBroadcastReceiver mTestAppBroadcastInfoProvider;

    /* Receiver to listen to the response from the test app's activity. */
    private BlockingBroadcastReceiver mTestAppCreationInfoProvider;

    /* Receiver to listen to the response from the test app's onConfigChanged method. */
    private BlockingBroadcastReceiver mTestAppConfigChangedInfoProvider;

    /* Receiver to listen to the response from the installer app. */
    private BlockingBroadcastReceiver mInstallerBroadcastInfoProvider;

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

        resetAppLocalesAsEmpty();
        AmUtils.waitForBroadcastIdle();

        mCallingAppBroadcastReceiver = new BlockingBroadcastReceiver();
        mTestAppBroadcastInfoProvider = new BlockingBroadcastReceiver();
        mInstallerBroadcastInfoProvider = new BlockingBroadcastReceiver();
        mTestAppCreationInfoProvider = new BlockingBroadcastReceiver();
        mTestAppConfigChangedInfoProvider = new BlockingBroadcastReceiver();

        mContext.registerReceiver(mCallingAppBroadcastReceiver,
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
        mContext.registerReceiver(mTestAppBroadcastInfoProvider,
                new IntentFilter(TEST_APP_BROADCAST_INFO_PROVIDER_ACTION));
        mContext.registerReceiver(mInstallerBroadcastInfoProvider,
                new IntentFilter(INSTALLER_APP_BROADCAST_INFO_PROVIDER_ACTION));
        mContext.registerReceiver(mTestAppCreationInfoProvider,
                new IntentFilter(TEST_APP_CREATION_INFO_PROVIDER_ACTION));
        mContext.registerReceiver(mTestAppConfigChangedInfoProvider,
                new IntentFilter(TEST_APP_CONFIG_CHANGED_INFO_PROVIDER_ACTION));

        setInstallerForPackage(CALLING_PACKAGE);
        setInstallerForPackage(TEST_APP_PACKAGE);

        bindToBroadcastReceiverOfApp(TEST_APP_PACKAGE, TEST_APP_BROADCAST_RECEIVER);
        bindToBroadcastReceiverOfApp(INSTALLER_PACKAGE, INSTALLER_APP_BROADCAST_RECEIVER);

        resetReceivers();
    }

    @After
    public void tearDown() throws Exception {
        unRegisterReceiver(mCallingAppBroadcastReceiver);
        unRegisterReceiver(mTestAppBroadcastInfoProvider);
        unRegisterReceiver(mInstallerBroadcastInfoProvider);
        unRegisterReceiver(mTestAppCreationInfoProvider);
        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setSystemLocales(mPreviousSystemLocales),
                Manifest.permission.CHANGE_CONFIGURATION, Manifest.permission.WRITE_SETTINGS);
    }

    private void unRegisterReceiver(BlockingBroadcastReceiver receiver) {
        if (receiver != null) {
            mContext.unregisterReceiver(receiver);
            receiver = null;
        }
    }

    /**
     * Send broadcast to given app's receiver with flag {@link Intent#FLAG_INCLUDE_STOPPED_PACKAGES}
     * and wait for it to be received.
     *
     * <p/> This is required since app is in stopped state after installation by tradefed,
     * and the receivers in another apps don't receive the broadcast without
     * FLAG_INCLUDE_STOPPED_PACKAGES because they have never been opened even once
     * after installation.
     * By doing this we make sure that app is un-stopped when the system sends the broadcasts
     * in the tests.
     */
    private void bindToBroadcastReceiverOfApp(String packageName, String broadcastReceiver) {
        final Intent intent = new Intent()
                .setComponent(new ComponentName(packageName, broadcastReceiver))
                .setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        CountDownLatch latch = new CountDownLatch(1);
        mContext.sendOrderedBroadcast(
                intent,
                null /* receiverPermission */,
                new BroadcastReceiver() { /* resultReceiver */
                    @Override public void onReceive(Context context, Intent intent) {
                        latch.countDown();
                    }
                },
                null /* scheduler */,
                Activity.RESULT_OK, /* initialCode */
                null /* initialData */,
                null /* initialExtras */);
        try {
            assertTrue("Timed out waiting for test broadcast to be received",
                    latch.await(5_000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
    }

    /**
     * Sets the installer as {@link #INSTALLER_PACKAGE} for the target package.
     */
    private void setInstallerForPackage(String targetPackageName) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mContext.getPackageManager(),
                (pm) -> pm.setInstallerPackageName(targetPackageName, INSTALLER_PACKAGE));
    }

    /**
     * Resets the countdown latch in all the receivers.
     */
    private void resetReceivers() {
        mCallingAppBroadcastReceiver.reset();
        mInstallerBroadcastInfoProvider.reset();
        mTestAppBroadcastInfoProvider.reset();
        mTestAppCreationInfoProvider.reset();
        mTestAppConfigChangedInfoProvider.reset();
    }

    @Test
    public void testSetApplicationLocales_persistsAndSendsBroadcast() throws Exception {
        mLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);

        assertLocalesCorrectlySetForCallingApp(DEFAULT_APP_LOCALES);
        mCallingAppBroadcastReceiver.await();
        assertReceivedBroadcastContains(mCallingAppBroadcastReceiver,
                CALLING_PACKAGE, DEFAULT_APP_LOCALES);

        mInstallerBroadcastInfoProvider.await();
        assertReceivedBroadcastContains(mInstallerBroadcastInfoProvider,
                CALLING_PACKAGE, DEFAULT_APP_LOCALES);
    }

    @Test
    public void testSetApplicationLocales_getDefaultLocaleList_returnsCorrectList()
            throws Exception {
        // Fetch the current system locales when there are no app-locales set.
        LocaleList systemLocales = LocaleList.getDefault();
        assertEquals(DEFAULT_SYSTEM_LOCALES, systemLocales);

        mLocaleManager.setApplicationLocales(DEFAULT_APP_LOCALES);

        // Wait for a while since LocaleList::getDefault could take a while to
        // reflect the new app locales.
        mCallingAppBroadcastReceiver.await();
        assertEquals(combineLocales(DEFAULT_APP_LOCALES, systemLocales), LocaleList.getDefault());
    }

    @Test
    public void testSetApplicationLocales_forAnotherAppInForeground_persistsAndSendsBroadcast()
            throws Exception {
        // Bring the TestApp to the foreground by invoking an activity and verify its visibility.
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        mWmState.assertVisibility(TEST_APP_MAIN_ACTIVITY, /* visible*/ true);

        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);
        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

        mTestAppBroadcastInfoProvider.await();
        assertReceivedBroadcastContains(mTestAppBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);

        mInstallerBroadcastInfoProvider.await();
        assertReceivedBroadcastContains(mInstallerBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);
    }

    @Test
    public void testSetApplicationLocales_forAnotherAppInBackground_persistsAndSendsBroadcast()
            throws Exception {
        // Invoke the app by launching an activity.
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        // Send the TestApp to the background.
        launchHomeActivity();
        mWmState.waitAndAssertVisibilityGone(TEST_APP_MAIN_ACTIVITY);

        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);

        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

        mTestAppBroadcastInfoProvider.await();
        assertReceivedBroadcastContains(mTestAppBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);

        mInstallerBroadcastInfoProvider.await();
        assertReceivedBroadcastContains(mInstallerBroadcastInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);
    }

    @Test
    public void testSetApplicationLocales_forAnotherApp_persistsOnAppRestart() throws Exception {
        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);

        // Re-start the app by starting an activity and check if locales correctly
        // received by the app and listen to the broadcast for result from the app.
        launchActivity(TEST_APP_MAIN_ACTIVITY, extraString(EXTRA_QUERY_LOCALES, "true"));

        mTestAppCreationInfoProvider.await();
        assertReceivedBroadcastContains(mTestAppCreationInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);
    }

    @Test
    public void
            testSetApplicationLocales_wthoutPermissionforAnotherApp_throwsExceptionAndNoBroadcast()
            throws Exception {
        try {
            mLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);
        } catch (SecurityException e) {
            // expected as not having appropriate permission to change locales for another app.
        }

        // Since the locales weren't allowed to persist, no broadcasts should be sent by the system.
        mTestAppBroadcastInfoProvider.assertNoBroadcastReceived();
        mInstallerBroadcastInfoProvider.assertNoBroadcastReceived();
    }

    @Test
    public void testSetApplicationLocales_forAnotherAppInForeground_callsOnConfigChanged()
            throws Exception {
        // Bring the TestApp to the foreground by invoking an activity and verify its visibility.
        launchActivity(TEST_APP_MAIN_ACTIVITY);
        mWmState.assertVisibility(TEST_APP_MAIN_ACTIVITY, /* visible*/ true);

        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setApplicationLocales(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES),
                Manifest.permission.CHANGE_CONFIGURATION);
        assertLocalesCorrectlySetForAnotherApp(TEST_APP_PACKAGE, DEFAULT_APP_LOCALES);

        mTestAppConfigChangedInfoProvider.await();
        assertReceivedBroadcastContains(mTestAppConfigChangedInfoProvider, TEST_APP_PACKAGE,
                DEFAULT_APP_LOCALES);
    }

    @Test(expected = SecurityException.class)
    public void testGetApplicationLocales_withoutPermissionforAnotherApp_throwsException()
            throws Exception {
        mLocaleManager.getApplicationLocales(TEST_APP_PACKAGE);
        fail("Expected SecurityException due to not having appropriate permission "
                + "for querying locales of another app.");
    }

    @Test
    public void testGetApplicationLocales_noAppLocalesSet_returnsEmptyList() {
        // When app-specific locales aren't set, we expect get api to return empty list
        // and not throw any error.
        assertEquals(LocaleList.getEmptyLocaleList(), mLocaleManager.getApplicationLocales());
    }

    /**
     * Verifies that the locales are correctly set for calling(instrumentation) app
     * by fetching locales of the app with a binder call.
     */
    private void assertLocalesCorrectlySetForCallingApp(LocaleList expectedLocales) {
        assertEquals(expectedLocales, mLocaleManager.getApplicationLocales());
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
                mLocaleManager.getApplicationLocales(packageName),
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

    /**
     * Creates a combined {@link LocaleList} by placing app locales before system locales and
     * dropping any duplicates.
     *
     * We need to combine them since {@link LocaleList} does not have any direct way like
     * a normal list to check if locale or subset of locales is present.
     */
    private  LocaleList combineLocales(LocaleList appLocales, LocaleList systemLocales) {
        Locale[] combinedLocales = new Locale[appLocales.size() + systemLocales.size()];
        for (int i = 0; i < appLocales.size(); i++) {
            combinedLocales[i] = appLocales.get(i);
        }
        for (int i = 0; i < systemLocales.size(); i++) {
            combinedLocales[i + appLocales.size()] = systemLocales.get(i);
        }
        // Constructor of {@link LocaleList} removes duplicates.
        return new LocaleList(combinedLocales);
    }

    /**
     * Reset the locales for the apps to empty list as they could have been set previously
     * by some other test.
     */
    private void resetAppLocalesAsEmpty() throws Exception {
        // Reset locales for the calling app.
        mLocaleManager.setApplicationLocales(LocaleList.getEmptyLocaleList());

        // Reset locales for the TestApp.
        runWithShellPermissionIdentity(() ->
                        mLocaleManager.setApplicationLocales(TEST_APP_PACKAGE,
                                LocaleList.getEmptyLocaleList()),
                Manifest.permission.CHANGE_CONFIGURATION);
    }
}
