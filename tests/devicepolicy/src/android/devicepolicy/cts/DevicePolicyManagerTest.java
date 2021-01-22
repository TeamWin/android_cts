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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.enterprise.DeviceState;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DevicePolicyManagerTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME = new ComponentName(
            sContext, CtsDeviceAdminProfileOwner.class);
    private static final String PROFILE_OWNER_NAME = "testDeviceAdmin";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @RequireRunOnPrimaryUser
    @Test
    public void testCreateAndProvisionManagedProfile_setsProfileOwner() throws Exception {
        UserHandle profile = null;
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            ManagedProfileProvisioningParams params = createManagedProfileProvisioningParams();
            profile = sDevicePolicyManager.createAndProvisionManagedProfile(params);

            final DevicePolicyManager profileDpm = getDpmForProfile(profile);
            assertTrue("Profile owner not set", profileDpm.isProfileOwnerApp(
                                    sContext.getPackageName()));
        } finally {
            if (profile != null) {
                sContext.getSystemService(UserManager.class).removeUser(profile);
            }
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    @RequireRunOnPrimaryUser
    @Test
    public void testCreateAndProvisionManagedProfile_createsProfile() throws Exception {
        UserHandle profile = null;
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            final ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParams();
            profile = sDevicePolicyManager.createAndProvisionManagedProfile(params);

            assertNotNull(profile);
        } finally {
            if (profile != null) {
                sContext.getSystemService(UserManager.class).removeUser(profile);
            }
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    private ManagedProfileProvisioningParams createManagedProfileProvisioningParams() {
        return new ManagedProfileProvisioningParams.Builder(
                        DEVICE_ADMIN_COMPONENT_NAME,
                        PROFILE_OWNER_NAME)
                        .build();
    }

    private DevicePolicyManager getDpmForProfile(UserHandle profile) {
        return sContext.createContextAsUser(profile, /* flags= */ 0).getSystemService(
                DevicePolicyManager.class);
    }
}
