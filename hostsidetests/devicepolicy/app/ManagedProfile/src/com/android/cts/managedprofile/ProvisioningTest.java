/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.managedprofile;

import static org.junit.Assert.assertTrue;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import 	android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.util.Log;

import com.android.compatibility.common.util.devicepolicy.provisioning.SilentProvisioningTestManager;
import org.junit.Before;
import org.junit.Test;

@SmallTest
public class ProvisioningTest {
    private static final String TAG = ProvisioningTest.class.getSimpleName();

    private static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            ProvisioningAdminReceiver.class.getPackage().getName(),
            ProvisioningAdminReceiver.class.getName());

    public static class ProvisioningAdminReceiver extends DeviceAdminReceiver {
        @Override
        public void onProfileProvisioningComplete(Context context, Intent intent) {
            super.onProfileProvisioningComplete(context, intent);
            // Enabled profile
            getManager(context).setProfileName(ADMIN_RECEIVER_COMPONENT, "Managed Profile");
            getManager(context).setProfileEnabled(ADMIN_RECEIVER_COMPONENT);
            Log.i(TAG, "onProfileProvisioningComplete");
        }

    }

    private Context mContext;
    private DevicePolicyManager mDpm;

    @Before
    protected void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
    }

    @Test
    public void testIsManagedProfile() {
        assertTrue(mDpm.isManagedProfile(ADMIN_RECEIVER_COMPONENT));
        Log.i(TAG, "managed profile app: " + ADMIN_RECEIVER_COMPONENT.getPackageName());
    }

    @Test
    public void testProvisionManagedProfile() throws InterruptedException {
        provisionManagedProfile();
    }

    private void provisionManagedProfile() throws InterruptedException {
        Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                        ADMIN_RECEIVER_COMPONENT)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
        SilentProvisioningTestManager provisioningManager =
                new SilentProvisioningTestManager(mContext);
        assertTrue(provisioningManager.startProvisioningAndWait(intent));
        Log.i(TAG, "managed profile provisioning successful");
    }
}
