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
package com.android.cts.devicepolicy;

import static android.app.admin.DevicePolicyManager.OPERATION_LOCK_NOW;
import static android.app.admin.DevicePolicyManager.OPERATION_LOGOUT_USER;
import static android.app.admin.DevicePolicyManager.OPERATION_REMOVE_ACTIVE_ADMIN;
import static android.app.admin.DevicePolicyManager.OPERATION_REMOVE_KEY_PAIR;
import static android.app.admin.DevicePolicyManager.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_ALWAYS_ON_VPN_PACKAGE;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_MASTER_VOLUME_MUTED;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_PERMISSION_GRANT_STATE;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_PERMISSION_POLICY;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_RESTRICTIONS_PROVIDER;
import static android.app.admin.DevicePolicyManager.OPERATION_SET_USER_RESTRICTION;
import static android.app.admin.DevicePolicyManager.operationSafetyReasonToString;
import static android.app.admin.DevicePolicyManager.operationToString;

import static org.junit.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.app.admin.UnsafeStateException;
import android.content.ComponentName;
import android.os.UserManager;
import android.util.Log;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Helper class to test that DPM calls fail when determined by the
 * {@link android.app.admin.DevicePolicySafetyChecker}; it provides the base infra, so it can be
 * used by both device and profile owner tests.
 */
public class DevicePolicySafetyCheckerIntegrationTester {

    public static final String TAG = DevicePolicySafetyCheckerIntegrationTester.class
            .getSimpleName();

    private static final int[] OPERATIONS = new int[] {
            OPERATION_LOCK_NOW,
            OPERATION_LOGOUT_USER,
            OPERATION_REMOVE_ACTIVE_ADMIN,
            OPERATION_REMOVE_KEY_PAIR,
            OPERATION_SET_MASTER_VOLUME_MUTED,
            OPERATION_SET_USER_RESTRICTION,
            OPERATION_SET_PERMISSION_GRANT_STATE,
            OPERATION_SET_PERMISSION_POLICY,
            OPERATION_SET_RESTRICTIONS_PROVIDER
    };

    private static final int[] OVERLOADED_OPERATIONS = new int[] {
            OPERATION_LOCK_NOW,
            OPERATION_SET_ALWAYS_ON_VPN_PACKAGE
    };

    /**
     * Tests that all safety-aware operations are properly implemented.
     */
    public final void testAllOperations(DevicePolicyManager dpm, ComponentName admin) {
        Objects.requireNonNull(dpm);

        List<String> failures = new ArrayList<>();
        for (int operation : OPERATIONS) {
            safeOperationTest(dpm, admin, failures, operation, /* overloaded= */ false);
        }

        for (int operation : OVERLOADED_OPERATIONS) {
            safeOperationTest(dpm, admin, failures, operation, /* overloaded= */ true);
        }

        for (int operation : getSafetyAwareOperations()) {
            safeOperationTest(dpm, admin, failures, operation, /* overloaded= */ false);
        }

        for (int operation : getOverloadedSafetyAwareOperations()) {
            safeOperationTest(dpm, admin, failures, operation, /* overloaded= */ true);
        }

        if (!failures.isEmpty()) {
            fail(failures.size() + " operations failed: " + failures);
        }
    }

    /**
     * Gets the device / profile owner-specific operations.
     *
     * <p>By default it returns an empty array, but sub-classes can override to add its supported
     * operations.
     */
    protected int[] getSafetyAwareOperations() {
        return new int[] {};
    }

    /**
     * Gets the device / profile owner-specific operations that are overloaded.
     *
     * <p>For example, {@code OPERATION_WIPE_DATA} is used for both {@code wipeData(flags)} and
     * {@code wipeData(flags, reason)}, so it should be returned both here and on
     * {@link #getSafetyAwareOperations()}, then
     * {@link #runOperation(DevicePolicyManager, int, boolean)} will handle which method to call for
     * each case.
     *
     * <p>By default it returns an empty array, but sub-classes can override to add its supported
     * operations.
     */
    protected int[] getOverloadedSafetyAwareOperations() {
        return new int[] {};
    }

    /**
     * Runs the device / profile owner-specific operation.
     *
     * <p>MUST be overridden if {@link #getSafetyAwareOperations()} is overridden as well.
     */
    protected void runOperation(DevicePolicyManager dpm, ComponentName admin, int operation,
            boolean overloaded) {
        throwUnsupportedOperationException(operation, overloaded);
    }

