/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.suspendapps.cts;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.content.Intent.EXTRA_PACKAGE_NAME;
import static android.content.pm.SuspendDialogInfo.BUTTON_ACTION_UNSUSPEND;
import static android.os.UserManager.DISALLOW_APPS_CONTROL;
import static android.os.UserManager.DISALLOW_UNINSTALL_APPS;
import static android.suspendapps.cts.Constants.ACTION_REPORT_MORE_DETAILS_ACTIVITY_STARTED;
import static android.suspendapps.cts.Constants.ACTION_REPORT_PACKAGE_UNSUSPENDED_MANUALLY;
import static android.suspendapps.cts.Constants.ALL_TEST_PACKAGES;
import static android.suspendapps.cts.Constants.DEVICE_ADMIN_COMPONENT;
import static android.suspendapps.cts.Constants.DEVICE_ADMIN_PACKAGE;
import static android.suspendapps.cts.Constants.EXTRA_RECEIVED_PACKAGE_NAME;
import static android.suspendapps.cts.Constants.TEST_APP_PACKAGE_NAME;
import static android.suspendapps.cts.Constants.TEST_PACKAGE_ARRAY;
import static android.suspendapps.cts.SuspendTestUtils.addAndAssertProfileOwner;
import static android.suspendapps.cts.SuspendTestUtils.createSingleKeyBundle;
import static android.suspendapps.cts.SuspendTestUtils.removeDeviceAdmin;
import static android.suspendapps.cts.SuspendTestUtils.requestDpmAction;

import static com.android.suspendapps.suspendtestapp.SuspendTestActivity.ACTION_REPORT_TEST_ACTIVITY_STARTED;
import static com.android.suspendapps.suspendtestapp.SuspendTestActivity.ACTION_REPORT_TEST_ACTIVITY_STOPPED;
import static com.android.suspendapps.suspendtestapp.SuspendTestReceiver.ACTION_REPORT_MY_PACKAGE_SUSPENDED;
import static com.android.suspendapps.suspendtestapp.SuspendTestReceiver.ACTION_REPORT_MY_PACKAGE_UNSUSPENDED;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.ACTION_ADD_USER_RESTRICTION;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.ACTION_BLOCK_UNINSTALL;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.ACTION_SUSPEND;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.ACTION_UNBLOCK_UNINSTALL;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.ACTION_UNSUSPEND;
import static com.android.suspendapps.testdeviceadmin.TestCommsReceiver.EXTRA_USER_RESTRICTION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.SuspendDialogInfo;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.suspendapps.cts.R;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;
import com.android.suspendapps.suspendtestapp.SuspendTestActivity;
import com.android.suspendapps.suspendtestapp.SuspendTestReceiver;

