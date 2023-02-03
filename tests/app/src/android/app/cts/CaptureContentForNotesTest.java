/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.role.RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP;
import static android.app.role.RoleManager.ROLE_NOTES;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.ResolveInfoFlags;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.role.RoleManager;
import android.app.stubs.MockNotesActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
public class CaptureContentForNotesTest {

    private static final String REQUIRED_PERMISSION =
            "android.permission.LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE";
    private static final Intent CAPTURE_CONTENT_FOR_NOTES_INTENT =
            new Intent(Intent.ACTION_LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE);
    private static final int WAIT_FOR_TIME_OUT = 10000;
    private static final int DEFAULT = -100;
    private static final String STYLUS_BUTTON_CLICK_EVENT_COMMAND = "input keyevent 311";
    private static final String ADD_TO_NOTE = "Add to note";
    private static final String CANCEL = "Cancel";
    ActivityScenario<MockNotesActivity> mScenario;
    private Instrumentation mInstrumentation;
    private Context mContext;
    private RoleManager mRoleManager;
    private PackageManager mPackageManager;
    private String mOriginalRoleHolderPackage;

    private static int getActivityResultCode(Intent intent) {
        return intent.getIntExtra(MockNotesActivity.EXTRA_ACTIVITY_RESULT_CODE, DEFAULT);
    }

    private static int getCaptureContentStatusCode(Intent intent) {
        return intent.getParcelableExtra(MockNotesActivity.EXTRA_ACTIVITY_RESULT_DATA, Intent.class)
                .getIntExtra(Intent.EXTRA_CAPTURE_CONTENT_FOR_NOTE_STATUS_CODE, DEFAULT);
    }

    private static Uri getUriResponse(Intent intent) {
        return intent.getParcelableExtra(MockNotesActivity.EXTRA_ACTIVITY_RESULT_DATA, Intent.class)
                .getData();
    }

