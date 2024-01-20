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

package android.telecom.cts.cuj.app.integration;

import static android.telecom.cts.apps.TelecomTestApp.SELF_MANAGED_CS_MAIN_ACCOUNT_CUSTOM;
import static android.telecom.cts.apps.TelecomTestApp.TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class PhoneAccountTest extends BaseAppVerifier {
    /*********************************************************************************************
     *                           ConnectionServiceVoipAppMain
     /*********************************************************************************************/

    /**
     * Test the scenario where a self-managed application that registers a default phone account at
     * startup can retrieve their account and unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testAccountTest_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            PhoneAccount account = verifyDefaultAccountIsRegistered(voipCsApp);
            unregisterDefaultAndVerify(voipCsApp, account);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a self-managed application that registers a secondary phone account
     * on top of an already existing phone account.  The application should also be able to query
     * and unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a secondary  {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testCustomAccountTest_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);

            registerCustomAcctAndVerify(
                    voipCsApp,
                    SELF_MANAGED_CS_MAIN_ACCOUNT_CUSTOM,
                    2 /* numOfExpectedAccounts */);

            unregisterCustomAcctAndVerify(
                    voipCsApp,
                    SELF_MANAGED_CS_MAIN_ACCOUNT_CUSTOM,
                    1 /* numOfExpectedAccounts */);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /*********************************************************************************************
     *                       TransactionalVoipAppMain
     /*********************************************************************************************/

    /**
     * Test the scenario where a self-managed application that registers a default phone account at
     * startup can retrieve their account and unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed  {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testAccountTest_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            PhoneAccount account = verifyDefaultAccountIsRegistered(transactionalApp);
            unregisterDefaultAndVerify(transactionalApp, account);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where a managed application that registers a secondary phone account on top
     * of an already existing phone account.  The application should also be able to query and
     * unregister it.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a secondary  {@link android.telecom.PhoneAccount }
     * <p>
     *  2. register the managed {@link android.telecom.PhoneAccount } when the application is
     *  started via {@link TelecomManager#registerPhoneAccount(PhoneAccount)}
     * <p>
     *  3. verify the {@link PhoneAccount} is retrievable via
     *  {@link TelecomManager#getOwnSelfManagedPhoneAccounts()}
     * <p>
     *  4. unregister the {@link PhoneAccount} via
     *  {@link TelecomManager#unregisterPhoneAccount(PhoneAccountHandle)}
     * <p>
     *  5. verify the account is unregistered
     *  </ul>
     */
    @Test
    public void testCustomAccountTest_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom || S_IS_TEST_DISABLED) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            registerCustomAcctAndVerify(
                    transactionalApp,
                    TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT,
                    2 /* numOfExpectedAccounts */);

            unregisterCustomAcctAndVerify(
                    transactionalApp,
                    TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT,
                    1 /* numOfExpectedAccounts */);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /*********************************************************************************************
     *                           Helpers
     /*********************************************************************************************/

    private PhoneAccount verifyDefaultAccountIsRegistered(AppControlWrapper appControlWrapper)
            throws RemoteException {
        PhoneAccount account = appControlWrapper.getDefaultPhoneAccount();
        assertNotNull(account);
        assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
        List<PhoneAccountHandle> handles = appControlWrapper.getAccountHandlesForApp();
        assertEquals(1, handles.size());
        return account;
    }

    private void unregisterDefaultAndVerify(AppControlWrapper appControlWrapper,
                                            PhoneAccount acct) throws RemoteException {
        appControlWrapper.unregisterPhoneAccountWithHandle(acct.getAccountHandle());
        assertFalse(isPhoneAccountRegistered(acct.getAccountHandle()));
        List<PhoneAccountHandle> handles = appControlWrapper.getAccountHandlesForApp();
        assertEquals(0, handles.size());
    }

    private void registerCustomAcctAndVerify(AppControlWrapper appControlWrapper,
                                            PhoneAccount acct,
                                            int numOfExpectedAccounts) throws RemoteException {
        appControlWrapper.registerCustomPhoneAccount(acct);
        List<PhoneAccountHandle> handles = appControlWrapper.getAccountHandlesForApp();
        assertEquals(numOfExpectedAccounts, handles.size()); // TODO:: fails here for transactional
        // services due to b/310739126
    }

    private void unregisterCustomAcctAndVerify(AppControlWrapper appControlWrapper,
                                              PhoneAccount acct,
                                              int numOfExpectedAccounts) throws RemoteException {
        appControlWrapper.unregisterPhoneAccountWithHandle(acct.getAccountHandle());
        assertFalse(isPhoneAccountRegistered(acct.getAccountHandle()));
        assertEquals(numOfExpectedAccounts, appControlWrapper.getAccountHandlesForApp().size());
    }
}
