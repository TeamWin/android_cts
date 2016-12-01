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
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.test.MoreAsserts;
import android.util.Log;

import com.android.cts.comp.AdminReceiver;
import com.android.cts.comp.CrossUserService;
import com.android.cts.comp.ExportedCrossUserService;
import com.android.cts.comp.ICrossUserService;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Test suite for the case where we have the same package for both device owner and profile owner.
 */
public class BindDeviceAdminServiceCorpOwnedManagedProfileTestSuite {
    private static final String TAG = "BindServiceTest";

    private static final ServiceConnection EMPTY_SERVICE_CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    private final Context mContext;
    private static final String NON_MANAGING_PACKAGE = AdminReceiver.COMP_DPC_2_PACKAGE_NAME;
    private final UserHandle mTargetUserHandle;
    private final DevicePolicyManager mDpm;

    /**
     * @param context
     * @param targetUserHandle Which user we are talking to.
     */
    public BindDeviceAdminServiceCorpOwnedManagedProfileTestSuite(
            Context context, UserHandle targetUserHandle) {
        mContext = context;
        mTargetUserHandle = targetUserHandle;
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
    }

    public void execute() throws Exception {
        checkCannotBind_implicitIntent();
        checkCannotBind_notResolvableIntent();
        checkCannotBind_exportedCrossUserService();
        checkCannotBind_nonManagingPackage();
        checkCannotBind_sameUser();
        checkCrossProfileCall_echo();
        checkCrossProfileCall_getUserHandle();
    }

    /**
     * If the intent is implicit, expected to throw {@link IllegalArgumentException}.
     */
    private void checkCannotBind_implicitIntent() throws Exception {
        try {
            final Intent implicitIntent = new Intent(Intent.ACTION_VIEW);
            bind(implicitIntent, EMPTY_SERVICE_CONNECTION, mTargetUserHandle);
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ex) {
            MoreAsserts.assertContainsRegex("Service intent must be explicit", ex.getMessage());
        }
    }

    /**
     * If the intent is not resolvable, it should return {@code {@code null}}.
     */
    private void checkCannotBind_notResolvableIntent() throws Exception {
        final Intent notResolvableIntent = new Intent();
        notResolvableIntent.setClassName(mContext, "NotExistService");
        Log.d(TAG, "checkCannotBind_notResolvableIntent: ");
        assertFalse(bind(notResolvableIntent, EMPTY_SERVICE_CONNECTION, mTargetUserHandle));
    }

    /**
     * Make sure we cannot bind exported service.
     */
    private void checkCannotBind_exportedCrossUserService() throws Exception {
        try {
            final Intent serviceIntent = new Intent(mContext, ExportedCrossUserService.class);
            bind(serviceIntent, EMPTY_SERVICE_CONNECTION, mTargetUserHandle);
            fail("SecurityException should be thrown");
        } catch (SecurityException ex) {
            MoreAsserts.assertContainsRegex("must be unexported", ex.getMessage());
        }
    }

    /**
     * Talk to a DPC package that is neither device owner nor profile owner.
     */
    private void checkCannotBind_nonManagingPackage() throws Exception {
        try {
            final Intent serviceIntent = new Intent();
            serviceIntent.setClassName(NON_MANAGING_PACKAGE, CrossUserService.class.getName());
            bind(serviceIntent, EMPTY_SERVICE_CONNECTION, mTargetUserHandle);
            fail("SecurityException should be thrown");
        } catch (SecurityException ex) {
            MoreAsserts.assertContainsRegex("Only allow to bind service", ex.getMessage());
        }
    }

    /**
     * Talk to the same DPC in same user, that is talking to itself.
     */
    private void checkCannotBind_sameUser() throws Exception {
        try {
            final Intent serviceIntent = new Intent(mContext, CrossUserService.class);
            bind(serviceIntent, EMPTY_SERVICE_CONNECTION, Process.myUserHandle());
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ex) {
            MoreAsserts.assertContainsRegex("target user id must be different", ex.getMessage());
        }
    }

    /**
     * Send a String to other side and expect the exact same string is echoed back.
     */
    private void checkCrossProfileCall_echo() throws Exception {
        final String ANSWER = "42";
        assertCrossProfileCall(ANSWER, service -> service.echo(ANSWER), mTargetUserHandle);
    }

    /**
     * Make sure we are talking to the target user.
     */
    private void checkCrossProfileCall_getUserHandle() throws Exception {
        assertCrossProfileCall(
                mTargetUserHandle, service -> service.getUserHandle(), mTargetUserHandle);
    }

    /**
     * Convenient method for you to execute a cross user call and assert the return value of it.
     * @param expected The expected result of the cross user call.
     * @param callable It is called when the service is bound, use this to make the service call.
     * @param targetUserHandle Which user are we talking to.
     * @param <T> The return type of the service call.
     */
    private <T> void assertCrossProfileCall(
            T expected, CrossUserCallable<T> callable, UserHandle targetUserHandle)
            throws Exception {
        final LinkedBlockingQueue<ICrossUserService> queue
                = new LinkedBlockingQueue<ICrossUserService>();
        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected is called");
                queue.add(ICrossUserService.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected is called");
            }
        };
        final Intent serviceIntent = new Intent(mContext, CrossUserService.class);
        assertTrue(bind(serviceIntent, serviceConnection, targetUserHandle));
        ICrossUserService service = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull("binding to the target service timed out", service);
        try {
            assertEquals(expected, callable.call(service));
        } finally {
            mContext.unbindService(serviceConnection);
        }
    }

    private boolean bind(Intent serviceIntent, ServiceConnection serviceConnection,
            UserHandle userHandle) {
        return mDpm.bindDeviceAdminServiceAsUser(AdminReceiver.getComponentName(mContext),
                serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE, userHandle);
    }

    interface CrossUserCallable<T> {
        T call(ICrossUserService service) throws RemoteException;
    }
}
