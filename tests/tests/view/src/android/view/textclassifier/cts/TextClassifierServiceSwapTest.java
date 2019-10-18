/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.view.textclassifier.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.test.uiautomator.UiDevice;
import android.text.TextUtils;
import android.util.Log;
import android.view.textclassifier.cts.CtsTextClassifierService.ServiceWatcher;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.SafeCleanerRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for TextClassifierService query related functions.
 *
 * <p>
 * We use a non-standard TextClassifierService for TextClassifierService-related CTS tests. A
 * non-standard TextClassifierService that is set via device config. This non-standard
 * TextClassifierService is not defined in the trust TextClassifierService, it should only receive
 * queries from clients in the same package.
 */
@RunWith(AndroidJUnit4.class)
public class TextClassifierServiceSwapTest {
    // TODO: Add more tests to verify all the TC APIs call between caller and TCS.
    private static final String TAG = TextClassifierServiceSwapTest.class.getSimpleName();

    @Rule
    public final SafeCleanerRule mSafeCleanerRule = new SafeCleanerRule()
            .add(() -> {
                return CtsTextClassifierService.getExceptions();
            });

    private String mOriginalOverrideService;
    private boolean mOriginalSystemTextClassifierEnabled;
    private ServiceWatcher mServiceWatcher;
    private BlockingBroadcastReceiver mFinishReceiver;

    @Before
    public void setup() throws Exception {
        prepareDevice();
        CtsTextClassifierService.resetStaticState();
        // get original settings
        mOriginalOverrideService = getOriginalOverrideService();
        mOriginalSystemTextClassifierEnabled = isSystemTextClassifierEnabled();

        // set system TextClassifier enabled
        runShellCommand("device_config put textclassifier system_textclassifier_enabled true");
        // enable test service
        setService();
    }

    @After
    public void tearDown() throws Exception {
        if (mFinishReceiver != null) {
            mFinishReceiver.unregisterQuietly();
        }
        // restore original settings
        runShellCommand("device_config put textclassifier system_textclassifier_enabled "
                + mOriginalSystemTextClassifierEnabled);
        // restore service and make sure service disconnected
        resetService();
    }

    @AfterClass
    public static void resetStates() {
        CtsTextClassifierService.resetStaticState();
    }

    private void prepareDevice() {
        Log.v(TAG, "prepareDevice()");
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");

        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
    }

    private String getOriginalOverrideService() {
        final String deviceConfigSetting = runShellCommand(
                "device_config get textclassifier textclassifier_service_package_override");
        if (!TextUtils.isEmpty(deviceConfigSetting) && !deviceConfigSetting.equals("null")) {
            return deviceConfigSetting;
        }
        // TODO: remove the logic when text_classifier_constants is removed
        final String globalSetting = runShellCommand(
                "settings get global text_classifier_constants");
        if (!TextUtils.isEmpty(globalSetting) && globalSetting.contains(
                "extclassifier_service_package_override")) {
            return globalSetting.substring(globalSetting.indexOf('=') + 1);
        }
        // return default value defined in TextClassificationConstants
        return null;
    }

    private boolean isSystemTextClassifierEnabled() {
        final String deviceConfigSetting = runShellCommand(
                "device_config get textclassifier system_textclassifier_enabled");
        if (!TextUtils.isEmpty(deviceConfigSetting) && !deviceConfigSetting.equals("null")) {
            return deviceConfigSetting.toLowerCase().equals("true");
        }
        // TODO: remove the logic when text_classifier_constants is removed
        final String globalSetting = runShellCommand(
                "settings get global text_classifier_constants");
        if (!TextUtils.isEmpty(globalSetting) && globalSetting.contains(
                "system_textclassifier_enabled")) {
            return globalSetting.substring(globalSetting.indexOf('=') + 1).toLowerCase().equals(
                    "true");
        }
        // return default value defined in TextClassificationConstants
        return true;
    }

    private void setService() {
        mServiceWatcher = CtsTextClassifierService.setServiceWatcher();
        // set test service
        runShellCommand("device_config put textclassifier textclassifier_service_package_override "
                + CtsTextClassifierService.MY_PACKAGE);
    }

    private CtsTextClassifierService waitServiceLazyConnect() throws InterruptedException {
        return mServiceWatcher.waitOnConnected();
    }

    private void resetService() throws InterruptedException {
        resetOriginalService();
        if (mServiceWatcher.mService != null) {
            mServiceWatcher.waitOnDisconnected();
        }
        CtsTextClassifierService.clearServiceWatcher();
        mServiceWatcher = null;
    }

    private void resetOriginalService() {
        Log.d(TAG, "reset to " + mOriginalOverrideService);
        runShellCommand(
                "device_config put textclassifier textclassifier_service_package_override "
                        + mOriginalOverrideService);
    }

    @Test
    public void testOutsideOfPackageActivity_noRequestReceived() throws Exception {
        // Start an Activity from another package to trigger a TextClassifier call
        runQueryTextClassifierServiceActivity();

        waitForIdle();

        final Intent intent = mFinishReceiver.awaitForBroadcast();
        assertThat(intent).isNotNull();

        // We only bind to TextClassifierService when we are serving the first request.
        // Wait service connected
        final CtsTextClassifierService service = waitServiceLazyConnect();

        // Wait a delay for the query is delivered.
        service.awaitQuery(1_000);

        // Verify the request can not pass to service
        assertThat(service.getRequestSessions()).isEmpty();
    }

    private static void waitForIdle() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .waitForIdle();
    }

    private void runQueryTextClassifierServiceActivity() {
        final String actionQuetyActivityFinish =
                "ACTION_QUERY_SERVICE_ACTIVITY_FINISH_" + SystemClock.uptimeMillis();
        final Context context = InstrumentationRegistry.getTargetContext();

        mFinishReceiver = new BlockingBroadcastReceiver(context, actionQuetyActivityFinish);
        mFinishReceiver.register();

        final Intent outsideActivity = new Intent();
        outsideActivity.setComponent(new ComponentName("android.textclassifier.cts2",
                "android.textclassifier.cts2.QueryTextClassifierServiceActivity"));
        outsideActivity.setFlags(FLAG_ACTIVITY_NEW_TASK);
        final Intent broadcastIntent = new Intent(actionQuetyActivityFinish);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, broadcastIntent,
                0);
        outsideActivity.putExtra("finishBroadcast", pendingIntent);

        context.startActivity(outsideActivity);
    }
}
