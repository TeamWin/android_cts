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
package com.android.cts.deviceowner;

import static android.app.admin.DevicePolicyManager.OPERATION_CREATE_AND_MANAGE_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_REMOVE_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_START_USER_IN_BACKGROUND;
import static android.app.admin.DevicePolicyManager.OPERATION_STOP_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_SWITCH_USER;

import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.DevicePolicyOperation;
import android.os.UserHandle;

import com.android.cts.devicepolicy.DevicePolicySafetyCheckerIntegrationTester;

//TODO(b/174859111): move to automotive-only section
/**
 * Tests that DPM calls fail when determined by the
 * {@link android.app.admin.DevicePolicySafetyChecker}.
 */
public final class DevicePolicySafetyCheckerIntegrationTest extends BaseDeviceOwnerTest {

    private static final int NO_FLAGS = 0;
    private static final UserHandle USER_HANDLE = UserHandle.of(42);

    private final DevicePolicySafetyCheckerIntegrationTester mTester =
            new DevicePolicySafetyCheckerIntegrationTester() {

        @Override
        protected @DevicePolicyOperation int[] getSafetyAwareOperations() {
            return new int [] {
                    OPERATION_CREATE_AND_MANAGE_USER,
                    OPERATION_REMOVE_USER,
                    OPERATION_START_USER_IN_BACKGROUND,
                    OPERATION_STOP_USER,
                    OPERATION_SWITCH_USER};
        }

        @Override
        protected void runOperation(DevicePolicyManager dpm,
                @DevicePolicyOperation int operation, boolean overloaded) {
            switch (operation) {
                case OPERATION_CREATE_AND_MANAGE_USER:
                    dpm.createAndManageUser(/* admin= */ getWho(), /* name= */ null,
                            /* profileOwner= */ getWho(), /* adminExtras= */ null, NO_FLAGS);
                    break;
                case OPERATION_REMOVE_USER:
                    dpm.removeUser(getWho(), USER_HANDLE);
                    break;
                case OPERATION_START_USER_IN_BACKGROUND:
                    dpm.startUserInBackground(getWho(), USER_HANDLE);
                    break;
                case OPERATION_STOP_USER:
                    dpm.stopUser(getWho(), USER_HANDLE);
                    break;
                case OPERATION_SWITCH_USER:
                    dpm.switchUser(getWho(), USER_HANDLE);
                    break;
                default:
                    throwUnsupportedOperationException(operation, overloaded);
            }
        }
    };

    /**
     * Tests that all safety-aware operations are properly implemented.
     */
    public void testAllOperations() {
        mTester.testAllOperations(mDevicePolicyManager);
    }
}
