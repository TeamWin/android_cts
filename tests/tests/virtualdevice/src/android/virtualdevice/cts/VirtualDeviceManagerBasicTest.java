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

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.companion.virtual.VirtualDeviceManager.DEFAULT_DEVICE_ID;
import static android.companion.virtual.VirtualDeviceManager.INVALID_DEVICE_ID;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.annotation.Nullable;
import android.companion.virtual.VirtualDevice;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceManagerBasicTest {

    private static final String VIRTUAL_DEVICE_NAME = "VirtualDeviceName";

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();
    private static final VirtualDeviceParams NAMED_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder()
                    .setName(VIRTUAL_DEVICE_NAME)
                    .build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule =
            new FakeAssociationRule(/* numAssociations= */2);

    private VirtualDeviceManager mVirtualDeviceManager;
    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    @Nullable
    private VirtualDeviceManager.VirtualDevice mAnotherVirtualDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
        if (mAnotherVirtualDevice != null) {
            mAnotherVirtualDevice.close();
        }
    }

    @Test
    public void createVirtualDevice_shouldNotThrowException() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        assertThat(mVirtualDevice).isNotNull();
        assertThat(mVirtualDevice.getDeviceId()).isGreaterThan(DEFAULT_DEVICE_ID);
    }

    @Test
    public void createVirtualDevice_deviceIdIsUniqueAndIncremented() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo(0).getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mAnotherVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo(1).getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        assertThat(mAnotherVirtualDevice).isNotNull();
        assertThat(mVirtualDevice.getDeviceId() + 1).isEqualTo(mAnotherVirtualDevice.getDeviceId());
    }

    @Test
    public void createVirtualDevice_noPermission_shouldThrowSecurityException() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();

        assertThrows(
                SecurityException.class,
                () -> mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS));
    }

    @Test
    public void createVirtualDevice_invalidAssociationId_shouldThrowIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mVirtualDeviceManager.createVirtualDevice(
                        /* associationId= */ -1,
                        DEFAULT_VIRTUAL_DEVICE_PARAMS));
    }

    @Test
    public void getVirtualDevices_noVirtualDevices_returnsEmptyList() {
        assertThat(mVirtualDeviceManager.getVirtualDevices()).isEmpty();
    }

    @Test
    public void getVirtualDevices_returnsAllVirtualDevices() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo(0).getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mAnotherVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo(1).getId(),
                        NAMED_VIRTUAL_DEVICE_PARAMS);
        assertThat(mAnotherVirtualDevice).isNotNull();

        List<VirtualDevice> virtualDevices = mVirtualDeviceManager.getVirtualDevices();
        assertThat(virtualDevices).hasSize(2);

        VirtualDevice device = virtualDevices.get(0);
        assertThat(device.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
        assertThat(device.getName()).isNull();

        VirtualDevice anotherDevice = virtualDevices.get(1);
        assertThat(anotherDevice.getDeviceId()).isEqualTo(mAnotherVirtualDevice.getDeviceId());
        assertThat(anotherDevice.getName()).isEqualTo(VIRTUAL_DEVICE_NAME);
    }

    @Test
    public void createDeviceContext_invalidDeviceId_shouldThrowIllegalArgumentException() {
        Context context = getApplicationContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.createDeviceContext(INVALID_DEVICE_ID));
    }

    @Test
    public void createDeviceContext_missingDeviceId_shouldThrowIllegalArgumentException() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        Context context = getApplicationContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.createDeviceContext(mVirtualDevice.getDeviceId() + 1));
    }

    @Test
    public void createDeviceContext_defaultDeviceId() {
        Context context = getApplicationContext();
        Context defaultDeviceContext = context.createDeviceContext(DEFAULT_DEVICE_ID);

        assertThat(defaultDeviceContext.getDeviceId()).isEqualTo(DEFAULT_DEVICE_ID);
    }

    @Test
    public void createDeviceContext_validVirtualDeviceId() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        Context context = getApplicationContext();
        Context virtualDeviceContext =
                context.createDeviceContext(mVirtualDevice.getDeviceId());

        assertThat(virtualDeviceContext.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());

        // The default device context should be available from the virtual device one.
        Context defaultDeviceContext = virtualDeviceContext.createDeviceContext(DEFAULT_DEVICE_ID);

        assertThat(defaultDeviceContext.getDeviceId()).isEqualTo(DEFAULT_DEVICE_ID);
    }

    @Test
    public void getDevicePolicy_noPolicySpecified_shouldReturnDefault() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);

        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void getDevicePolicy_shouldReturnConfiguredValue() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder()
                                .addDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                                .build());

        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
    }

    @Test
    public void getDevicePolicy_virtualDeviceClosed_shouldReturnDefault() {
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        new VirtualDeviceParams.Builder()
                                .addDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                                .build());
        mVirtualDevice.close();

        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }
}

