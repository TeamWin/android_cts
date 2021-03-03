/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.bedstead.dpmwrapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.HashMap;

final class DevicePolicyManagerWrapper
        extends TestAppSystemServiceFactory.ServiceManagerWrapper<DevicePolicyManager> {

    private static final String TAG = DevicePolicyManagerWrapper.class.getSimpleName();

    private static final HashMap<Context, DevicePolicyManager> sSpies = new HashMap<>();

    @Override
    DevicePolicyManager getWrapper(Context context, DevicePolicyManager dpm, Answer<?> answer) {
        int userId = context.getUserId();
        DevicePolicyManager spy = sSpies.get(context);
        if (spy != null) {
            Log.d(TAG, "get(): returning cached spy for user " + userId);
            return spy;
        }

        Log.d(TAG, "get(): creating spy for user " + context.getUserId());
        spy = Mockito.spy(dpm);

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        try {
            // Basic methods used by most tests
            doAnswer(answer).when(spy).isAdminActive(any());
            doAnswer(answer).when(spy).isDeviceOwnerApp(any());
            doAnswer(answer).when(spy).isManagedProfile(any());
            doAnswer(answer).when(spy).isProfileOwnerApp(any());
            doAnswer(answer).when(spy).isAffiliatedUser();

            // Used by SetTimeTest
            doAnswer(answer).when(spy).setTime(any(), anyLong());
            doAnswer(answer).when(spy).setTimeZone(any(), any());
            doAnswer(answer).when(spy).setGlobalSetting(any(), any(), any());

            // Used by UserControlDisabledPackagesTest
            doAnswer(answer).when(spy).setUserControlDisabledPackages(any(), any());
            doAnswer(answer).when(spy).getUserControlDisabledPackages(any());

            // Used by DeviceOwnerProvisioningTest
            doAnswer(answer).when(spy).enableSystemApp(any(), any(String.class));
            doAnswer(answer).when(spy).enableSystemApp(any(), any(Intent.class));
            doAnswer(answer).when(spy).canAdminGrantSensorsPermissions();

            // Used by HeadlessSystemUserTest
            doAnswer(answer).when(spy).getProfileOwnerAsUser(anyInt());
            doAnswer(answer).when(spy).getProfileOwnerAsUser(any());

            // Used by NetworkLoggingTest
            doAnswer(answer).when(spy).retrieveNetworkLogs(any(), anyLong());
            doAnswer(answer).when(spy).setNetworkLoggingEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).isNetworkLoggingEnabled(any());

            // Used by CtsVerifier
            doAnswer(answer).when(spy).addUserRestriction(any(), any());
            doAnswer(answer).when(spy).clearUserRestriction(any(), any());
            doAnswer(answer).when(spy).clearDeviceOwnerApp(any());
            doAnswer(answer).when(spy).setKeyguardDisabledFeatures(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordQuality(any(), anyInt());
            doAnswer(answer).when(spy).setMaximumTimeToLock(any(), anyInt());
            doAnswer(answer).when(spy).setPermittedAccessibilityServices(any(), any());
            doAnswer(answer).when(spy).setPermittedInputMethods(any(), any());
            doAnswer(answer).when(spy).setDeviceOwnerLockScreenInfo(any(), any());
            doAnswer(answer).when(spy).setKeyguardDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setAutoTimeRequired(any(), anyBoolean());
            doAnswer(answer).when(spy).setStatusBarDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setOrganizationName(any(), any());
            doAnswer(answer).when(spy).setSecurityLoggingEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setPermissionGrantState(any(), any(), any(), anyInt());
            doAnswer(answer).when(spy).clearPackagePersistentPreferredActivities(any(), any());
            doAnswer(answer).when(spy).setAlwaysOnVpnPackage(any(), any(), anyBoolean());
            doAnswer(answer).when(spy).setRecommendedGlobalProxy(any(), any());
            doAnswer(answer).when(spy).uninstallCaCert(any(), any());
            doAnswer(answer).when(spy).setMaximumFailedPasswordsForWipe(any(), anyInt());
            doAnswer(answer).when(spy).setSecureSetting(any(), any(), any());
            doAnswer(answer).when(spy).setAffiliationIds(any(), any());
            doAnswer(answer).when(spy).setStartUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).setEndUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).setLogoutEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).removeUser(any(), any());

            // Used by DevicePolicySafetyCheckerIntegrationTest
            doAnswer(answer).when(spy).createAndManageUser(any(), any(), any(), any(), anyInt());
            doAnswer(answer).when(spy).lockNow();
            doAnswer(answer).when(spy).lockNow(anyInt());
            doAnswer(answer).when(spy).logoutUser(any());
            doAnswer(answer).when(spy).reboot(any());
            doAnswer(answer).when(spy).removeActiveAdmin(any());
            doAnswer(answer).when(spy).removeKeyPair(any(), any());
            doAnswer(answer).when(spy).requestBugreport(any());
            doAnswer(answer).when(spy).setAlwaysOnVpnPackage(any(), any(), anyBoolean(), any());
            doAnswer(answer).when(spy).setApplicationHidden(any(), any(), anyBoolean());
            doAnswer(answer).when(spy).setApplicationRestrictions(any(), any(), any());
            doAnswer(answer).when(spy).setCameraDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setFactoryResetProtectionPolicy(any(), any());
            doAnswer(answer).when(spy).setGlobalPrivateDnsModeOpportunistic(any());
            doAnswer(answer).when(spy).setKeepUninstalledPackages(any(), any());
            doAnswer(answer).when(spy).setLockTaskFeatures(any(), anyInt());
            doAnswer(answer).when(spy).setLockTaskPackages(any(), any());
            doAnswer(answer).when(spy).setMasterVolumeMuted(any(), anyBoolean());
            doAnswer(answer).when(spy).setOverrideApnsEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setPermissionPolicy(any(), anyInt());
            doAnswer(answer).when(spy).setRestrictionsProvider(any(), any());
            doAnswer(answer).when(spy).setSystemUpdatePolicy(any(), any());
            doAnswer(answer).when(spy).setTrustAgentConfiguration(any(), any(), any());
            doAnswer(answer).when(spy).startUserInBackground(any(), any());
            doAnswer(answer).when(spy).stopUser(any(), any());
            doAnswer(answer).when(spy).switchUser(any(), any());
            doAnswer(answer).when(spy).wipeData(anyInt(), any());
            doAnswer(answer).when(spy).wipeData(anyInt());

            // Used by ListForegroundAffiliatedUsersTest
            doAnswer(answer).when(spy).listForegroundAffiliatedUsers();

            // Used by UserSessionTest
            doAnswer(answer).when(spy).getStartUserSessionMessage(any());
            doAnswer(answer).when(spy).setStartUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).getEndUserSessionMessage(any());
            doAnswer(answer).when(spy).setEndUserSessionMessage(any(), any());

            // TODO(b/176993670): add more methods below as tests are converted
        } catch (Exception e) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e);
        }

        sSpies.put(context, spy);
        Log.d(TAG, "get(): returning new spy for context " + context + " and user "
                + userId);

        return spy;
    }
}
