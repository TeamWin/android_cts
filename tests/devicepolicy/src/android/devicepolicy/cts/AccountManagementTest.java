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

import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

import static com.android.bedstead.nene.flags.CommonFlags.DevicePolicyManager.PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG;
import static com.android.bedstead.nene.flags.CommonFlags.NAMESPACE_DEVICE_POLICY_MANAGER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accounts.AccountManager;
import android.accounts.OperationCanceledException;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasAccount;
import com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeatureFlagNotEnabled;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.AccountManagement;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountReference;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.remotedpc.RemotePolicyManager;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(BedsteadJUnit4.class)
@EnsureHasAccountAuthenticator
public final class AccountManagementTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private ComponentName mAdmin;
    private RemoteDevicePolicyManager mDpm;
    private AccountManager mAccountManager;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDpm = dpc.devicePolicyManager();
        mAccountManager = sContext.getSystemService(AccountManager.class);
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void getAccountTypesWithManagementDisabled_emptyByDefault() {
        assertThat(mDpm.getAccountTypesWithManagementDisabled()).isEmpty();
    }

    @Postsubmit(reason = "new test")
    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = AccountManagement.class, includeNonDeviceAdminStates = false)
    public void setAccountTypesWithManagementDisabled_invalidAdmin_throwsException() {
        Exception exception = assertThrows(Exception.class, () ->
                mDpm.setAccountManagementDisabled(
                        mAdmin, sDeviceState.accounts().accountType(), /* disabled= */ false));

        assertTrue("Expected OperationCanceledException or SecurityException to be thrown",
                (exception instanceof OperationCanceledException)
                        || (exception instanceof SecurityException));
    }

    @CanSetPolicyTest(policy = AccountManagement.class, singleTestOnly = true)
    @RequireFeatureFlagNotEnabled(namespace = NAMESPACE_DEVICE_POLICY_MANAGER,
            key = PERMISSION_BASED_ACCESS_EXPERIMENT_FLAG)
    public void setAccountTypesWithManagementDisabled_nullAdmin_throwsException() {
        assertThrows(NullPointerException.class, () ->
                mDpm.setAccountManagementDisabled(
                        /* admin= */ null,
                        sDeviceState.accounts().accountType(), /* disabled= */ false));
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableAccountType_works() {
        try {
            mDpm.setAccountManagementDisabled(
                    mAdmin, sDeviceState.accounts().accountType(), /* disabled= */ true);

            assertThat(mDpm.getAccountTypesWithManagementDisabled()).asList().contains(
                    sDeviceState.accounts().accountType());
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin,
                    sDeviceState.accounts().accountType(), /* disabled= */ false);
        }
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_addSameAccountTypeTwice_presentOnlyOnce() {
        try {
            mDpm.setAccountManagementDisabled(
                    mAdmin, sDeviceState.accounts().accountType(), /* disabled= */ true);
            mDpm.setAccountManagementDisabled(
                    mAdmin, sDeviceState.accounts().accountType(), /* disabled= */ true);

            assertThat(Arrays.stream(mDpm.getAccountTypesWithManagementDisabled())
                    .filter(s -> s.equals(sDeviceState.accounts().accountType()))
                    .count()).isEqualTo(1);
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin,
                    sDeviceState.accounts().accountType(), /* disabled= */ false);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableThenEnable_notDisabled() {
        mDpm.setAccountManagementDisabled(mAdmin,
                sDeviceState.accounts().accountType(), /* disabled= */ true);
        mDpm.setAccountManagementDisabled(mAdmin,
                sDeviceState.accounts().accountType(), /* disabled= */ false);

        assertThat(mDpm.getAccountTypesWithManagementDisabled()).asList().doesNotContain(
                sDeviceState.accounts().accountType());
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void addAccount_fromDpcWithAccountManagementDisabled_accountAdded()
            throws Exception {
        try {
            mDpm.setAccountManagementDisabled(
                    mAdmin, sDeviceState.accounts().accountType(), /* disabled= */ true);

            // Management is disabled, but the DO/PO is still allowed to use the APIs

            try (AccountReference account = TestApis.accounts().wrap(
                    sDeviceState.dpc().user(),
                            sDeviceState.dpc().accountManager())
                    .addAccount()
                    .type(sDeviceState.accounts().accountType())
                    .add()) {
                assertThat(sDeviceState.accounts().allAccounts()).contains(account);
            }
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin,
                    sDeviceState.accounts().accountType(), /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void addAccount_fromDpcWithDisallowModifyAccountsRestriction_accountAdded()
            throws Exception {
        try {
            mDpm.addUserRestriction(mAdmin, DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            try (AccountReference account = TestApis.accounts().wrap(
                            sDeviceState.dpc().user(),
                            sDeviceState.dpc().accountManager())
                    .addAccount()
                    .type(sDeviceState.accounts().accountType())
                    .add()) {
                assertThat(sDeviceState.accounts().allAccounts()).contains(account);
            }
        } finally {
            mDpm.clearUserRestriction(mAdmin, DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void removeAccount_fromDpcWithDisallowModifyAccountsRestriction_accountRemoved()
            throws Exception {
        try {
            mDpm.addUserRestriction(mAdmin, DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            AccountReference account = TestApis.accounts().wrap(
                            sDeviceState.dpc().user(), sDeviceState.dpc().accountManager())
                    .addAccount()
                    .type(sDeviceState.accounts().accountType())
                    .add();

            Bundle result = sDeviceState.dpc().accountManager().removeAccount(
                            account.account(),
                            /* activity= */ null,
                            /* callback= */  null,
                            /* handler= */ null)
                    .getResult();

            assertThat(result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)).isTrue();
            assertThat(sDeviceState.accounts().allAccounts()).doesNotContain(account);
        } finally {
            mDpm.clearUserRestriction(mAdmin, DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void addAccount_withDisallowModifyAccountsRestriction_throwsException() {
        try {
            mDpm.addUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);

            assertThrows(OperationCanceledException.class, () ->
                    mAccountManager.addAccount(
                            sDeviceState.accounts().accountType(),
                            /* authTokenType= */ null,
                            /* requiredFeatures= */ null,
                            /* addAccountOptions= */ null,
                            /* activity= */ null,
                            /* callback= */ null,
                            /* handler= */ null).getResult());
        } finally {
            mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void removeAccount_withDisallowModifyAccountsRestriction_throwsException()
            throws Exception {
        AccountReference account = null;
        try {
            account = sDeviceState.accounts().addAccount().add();

            mDpm.addUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);

            NeneException e = assertThrows(NeneException.class, account::remove);
            assertThat(e).hasCauseThat().isInstanceOf(OperationCanceledException.class);
        } finally {
            mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);

            if (account != null) {
                account.remove();
            }
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void addAccount_withAccountManagementDisabled_throwsException() {
        try {
            mDpm.setAccountManagementDisabled(mAdmin,
                    sDeviceState.accounts().accountType(), /* disabled= */ true);

            assertThrows(Exception.class, () ->
                    sDeviceState.accounts().addAccount().add());
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin,
                    sDeviceState.accounts().accountType(), /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    @EnsureHasAccount
    public void removeAccount_withAccountManagementDisabled_throwsException()
            throws Exception {
        try {
            mDpm.setAccountManagementDisabled(mAdmin,
                    sDeviceState.account().type(), /* disabled= */ true);

            NeneException e = assertThrows(NeneException.class, () ->
                    sDeviceState.account().remove());
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin,
                    sDeviceState.accounts().accountType(), /* disabled= */ false);
        }
    }
}