    /**
     * Throws a {@link UnsupportedOperationException} then the given {@code operation} is not
     * supported.
     */
    protected final void throwUnsupportedOperationException(int operation, boolean overloaded) {
        throw new UnsupportedOperationException(
                "Unsupported operation " + getOperationName(operation, overloaded));
    }

    private void safeOperationTest(DevicePolicyManager dpm, ComponentName admin,
            List<String> failures, int operation, boolean overloaded) {
        String name = getOperationName(operation, overloaded);
        // Currently there's just one reason...
        int reason = OPERATION_SAFETY_REASON_DRIVING_DISTRACTION;

        if (!dpm.isSafeOperation(reason)) {
            failures.add("Operation " + name + " should be safe");
            return;
        }
        try {
            setOperationUnsafe(dpm, operation, reason);
            if (dpm.isSafeOperation(reason)) {
                failures.add("Operation " + name + " should be unsafe");
                return;
            }
            runCommonOrSpecificOperation(dpm, admin, operation, overloaded);
            Log.e(TAG, name + " didn't throw an UnsafeStateException");
            failures.add(name);
        } catch (UnsafeStateException e) {
            Log.d(TAG, name + " failed as expected: " + e);
            List<Integer> actualReasons = e.getReasons();
            if (actualReasons.size() != 1) {
                failures.add(String.format("received invalid number of reasons (%s); expected just "
                        + "1 (%d - %s)", actualReasons, reason,
                        operationSafetyReasonToString(reason)));

            } else {
                int actualReason = actualReasons.get(0);
                if (actualReason != reason) {
                    failures.add(String.format("received exception with reason %s instead of %s",
                            operationSafetyReasonToString(actualReason),
                            operationSafetyReasonToString(reason)));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, name + " threw unexpected exception", e);
            failures.add(name + "(" + e + ")");
        }
    }

    private String getOperationName(int operation, boolean overloaded) {
        String name = operationToString(operation);
        return overloaded ? name + "(OVERLOADED)" : name;
    }

    private void runCommonOrSpecificOperation(DevicePolicyManager dpm, ComponentName admin,
            int operation, boolean overloaded) throws Exception {
        String name = getOperationName(operation, overloaded);
        Log.v(TAG, "runOperation(): " + name);
        switch (operation) {
            case OPERATION_LOCK_NOW:
                if (overloaded) {
                    dpm.lockNow(/* flags= */ 0);
                } else {
                    dpm.lockNow();
                }
                break;
            case OPERATION_LOGOUT_USER:
                dpm.logoutUser(admin);
                break;
            case OPERATION_SET_ALWAYS_ON_VPN_PACKAGE:
                if (overloaded) {
                    dpm.setAlwaysOnVpnPackage(admin, "vpnPackage", /* lockdownEnabled= */ true);
                } else {
                    dpm.setAlwaysOnVpnPackage(admin, "vpnPackage", /* lockdownEnabled= */ true,
                            /* lockdownAllowlist= */ Set.of("vpnPackage"));
                }
                break;
            case OPERATION_SET_MASTER_VOLUME_MUTED:
                dpm.setMasterVolumeMuted(admin, /* on= */ true);
                break;
            case OPERATION_SET_PERMISSION_GRANT_STATE:
                dpm.setPermissionGrantState(admin, "package", "permission", /* grantState= */ 0);
                break;
            case OPERATION_SET_PERMISSION_POLICY:
                dpm.setPermissionPolicy(admin, /* policy= */ 0);
                break;
            case OPERATION_SET_RESTRICTIONS_PROVIDER:
                dpm.setRestrictionsProvider(admin,
                        /* provider= */ new ComponentName("package", "component"));
                break;
            case OPERATION_SET_USER_RESTRICTION:
                dpm.addUserRestriction(admin, UserManager.DISALLOW_REMOVE_USER);
                break;
            case OPERATION_REMOVE_ACTIVE_ADMIN:
                dpm.removeActiveAdmin(admin);
                break;
            case OPERATION_REMOVE_KEY_PAIR:
                dpm.removeKeyPair(admin, "keyAlias");
                break;
            default:
                runOperation(dpm, admin, operation, overloaded);
        }
    }

    private void setOperationUnsafe(DevicePolicyManager dpm, int operation, int reason) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(dpm,
                (obj) -> obj.setNextOperationSafety(operation, reason));
    }
}