import libcore.util.EmptyArray;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class SuspendPackagesTest {
    private static final String TEST_APP_LABEL = "Suspend Test App";

    private Context mContext;
    private PackageManager mPackageManager;
    private AppOpsManager mAppOpsManager;
    private Handler mReceiverHandler;
    private AppCommunicationReceiver mAppCommsReceiver;
    private UiDevice mUiDevice;

    /** Do not use with {@link #mAppCommsReceiver} in the same test as both use the same handler. */
    private Bundle requestAppAction(String action) throws InterruptedException {
        final AtomicReference<Bundle> result = new AtomicReference<>();
        final CountDownLatch receiverLatch = new CountDownLatch(1);
        final ComponentName testReceiverComponent = new ComponentName(TEST_APP_PACKAGE_NAME,
                SuspendTestReceiver.class.getCanonicalName());
        final Intent broadcastIntent = new Intent(action)
                .setComponent(testReceiverComponent)
                .setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendOrderedBroadcast(broadcastIntent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result.set(getResultExtras(true));
                receiverLatch.countDown();
            }
        }, mReceiverHandler, 0, null, null);

        assertTrue("Test receiver timed out ", receiverLatch.await(5, TimeUnit.SECONDS));
        return result.get();
    }

    private PersistableBundle getExtras(String keyPrefix, long lval, String sval, double dval) {
        final PersistableBundle extras = new PersistableBundle(3);
        extras.putLong(keyPrefix + ".LONG_VALUE", lval);
        extras.putDouble(keyPrefix + ".DOUBLE_VALUE", dval);
        extras.putString(keyPrefix + ".STRING_VALUE", sval);
        return extras;
    }

    private void startTestAppActivity(@Nullable Bundle extras) {
        final Intent testActivity = new Intent()
                .setComponent(new ComponentName(TEST_APP_PACKAGE_NAME,
                        SuspendTestActivity.class.getCanonicalName()))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) {
            testActivity.putExtras(extras);
        }
        mContext.startActivity(testActivity);
    }

    private static boolean areSameExtras(BaseBundle expected, BaseBundle received) {
        if (expected == null || received == null) {
            return expected == received;
        }
        final Set<String> keys = expected.keySet();
        if (keys.size() != received.keySet().size()) {
            return false;
        }
        for (String key : keys) {
            if (!Objects.equals(expected.get(key), received.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static void assertSameExtras(String message, BaseBundle expected, BaseBundle received) {
        if (!areSameExtras(expected, received)) {
            fail(message + ": [expected: " + expected + "; received: " + received + "]");
        }
    }

    private void addAndAssertDeviceOwner() {
        SystemUtil.runShellCommand("dpm set-device-owner --user cur " + DEVICE_ADMIN_COMPONENT,
                output -> output.startsWith("Success"));
    }

    private void addAndAssertDeviceAdmin() {
        SystemUtil.runShellCommand("dpm set-active-admin --user cur " + DEVICE_ADMIN_COMPONENT,
                output -> output.startsWith("Success"));
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mPackageManager = mContext.getPackageManager();
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mReceiverHandler = new Handler(Looper.getMainLooper());
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mAppCommsReceiver = new AppCommunicationReceiver(mContext);
    }

    @Test
    public void testIsPackageSuspended() throws Exception {
        SuspendTestUtils.suspend(null, null, null);
        assertTrue("isPackageSuspended is false",
                mPackageManager.isPackageSuspended(TEST_APP_PACKAGE_NAME));
    }

    @Test
    public void testSuspendedStateFromApp() throws Exception {
        Bundle resultFromApp = requestAppAction(SuspendTestReceiver.ACTION_GET_SUSPENDED_STATE);
        assertFalse(resultFromApp.getBoolean(SuspendTestReceiver.EXTRA_SUSPENDED, true));
        assertNull(resultFromApp.getBundle(SuspendTestReceiver.EXTRA_SUSPENDED_APP_EXTRAS));

        final PersistableBundle appExtras = getExtras("testSuspendedStateFromApp", 20, "20", 0.2);
        SuspendTestUtils.suspend(appExtras, null, null);

        resultFromApp = requestAppAction(SuspendTestReceiver.ACTION_GET_SUSPENDED_STATE);
        assertTrue("resultFromApp:suspended is false",
                resultFromApp.getBoolean(SuspendTestReceiver.EXTRA_SUSPENDED));
        final Bundle receivedAppExtras =
                resultFromApp.getBundle(SuspendTestReceiver.EXTRA_SUSPENDED_APP_EXTRAS);
        assertSameExtras("Received app extras different to the ones supplied",
                appExtras, receivedAppExtras);
    }

    @Test
    public void testMyPackageSuspendedUnsuspended() throws Exception {
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MY_PACKAGE_SUSPENDED,
                ACTION_REPORT_MY_PACKAGE_UNSUSPENDED);
        mAppCommsReceiver.drainPendingBroadcasts();
        final PersistableBundle appExtras = getExtras("testMyPackageSuspendBroadcasts", 1, "1", .1);
        SuspendTestUtils.suspend(appExtras, null, null);
        Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        assertEquals("MY_PACKAGE_SUSPENDED delivery not reported",
                ACTION_REPORT_MY_PACKAGE_SUSPENDED, intentFromApp.getAction());
        assertSameExtras("Received app extras different to the ones supplied", appExtras,
                intentFromApp.getBundleExtra(SuspendTestReceiver.EXTRA_SUSPENDED_APP_EXTRAS));
        SuspendTestUtils.unsuspendAll();
        intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        assertEquals("MY_PACKAGE_UNSUSPENDED delivery not reported",
                ACTION_REPORT_MY_PACKAGE_UNSUSPENDED, intentFromApp.getAction());
    }

    @Test
    public void testUpdatingAppExtras() throws Exception {
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MY_PACKAGE_SUSPENDED);
        final PersistableBundle extras1 = getExtras("testMyPackageSuspendedOnChangingExtras", 1,
                "1", 0.1);
        SuspendTestUtils.suspend(extras1, null, null);
        Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        assertEquals("MY_PACKAGE_SUSPENDED delivery not reported",
                ACTION_REPORT_MY_PACKAGE_SUSPENDED, intentFromApp.getAction());
        assertSameExtras("Received app extras different to the ones supplied", extras1,
                intentFromApp.getBundleExtra(SuspendTestReceiver.EXTRA_SUSPENDED_APP_EXTRAS));
        final PersistableBundle extras2 = getExtras("testMyPackageSuspendedOnChangingExtras", 2,
                "2", 0.2);
        SuspendTestUtils.suspend(extras2, null, null);
        intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        assertEquals("MY_PACKAGE_SUSPENDED delivery not reported",
                ACTION_REPORT_MY_PACKAGE_SUSPENDED, intentFromApp.getAction());
        assertSameExtras("Received app extras different to the updated extras", extras2,
                intentFromApp.getBundleExtra(SuspendTestReceiver.EXTRA_SUSPENDED_APP_EXTRAS));
    }

    @Test
    public void testCannotSuspendSelf() throws Exception {
        final String[] self = new String[]{mContext.getPackageName()};
        SuspendTestUtils.suspendAndAssertResult(self, null, null, null, self);
    }

    @Test
    public void testActivityStoppedOnSuspend() throws Exception {
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_TEST_ACTIVITY_STARTED,
                ACTION_REPORT_TEST_ACTIVITY_STOPPED);
        startTestAppActivity(null);
        Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        assertEquals("Test activity start not reported",
                ACTION_REPORT_TEST_ACTIVITY_STARTED, intentFromApp.getAction());
        SuspendTestUtils.suspend(null, null, null);
        intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        assertEquals("Test activity stop not reported on suspending the test app",
                ACTION_REPORT_TEST_ACTIVITY_STOPPED, intentFromApp.getAction());
    }

    @Test
    public void testCanSuspendWhenProfileOwner() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        addAndAssertProfileOwner();
        SuspendTestUtils.suspend(null, null, null);
    }

    @Test
    public void testCanSuspendWhenDeviceOwner() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        addAndAssertDeviceOwner();
        SuspendTestUtils.suspend(null, null, null);
    }

    private int getCurrentUserId() {
        final String result = SystemUtil.runShellCommand("am get-current-user");
        return Integer.parseInt(result.trim());
    }

    @Test
    public void testLauncherCallback() throws Exception {
        final PersistableBundle suppliedExtras = getExtras("testLauncherCallback", 2, "2", 0.2);
        final int currentUserId = getCurrentUserId();
        final AtomicReference<String> callBackErrors = new AtomicReference<>("");
        final CountDownLatch oldCallbackLatch = new CountDownLatch(1);
        final CountDownLatch newCallbackLatch = new CountDownLatch(1);
        LauncherApps.Callback testCallback = new StubbedCallback() {
            @Override
            public void onPackagesSuspended(String[] packageNames, UserHandle user) {
                oldCallbackLatch.countDown();
            }

            @Override
            public void onPackagesSuspended(String[] packageNames, UserHandle user,
                    Bundle launcherExtras) {
                final StringBuilder errorString = new StringBuilder();
                if (!Arrays.equals(packageNames, TEST_PACKAGE_ARRAY)) {
                    errorString.append("Received unexpected packageNames in onPackagesSuspended: ");
                    errorString.append(Arrays.toString(packageNames));
                    errorString.append(". ");
                }
                if (user.getIdentifier() != currentUserId) {
                    errorString.append("Received wrong user " + user.getIdentifier()
                            + ", current user: " + currentUserId + ". ");
                }
                if (!areSameExtras(launcherExtras, suppliedExtras)) {
                    errorString.append("Unexpected launcherExtras, supplied: " + suppliedExtras
                            + ", received: " + launcherExtras + ". ");
                }
                callBackErrors.set(errorString.toString());
                newCallbackLatch.countDown();
            }
        };
        final LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
        launcherApps.registerCallback(testCallback, mReceiverHandler);
        SuspendTestUtils.suspend(null, suppliedExtras, null);
        assertFalse("Old callback invoked", oldCallbackLatch.await(2, TimeUnit.SECONDS));
        assertTrue("New callback not invoked", newCallbackLatch.await(2, TimeUnit.SECONDS));
        final String errors = callBackErrors.get();
        assertTrue("Callbacks did not complete as expected: " + errors, errors.isEmpty());
        launcherApps.unregisterCallback(testCallback);
    }

    private void turnScreenOn() throws Exception {
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        SystemUtil.runShellCommandForNoOutput("wm dismiss-keyguard");
    }

    @Test
    public void testInterceptorActivity_moreDetails() throws Exception {
        turnScreenOn();
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MORE_DETAILS_ACTIVITY_STARTED,
                ACTION_REPORT_TEST_ACTIVITY_STARTED);
        final SuspendDialogInfo dialogInfo = new SuspendDialogInfo.Builder()
                .setIcon(R.drawable.ic_settings)
                .setTitle(R.string.dialog_title)
                .setMessage(R.string.dialog_message)
                .setNeutralButtonText(R.string.more_details_button_text)
                .build();
        SuspendTestUtils.suspend(null, null, dialogInfo);
        startTestAppActivity(null);
        // Test activity should not start
        assertNull("No broadcast was expected from app", mAppCommsReceiver.pollForIntent(2));

        // The dialog should have correct specifications
        final String expectedTitle = mContext.getResources().getString(R.string.dialog_title);
        final String expectedMessage = mContext.getResources().getString(R.string.dialog_message,
                TEST_APP_LABEL);
        final String expectedButtonText = mContext.getResources().getString(
                R.string.more_details_button_text);

        assertNotNull("Given dialog title: " + expectedTitle + " not shown",
                mUiDevice.wait(Until.findObject(By.text(expectedTitle)), 5000));
        assertNotNull("Given dialog message: " + expectedMessage + " not shown",
                mUiDevice.findObject(By.text(expectedMessage)));
        // Sometimes, button texts can have styles that override case (e.g. b/134033532)
        final Pattern buttonTextIgnoreCase = Pattern.compile(Pattern.quote(expectedButtonText),
                Pattern.CASE_INSENSITIVE);
        final UiObject2 moreDetailsButton = mUiDevice.findObject(
                By.clickable(true).text(buttonTextIgnoreCase));
        assertNotNull(expectedButtonText + " button not shown", moreDetailsButton);

        // Tapping on the neutral button should start the correct intent.
        moreDetailsButton.click();
        final Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        assertEquals("More details activity start not reported",
                ACTION_REPORT_MORE_DETAILS_ACTIVITY_STARTED, intentFromApp.getAction());
        final String receivedPackageName = intentFromApp.getStringExtra(
                EXTRA_RECEIVED_PACKAGE_NAME);
        assertEquals("Wrong package name received by \'More Details\' activity",
                TEST_APP_PACKAGE_NAME, receivedPackageName);
    }

    @Test
    public void testInterceptorActivity_unsuspend() throws Exception {
        turnScreenOn();
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_PACKAGE_UNSUSPENDED_MANUALLY,
                ACTION_REPORT_TEST_ACTIVITY_STARTED);
        final SuspendDialogInfo dialogInfo = new SuspendDialogInfo.Builder()
                .setIcon(R.drawable.ic_settings)
                .setTitle(R.string.dialog_title)
                .setMessage(R.string.dialog_message)
                .setNeutralButtonText(R.string.unsuspend_button_text)
                .setNeutralButtonAction(BUTTON_ACTION_UNSUSPEND)
                .build();
        SuspendTestUtils.suspend(null, null, dialogInfo);
        final Bundle extrasForStart = new Bundle(getExtras("unsuspend", 90, "sval", 0.9));
        startTestAppActivity(extrasForStart);
        // Test activity should not start. Not yet.
        assertNull("No broadcast was expected from app", mAppCommsReceiver.pollForIntent(2));

        final String expectedTitle = mContext.getResources().getString(R.string.dialog_title);
        final String expectedButtonText = mContext.getResources().getString(
                R.string.unsuspend_button_text);

        assertNotNull("Given dialog title: " + expectedTitle + " not shown",
                mUiDevice.wait(Until.findObject(By.text(expectedTitle)), 5000));

        // Sometimes, button texts can have styles that override case (e.g. b/134033532)
        final Pattern buttonTextIgnoreCase = Pattern.compile(Pattern.quote(expectedButtonText),
                Pattern.CASE_INSENSITIVE);
        final UiObject2 unsuspendButton = mUiDevice.findObject(
                By.clickable(true).text(buttonTextIgnoreCase));
        assertNotNull(expectedButtonText + " button not shown", unsuspendButton);

        // Tapping on the neutral button should:
        // 1. Unsuspend the test app
        // 2. Launch the previously intercepted intent
        // 3. Tell the suspending package that the test app was unsuspended
        unsuspendButton.click();

        final ArrayMap<String, Bundle> receivedActionExtras = new ArrayMap<>(2);
        Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Did not receive any broadcast from test app", intentFromApp);
        receivedActionExtras.put(intentFromApp.getAction(), intentFromApp.getExtras());

        intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("Expecting 2 broadcasts, received only 1", intentFromApp);
        receivedActionExtras.put(intentFromApp.getAction(), intentFromApp.getExtras());

        assertTrue("Test activity start not reported",
                receivedActionExtras.containsKey(ACTION_REPORT_TEST_ACTIVITY_STARTED));
        assertSameExtras("Did not receive extras supplied to startActivity on unsuspend",
                extrasForStart, receivedActionExtras.get(ACTION_REPORT_TEST_ACTIVITY_STARTED));

        assertTrue("PACKAGE_UNSUSPENDED_MANUALLY not reported",
                receivedActionExtras.containsKey(ACTION_REPORT_PACKAGE_UNSUSPENDED_MANUALLY));
        final Bundle extras = receivedActionExtras.get(ACTION_REPORT_PACKAGE_UNSUSPENDED_MANUALLY);
        assertNotNull("No extras with ACTION_REPORT_PACKAGE_UNSUSPENDED_MANUALLY", extras);
        assertEquals("Did not receive unsuspended package name", TEST_APP_PACKAGE_NAME,
                extras.getString(EXTRA_RECEIVED_PACKAGE_NAME));

        assertFalse("Test package still suspended",
                mPackageManager.isPackageSuspended(TEST_APP_PACKAGE_NAME));

        intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNull("Expecting only 2 broadcasts, received one more ", intentFromApp);
    }

    @Test
    public void testTestAppsSuspendable() throws Exception {
        final String[] unsuspendablePkgs = SystemUtil.callWithShellPermissionIdentity(() ->
                mPackageManager.getUnsuspendablePackages(ALL_TEST_PACKAGES));
        assertTrue("Normal test apps unsuspendable: " + Arrays.toString(unsuspendablePkgs),
                unsuspendablePkgs.length == 0);
    }

    @Test
    public void testDeviceAdminUnsuspendable() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        addAndAssertDeviceAdmin();
        final String[] unsuspendablePkgs = SystemUtil.callWithShellPermissionIdentity(() ->
                mPackageManager.getUnsuspendablePackages(new String[]{DEVICE_ADMIN_PACKAGE}));
        assertTrue("Device admin suspendable", (unsuspendablePkgs.length == 1)
                && DEVICE_ADMIN_PACKAGE.equals(unsuspendablePkgs[0]));
    }

    private void assertOpDisallowed(int op) throws Exception {
        final int testUid = mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0);
        final int mode = SystemUtil.callWithShellPermissionIdentity(
                () -> mAppOpsManager.checkOpNoThrow(op, testUid, TEST_APP_PACKAGE_NAME));
        assertNotEquals("Op " + AppOpsManager.opToName(op) + " allowed while package is suspended",
                MODE_ALLOWED, mode);
    }

    @Test
    public void testOpRecordAudioOnSuspend() throws Exception {
        final int recordAudioOp = AppOpsManager.permissionToOpCode(
                Manifest.permission.RECORD_AUDIO);
        SuspendTestUtils.suspend(null, null, null);
        assertOpDisallowed(recordAudioOp);
    }

    @Test
    public void testOpCameraOnSuspend() throws Exception {
        final int cameraOp = AppOpsManager.permissionToOpCode(Manifest.permission.CAMERA);
        SuspendTestUtils.suspend(null, null, null);
        assertOpDisallowed(cameraOp);
    }

    @Test
    public void testCannotSuspendWhenUninstallBlocked() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        addAndAssertProfileOwner();
        final Bundle extras = createSingleKeyBundle(EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME);
        assertTrue("Block uninstall request failed", requestDpmAction(
                ACTION_BLOCK_UNINSTALL, extras, mReceiverHandler));
        SuspendTestUtils.suspendAndAssertResult(ALL_TEST_PACKAGES, null, null, null,
                TEST_PACKAGE_ARRAY);
    }

    private void assertCannotSuspendUnderUserRestriction(String userRestriction) throws Exception {
        addAndAssertProfileOwner();
        final Bundle extras = createSingleKeyBundle(EXTRA_USER_RESTRICTION, userRestriction);
        assertTrue("Request to add restriction" + userRestriction + " failed",
                requestDpmAction(ACTION_ADD_USER_RESTRICTION, extras, mReceiverHandler));
        SuspendTestUtils.suspendAndAssertResult(ALL_TEST_PACKAGES, null, null, null,
                ALL_TEST_PACKAGES);
    }

    @Test
    public void testCannotSuspendUnderDisallowAppsControl() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        assertCannotSuspendUnderUserRestriction(DISALLOW_APPS_CONTROL);
    }

    @Test
    public void testCannotSuspendUnderDisallowUninstallApps() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        assertCannotSuspendUnderUserRestriction(DISALLOW_UNINSTALL_APPS);
    }

    private void assertDpmCanSuspendUnderUserRestriction(String userRestriction) throws Exception {
        addAndAssertProfileOwner();
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MY_PACKAGE_SUSPENDED);
        mAppCommsReceiver.drainPendingBroadcasts();
        Bundle extra = createSingleKeyBundle(EXTRA_USER_RESTRICTION, userRestriction);
        assertTrue("Request to add restriction" + userRestriction + " failed",
                requestDpmAction(ACTION_ADD_USER_RESTRICTION, extra, mReceiverHandler));
        extra = createSingleKeyBundle(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME);
        assertTrue("Request to suspend via dpm failed",
                requestDpmAction(ACTION_SUSPEND, extra, mReceiverHandler));
        final Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("No intent received from app", intentFromApp);
        assertEquals(ACTION_REPORT_MY_PACKAGE_SUSPENDED, intentFromApp.getAction());
    }

    @Test
    public void testDpmCanSuspendUnderDisallowAppsControl() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        assertDpmCanSuspendUnderUserRestriction(DISALLOW_APPS_CONTROL);
    }

    @Test
    public void testDpmCanSuspendUnderDisallowUninstallApps() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        assertDpmCanSuspendUnderUserRestriction(DISALLOW_UNINSTALL_APPS);
    }

    private void assertUnsuspendedOnUserRestriction(String userRestriction) throws Exception {
        assertTrue("Test app not suspended before setting user restriction",
                mPackageManager.isPackageSuspended(TEST_APP_PACKAGE_NAME));
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MY_PACKAGE_UNSUSPENDED);
        mAppCommsReceiver.drainPendingBroadcasts();
        addAndAssertProfileOwner();
        final Bundle extras = createSingleKeyBundle(EXTRA_USER_RESTRICTION, userRestriction);
        assertTrue("Request to add restriction " + userRestriction + " failed",
                requestDpmAction(ACTION_ADD_USER_RESTRICTION, extras, mReceiverHandler));
        final Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("No intent received from app", intentFromApp);
        assertEquals(ACTION_REPORT_MY_PACKAGE_UNSUSPENDED, intentFromApp.getAction());
    }

    @Test
    public void testUnsuspendedOnDisallowUninstallApps() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        SuspendTestUtils.suspend(null, null, null);
        assertUnsuspendedOnUserRestriction(DISALLOW_UNINSTALL_APPS);
    }

    @Test
    public void testUnsuspendedOnDisallowAppsControl() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        SuspendTestUtils.suspend(null, null, null);
        assertUnsuspendedOnUserRestriction(DISALLOW_APPS_CONTROL);
    }

    @Test
    public void testUnsuspendedOnUninstallBlocked() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN));
        SuspendTestUtils.suspendAndAssertResult(ALL_TEST_PACKAGES, null, null, null,
                EmptyArray.STRING);
        mAppCommsReceiver.register(mReceiverHandler, ACTION_REPORT_MY_PACKAGE_UNSUSPENDED);
        mAppCommsReceiver.drainPendingBroadcasts();
        addAndAssertProfileOwner();
        final Bundle extras = createSingleKeyBundle(EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME);
        assertTrue("Block uninstall request failed", requestDpmAction(
                ACTION_BLOCK_UNINSTALL, extras, mReceiverHandler));
        final Intent intentFromApp = mAppCommsReceiver.pollForIntent(5);
        assertNotNull("No intent received from app", intentFromApp);
        assertEquals(ACTION_REPORT_MY_PACKAGE_UNSUSPENDED, intentFromApp.getAction());
        assertEquals(TEST_APP_PACKAGE_NAME, intentFromApp.getStringExtra(EXTRA_PACKAGE_NAME));
        assertNull("Unexpected 2nd broadcast", mAppCommsReceiver.pollForIntent(5));
    }

    @After
    public void tearDown() throws Exception {
        final DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        if (dpm.isAdminActive(ComponentName.unflattenFromString(DEVICE_ADMIN_COMPONENT))) {
            final Bundle extras = createSingleKeyBundle(EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME);
            requestDpmAction(ACTION_UNBLOCK_UNINSTALL, extras, mReceiverHandler);
            requestDpmAction(ACTION_UNSUSPEND, extras, mReceiverHandler);
            removeDeviceAdmin();
        }
        SuspendTestUtils.unsuspendAll();
        mAppCommsReceiver.unregister();
    }

    private abstract static class StubbedCallback extends LauncherApps.Callback {
        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) {
        }
    }
}
