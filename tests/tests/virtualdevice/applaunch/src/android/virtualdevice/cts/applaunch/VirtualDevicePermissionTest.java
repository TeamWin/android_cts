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

package android.virtualdevice.cts;

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.content.pm.PackageManager.ACTION_REQUEST_PERMISSIONS;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.virtualdevice.cts.common.util.TestAppHelper.MAIN_ACTIVITY_COMPONENT;
import static android.virtualdevice.cts.common.util.TestAppHelper.createPermissionTestIntent;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.BLOCKED_ACTIVITY_COMPONENT;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.Instrumentation;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.virtualdevice.cts.applaunch.util.EmptyActivity;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.TestAppHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = " cannot be accessed by instant apps")
public class VirtualDevicePermissionTest {

    private static final String NORMAL_PERMISSION_GRANTED =
            Manifest.permission.HIGH_SAMPLING_RATE_SENSORS;
    private static final String NORMAL_PERMISSION_NOT_GRANTED = Manifest.permission.SET_ALARM;
    // Dangerous permissions specified in AndroidManifest.xml are automatically granted to CTS apps
    private static final String DANGEROUS_PERMISSION_GRANTED = Manifest.permission.RECORD_AUDIO;
    // Tests have not been granted CAMERA permission as per AndroidManifest.xml
    private static final String DANGEROUS_PERMISSION_NOT_GRANTED = Manifest.permission.CAMERA;
    private static final String PRIVILEGED_PERMISSION = Manifest.permission.LOCATION_BYPASS;
    private static final String SIGNATURE_PERMISSION =
            Manifest.permission.READ_APP_SPECIFIC_LOCALES;

