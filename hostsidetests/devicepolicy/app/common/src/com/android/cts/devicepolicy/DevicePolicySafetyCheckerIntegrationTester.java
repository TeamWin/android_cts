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
import static android.app.admin.DevicePolicyManager.operationToString;

import static org.junit.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.app.admin.UnsafeStateException;
import android.util.Log;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Helper class to test that DPM calls fail when determined by the
 * {@link android.app.admin.DevicePolicySafetyChecker}; it provides the base infra, so it can be
 * used by both device and profile owner tests.
 */
public class DevicePolicySafetyCheckerIntegrationTester {

    public static final String TAG = DevicePolicySafetyCheckerIntegrationTester.class
            .getSimpleName();

    private static final int[] OPERATIONS = new int[] {
            OPERATION_LOCK_NOW
    };

    private static final int[] OVERLOADED_OPERATIONS = new int[] {
            OPERATION_LOCK_NOW
    };

    /**
     * Tests that all safety-aware operations are properly implemented.
     */
    public final void testAllOperations(DevicePolicyManager dpm) {
        Objects.requireNonNull(dpm);

        List<String> failures = new ArrayList<>();
        for (int operation : OPERATIONS) {
            safeOperationTest(dpm, failures, operation, /* overloaded= */ false);
        }

        for (int operation : OVERLOADED_OPERATIONS) {
            safeOperationTest(dpm, failures, operation, /* overloaded= */ true);
        }

        for (int operation : getSafetyAwareOperations()) {
            safeOperationTest(dpm, failures, operation, /* overloaded= */ false);
        }

        for (int operation : getOverloadedSafetyAwareOperations()) {
            safeOperationTest(dpm, failures, operation, /* overloaded= */ true);
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
    protected void runOperation(DevicePolicyManager dpm, int operation,
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

    private void safeOperationTest(DevicePolicyManager dpm, List<String> failures, int operation,
            boolean overloaded) {
        String name = getOperationName(operation, overloaded);
        try {
            setOperationUnsafe(dpm, operation);
            runCommonOrSpecificOperation(dpm, operation, overloaded);
            Log.e(TAG, name + " didn't throw an UnsafeStateException");
            failures.add(name);
        } catch (UnsafeStateException e) {
            Log.d(TAG, name + " failed as expected: " + e);
        } catch (Exception e) {
            Log.e(TAG, name + " threw unexpected exception", e);
            failures.add(name + "(" + e + ")");
        }
    }

    private String getOperationName(int operation, boolean overloaded) {
        String name = operationToString(operation);
        return overloaded ? name + "(OVERLOADED)" : name;
    }

    private void runCommonOrSpecificOperation(DevicePolicyManager dpm, int operation,
            boolean overloaded) {
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
            default:
                runOperation(dpm, operation, overloaded);
        }
    }

    private void setOperationUnsafe(DevicePolicyManager dpm, int operation) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(dpm,
                (obj) -> obj.setNextOperationSafety(operation, false));
    }
}