    private static boolean getSupportApiResponse(Intent intent) {
        return intent.getBooleanExtra(MockNotesActivity.EXTRA_SUPPORT_API_RESPONSE, false);
    }

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mRoleManager = mContext.getSystemService(RoleManager.class);
        mPackageManager = mContext.getPackageManager();
    }

    @After
    public void tearDown() throws ExecutionException, InterruptedException {
        if (mScenario != null) {
            mScenario.close();
            mScenario = null;
        }

        sendBroadcast(MockNotesActivity.FINISH_THIS_ACTIVITY);

        resetNotesRole();
    }

    @Test
    public void whenRoleAvailable_shouldResolveIntentAction() {
        assumeTrue(ROLE_NOTES + " must be available",
                mRoleManager.isRoleAvailable(ROLE_NOTES));

        ResolveInfo info = mPackageManager.resolveActivity(CAPTURE_CONTENT_FOR_NOTES_INTENT,
                ResolveInfoFlags.of(MATCH_SYSTEM_ONLY | MATCH_DEFAULT_ONLY));

        assertTrue(info != null && info.activityInfo != null);
        assertEquals(REQUIRED_PERMISSION, info.activityInfo.permission);
    }

    @Test
    public void appBubbleActivityStarted_userConsented_shouldReturnScreenshot()
            throws IOException, UiObjectNotFoundException, ExecutionException,
            InterruptedException {
        assumeTrue(ROLE_NOTES + " must be available",
                mRoleManager.isRoleAvailable(ROLE_NOTES));

        setUpNotesRole();

        WaitForBroadcast waitForActivityCreatedBroadcast = setUpActivityCreatedBroadcastReceiver();
        WaitForBroadcast waitForIntentFiredBroadcast = setUpIntentFiredBroadcastReceiver();
        WaitForBroadcast waitForActivityResultBroadcast = setUpActivityResultBroadcastReceiver();

        // Start the app bubble through user action, then wait for activity to respond through
        // broadcast as there is no way to access the started activity instance.
        executeShellCommandToMimicStylusButtonClick();
        waitForActivityCreatedBroadcast.doWait(WAIT_FOR_TIME_OUT);

        // Inform test activity to start the content capture activity.
        sendBroadcast(MockNotesActivity.LAUNCH_CAPTURE_CONTENT_INTENT);
        waitForBroadcast(waitForIntentFiredBroadcast);

        findButtonThenClickAndWait(ADD_TO_NOTE);

        Intent response = waitForActivityResultBroadcast.doWait(WAIT_FOR_TIME_OUT);
        assertEquals(Activity.RESULT_OK, getActivityResultCode(response));
        assertEquals(Intent.CAPTURE_CONTENT_FOR_NOTE_SUCCESS,
                getCaptureContentStatusCode(response));
        assertNotNull(getUriResponse(response));
    }

    @Test
    public void appBubbleActivityStarted_userCanceled_shouldReturnCancelStatus()
            throws IOException, UiObjectNotFoundException, ExecutionException,
            InterruptedException {
        assumeTrue(ROLE_NOTES + " must be available",
                mRoleManager.isRoleAvailable(ROLE_NOTES));

        setUpNotesRole();

        WaitForBroadcast activityCreatedBroadcastReceiver = setUpActivityCreatedBroadcastReceiver();
        WaitForBroadcast intentFiredBroadcastReceiver = setUpIntentFiredBroadcastReceiver();
        WaitForBroadcast waitForActivityResultBroadcast = setUpActivityResultBroadcastReceiver();

        // Start the app bubble through user action, then wait for activity to respond through
        // broadcast as there is no way to access the started activity instance.
        executeShellCommandToMimicStylusButtonClick();
        waitForBroadcast(activityCreatedBroadcastReceiver);

        // Inform test activity to start the content capture activity.
        sendBroadcast(MockNotesActivity.LAUNCH_CAPTURE_CONTENT_INTENT);
        waitForBroadcast(intentFiredBroadcastReceiver);

        findButtonThenClickAndWait(CANCEL);

        Intent response = waitForActivityResultBroadcast.doWait(WAIT_FOR_TIME_OUT);
        assertEquals(Activity.RESULT_OK, getActivityResultCode(response));
        assertEquals(Intent.CAPTURE_CONTENT_FOR_NOTE_USER_CANCELED,
                getCaptureContentStatusCode(response));
    }

    @Test
    public void regularActivityStarted_intentActionTriggered_shouldReturnWindowModeUnsupported() {
        assumeTrue(ROLE_NOTES + " must be available",
                mRoleManager.isRoleAvailable(ROLE_NOTES));

        WaitForBroadcast intentFiredBroadcastReceiver = setUpIntentFiredBroadcastReceiver();
        WaitForBroadcast waitForResultBroadcast = setUpActivityResultBroadcastReceiver();

        mScenario = ActivityScenario.launch(MockNotesActivity.class);
        waitForIdle();

        // Inform test activity to start the content capture activity.
        sendBroadcast(MockNotesActivity.LAUNCH_CAPTURE_CONTENT_INTENT);
        waitForBroadcast(intentFiredBroadcastReceiver);

        Intent response = waitForResultBroadcast.doWait(WAIT_FOR_TIME_OUT);
        assertEquals(Activity.RESULT_OK, getActivityResultCode(response));
        assertEquals(Intent.CAPTURE_CONTENT_FOR_NOTE_WINDOW_MODE_UNSUPPORTED,
                getCaptureContentStatusCode(response));
    }

    @Test
    public void regularActivityStarted_querySupportApi_shouldReturnFalse() {
        assumeTrue(ROLE_NOTES + " must be available",
                mRoleManager.isRoleAvailable(ROLE_NOTES));

        WaitForBroadcast activityCreatedBroadcastReceiver = setUpActivityCreatedBroadcastReceiver();
        WaitForBroadcast supportApiResponseBroadcastReceiver =
                setUpQuerySupportApiBroadcastReceiver();

        mScenario = ActivityScenario.launch(MockNotesActivity.class);
        waitForIdle();

        // Wait for activity to start and then query support API.
        waitForBroadcast(activityCreatedBroadcastReceiver);
        sendBroadcast(MockNotesActivity.QUERY_SUPPORT_API);


        Intent response = supportApiResponseBroadcastReceiver.doWait(WAIT_FOR_TIME_OUT);
        assertThat(getSupportApiResponse(response)).isFalse();
    }

    @Test
    public void appBubbleActivityStarted_querySupportApi_shouldReturnTrue()
            throws ExecutionException, InterruptedException, IOException {
        assumeTrue(ROLE_NOTES + " must be available",
                mRoleManager.isRoleAvailable(ROLE_NOTES));

        setUpNotesRole();

        WaitForBroadcast activityCreatedBroadcastReceiver = setUpActivityCreatedBroadcastReceiver();
        WaitForBroadcast supportApiResponseBroadcastReceiver =
                setUpQuerySupportApiBroadcastReceiver();

        // Start the app bubble through user action, then wait for activity to respond through
        // broadcast as there is no way to access the started activity instance.
        executeShellCommandToMimicStylusButtonClick();
        waitForBroadcast(activityCreatedBroadcastReceiver);

        sendBroadcast(MockNotesActivity.QUERY_SUPPORT_API);

        Intent response = supportApiResponseBroadcastReceiver.doWait(WAIT_FOR_TIME_OUT);
        assertThat(getSupportApiResponse(response)).isTrue();
    }

    private WaitForBroadcast setUpActivityCreatedBroadcastReceiver() {
        WaitForBroadcast waitForBroadcast = new WaitForBroadcast(mContext);
        waitForBroadcast.prepare(MockNotesActivity.INFORM_ACTIVITY_CREATED);
        return waitForBroadcast;
    }

    private WaitForBroadcast setUpIntentFiredBroadcastReceiver() {
        WaitForBroadcast waitForBroadcast = new WaitForBroadcast(mContext);
        waitForBroadcast.prepare(MockNotesActivity.INFORM_CAPTURE_CONTENT_INTENT_FIRED);
        return waitForBroadcast;
    }

    private WaitForBroadcast setUpActivityResultBroadcastReceiver() {
        WaitForBroadcast waitForBroadcast = new WaitForBroadcast(mContext);
        waitForBroadcast.prepare(MockNotesActivity.INFORM_ACTIVITY_RESULT_RECEIVED);
        return waitForBroadcast;
    }

    private WaitForBroadcast setUpQuerySupportApiBroadcastReceiver() {
        WaitForBroadcast waitForBroadcast = new WaitForBroadcast(mContext);
        waitForBroadcast.prepare(MockNotesActivity.INFORM_SUPPORT_API_RESPONSE);
        return waitForBroadcast;
    }

    private void waitForBroadcast(WaitForBroadcast broadcastToWaitFor) {
        broadcastToWaitFor.doWait(WAIT_FOR_TIME_OUT);
        waitForIdle();
    }

    private void sendBroadcast(String action) {
        mContext.sendBroadcast(new Intent(action));
        waitForIdle();
    }

    private void setUpNotesRole() throws ExecutionException, InterruptedException {
        UserHandle user = android.os.Process.myUserHandle();
        CompletableFuture<Boolean> originalRoleClearedFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> newRoleSetFuture = new CompletableFuture<>();
        String packageName = mContext.getPackageName();

        SystemUtil.runWithShellPermissionIdentity(() -> {
            List<String> roleHolders = mRoleManager.getRoleHolders(ROLE_NOTES);
            if (roleHolders != null && roleHolders.size() > 0) {
                mOriginalRoleHolderPackage = roleHolders.get(0);
                mRoleManager.clearRoleHoldersAsUser(ROLE_NOTES, MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                        user, mContext.getMainExecutor(), originalRoleClearedFuture::complete);

                // Wait for the future to complete.
                originalRoleClearedFuture.get();
            }

            mRoleManager.addRoleHolderAsUser(ROLE_NOTES, packageName,
                    MANAGE_HOLDERS_FLAG_DONT_KILL_APP, user, mContext.getMainExecutor(),
                    newRoleSetFuture::complete);
        });

        // Wait for this future in the test's thread for synchronous behavior.
        newRoleSetFuture.get();
    }

    private void resetNotesRole() throws ExecutionException, InterruptedException {
        UserHandle user = android.os.Process.myUserHandle();
        CompletableFuture<Boolean> testRoleClearedFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> roleUpdateFuture = new CompletableFuture<>();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mRoleManager.clearRoleHoldersAsUser(ROLE_NOTES, MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    user, mContext.getMainExecutor(), testRoleClearedFuture::complete);

            // Wait for the future to complete.
            testRoleClearedFuture.get();

            if (mOriginalRoleHolderPackage == null) {
                roleUpdateFuture.complete(true);
                return;
            }

            mRoleManager.addRoleHolderAsUser(ROLE_NOTES, mOriginalRoleHolderPackage,
                    MANAGE_HOLDERS_FLAG_DONT_KILL_APP, user, mContext.getMainExecutor(),
                    roleUpdateFuture::complete);

            // Reset the local previous role variable.
            mOriginalRoleHolderPackage = null;
        });

        // Wait for this future in the test's thread for synchronous behavior.
        roleUpdateFuture.get();
    }

    private void executeShellCommandToMimicStylusButtonClick() throws IOException {
        getUiDevice().executeShellCommand(STYLUS_BUTTON_CLICK_EVENT_COMMAND);
        waitForIdle();
    }

    private void findButtonThenClickAndWait(String buttonText) throws UiObjectNotFoundException {
        getUiDevice().wait(Until.hasObject(By.text(buttonText).clickable(true)), WAIT_FOR_TIME_OUT);
        UiObject button = getUiDevice().findObject(
                new UiSelector().textContains(buttonText).clickable(true));
        button.click();
        button.waitUntilGone(WAIT_FOR_TIME_OUT);
    }

    private void waitForIdle() {
        getUiDevice().waitForIdle();
    }

    private UiDevice getUiDevice() {
        return UiDevice.getInstance(mInstrumentation);
    }
}
