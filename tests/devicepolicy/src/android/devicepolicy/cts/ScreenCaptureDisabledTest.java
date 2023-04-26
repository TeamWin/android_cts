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

package android.devicepolicy.cts;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.stats.devicepolicy.EventId;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.ScreenCaptureDisabled;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(BedsteadJUnit4.class)
public final class ScreenCaptureDisabledTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();


    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private static final DevicePolicyManager sLocalDevicePolicyManager = TestApis.context().instrumentedContext()
        .getSystemService(DevicePolicyManager.class);

    private static final TestApp sTestApp =
            sDeviceState.testApps().query().whereActivities().isNotEmpty().get();

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_works() {
        sDeviceState.dpc().devicePolicyManager()
                .setScreenCaptureDisabled(sDeviceState.dpc().componentName(), false);

        assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isFalse();
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_checkWithDPC_works() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(sDeviceState.dpc().devicePolicyManager().getScreenCaptureDisabled(
                    sDeviceState.dpc().componentName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CannotSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setScreenCaptureDisabled(sDeviceState.dpc().componentName(), false));
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_works() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(
                    /* admin= */ null)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_checkWithDPC_works() {
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager().getScreenCaptureDisabled(
                    sDeviceState.dpc().componentName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_doesNotApply() {
        sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                sDeviceState.dpc().componentName(), true);

        assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isFalse();
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                sDeviceState.dpc().componentName(), true);

        assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureRedactedOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(takeScreenshotExpectingRedactionOrNull()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_metricsLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_metricsLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    private boolean takeScreenshotExpectingRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(true).await();
        }
    }

    private boolean takeScreenshotExpectingNoRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(false).await();
        }
    }

    private boolean checkScreenshotIsRedactedOrNull(Bitmap screenshot) {
        if (screenshot == null) {
            return true;
        }
        int width = screenshot.getWidth();
        int height = screenshot.getHeight();

        // Getting pixels of only the middle part(from y  = height/4 to 3/4(height)) of the
        // screenshot to check(screenshot is redacted) for only the middle part of the screen,
        // as there could be notifications in the top part and white line(navigation bar) at bottom
        // which are included in the screenshot and are not redacted(black). It's not perfect, but
        // seems best option to avoid any flakiness at this point.
        int[] pixels = new int[width * (height / 2)];
        screenshot.getPixels(pixels, 0, width, 0, height / 4, width, height / 2);

        for (int pixel : pixels) {
            if (!(pixel == Color.BLACK)) {
                return false;
            }
        }
        return true;
    }
}
