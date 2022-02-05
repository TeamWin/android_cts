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

package android.telecom.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.cts.carmodetestapp.ICtsCarModeInCallServiceControl;
import android.telecom.cts.carmodetestappselfmanaged.CtsCarModeInCallServiceControlSelfManaged;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PhoneAccountRegistrarTest extends BaseTelecomTestWithMockServices {

    private static final String TAG = "PhoneAccountRegistrarTest";
    private static final long TIMEOUT = 3000L;
    public static final long SEED = 52L; // random seed chosen

    // mirrors constant in PhoneAccountRegistrar called MAX_PHONE_ACCOUNT_REGISTRATIONS
    public static final int MAX_PHONE_ACCOUNT_REGISTRATIONS = 10;

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (!mShouldTestTelecom) return;
        setupConnectionService(null, 0);
    }

    /**
     * Test scenario where a single package can register MAX_PHONE_ACCOUNT_REGISTRATIONS via
     * {@link android.telecom.TelecomManager#registerPhoneAccount(PhoneAccount)}  without an
     * exception being thrown.
     */
    public void testRegisterMaxPhoneAccountsWithoutException() {
        if (!mShouldTestTelecom) return;

        // create MAX_PHONE_ACCOUNT_REGISTRATIONS via helper function
        ArrayList<PhoneAccount> accounts = TestUtils.generateRandomPhoneAccounts(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS, TestUtils.PACKAGE, TestUtils.COMPONENT);
        try {
            // register MAX_PHONE_ACCOUNT_REGISTRATIONS to PhoneAccountRegistrar
            accounts.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // assert all were successfully registered
            assertEquals(MAX_PHONE_ACCOUNT_REGISTRATIONS,
                    mTelecomManager.getSelfManagedPhoneAccounts().size());
        } finally {
            // cleanup accounts registered
            accounts.stream().forEach(
                    d -> mTelecomManager.unregisterPhoneAccount(d.getAccountHandle()));
        }
    }

    /**
     * Tests a scenario where a single package exceeds MAX_PHONE_ACCOUNT_REGISTRATIONS and
     * an {@link IllegalArgumentException}  is thrown
     */
    public void testExceptionThrownDueExceededMaxPhoneAccountsRegistrations()
            throws IllegalArgumentException {
        if (!mShouldTestTelecom) return;

        // Create MAX_PHONE_ACCOUNT_REGISTRATIONS + 1 via helper function
        ArrayList<PhoneAccount> accounts = TestUtils.generateRandomPhoneAccounts(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS + 1, TestUtils.PACKAGE,
                TestUtils.COMPONENT);

        try {
            // Try to register more phone accounts than allowed by the upper bound limit
            // MAX_PHONE_ACCOUNT_REGISTRATIONS
            accounts.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // A successful test should never reach this line of execution.
            // However, if it does, fail the test by throwing a fail(...)
            fail("Test failed. The test did not throw an IllegalArgumentException when "
                    + "registering phone accounts over the upper bound: "
                    + "MAX_PHONE_ACCOUNT_REGISTRATIONS");
        } catch (IllegalArgumentException e) {
            // Assert the IllegalArgumentException was thrown
            assertNotNull(e.toString());
        } finally {
            // Cleanup accounts registered
            accounts.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));
        }
    }

    /**
     * Test scenario where two distinct packages register MAX_PHONE_ACCOUNT_REGISTRATIONS via
     * {@link
     * android.telecom.TelecomManager#registerPhoneAccount(PhoneAccount)} without an exception being
     * thrown.
     * This ensures that PhoneAccountRegistrar is handling {@link PhoneAccount} registrations
     * to distinct packages correctly.
     */
    public void testTwoPackagesRegisterMax() throws Exception {
        if (!mShouldTestTelecom) return;

        // Constants for creating a second package to register phone accounts
        final String carPkgSelfManaged =
                CtsCarModeInCallServiceControlSelfManaged.class
                        .getPackage().getName();
        final ComponentName carComponentSelfManaged = ComponentName.createRelative(
                carPkgSelfManaged, CtsCarModeInCallServiceControlSelfManaged.class.getName());
        final String carModeControl =
                "android.telecom.cts.carmodetestapp.ACTION_CAR_MODE_CONTROL";

        // Set up binding for second package. This is needed in order to bypass a SecurityException
        // thrown by a second test package registering phone accounts.
        TestServiceConnection control = setUpControl(carModeControl,
                carComponentSelfManaged);

        ICtsCarModeInCallServiceControl carModeIncallServiceControlSelfManaged =
                ICtsCarModeInCallServiceControl.Stub
                        .asInterface(control.getService());

        carModeIncallServiceControlSelfManaged.reset(); //... done setting up binding

        // Create MAX phone accounts for package 1
        ArrayList<PhoneAccount> accountsPackage1 = TestUtils.generateRandomPhoneAccounts(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS, TestUtils.PACKAGE, TestUtils.COMPONENT);

        // Create MAX phone accounts for package 2
        ArrayList<PhoneAccount> accountsPackage2 = TestUtils.generateRandomPhoneAccounts(SEED,
                MAX_PHONE_ACCOUNT_REGISTRATIONS, carPkgSelfManaged,
                TestUtils.SELF_MANAGED_COMPONENT);

        try {

            // Register all phone accounts created
            for (int i = 0; i < MAX_PHONE_ACCOUNT_REGISTRATIONS; i++) {
                mTelecomManager.registerPhoneAccount(accountsPackage1.get(i));
                carModeIncallServiceControlSelfManaged.registerPhoneAccount(
                        accountsPackage2.get(i));
            }

            // Get all the self-managed phone accounts registered in PhoneAccountRegistrar
            int package1Count, package2Count;
            package1Count = package2Count = 0;

            // Iterate over all phone accounts returned by PhoneAccountRegistrar
            for (PhoneAccountHandle pah : mTelecomManager.getSelfManagedPhoneAccounts()) {
                String packageName = pah.getComponentName().getPackageName();
                if (packageName.equals(TestUtils.PACKAGE)) {
                    package1Count++;
                }
                if (packageName.equals(carPkgSelfManaged)) {
                    package2Count++;
                }
            }

            // assert all accounts were registered successfully to the correct package

            // assert MAX_PHONE_ACCOUNT_REGISTRATIONS were successfully registered to package 1
            assertEquals(MAX_PHONE_ACCOUNT_REGISTRATIONS,
                    package1Count);

            // assert MAX_PHONE_ACCOUNT_REGISTRATIONS were successfully registered to package 2
            assertEquals(MAX_PHONE_ACCOUNT_REGISTRATIONS,
                    package2Count);
        } finally {
            // cleanup all phone accounts registered. Note, unregisterPhoneAccount will not
            // cause a runtime error if no phone account is found when trying to unregister.
            for (int i = 0; i < MAX_PHONE_ACCOUNT_REGISTRATIONS; i++) {
                mTelecomManager.unregisterPhoneAccount(accountsPackage1.get(i).getAccountHandle());
                carModeIncallServiceControlSelfManaged.unregisterPhoneAccount(
                        accountsPackage2.get(i).getAccountHandle());
            }
        }
        // unbind from second package
        mContext.unbindService(control);
    }

    // The following are helper methods for this testing class.

    private TestServiceConnection setUpControl(String action, ComponentName componentName) {
        Intent bindIntent = new Intent(action);
        bindIntent.setComponent(componentName);

        TestServiceConnection
                serviceConnection = new TestServiceConnection();
        mContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!serviceConnection.waitBind()) {
            fail("fail bind to service");
        }
        return serviceConnection;
    }

    private class TestServiceConnection implements ServiceConnection {
        private IBinder mService;
        private CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mIsConnected;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service Connected: " + componentName);
            mService = service;
            mIsConnected = true;
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }

        public IBinder getService() {
            return mService;
        }

        public boolean waitBind() {
            try {
                mLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
                return mIsConnected;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }
}
