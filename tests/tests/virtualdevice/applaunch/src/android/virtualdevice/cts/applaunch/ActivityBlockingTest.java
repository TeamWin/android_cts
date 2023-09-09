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

package android.virtualdevice.cts;

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.virtualdevice.cts.common.util.TestAppHelper.EXTRA_DISPLAY;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createResultReceiver;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.ActivityManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.ActivityListener;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.virtualdevice.cts.applaunch.util.EmptyActivity;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.TestAppHelper;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.OnReceiveResultListener;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.internal.app.BlockedAppStreamingActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Tests for blocking of activities that should not be shown on the virtual device.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityBlockingTest {

    private static final int TIMEOUT_MS = 3000;
    private static final ComponentName BLOCKED_APP_STREAMING_ACTIVITY_COMPONENT =
            new ComponentName("android", BlockedAppStreamingActivity.class.getName());

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    private ActivityManager mActivityManager;
    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    @Mock
    private OnReceiveResultListener mOnReceiveResultListener;
    private ResultReceiver mResultReceiver;
    @Mock
    private ActivityListener mActivityListener;
    private Context mTargetContext;

    private Intent mMonitoriedIntent;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mResultReceiver = createResultReceiver(mOnReceiveResultListener);
        mMonitoriedIntent = TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void nonTrustedDisplay_startNonEmbeddableActivity_shouldThrowSecurityException() {
        createVirtualDeviceAndNonTrustedDisplay();
        Intent intent = TestAppHelper.createNoEmbedIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mTargetContext, mVirtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        assertThrows(SecurityException.class,
                () -> mTargetContext.startActivity(intent, createActivityOptions(mVirtualDisplay)));
    }

    @Test
    public void cannotDisplayOnRemoteActivity_newTask_shouldBeBlockedFromLaunching() {
        createVirtualDeviceAndTrustedDisplay();
        Intent blockedIntent = TestAppHelper.createCannotDisplayOnRemoteIntent(
                /* newTask= */ true, mResultReceiver).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertActivityLaunchBlocked(blockedIntent);
    }

    @Test
    public void cannotDisplayOnRemoteActivity_sameTask_shouldBeBlockedFromLaunching() {
        createVirtualDeviceAndTrustedDisplay();
        Intent blockedIntent = TestAppHelper.createCannotDisplayOnRemoteIntent(
                /* newTask= */ false, mResultReceiver).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertActivityLaunchBlocked(blockedIntent);
    }

    @Test
    public void trustedDisplay_startNonEmbeddableActivity_shouldSucceed() {
        createVirtualDeviceAndTrustedDisplay();
        assertActivityLaunchAllowed(mMonitoriedIntent);
    }

    @Test
    public void setAllowedActivities_shouldBlockNonAllowedActivities() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(emptyActivityComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoriedIntent);
    }

    @Test
    public void setAllowedActivities_shouldAllowActivitiesInAllowlist() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mMonitoriedIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoriedIntent);
    }

    @Test
    public void setBlockedActivities_shouldBlockActivityFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mMonitoriedIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoriedIntent);
    }

    @Test
    public void setBlockedActivities_shouldAllowOtherActivitiesToLaunch() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(emptyActivityComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setBlockedActivities_removeExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mMonitoriedIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoriedIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setAllowedActivities_removeExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mMonitoriedIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoriedIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void defaultActivityPolicy_addExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        assertActivityLaunchAllowed(mMonitoriedIntent);

        mVirtualDevice.addActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void customActivityPolicy_addExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        assertActivityLaunchBlocked(mMonitoriedIntent);

        mVirtualDevice.addActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void defaultActivityPolicy_removeExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoriedIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void customActivityPolicy_removeExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoriedIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoriedIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void customActivityPolicy_changeToDefault_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        assertActivityLaunchBlocked(mMonitoriedIntent);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchAllowed(mMonitoriedIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void defaultActivityPolicy_changeToCustom_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        assertActivityLaunchAllowed(mMonitoriedIntent);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        assertActivityLaunchBlocked(mMonitoriedIntent);
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(Set.of(emptyActivityComponent()))
                .build());
        EmptyActivity emptyActivity = startEmptyActivityOnVirtualDisplay();
        emptyActivity.startActivity(mMonitoriedIntent);
        assertNoActivityLaunched();
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldAllowNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(Set.of(mMonitoriedIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity = startEmptyActivityOnVirtualDisplay();
        emptyActivity.startActivity(mMonitoriedIntent);
        assertActivityLaunched(mMonitoriedIntent.getComponent());
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldAllowNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(Set.of(emptyActivityComponent()))
                .build());
        EmptyActivity emptyActivity = startEmptyActivityOnVirtualDisplay();
        emptyActivity.startActivity(mMonitoriedIntent);
        assertActivityLaunched(mMonitoriedIntent.getComponent());
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(Set.of(mMonitoriedIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity = startEmptyActivityOnVirtualDisplay();
        emptyActivity.startActivity(mMonitoriedIntent);
        assertNoActivityLaunched();
    }

    private static ComponentName emptyActivityComponent() {
        return new ComponentName(getApplicationContext(), EmptyActivity.class);
    }

    private EmptyActivity startEmptyActivityOnVirtualDisplay() {
        Intent intent = new Intent(getApplicationContext(), EmptyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(intent, createActivityOptions(mVirtualDisplay));
    }

    private void createVirtualDeviceAndNonTrustedDisplay() {
        createVirtualDeviceAndDisplay(
                new VirtualDeviceParams.Builder().build(), /* virtualDisplayFlags= */0);
    }

    private void createVirtualDeviceAndTrustedDisplay() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build());
    }

    private void createVirtualDeviceAndTrustedDisplay(VirtualDeviceParams virtualDeviceParams) {
        createVirtualDeviceAndDisplay(
                virtualDeviceParams, DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    private void createVirtualDeviceAndDisplay(
            VirtualDeviceParams virtualDeviceParams, int virtualDisplayFlags) {
        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(), virtualDeviceParams);
        mVirtualDevice.addActivityListener(getApplicationContext().getMainExecutor(),
                mActivityListener);
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(virtualDisplayFlags)
                        .build(),
                /* executor= */ null, /* callback= */ null);
    }

    private void assertActivityLaunchBlocked(Intent intent) {
        reset(mOnReceiveResultListener);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mTargetContext, mVirtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        mTargetContext.startActivity(intent, createActivityOptions(mVirtualDisplay));
        assertNoActivityLaunched();
    }

    private void assertActivityLaunchAllowed(Intent intent) {
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mTargetContext, mVirtualDisplay.getDisplay().getDisplayId(), intent)).isTrue();
        mTargetContext.startActivity(intent, createActivityOptions(mVirtualDisplay));
        assertActivityLaunched(intent.getComponent());
    }

    private void assertNoActivityLaunched() {
        verify(mActivityListener, timeout(TIMEOUT_MS).times(1)).onTopActivityChanged(
                eq(mVirtualDisplay.getDisplay().getDisplayId()),
                eq(BLOCKED_APP_STREAMING_ACTIVITY_COMPONENT), anyInt());
        verify(mOnReceiveResultListener, never()).onReceiveResult(anyInt(), any());
        reset(mOnReceiveResultListener);
    }

    private void assertActivityLaunched(ComponentName componentName) {
        verify(mActivityListener, timeout(TIMEOUT_MS).times(1)).onTopActivityChanged(
                eq(mVirtualDisplay.getDisplay().getDisplayId()), eq(componentName), anyInt());
        verify(mOnReceiveResultListener, timeout(TIMEOUT_MS)).onReceiveResult(
                eq(Activity.RESULT_OK), argThat(r ->
                        r.getInt(EXTRA_DISPLAY) == mVirtualDisplay.getDisplay().getDisplayId()));
        reset(mOnReceiveResultListener);
    }
}