    private static final int TIMEOUT_MILLIS = 5_000;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            GRANT_RUNTIME_PERMISSIONS,
            REVOKE_RUNTIME_PERMISSIONS);
    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private Context mContext;
    private VirtualDevice mVirtualDevice;

    @Mock
    private VirtualDeviceManager.ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = getApplicationContext();
        final PackageManager packageManager = mContext.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));

        VirtualDeviceManager mVirtualDeviceManager = mContext.getSystemService(
                VirtualDeviceManager.class);
        assumeNotNull(mVirtualDeviceManager);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void normalPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        createVirtualDevice();
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void normalPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_GRANTED, displayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void normalPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        createVirtualDevice();
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_NOT_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void normalPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_NOT_GRANTED, displayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void dangerousPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        createVirtualDevice();
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void dangerousPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_GRANTED, displayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void dangerousPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        createVirtualDevice();
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_NOT_GRANTED,
                DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void dangerousPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_NOT_GRANTED, displayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void privilegedPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        createVirtualDevice();
        try {
            runWithShellPermissionIdentity(() -> assertThat(
                    checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, DEFAULT_DISPLAY))
                    .isEqualTo(PackageManager.PERMISSION_GRANTED), PRIVILEGED_PERMISSION);
        } finally {
            // Re-grant CREATE_VIRTUAL_DEVICE for tearDown() as the above call drops all shell
            // permissions
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(CREATE_VIRTUAL_DEVICE);
        }
    }

    @Test
    public void privilegedPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        try {
            runWithShellPermissionIdentity(() -> assertThat(
                            checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, displayId))
                            .isEqualTo(PackageManager.PERMISSION_GRANTED),
                    PRIVILEGED_PERMISSION);
        } finally {
            // Re-grant CREATE_VIRTUAL_DEVICE for tearDown() as the above call drops all shell
            // permissions
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(CREATE_VIRTUAL_DEVICE);
        }
    }

    @Test
    public void privilegedPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        createVirtualDevice();
        assertThat(checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

    }

    @Test
    public void privilegedPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        assertThat(checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, displayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void signaturePermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        createVirtualDevice();
        try {
            runWithShellPermissionIdentity(() -> assertThat(
                    checkPermissionStateOnDevice(SIGNATURE_PERMISSION, DEFAULT_DISPLAY))
                    .isEqualTo(PackageManager.PERMISSION_GRANTED), SIGNATURE_PERMISSION);
        } finally {
            // Re-grant CREATE_VIRTUAL_DEVICE for tearDown() as the above call drops all shell
            // permissions
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(CREATE_VIRTUAL_DEVICE);
        }
    }

    @Test
    public void signaturePermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        try {
            runWithShellPermissionIdentity(() -> assertThat(
                            checkPermissionStateOnDevice(SIGNATURE_PERMISSION, displayId))
                            .isEqualTo(PackageManager.PERMISSION_GRANTED),
                    SIGNATURE_PERMISSION);
        } finally {
            // Re-grant CREATE_VIRTUAL_DEVICE for tearDown() as the above call drops all shell
            // permissions
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(CREATE_VIRTUAL_DEVICE);
        }
    }

    @Test
    public void signaturePermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        createVirtualDevice();
        assertThat(checkPermissionStateOnDevice(SIGNATURE_PERMISSION, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void signaturePermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        createVirtualDevice();
        int displayId = createVirtualDisplay(mVirtualDevice);
        assertThat(checkPermissionStateOnDevice(SIGNATURE_PERMISSION, displayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @RequiresFlagsEnabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialog_streamingEnabled_isShown() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            createVirtualDevice();

            verifyComponentShownAfterPermissionRequest(getPermissionDialogComponentName());
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialogDefaultParams_streamingDisabled_showsBlockedDialog() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            createVirtualDevice();
            verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialogAllowlisted_streamingDisabled_showsBlockedDialog() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                    .setAllowedActivities(new HashSet<>(Arrays.asList(MAIN_ACTIVITY_COMPONENT,
                            getPermissionDialogComponentName())))
                    .build();
            createVirtualDevice(params);
            verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_STREAM_PERMISSIONS)
    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void permissionDialogDynamicallyAllowlisted_streamingDisabled_showsBlockedDialog() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            createVirtualDevice();
            mVirtualDevice.setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_ACTIVITY,
                    VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
            mVirtualDevice.addActivityPolicyExemption(MAIN_ACTIVITY_COMPONENT);
            mVirtualDevice.addActivityPolicyExemption(getPermissionDialogComponentName());
            verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_STREAM_PERMISSIONS)
    @Test
    public void permissionDialogInBlocklist_streamingEnabled_showsBlockedDialog() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                    .setBlockedActivities(Collections.singleton(getPermissionDialogComponentName()))
                    .build();

            createVirtualDevice(params);
            verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    @RequiresFlagsEnabled({Flags.FLAG_STREAM_PERMISSIONS, Flags.FLAG_DYNAMIC_POLICY})
    @Test
    public void permissionDialogInDynamicBlocklist_streamingEnabled_showsBlockedDialog() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            createVirtualDevice();
            mVirtualDevice.setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_ACTIVITY,
                    VirtualDeviceParams.DEVICE_POLICY_DEFAULT);
            mVirtualDevice.addActivityPolicyExemption(getPermissionDialogComponentName());
            verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    @Test
    public void mVirtualDeviceWithAllowlistPolicy_permissionDialogNotAllowlisted_showsBlockedDialog() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                    .setAllowedActivities(Collections.singleton(MAIN_ACTIVITY_COMPONENT))
                    .build();
            createVirtualDevice(params);
            verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void mVirtualDeviceWithDynamicAllowlistPolicy_permissionDialogNotAllowlisted_showsBlockedDialog() {
        revokePermissionAndAssertDenied(DANGEROUS_PERMISSION_GRANTED, TestAppHelper.PACKAGE_NAME);
        try {
            createVirtualDevice();
            mVirtualDevice.setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_ACTIVITY,
                    VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
            mVirtualDevice.addActivityPolicyExemption(MAIN_ACTIVITY_COMPONENT);
            verifyComponentShownAfterPermissionRequest(BLOCKED_ACTIVITY_COMPONENT);
        } finally {
            grantPermissionAndAssertGranted(DANGEROUS_PERMISSION_GRANTED,
                    TestAppHelper.PACKAGE_NAME);
        }
    }

    private ComponentName getPermissionDialogComponentName() {
        Intent intent = new Intent(ACTION_REQUEST_PERMISSIONS);
        PackageManager packageManager = mContext.getPackageManager();
        intent.setPackage(packageManager.getPermissionControllerPackageName());
        return intent.resolveActivity(packageManager);
    }

    private void grantPermissionAndAssertGranted(String permissionName, String packageName) {
        mContext.getPackageManager().grantRuntimePermission(packageName, permissionName,
                UserHandle.of(mContext.getUserId()));
        assertThat(mContext.getPackageManager().checkPermission(permissionName, packageName))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    // Make sure not to revoke permissions of the CTS test module APK itself as revoking
    // force-stops the process that gets its permissions revoked. That leads to the test process
    // crashing itself.
    private void revokePermissionAndAssertDenied(String permissionName, String packageName) {
        mContext.getPackageManager().revokeRuntimePermission(packageName, permissionName,
                UserHandle.of(mContext.getUserId()));
        assertThat(mContext.getPackageManager().checkPermission(permissionName, packageName))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    private int checkPermissionStateOnDevice(String permissionName, int displayId) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Bundle options = createActivityOptions(displayId);

        EmptyActivity activity = (EmptyActivity) instrumentation.startActivitySync(
                new Intent(mContext, EmptyActivity.class).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK), options);

        return activity.checkSelfPermission(permissionName);
    }

    private void verifyComponentShownAfterPermissionRequest(ComponentName componentName) {
        int displayId = createVirtualDisplay(mVirtualDevice);
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        requestPermissionOnDevice(DANGEROUS_PERMISSION_GRANTED, displayId);

        ArgumentCaptor<ComponentName> componentCaptor = ArgumentCaptor.forClass(
                ComponentName.class);
        verify(mActivityListener, timeout(TIMEOUT_MILLIS).times(2)).onTopActivityChanged(
                eq(displayId), componentCaptor.capture(), anyInt());

        assertThat(componentCaptor.getAllValues()).isEqualTo(
                Arrays.asList(MAIN_ACTIVITY_COMPONENT, componentName));
    }

    private void requestPermissionOnDevice(String permissionName, int displayId) {
        Bundle options = createActivityOptions(displayId);
        mContext.startActivity(createPermissionTestIntent(permissionName).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK), options);
    }

    private void createVirtualDevice() {
        createVirtualDevice(new VirtualDeviceParams.Builder().build());
    }

    private void createVirtualDevice(VirtualDeviceParams params) {
        VirtualDeviceManager mVirtualDeviceManager = mContext.getSystemService(
                VirtualDeviceManager.class);

        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(), params);
    }

    private int createVirtualDisplay(VirtualDevice mVirtualDevice) {
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
                        .build(),
                null, null);
        return virtualDisplay.getDisplay().getDisplayId();
    }
}
