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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.util.Log;

import com.android.compatibility.common.util.devicepolicy.provisioning.SilentProvisioningTestManager;
import org.junit.Before;
import org.junit.Test;

@SmallTest
public class ProvisioningTest {
    private static final String TAG = ProvisioningTest.class.getSimpleName();

    private static final String SHARED_PREFERENCE_FILE_NAME = "shared-preferences-file-name";

    private static final PersistableBundle ADMIN_EXTRAS_BUNDLE = new PersistableBundle();
    private static final String ADMIN_EXTRAS_BUNDLE_KEY_1 = "KEY_1";
    private static final String ADMIN_EXTRAS_BUNDLE_VALUE_1 = "VALUE_1";
    static {
        ADMIN_EXTRAS_BUNDLE.putString(ADMIN_EXTRAS_BUNDLE_KEY_1, ADMIN_EXTRAS_BUNDLE_VALUE_1);
    }

    public static final String KEY_PROVISIONING_SUCCESSFUL_RECEIVED =
            "key-provisioning-successful-received";

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

            saveBundle(context, intent.getParcelableExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE));
        }
    }

    private Context mContext;
    private DevicePolicyManager mDpm;

    @Before
    public void setUp() {
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

    @Test
    public void testVerifyAdminExtraBundle() {
        PersistableBundle bundle = loadBundle(mContext);
        assertNotNull(bundle);
        assertEquals(ADMIN_EXTRAS_BUNDLE_VALUE_1, bundle.getString(ADMIN_EXTRAS_BUNDLE_KEY_1));
    }

    @Test
    public void testVerifySuccessfulIntentWasReceived() {
        assertTrue(getSharedPreferences(mContext).getBoolean(KEY_PROVISIONING_SUCCESSFUL_RECEIVED,
                false));
    }

    private void provisionManagedProfile() throws InterruptedException {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, ADMIN_RECEIVER_COMPONENT)
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, ADMIN_EXTRAS_BUNDLE);
        SilentProvisioningTestManager provisioningManager =
                new SilentProvisioningTestManager(mContext);
        assertTrue(provisioningManager.startProvisioningAndWait(intent));
        Log.i(TAG, "managed profile provisioning successful");
    }

    private static void saveBundle(Context context, PersistableBundle bundle) {
        if (bundle == null) {
            Log.e(TAG, "null saveBundle");
            return;
        }

        getSharedPreferences(context).edit()
                .putString(ADMIN_EXTRAS_BUNDLE_KEY_1, bundle.getString(ADMIN_EXTRAS_BUNDLE_KEY_1))
                .commit();
    }

    private static PersistableBundle loadBundle(Context context) {
        SharedPreferences pref = getSharedPreferences(context);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ADMIN_EXTRAS_BUNDLE_KEY_1,
                pref.getString(ADMIN_EXTRAS_BUNDLE_KEY_1, null));
        return bundle;
    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCE_FILE_NAME, 0);
    }

}
