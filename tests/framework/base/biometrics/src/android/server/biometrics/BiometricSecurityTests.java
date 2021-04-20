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

package android.server.biometrics;

import static android.server.biometrics.Components.CLASS_2_BIOMETRIC_ACTIVITY;
import static android.server.biometrics.Components.CLASS_3_BIOMETRIC_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.platform.test.annotations.Presubmit;
import android.server.wm.TestJournalProvider.TestJournal;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.util.Log;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.List;

/**
 * Tests for security related functionality such as downgrading and upgrading biometric strength.
 */
@Presubmit
public class BiometricSecurityTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Security";

    /**
     * The strength of a Strong biometric may need to be downgraded to a weaker one if the biometric
     * requires a security update. After downgrading, the biometric may or may not be able to
     * perform auth with the requested strength. For example,
     * +-------------------+-----------------------+--------------------+----------+
     * | Original Strength | Target Strength       | Requested Strength | Result   |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_WEAK        | BIOMETRIC_STRONG   | Error    |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_WEAK        | BIOMETRIC_WEAK     | Accepted |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_CONVENIENCE | BIOMETRIC_STRONG   | Error    |
     * +-------------------+-----------------------+--------------------+----------+
     * | BIOMETRIC_STRONG  | BIOMETRIC_CONVENIENCE | BIOMETRIC_WEAK     | Error    |
     * +-------------------+-----------------------+--------------------+----------+
     * Note that since BiometricPrompt does not support Convenience biometrics, currently we don't
     * have a way to test cases where the requested strength is BIOMETRIC_CONVENIENCE.
     */
    @Test
    public void testBiometricStrengthDowngraded_StrongSensor() throws Exception {
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_STRONG);
        assumeTrue("testBiometricStrengthDowngraded_StrongSensor: numSensors=" + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Downgrade Strong to Weak, and request Strong auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_WEAK,
                        Authenticators.BIOMETRIC_STRONG},

                // Downgrade Strong to Weak, and request Weak auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_WEAK,
                        Authenticators.BIOMETRIC_WEAK},

                // Downgrade Strong to Convenience, and request Strong auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_CONVENIENCE,
                        Authenticators.BIOMETRIC_STRONG},

                // Downgrade Strong to Convenience, and request Weak auth
                {Authenticators.BIOMETRIC_STRONG, Authenticators.BIOMETRIC_CONVENIENCE,
                        Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthDowngraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        sensors.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    /**
     * The strength of a Weak biometric may need to be downgraded to a weaker one if the biometric
     * requires a security update. After downgrading, the biometric may or may not be able to
     * perform auth with the requested strength. For example,
     * +-------------------+-----------------------+--------------------+--------+
     * | Original Strength | Target Strength       | Requested Strength | Result |
     * +-------------------+-----------------------+--------------------+--------+
     * | BIOMETRIC_WEAK    | BIOMETRIC_CONVENIENCE | BIOMETRIC_WEAK     | Error  |
     * +-------------------+-----------------------+--------------------+--------+
     * Note that since BiometricPrompt does not support Convenience biometrics, currently we don't
     * have a way to test cases where the requested strength is BIOMETRIC_CONVENIENCE.
     */
    @Test
    public void testBiometricStrengthDowngraded_WeakSensor() throws Exception {
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_WEAK);
        assumeTrue("testBiometricStrengthDowngraded_WeakSensor: numSensors: " + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Downgrade Weak to Convenience, and request Weak auth
                {Authenticators.BIOMETRIC_WEAK, Authenticators.BIOMETRIC_CONVENIENCE,
                        Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthDowngraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        sensors.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    private void testBiometricStrengthDowngraded_forSensor(int sensorId, int originalStrength,
            int targetStrength, int requestedStrength, boolean hasMultiSensors) throws Exception {
        assertTrue("requestedStrength: " + requestedStrength,
                requestedStrength == Authenticators.BIOMETRIC_STRONG ||
                        requestedStrength == Authenticators.BIOMETRIC_WEAK);

        final ComponentName componentName;
        if (requestedStrength == Authenticators.BIOMETRIC_STRONG) {
            componentName = CLASS_3_BIOMETRIC_ACTIVITY;
        } else {
            componentName = CLASS_2_BIOMETRIC_ACTIVITY;
        }

        // Reset to the original strength in case it's ever changed before the test
        updateStrength(sensorId, originalStrength);

        // Test downgrading the biometric strength to the target strength
        testBiometricStrengthDowngraded_forSensor_afterDowngrading(componentName, sensorId,
                originalStrength, targetStrength, requestedStrength, hasMultiSensors);

        // Test undo downgrading (ie, reset to the original strength)
        testBiometricStrengthDowngraded_forSensor_afterUndoDowngrading(componentName, sensorId,
                originalStrength, targetStrength, requestedStrength);
    }

    private void testBiometricStrengthDowngraded_forSensor_afterDowngrading(
            @NonNull ComponentName componentName, int sensorId, int originalStrength,
            int targetStrength, int requestedStrength, boolean hasMultiSensors) throws Exception {
        Log.d(TAG, "testBiometricStrengthDowngraded_forSensor_afterDowngrading: "
                + "componentName=" + componentName
                + ", sensorId=" + sensorId
                + ", originalStrength=" + originalStrength
                + ", targetStrength=" + targetStrength
                + ", requestedStrength=" + requestedStrength
                + ", hasMultiSensors=" + hasMultiSensors);

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
             ActivitySession activitySession = new ActivitySession(this, componentName)) {
            final int userId = 0;
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
            final TestJournal journal =
                    TestJournalContainer.get(activitySession.getComponentName());

            BiometricCallbackHelper.State callbackState;
            BiometricServiceState state;

            // Downgrade the biometric strength to the target strength
            updateStrength(sensorId, targetStrength);

            // After downgrading, check whether auth works
            // TODO: should check if targetStrength is at least as strong as the requestedStrength,
            // but some strength constants that are needed for the calculation are not exposed in
            // BiometricManager.
            if (targetStrength == requestedStrength) {
                Log.d(TAG, "The targetStrength is as strong as the requestedStrength");
                // No error code should be returned since biometric has sufficient strength if
                // request weak auth
                int errCode = mBiometricManager.canAuthenticate(requestedStrength);
                assertEquals("Device should allow auth with the requested biometric",
                        BiometricManager.BIOMETRIC_SUCCESS, errCode);

                // Launch test activity
                launchActivityAndWaitForResumed(activitySession);

                state = getCurrentState();
                assertTrue(state.toString(),
                        state.mSensorStates.sensorStates.get(sensorId).isBusy());

                // Auth should work
                successfullyAuthenticate(session, userId);
                mInstrumentation.waitForIdleSync();
                callbackState = getCallbackState(journal);
                assertNotNull(callbackState);
                assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
                assertEquals(callbackState.toString(), 1, callbackState.mNumAuthAccepted);
                assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
                assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
            } else {
                Log.d(TAG, "The targetStrength is not strong enough");
                // Error code should be returned
                int errCode = mBiometricManager.canAuthenticate(requestedStrength);
                checkErrCode("Device shouldn't allow auth with biometrics that require security"
                                + " update. errCode: " + errCode,
                        errCode, BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
                        hasMultiSensors);

                // Launch test activity
                launchActivityAndWaitForResumed(activitySession);

                // Auth shouldn't work and error code should be returned
                mInstrumentation.waitForIdleSync();
                callbackState = getCallbackState(journal);
                assertNotNull(callbackState);
                assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
                assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
                assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
                assertEquals(callbackState.toString(), 1, callbackState.mErrorsReceived.size());
                checkErrCode(callbackState.toString(), (int) callbackState.mErrorsReceived.get(0),
                        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, hasMultiSensors);
            }
        }
    }

    private void testBiometricStrengthDowngraded_forSensor_afterUndoDowngrading(
            @NonNull ComponentName componentName, int sensorId, int originalStrength,
            int targetStrength, int requestedStrength) throws Exception {
        Log.d(TAG, "testBiometricStrengthDowngraded_forSensor_afterUndoDowngrading: "
                + "componentName=" + componentName
                + ", sensorId=" + sensorId
                + ", originalStrength=" + originalStrength
                + ", targetStrength=" + targetStrength
                + ", requestedStrength=" + requestedStrength);

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
             ActivitySession activitySession = new ActivitySession(this, componentName)) {
            final int userId = 0;
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
            final TestJournal journal =
                    TestJournalContainer.get(activitySession.getComponentName());

            // Reset to the original strength
            updateStrength(sensorId, originalStrength);

            // No error code should be returned for the requested strength
            int errCode = mBiometricManager.canAuthenticate(requestedStrength);
            assertEquals("Device should allow auth with the requested biometric",
                    BiometricManager.BIOMETRIC_SUCCESS, errCode);

            // Launch test activity
            launchActivityAndWaitForResumed(activitySession);

            BiometricCallbackHelper.State callbackState = getCallbackState(journal);
            assertNotNull(callbackState);

            BiometricServiceState state = getCurrentState();
            assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());

            // Auth should work
            successfullyAuthenticate(session, userId);
            mInstrumentation.waitForIdleSync();
            callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
            assertEquals(callbackState.toString(), 1, callbackState.mNumAuthAccepted);
            assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
            assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
        }
    }

    /**
     * Trying to upgrade the strength of a Weak biometric to a stronger strength will not
     * succeed (ie, it's no-op and the biometric strength is still Weak), since the biometric's
     * actual strength can't go past its original strength. After upgrading, the biometric without
     * sufficient strength should not be able to perform the requested auth. For example,
     * +-------------------+------------------+--------------------+--------+
     * | Original Strength | Target Strength  | Requested Strength | Result |
     * +-------------------+------------------+--------------------+--------+
     * | BIOMETRIC_WEAK    | BIOMETRIC_STRONG | BIOMETRIC_STRONG   | Error  |
     * +-------------------+------------------+--------------------+--------+
     */
    @Test
    public void testBiometricStrengthUpgraded_WeakSensor() throws Exception {
        final List<Integer> sensors = getSensorsOfTargetStrength(SensorProperties.STRENGTH_WEAK);
        assumeTrue("testBiometricStrengthUpgraded_WeakSensor: numSensors: " + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Upgrade Weak to Strong, and request Strong auth
                {Authenticators.BIOMETRIC_WEAK, Authenticators.BIOMETRIC_STRONG,
                        Authenticators.BIOMETRIC_STRONG}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthUpgraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        sensors.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    /**
     * Trying to upgrade the strength of a Convenience biometric to a stronger strength will not
     * succeed (ie, it's no-op and the biometric strength is still Convenience), since the
     * biometric's actual strength can't go past its original strength. After upgrading, the
     * biometric without sufficient strength should not be able to perform the requested auth.
     * For example,
     * +-----------------------+------------------+--------------------+--------+
     * | Original Strength     | Target Strength  | Requested Strength | Result |
     * +-----------------------+------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_STRONG | BIOMETRIC_STRONG   | Error  |
     * +-----------------------+------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_STRONG | BIOMETRIC_WEAK     | Error  |
     * +-----------------------+------------------+--------------------+--------+
     * | BIOMETRIC_CONVENIENCE | BIOMETRIC_WEAK   | BIOMETRIC_WEAK     | Error  |
     * +-----------------------+------------------+--------------------+--------+
     */
    @Test
    public void testBiometricStrengthUpgraded_ConvenienceSensor() throws Exception {
        final List<Integer> sensors =
                getSensorsOfTargetStrength(SensorProperties.STRENGTH_CONVENIENCE);
        assumeTrue("testBiometricStrengthUpgraded_ConvenienceSensor: numSensors=" + sensors.size(),
                sensors.size() > 0);

        // Tuple of originalStrength, targetStrength, and requestedStrength
        final int[][] testCases = {
                // Upgrade Convenience to Strong, and request Strong auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_STRONG,
                        Authenticators.BIOMETRIC_STRONG},

                // Upgrade Convenience to Strong, and request Weak auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_STRONG,
                        Authenticators.BIOMETRIC_WEAK},

                // Upgrade Convenience to Weak, and request Weak auth
                {Authenticators.BIOMETRIC_CONVENIENCE, Authenticators.BIOMETRIC_WEAK,
                        Authenticators.BIOMETRIC_WEAK}
        };

        for (Integer sensorId : sensors) {
            for (int i = 0; i < testCases.length; i++) {
                testBiometricStrengthUpgraded_forSensor(sensorId,
                        testCases[i][0] /* originalStrength */,
                        testCases[i][1] /* targetStrength */,
                        testCases[i][2] /* requestedStrength */,
                        sensors.size() > 1 /* hasMultiSensors */);
            }
        }
    }

    private void testBiometricStrengthUpgraded_forSensor(int sensorId, int originalStrength,
            int targetStrength, int requestedStrength, boolean hasMultiSensors) throws Exception {
        Log.d(TAG, "testBiometricStrengthUpgraded_forSensor: "
                + "sensorId=" + sensorId
                + ", originalStrength=" + originalStrength
                + ", targetStrength=" + targetStrength
                + ", requestedStrength=" + requestedStrength
                + ", hasMultiSensors=" + hasMultiSensors);

        assertTrue("requestedStrength: " + requestedStrength,
                requestedStrength == Authenticators.BIOMETRIC_STRONG ||
                        requestedStrength == Authenticators.BIOMETRIC_WEAK);

        final ComponentName componentName;
        if (requestedStrength == Authenticators.BIOMETRIC_STRONG) {
            componentName = CLASS_3_BIOMETRIC_ACTIVITY;
        } else {
            componentName = CLASS_2_BIOMETRIC_ACTIVITY;
        }

        // Reset to the original strength in case it's ever changed before the test
        updateStrength(sensorId, originalStrength);

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
             ActivitySession activitySession = new ActivitySession(this, componentName)) {
            final int userId = 0;
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
            final TestJournal journal =
                    TestJournalContainer.get(activitySession.getComponentName());

            // Try to upgrade the biometric strength to the target strength. The upgrading operation
            // is no-op since the biometric can't be upgraded past its original strength.
            updateStrength(sensorId, targetStrength);
            final int currentStrength = getCurrentStrength(sensorId);
            assertTrue("currentStrength: " + currentStrength, currentStrength == originalStrength);

            // After upgrading, check whether auth works
            // Error code should be returned
            int errCode = mBiometricManager.canAuthenticate(requestedStrength);
            checkErrCode("Device shouldn't allow auth with biometrics without sufficient strength."
                            + " errCode: " + errCode,
                    errCode, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, hasMultiSensors);

            // Launch test activity
            launchActivityAndWaitForResumed(activitySession);

            // Auth shouldn't work and error code should be returned
            mInstrumentation.waitForIdleSync();
            BiometricCallbackHelper.State callbackState = getCallbackState(journal);
            assertNotNull(callbackState);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
            assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
            assertEquals(callbackState.toString(), 1, callbackState.mErrorsReceived.size());
            checkErrCode(callbackState.toString(), (int) callbackState.mErrorsReceived.get(0),
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, hasMultiSensors);
        }

        // Cleanup: reset to the original strength
        updateStrength(sensorId, originalStrength);
    }

    private void checkErrCode(String msg, int errCode, int expectedErrCode,
            boolean hasMultiSensors) {
        if (!hasMultiSensors) {
            assertTrue(msg, errCode == expectedErrCode);
        } else {
            // In the multi-sensor case, error code for the first ineligible sensor may be
            // returned so the following error codes are accepted
            assertTrue(msg, errCode == expectedErrCode
                    || errCode == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                    || errCode == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE);
        }
    }

}
