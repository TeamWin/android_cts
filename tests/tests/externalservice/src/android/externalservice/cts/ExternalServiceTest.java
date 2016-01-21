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

package android.externalservice.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.test.AndroidTestCase;
import android.util.Log;
import android.util.MutableInt;

import android.externalservice.common.ServiceMessages;

public class ExternalServiceTest extends AndroidTestCase {
    private static final String TAG = "ExternalServiceTest";

    static final String sServicePackage = "android.externalservice.service";

    private Connection mConnection = new Connection();

    private ConditionVariable mCondition = new ConditionVariable(false);

    static final int CONDITION_TIMEOUT = 10 * 1000 /* 10 seconds */;

    public void tearDown() {
        if (mConnection.service != null)
            getContext().unbindService(mConnection);
    }

    /** Tests that an isolatedProcess service cannot be bound to by an external package. */
    public void testFailBindIsolated() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".IsolatedService"));
        try {
            getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            fail("Should not be able to bind to non-exported, non-external service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE does not work with plain isolatedProcess services. */
    public void testFailBindExternalIsolated() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".IsolatedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-exported, non-external service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE does not work with exported, isolatedProcess services (
     * requires externalService as well). */
    public void testFailBindExternalExported() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExportedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-external service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE requires that an externalService be exported. */
    public void testFailBindExternalNonExported() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage, sServicePackage+".ExternalNonExportedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-exported service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE requires the service be an isolatedProcess. */
    public void testFailBindExternalNonIsolated() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage, sServicePackage+".ExternalNonIsolatedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-isolated service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that an externalService can only be bound with BIND_EXTERNAL_SERVICE. */
    public void testFailBindWithoutBindExternal() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));
        try {
            getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            fail("Should not be able to bind to an external service without BIND_EXTERNAL_SERVICE");
        } catch (SecurityException e) {
        }
    }

    /** Tests that an external service can be bound, and that it runs as a different principal. */
    public void testBindExternalService() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));

        mCondition.close();
        assertTrue(getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        assertEquals(getContext().getPackageName(), mConnection.name.getPackageName());
        assertNotSame(sServicePackage, mConnection.name.getPackageName());

        // Check the identity of the service.
        Messenger remote = new Messenger(mConnection.service);
        MutableInt uid = new MutableInt(0);
        MutableInt pid = new MutableInt(0);
        StringBuilder pkg = new StringBuilder();
        assertTrue(identifyService(remote, uid, pid, pkg));

        assertFalse(uid.value == 0 || pid.value == 0);
        assertNotEquals(Process.myUid(), uid.value);
        assertNotEquals(Process.myPid(), pid.value);
        assertEquals(getContext().getPackageName(), pkg.toString());
    }

    /** Tests that the APK providing the externalService can bind the service itself, and that
     * other APKs bind to a different instance of it. */
    public void testBindExternalServiceWithRunningOwn() {
        // Start the service that will create the externalService.
        final Connection creatorConnection = new Connection();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ServiceCreator"));

        mCondition.close();
        assertTrue(getContext().bindService(intent, creatorConnection, Context.BIND_AUTO_CREATE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Get the identity of the creator.
        Messenger remoteCreator = new Messenger(creatorConnection.service);
        MutableInt creatorUid = new MutableInt(0);
        MutableInt creatorPid = new MutableInt(0);
        StringBuilder creatorPkg = new StringBuilder();
        assertTrue(identifyService(remoteCreator, creatorUid, creatorPid, creatorPkg));
        assertFalse(creatorUid.value == 0 || creatorPid.value == 0);

        // Have the creator actually start its service.
        final Message creatorMsg =
                Message.obtain(null, ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE);
        Handler creatorHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "Received message: " + msg);
                switch (msg.what) {
                    case ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE_RESPONSE:
                        creatorMsg.copyFrom(msg);
                        mCondition.open();
                        break;
                }
                super.handleMessage(msg);
            }
        };
        Messenger localCreator = new Messenger(creatorHandler);
        creatorMsg.replyTo = localCreator;
        try {
            mCondition.close();
            remoteCreator.send(creatorMsg);
        } catch (RemoteException e) {
            fail("Unexpected remote exception" + e);
            return;
        }
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Get the connection to the creator's service.
        assertNotNull(creatorMsg.obj);
        Messenger remoteCreatorService = (Messenger) creatorMsg.obj;
        MutableInt creatorServiceUid = new MutableInt(0);
        MutableInt creatorServicePid = new MutableInt(0);
        StringBuilder creatorServicePkg = new StringBuilder();
        assertTrue(identifyService(remoteCreatorService, creatorServiceUid, creatorServicePid,
                creatorServicePkg));
        assertFalse(creatorServiceUid.value == 0 || creatorPid.value == 0);

        // Create an external service from this (the test) process.
        intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));

        mCondition.close();
        assertTrue(getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        MutableInt serviceUid = new MutableInt(0);
        MutableInt servicePid = new MutableInt(0);
        StringBuilder servicePkg = new StringBuilder();
        assertTrue(identifyService(new Messenger(mConnection.service), serviceUid, servicePid,
                servicePkg));
        assertFalse(serviceUid.value == 0 || servicePid.value == 0);

        // Make sure that all the processes are unique.
        int myUid = Process.myUid();
        int myPid = Process.myPid();
        String myPkg = getContext().getPackageName();

        assertNotEquals(myUid, creatorUid.value);
        assertNotEquals(myUid, creatorServiceUid.value);
        assertNotEquals(myUid, serviceUid.value);
        assertNotEquals(myPid, creatorPid.value);
        assertNotEquals(myPid, creatorServicePid.value);
        assertNotEquals(myPid, servicePid.value);

        assertNotEquals(creatorUid.value, creatorServiceUid.value);
        assertNotEquals(creatorUid.value, serviceUid.value);
        assertNotEquals(creatorPid.value, creatorServicePid.value);
        assertNotEquals(creatorPid.value, servicePid.value);

        assertNotEquals(creatorServiceUid.value, serviceUid.value);
        assertNotEquals(creatorServicePid.value, servicePid.value);

        assertNotEquals(myPkg, creatorPkg.toString());
        assertNotEquals(myPkg, creatorServicePkg.toString());
        assertEquals(creatorPkg.toString(), creatorServicePkg.toString());
        assertEquals(myPkg, servicePkg.toString());

        getContext().unbindService(creatorConnection);
    }

    /** Tests that the binding to an externalService can be changed. */
    public void testBindExternalAboveClient() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));

        mCondition.close();
        Connection initialConn = new Connection();
        assertTrue(getContext().bindService(intent, initialConn,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        MutableInt uidOne = new MutableInt(0);
        MutableInt pidOne = new MutableInt(0);
        StringBuilder pkgOne = new StringBuilder();
        assertTrue(identifyService(new Messenger(initialConn.service), uidOne, pidOne, pkgOne));
        assertFalse(uidOne.value == 0 || pidOne.value == 0);

        // Bind the service with a different priority.
        mCondition.close();
        Connection prioConn = new Connection();
        assertTrue(getContext().bindService(intent, prioConn,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE |
                            Context.BIND_ABOVE_CLIENT));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        MutableInt uidTwo = new MutableInt(0);
        MutableInt pidTwo = new MutableInt(0);
        StringBuilder pkgTwo = new StringBuilder();
        Messenger prioMessenger = new Messenger(prioConn.service);
        assertTrue(identifyService(prioMessenger, uidTwo, pidTwo, pkgTwo));
        assertFalse(uidTwo.value == 0 || pidTwo.value == 0);

        assertEquals(uidOne.value, uidTwo.value);
        assertEquals(pidOne.value, pidTwo.value);
        assertEquals(pkgOne.toString(), pkgTwo.toString());
        assertNotEquals(Process.myUid(), uidOne.value);
        assertNotEquals(Process.myPid(), pidOne.value);
        assertEquals(getContext().getPackageName(), pkgOne.toString());

        getContext().unbindService(prioConn);
        getContext().unbindService(initialConn);
    }

    /** Given a Messenger, this will message the service to retrieve its UID, PID, and package name,
     * storing the results in the mutable parameters. */
    private boolean identifyService(Messenger service, final MutableInt uid, final MutableInt pid,
            final StringBuilder packageName) {
        Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "Received message: " + msg);
                switch (msg.what) {
                    case ServiceMessages.MSG_IDENTIFY_RESPONSE:
                        uid.value = msg.arg1;
                        pid.value = msg.arg2;
                        packageName.append(
                                msg.getData().getString(ServiceMessages.IDENTIFY_PACKAGE));
                        mCondition.open();
                        break;
                }
                super.handleMessage(msg);
            }
        };
        Messenger local = new Messenger(handler);

        Message msg = Message.obtain(null, ServiceMessages.MSG_IDENTIFY);
        msg.replyTo = local;
        try {
            mCondition.close();
            service.send(msg);
        } catch (RemoteException e) {
            fail("Unexpected remote exception: " + e);
            return false;
        }

        return mCondition.block(CONDITION_TIMEOUT);
    }

    private class Connection implements ServiceConnection {
        IBinder service = null;
        ComponentName name = null;
        boolean disconnected = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected " + name);
            this.service = service;
            this.name = name;
            mCondition.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
        }
    }

    private <T> void assertNotEquals(T expected, T actual) {
        assertFalse("Expected <" + expected + "> should not be equal to actual <" + actual + ">",
                expected.equals(actual));
    }
}
