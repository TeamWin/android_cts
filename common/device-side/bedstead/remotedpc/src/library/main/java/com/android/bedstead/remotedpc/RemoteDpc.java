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

package com.android.bedstead.remotedpc;

import static com.android.compatibility.common.util.FileUtils.readInputStreamFully;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.DevicePolicyController;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;

import java.io.IOException;
import java.io.InputStream;

/** Entry point to RemoteDPC. */
public final class RemoteDpc {

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final ComponentName DPC_COMPONENT = new ComponentName(
            "com.android.bedstead.remotedpc.dpc",
            "com.android.eventlib.premade.EventLibDeviceAdminReceiver"
    );

    /**
     * Get the {@link RemoteDpc} instance for the Device Owner.
     *
     * <p>This will return {@code null} if there is no Device Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc deviceOwner() {
        DeviceOwner deviceOwner = sTestApis.devicePolicy().getDeviceOwner();
        if (deviceOwner == null || !deviceOwner.componentName().equals(DPC_COMPONENT)) {
            return null;
        }

        return new RemoteDpc(deviceOwner);
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the current user.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner() {
        return profileOwner(sTestApis.users().instrumented());
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the given {@code profile}.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner(UserHandle profile) {
        if (profile == null) {
            throw new NullPointerException();
        }

        return profileOwner(sTestApis.users().find(profile));
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the given {@code profile}.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner(UserReference profile) {
        if (profile == null) {
            throw new NullPointerException();
        }

        ProfileOwner profileOwner = sTestApis.devicePolicy().getProfileOwner(profile);
        if (profileOwner == null || !profileOwner.componentName().equals(DPC_COMPONENT)) {
            return null;
        }

        return new RemoteDpc(profileOwner);
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any() {
        return any(sTestApis.users().instrumented());
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any(UserHandle user) {
        if (user == null) {
            throw new NullPointerException();
        }

        return any(sTestApis.users().find(user));
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any(UserReference user) {
        RemoteDpc remoteDPC = profileOwner(user);
        if (remoteDPC != null) {
            return remoteDPC;
        }
        return deviceOwner();
    }

    /**
     * Set RemoteDPC as the Device Owner.
     */
    public static RemoteDpc setAsDeviceOwner(UserHandle user) {
        if (user == null) {
            throw new NullPointerException();
        }
        return setAsDeviceOwner(sTestApis.users().find(user));
    }

    /**
     * Set RemoteDPC as the Device Owner.
     */
    public static RemoteDpc setAsDeviceOwner(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        DeviceOwner deviceOwner = sTestApis.devicePolicy().getDeviceOwner();
        if (deviceOwner != null) {
            if (deviceOwner.componentName().equals(DPC_COMPONENT)) {
                return new RemoteDpc(deviceOwner); // Already set
            }
            deviceOwner.remove();
        }

        ensureInstalled(user);
        return new RemoteDpc(sTestApis.devicePolicy().setDeviceOwner(user, DPC_COMPONENT));
    }

    /**
     * Set RemoteDPC as the Profile Owner.
     */
    public static RemoteDpc setAsProfileOwner(UserHandle user) {
        if (user == null) {
            throw new NullPointerException();
        }
        return setAsProfileOwner(sTestApis.users().find(user));
    }

    /**
     * Set RemoteDPC as the Profile Owner.
     */
    public static RemoteDpc setAsProfileOwner(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        ProfileOwner profileOwner = sTestApis.devicePolicy().getProfileOwner(user);
        if (profileOwner != null) {
            if (profileOwner.componentName().equals(DPC_COMPONENT)) {
                return new RemoteDpc(profileOwner); // Already set
            }
            profileOwner.remove();
        }

        ensureInstalled(user);
        return new RemoteDpc(sTestApis.devicePolicy().setProfileOwner(user, DPC_COMPONENT));
    }

    private static void ensureInstalled(UserReference user) {
        sTestApis.packages().install(user, apkBytes());
    }

    private static byte[] apkBytes() {
        int apkId = sContext.getResources().getIdentifier(
                "raw/RemoteDPC_DPC", /* defType= */ null, sContext.getPackageName());
        try (InputStream inputStream =
                     sContext.getResources().openRawResource(apkId)) {
            return readInputStreamFully(inputStream);
        } catch (IOException e) {
            throw new NeneException("Error when reading RemoteDPC bytes", e);
        }
    }

    private final DevicePolicyController mDevicePolicyController;

    private RemoteDpc(DevicePolicyController devicePolicyController) {
        if (devicePolicyController == null) {
            throw new NullPointerException();
        }
        mDevicePolicyController = devicePolicyController;
    }

    /**
     * Get the {@link DevicePolicyController} for this instance of RemoteDPC.
     */
    public DevicePolicyController devicePolicyController() {
        return mDevicePolicyController;
    }

    /**
     * Remove RemoteDPC as Device Owner or Profile Owner and uninstall the APK from the user.
     */
    public void remove() {
        mDevicePolicyController.remove();
        sTestApis.packages().find(DPC_COMPONENT.getPackageName())
                .uninstall(mDevicePolicyController.user());
    }

    @Override
    public int hashCode() {
        return mDevicePolicyController.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RemoteDpc)) {
            return false;
        }

        RemoteDpc other = (RemoteDpc) obj;
        return other.mDevicePolicyController.equals(mDevicePolicyController);
    }
}
