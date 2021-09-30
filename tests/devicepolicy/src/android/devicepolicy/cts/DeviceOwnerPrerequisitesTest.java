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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstanceReference;
import com.android.bedstead.testapp.TestAppProvider;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DeviceOwnerPrerequisitesTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    private static final TestApp sAccountManagementApp = sTestAppProvider
            .query()
            .wherePackageName()
            // TODO(b/198423919): Support Querying services in TestApp
            .isEqualTo("com.android.bedstead.testapp.AccountManagementApp")
            .get();
    private static final TestApp sDpcApp = sTestAppProvider
            .query()
            // TODO(b/198423919): Support Querying services in TestApp
            .wherePackageName().isEqualTo(RemoteDpc.DPC_COMPONENT_NAME.getPackageName())
            .get();

    private static final String EXISTING_ACCOUNT_TYPE =
            "com.android.bedstead.testapp.AccountManagementApp.account.type";
    private static final int ACCOUNT_MANAGER_WAIT_MILLIS = 1000;
    private static final int DEVICE_POLICY_MANAGER_WAIT_MILLIS = 5000;
    private static final String SET_DEVICE_OWNER_COMMAND = "dpm set-device-owner";
    private static final Account ACCOUNT_WITH_EXISTING_TYPE
            = new Account("user0", EXISTING_ACCOUNT_TYPE);
    private static final String TEST_PASSWORD = "password";

    private AccountManager mAccountManager;

    @Before
    public void setUp() {
        mAccountManager = sContext.getSystemService(AccountManager.class);
    }

    @Test
    @Postsubmit(reason = "new test with sleep")
    @EnsureHasNoDeviceOwner
    public void setDeviceOwnerViaAdb_deviceHasAccount_fails()
            throws InterruptedException {
        try (TestAppInstanceReference accountAuthenticatorApp =
                     sAccountManagementApp.install(sTestApis.users().instrumented());
            TestAppInstanceReference dpcApp = sDpcApp.install(sTestApis.users().instrumented())) {
            addAccountWithType();

            assertThrows(AdbException.class, () ->
                    ShellCommand
                            .builderForUser(
                                    sTestApis.users().instrumented(), SET_DEVICE_OWNER_COMMAND)
                            .addOperand(RemoteDpc.DPC_COMPONENT_NAME.flattenToString())
                            .execute());
            assertThat(sTestApis.devicePolicy().getDeviceOwner()).isNull();

            // TODO(b/199174169): Uninstalling a recently-set DPC sometimes fails
            Thread.sleep(DEVICE_POLICY_MANAGER_WAIT_MILLIS);
        }
    }

    /**
     * Blocks until an account of {@code type} is added.
     */
    // TODO(b/199077745): Remove sleep once AccountManager race condition is fixed
    private void addAccountWithType() throws InterruptedException {
        Thread.sleep(ACCOUNT_MANAGER_WAIT_MILLIS);
        mAccountManager.addAccountExplicitly(
                ACCOUNT_WITH_EXISTING_TYPE,
                TEST_PASSWORD,
                /* userdata= */ null);
    }
}
