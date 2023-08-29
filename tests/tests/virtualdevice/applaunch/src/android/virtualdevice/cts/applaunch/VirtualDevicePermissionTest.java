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
import static android.view.Display.DEFAULT_DISPLAY;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.util.EmptyActivity;
import android.virtualdevice.cts.common.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

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

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private Context mContext;
    private VirtualDevice mVirtualDevice;
    private int mVirtualDisplayId;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = getApplicationContext();
        final PackageManager packageManager = mContext.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));

        VirtualDeviceManager virtualDeviceManager = mContext.getSystemService(
                VirtualDeviceManager.class);
        assumeNotNull(virtualDeviceManager);

        mVirtualDevice = virtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder().build());
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
                        .build(),
                Runnable::run, null);
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void normalPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void normalPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void normalPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_NOT_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void normalPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(NORMAL_PERMISSION_NOT_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void dangerousPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_GRANTED, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void dangerousPermissionGranted_appRunningOnVirtualDevice_hasPermissionGranted() {
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void dangerousPermissionDenied_appRunningOnDefaultDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(DANGEROUS_PERMISSION_NOT_GRANTED,
                DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void dangerousPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(
                checkPermissionStateOnDevice(DANGEROUS_PERMISSION_NOT_GRANTED, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void privilegedPermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
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
        try {
            runWithShellPermissionIdentity(() -> assertThat(
                            checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, mVirtualDisplayId))
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
        assertThat(checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void privilegedPermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(PRIVILEGED_PERMISSION, mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void signaturePermissionGranted_appRunningOnDefaultDevice_hasPermissionGranted() {
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
        try {
            runWithShellPermissionIdentity(() -> assertThat(
                            checkPermissionStateOnDevice(SIGNATURE_PERMISSION, mVirtualDisplayId))
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
        assertThat(checkPermissionStateOnDevice(SIGNATURE_PERMISSION, DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void signaturePermissionDenied_appRunningOnVirtualDevice_hasPermissionDenied() {
        assertThat(checkPermissionStateOnDevice(SIGNATURE_PERMISSION, mVirtualDisplayId))
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
}
