/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.comp.bindservice;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.UserHandle;
import android.test.MoreAsserts;

import com.android.cts.comp.AdminReceiver;
import com.android.cts.comp.CrossUserService;

import static junit.framework.Assert.fail;

/**
 * Test suite for the case that device owner and profile owner are different packages.
 */
public class BindDeviceAdminServiceByodPlusDeviceOwnerTestSuite {

    private static final ServiceConnection EMPTY_SERVICE_CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final String mDpcPackageInOtherSide;
    private final UserHandle mTargetUserHandle;

    /**
     * @param context
     * @param targetUserHandle      Which user are we talking to.
     * @param dpcPackageInOtherSide The dpc package installed in other side, it must not be this
     *                              package.
     */
    public BindDeviceAdminServiceByodPlusDeviceOwnerTestSuite(
            Context context, UserHandle targetUserHandle, String dpcPackageInOtherSide) {
        MoreAsserts.assertNotEqual(context.getPackageName(), dpcPackageInOtherSide);
        mContext = context;
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
        mDpcPackageInOtherSide = dpcPackageInOtherSide;
        mTargetUserHandle = targetUserHandle;
    }

    public void execute() throws Exception {
        checkCannotBind_deviceOwnerProfileOwnerDifferentPackage();
        checkCannotBind_talkToNonManagingPackage();
    }

    /**
     * Device owner and profile owner try to talk to each other. It should fail as they are
     * different packages.
     */
    private void checkCannotBind_deviceOwnerProfileOwnerDifferentPackage() throws Exception {
        try {
            final Intent serviceIntent = new Intent();
            serviceIntent.setClassName(mDpcPackageInOtherSide, CrossUserService.class.getName());
            bind(serviceIntent, EMPTY_SERVICE_CONNECTION, mTargetUserHandle);
            fail("SecurityException should be thrown");
        } catch (SecurityException ex) {
            MoreAsserts.assertContainsRegex(
                    "Not allowed to bind to target user id", ex.getMessage());
        }
    }

    /**
     * Talk to our own instance in other end. It should fail as it is not the same app that
     * is managing that profile.
     */
    private void checkCannotBind_talkToNonManagingPackage() throws Exception {
        try {
            final Intent serviceIntent = new Intent(mContext, CrossUserService.class);
            bind(serviceIntent, EMPTY_SERVICE_CONNECTION, mTargetUserHandle);
            fail("SecurityException should be thrown");
        } catch (SecurityException ex) {
            MoreAsserts.assertContainsRegex(
                    "Not allowed to bind to target user id", ex.getMessage());
        }
    }

    private boolean bind(Intent serviceIntent, ServiceConnection serviceConnection,
            UserHandle userHandle) {
        return mDpm.bindDeviceAdminServiceAsUser(AdminReceiver.getComponentName(mContext),
                serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE, userHandle);
    }
}
