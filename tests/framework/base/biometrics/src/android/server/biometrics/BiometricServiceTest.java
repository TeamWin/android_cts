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

package android.server.biometrics;

import static android.os.PowerManager.FULL_WAKE_LOCK;
import static android.server.biometrics.Components.CLASS_2_BIOMETRIC_ACTIVITY;
import static android.server.biometrics.Components.CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY;
import static android.server.biometrics.SensorStates.SensorState;
import static android.server.biometrics.SensorStates.UserState;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_IDLE;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_PAUSED;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_PENDING_CONFIRM;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_STARTED;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_SHOWING_DEVICE_CREDENTIAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.os.Bundle;
import android.os.PowerManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.TestJournalProvider.TestJournal;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.UiDeviceUtils;
import android.server.wm.WindowManagerState;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.biometrics.nano.BiometricServiceStateProto;
import com.android.server.biometrics.nano.BiometricsProto;
import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Presubmit
public class BiometricServiceTest extends BiometricTestBase {

    private static final String TAG = "BiometricServiceTest";
    private static final String DUMPSYS_BIOMETRIC = "dumpsys biometric --proto";
    private static final String FLAG_CLEAR_SCHEDULER_LOG = " --clear-scheduler-buffer";

    // Negative-side (left) buttons
    private static final String BUTTON_ID_NEGATIVE = "button_negative";
    private static final String BUTTON_ID_CANCEL = "button_cancel";
    private static final String BUTTON_ID_USE_CREDENTIAL = "button_use_credential";

    // Positive-side (right) buttons
    private static final String BUTTON_ID_CONFIRM = "button_confirm";
    private static final String BUTTON_ID_TRY_AGAIN = "button_try_again";

    private static final String VIEW_ID_PASSWORD_FIELD = "lockPassword";

    @NonNull private Instrumentation mInstrumentation;
    @NonNull private BiometricManager mBiometricManager;
    @NonNull private List<SensorProperties> mSensorProperties;
    @Nullable private PowerManager.WakeLock mWakeLock;
    @NonNull private UiDevice mDevice;

    /**
     * Expose this functionality to our package, since ActivityManagerTestBase's is `protected`.
     * @param componentName
     */
    void launchActivity(@NonNull ComponentName componentName) {
        super.launchActivity(componentName);
    }

    /**
     * Retrieves the current states of all biometric sensor services (e.g. FingerprintService,
     * FaceService, etc).
     *
     * Note that the states are retrieved from BiometricService, instead of individual services.
     * This is because 1) BiometricService is the source of truth for all public API-facing things,
     * and 2) This to include other information, such as UI states, etc as well.
     */
    @NonNull
    private BiometricServiceState getCurrentState() throws Exception {
        final byte[] dump = Utils.executeShellCommand(DUMPSYS_BIOMETRIC);
        final BiometricServiceStateProto proto = BiometricServiceStateProto.parseFrom(dump);
        return BiometricServiceState.parseFrom(proto);
    }

    @NonNull
    private BiometricServiceState getCurrentStateAndClearSchedulerLog() throws Exception {
        final byte[] dump = Utils.executeShellCommand(DUMPSYS_BIOMETRIC
                + FLAG_CLEAR_SCHEDULER_LOG);
        final BiometricServiceStateProto proto = BiometricServiceStateProto.parseFrom(dump);
        return BiometricServiceState.parseFrom(proto);
    }

    @Nullable
    private UiObject2 findView(String id) {
        Log.d(TAG, "Finding view: " + id);
        return mDevice.findObject(By.res(mBiometricManager.getUiPackage(), id));
    }

    private void findAndPressButton(String id) {
        final UiObject2 button = findView(id);
        assertNotNull(button);
        Log.d(TAG, "Clicking button: " + id);
        button.click();
    }

    private void waitForState(@BiometricServiceState.AuthSessionState int state) throws Exception {
        for (int i = 0; i < 20; i++) {
            final BiometricServiceState serviceState = getCurrentState();
            if (serviceState.mState != state) {
                Log.d(TAG, "Not in state " + state + " yet, current: " + serviceState.mState);
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting for state to become: " + state);
    }

    private void waitForStateNotEqual(@BiometricServiceState.AuthSessionState int state)
            throws Exception {
        for (int i = 0; i < 20; i++) {
            final BiometricServiceState serviceState = getCurrentState();
            if (serviceState.mState == state) {
                Log.d(TAG, "Not out of state yet, current: " + serviceState.mState);
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting for state to not equal: " + state);
    }

    private boolean anyEnrollmentsExist() throws Exception {
        final BiometricServiceState serviceState = getCurrentState();

        for (SensorState sensorState : serviceState.mSensorStates.sensorStates.values()) {
            for (UserState userState : sensorState.getUserStates().values()) {
                if (userState.numEnrolled != 0) {
                    Log.d(TAG, "Enrollments still exist: " + serviceState);
                    return true;
                }
            }
        }
        return false;
    }

    private void waitForAllUnenrolled() throws Exception {
        for (int i = 0; i < 20; i++) {
            if (anyEnrollmentsExist()) {
                Log.d(TAG, "Enrollments still exist..");
                Thread.sleep(300);
            } else {
                return;
            }
        }
        fail("Some sensors still have enrollments. State: " + getCurrentState());
    }

    @NonNull
    private static BiometricCallbackHelper.State getCallbackState(@NonNull TestJournal journal) {
        waitFor("Waiting for authentication callback",
                () -> journal.extras.containsKey(BiometricCallbackHelper.KEY));

        final Bundle bundle = journal.extras.getBundle(BiometricCallbackHelper.KEY);
        if (bundle == null) {
            return new BiometricCallbackHelper.State();
        }

        final BiometricCallbackHelper.State state =
                BiometricCallbackHelper.State.fromBundle(bundle);

        // Clear the extras since we want to wait for the journal to sync any new info the next
        // time it's read
        journal.extras.clear();

        return state;
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = getInstrumentation();
        mBiometricManager = mInstrumentation.getContext().getSystemService(BiometricManager.class);

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mSensorProperties = mBiometricManager.getSensorProperties();

        assumeTrue(mInstrumentation.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_SECURE_LOCK_SCREEN));

        // Keep the screen on for the duration of each test, since BiometricPrompt goes away
        // when screen turns off.
        final PowerManager pm = mInstrumentation.getContext().getSystemService(PowerManager.class);
        mWakeLock = pm.newWakeLock(FULL_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        // Turn screen on and dismiss keyguard
        UiDeviceUtils.pressWakeupButton();
        UiDeviceUtils.pressUnlockButton();
    }

    @After
    public void cleanup() {
        mInstrumentation.waitForIdleSync();

        try {
            waitForIdleService();
        } catch (Exception e) {
            Log.e(TAG, "Exception when waiting for idle", e);
        }

        try {
            final BiometricServiceState state = getCurrentState();

            for (Map.Entry<Integer, SensorState> sensorEntry
                    : state.mSensorStates.sensorStates.entrySet()) {
                for (Map.Entry<Integer, UserState> userEntry
                        : sensorEntry.getValue().getUserStates().entrySet()) {
                    if (userEntry.getValue().numEnrolled != 0) {
                        Log.w(TAG, "Cleaning up for sensor: " + sensorEntry.getKey()
                                + ", user: " + userEntry.getKey());
                        BiometricTestSession session = mBiometricManager.createTestSession(
                                sensorEntry.getKey());
                        session.cleanupInternalState(userEntry.getKey());
                        session.close();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to get current state in cleanup()");
        }

        // Authentication lifecycle is done
        try {
            waitForIdleService();
        } catch (Exception e) {
            Log.e(TAG, "Exception when waiting for idle", e);
        }

        if (mWakeLock != null) {
            mWakeLock.release();
        }
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testEnroll() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId())){
                enrollForSensor(session, prop.getSensorId());
            }
        }
    }

    @Test
    public void testSensorPropertiesAndDumpsysMatch() throws Exception {
        final BiometricServiceState state = getCurrentState();

        assertEquals(mSensorProperties.size(), state.mSensorStates.sensorStates.size());
        for (SensorProperties prop : mSensorProperties) {
            assertTrue(state.mSensorStates.sensorStates.containsKey(prop.getSensorId()));
        }
    }

    @Test
    public void testPackageManagerAndDumpsysMatch() throws Exception {
        final BiometricServiceState state = getCurrentState();

        final PackageManager pm = mContext.getPackageManager();

        assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                state.mSensorStates.containsModality(SensorStateProto.FINGERPRINT));
        assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FACE),
                state.mSensorStates.containsModality(SensorStateProto.FACE));
        assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_IRIS),
                state.mSensorStates.containsModality(SensorStateProto.IRIS));
    }

    private void enrollForSensor(@NonNull BiometricTestSession session, int sensorId)
            throws Exception {
        Log.d(TAG, "Enrolling for sensor: " + sensorId);
        final int userId = 0;

        session.startEnroll(userId);
        mInstrumentation.waitForIdleSync();
        waitForBusySensor(sensorId);

        session.finishEnroll(userId);
        mInstrumentation.waitForIdleSync();
        waitForIdleService();

        final BiometricServiceState state = getCurrentState();
        assertEquals(1, state.mSensorStates.sensorStates
                .get(sensorId).getUserStates().get(userId).numEnrolled);
    }

    @Test
    public void testBiometricOnly_authenticateFromForegroundActivity() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId());
                ActivitySession activitySession =
                        new ActivitySession(this, CLASS_2_BIOMETRIC_ACTIVITY)) {
                testBiometricOnly_authenticateFromForegroundActivity_forSensor(
                        session, prop.getSensorId(), activitySession);
            }
        }
    }

    private void testBiometricOnly_authenticateFromForegroundActivity_forSensor(
            @NonNull BiometricTestSession session, int sensorId,
            @NonNull ActivitySession activitySession) throws Exception {
        Log.d(TAG, "testBiometricOnly_authenticateFromForegroundActivity_forSensor: " + sensorId);
        final int userId = 0;
        waitForAllUnenrolled();
        enrollForSensor(session, sensorId);
        final TestJournal journal = TestJournalContainer.get(activitySession.getComponentName());

        // Launch test activity
        activitySession.start();
        mWmState.waitForActivityState(activitySession.getComponentName(),
                WindowManagerState.STATE_RESUMED);
        mInstrumentation.waitForIdleSync();

        // The sensor being tested should not be idle
        BiometricServiceState state = getCurrentState();
        assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());

        // Nothing happened yet
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertNotNull(callbackState);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());

        // Auth and check again now
        session.acceptAuthentication(userId);
        mInstrumentation.waitForIdleSync();

        waitForStateNotEqual(STATE_AUTH_STARTED);

        state = getCurrentState();
        Log.d(TAG, "State after acceptAuthentication: " + state);
        if (state.mState == STATE_AUTH_PENDING_CONFIRM) {
            findAndPressButton(BUTTON_ID_CONFIRM);
            mInstrumentation.waitForIdleSync();
            waitForState(STATE_AUTH_IDLE);
        }

        mInstrumentation.waitForIdleSync();
        callbackState = getCallbackState(journal);
        assertNotNull(callbackState);
        assertTrue(callbackState.toString(), callbackState.mErrorsReceived.isEmpty());
        assertTrue(callbackState.toString(), callbackState.mAcquiredReceived.isEmpty());
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
    }

    @Test
    public void testBiometricOnly_rejectThenErrorFromForegroundActivity() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId());
                 ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_ACTIVITY)) {
                testBiometricOnly_rejectThenErrorFromForegroundActivity_forSensor(
                        session, prop.getSensorId(), activitySession);
            }
        }
    }

    private void testBiometricOnly_rejectThenErrorFromForegroundActivity_forSensor(
            @NonNull BiometricTestSession session, int sensorId,
            @NonNull ActivitySession activitySession) throws Exception {
        Log.d(TAG, "testBiometricOnly_rejectThenErrorFromForegroundActivity_forSensor: "
                + sensorId);
        final int userId = 0;
        waitForAllUnenrolled();
        enrollForSensor(session, sensorId);

        final TestJournal journal =
                TestJournalContainer.get(activitySession.getComponentName());

        // Launch test activity
        activitySession.start();
        mWmState.waitForActivityState(activitySession.getComponentName(),
                WindowManagerState.STATE_RESUMED);
        mInstrumentation.waitForIdleSync();
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertNotNull(callbackState);

        BiometricServiceState state = getCurrentState();
        assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());

        // Biometric rejected
        session.rejectAuthentication(userId);
        mInstrumentation.waitForIdleSync();
        callbackState = getCallbackState(journal);
        assertNotNull(callbackState);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());

        state = getCurrentState();
        Log.d(TAG, "State after rejectAuthentication: " + state);
        if (state.mState == STATE_AUTH_PAUSED) {
            findAndPressButton(BUTTON_ID_TRY_AGAIN);
            mInstrumentation.waitForIdleSync();
            waitForState(STATE_AUTH_STARTED);
        }

        // Send an error
        session.notifyError(userId, BiometricPrompt.BIOMETRIC_ERROR_CANCELED);
        mInstrumentation.waitForIdleSync();
        callbackState = getCallbackState(journal);
        assertNotNull(callbackState);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 1, callbackState.mErrorsReceived.size());
        assertEquals(callbackState.toString(), BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
                (int) callbackState.mErrorsReceived.get(0));
    }

    @Test
    public void testBiometricOnly_negativeButtonInvoked() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId());
                 ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_ACTIVITY)) {
                testBiometricOnly_negativeButtonInvoked_forSensor(
                        session, prop.getSensorId(), activitySession);
            }
        }
    }

    private void testBiometricOnly_negativeButtonInvoked_forSensor(
            @NonNull BiometricTestSession session, int sensorId,
            @NonNull ActivitySession activitySession) throws Exception {
        Log.d(TAG, "testBiometricOnly_negativeButtonInvoked_forSensor: " + sensorId);
        waitForAllUnenrolled();
        enrollForSensor(session, sensorId);
        final TestJournal journal = TestJournalContainer.get(activitySession.getComponentName());

        // Launch test activity
        activitySession.start();
        mWmState.waitForActivityState(activitySession.getComponentName(),
                WindowManagerState.STATE_RESUMED);
        mInstrumentation.waitForIdleSync();
        BiometricCallbackHelper.State callbackState = getCallbackState(journal);
        assertNotNull(callbackState);

        BiometricServiceState state = getCurrentState();
        assertFalse(state.toString(), state.mSensorStates.areAllSensorsIdle());
        assertFalse(state.toString(), callbackState.mNegativeButtonPressed);

        // Press the negative button
        findAndPressButton(BUTTON_ID_NEGATIVE);

        callbackState = getCallbackState(journal);
        assertTrue(callbackState.toString(), callbackState.mNegativeButtonPressed);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
    }

    @Test
    public void testBiometricOrCredential_credentialButtonInvoked_biometricEnrolled()
            throws Exception {
        // Test behavior for each sensor when biometrics are enrolled
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();
            for (SensorProperties prop : mSensorProperties) {
                try (BiometricTestSession session =
                             mBiometricManager.createTestSession(prop.getSensorId());
                     ActivitySession activitySession =
                             new ActivitySession(this, CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY)) {
                    testBiometricOrCredential_credentialButtonInvoked_forConfiguration(
                            session, prop.getSensorId(), true /* shouldEnrollBiometric */,
                            activitySession);
                }
            }
        }
    }

    @Test
    public void testBiometricOrCredential_credentialButtonInvoked_biometricNotEnrolled()
            throws Exception {
        // Test behavior for each sensor when biometrics are not enrolled
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();
            for (SensorProperties prop : mSensorProperties) {
                try (BiometricTestSession session =
                             mBiometricManager.createTestSession(prop.getSensorId());
                     ActivitySession activitySession =
                             new ActivitySession(this, CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY)) {
                    testBiometricOrCredential_credentialButtonInvoked_forConfiguration(
                            session, prop.getSensorId(), false /* shouldEnrollBiometric */,
                            activitySession);
                }
            }
        }
    }

    @Test
    public void testBiometricOrCredential_credentialButtonInvoked_noBiometricSensor()
            throws Exception {
        assumeTrue(mSensorProperties.isEmpty());
        try (CredentialSession credentialSession = new CredentialSession()) {
            try (ActivitySession activitySession =
                         new ActivitySession(this, CLASS_2_BIOMETRIC_OR_CREDENTIAL_ACTIVITY)){
                testBiometricOrCredential_credentialButtonInvoked_forConfiguration(null,
                        0 /* sensorId */, false /* shouldEnrollBiometric */, activitySession);
            }
        }
    }

    private void testBiometricOrCredential_credentialButtonInvoked_forConfiguration(
            @Nullable BiometricTestSession session, int sensorId, boolean shouldEnrollBiometric,
            @NonNull ActivitySession activitySession)
            throws Exception {
        Log.d(TAG, "testBiometricOrCredential_credentialButtonInvoked_forConfiguration: "
                + "sensorId=" + sensorId
                + ", shouldEnrollBiometric=" + shouldEnrollBiometric);
        if (shouldEnrollBiometric) {
            assertNotNull(session);
            waitForAllUnenrolled();
            enrollForSensor(session, sensorId);
        }

        final TestJournal journal = TestJournalContainer
                .get(activitySession.getComponentName());

        // Launch test activity
        activitySession.start();
        mWmState.waitForActivityState(activitySession.getComponentName(),
                WindowManagerState.STATE_RESUMED);
        mInstrumentation.waitForIdleSync();
        BiometricCallbackHelper.State callbackState;

        BiometricServiceState state = getCurrentState();
        Log.d(TAG, "State after launching activity: " + state);
        if (shouldEnrollBiometric) {
            waitForState(STATE_AUTH_STARTED);
            assertTrue(state.toString(), state.mSensorStates.sensorStates.get(sensorId).isBusy());
            // Press the credential button
            findAndPressButton(BUTTON_ID_USE_CREDENTIAL);
            callbackState = getCallbackState(journal);
            assertFalse(callbackState.toString(), callbackState.mNegativeButtonPressed);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
            assertEquals(callbackState.toString(), 0, callbackState.mNumAuthAccepted);
            assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
            assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
            waitForState(STATE_SHOWING_DEVICE_CREDENTIAL);
        }

        // All sensors are idle, BiometricService is waiting for device credential
        state = getCurrentState();
        assertTrue(state.toString(), state.mSensorStates.areAllSensorsIdle());
        assertEquals(state.toString(), STATE_SHOWING_DEVICE_CREDENTIAL, state.mState);

        // Wait for any animations to complete. Ideally, this should be reflected in
        // STATE_SHOWING_DEVICE_CREDENTIAL, but SysUI and BiometricService are different processes
        // so we'd need to add some additional plumbing. We can improve this in the future.
        Thread.sleep(1000);

        // Enter credential. AuthSession done, authentication callback received
        final UiObject2 passwordField = findView(VIEW_ID_PASSWORD_FIELD);
        Log.d(TAG, "Focusing, entering, submitting credential");
        passwordField.click();
        passwordField.setText(LOCK_CREDENTIAL);
        mDevice.pressEnter();
        waitForState(STATE_AUTH_IDLE);

        state = getCurrentState();
        assertEquals(state.toString(), STATE_AUTH_IDLE, state.mState);
        callbackState = getCallbackState(journal);
        assertEquals(callbackState.toString(), 0, callbackState.mNumAuthRejected);
        assertEquals(callbackState.toString(), 1, callbackState.mNumAuthAccepted);
        assertEquals(callbackState.toString(), 0, callbackState.mAcquiredReceived.size());
        assertEquals(callbackState.toString(), 0, callbackState.mErrorsReceived.size());
    }

    @Test
    public void testAuthenticatorIdsInvalidated() throws Exception {
        // On devices with multiple strong sensors, adding enrollments to one strong sensor
        // must cause authenticatorIds for all other strong sensors to be invalidated, if they
        // (the other strong sensors) have enrollments.
        final List<Integer> strongSensors = new ArrayList<>();
        for (SensorProperties prop : mSensorProperties) {
            if (prop.getSensorStrength() == SensorProperties.STRENGTH_STRONG) {
                strongSensors.add(prop.getSensorId());
            }
        }
        assumeTrue("numStrongSensors: " + strongSensors.size(), strongSensors.size() >= 2);

        Log.d(TAG, "testAuthenticatorIdsInvalidated, numStrongSensors: " + strongSensors.size());

        for (Integer sensorId : strongSensors) {
            testAuthenticatorIdsInvalidated_forSensor(sensorId, strongSensors);
        }
    }

    /**
     * Tests that the specified sensorId's authenticatorId when any other strong sensor adds
     * an enrollment.
     */
    private void testAuthenticatorIdsInvalidated_forSensor(int sensorId,
            @NonNull List<Integer> strongSensors) throws Exception {
        Log.d(TAG, "testAuthenticatorIdsInvalidated_forSensor: " + sensorId);
        final List<BiometricTestSession> biometricSessions = new ArrayList<>();

        final BiometricTestSession targetSensorTestSession =
                mBiometricManager.createTestSession(sensorId);

        // Get the state once. This intentionally clears the scheduler's recent operations dump.
        BiometricServiceState state = getCurrentStateAndClearSchedulerLog();

        waitForAllUnenrolled();
        Log.d(TAG, "Enrolling for: " + sensorId);
        enrollForSensor(targetSensorTestSession, sensorId);
        biometricSessions.add(targetSensorTestSession);
        state = getCurrentStateAndClearSchedulerLog();

        // Target sensorId has never been requested to invalidate authenticatorId yet.
        assertEquals(0, Utils.numberOfSpecifiedOperations(state, sensorId,
                BiometricsProto.CM_INVALIDATE));

        // Add enrollments for all other sensors. Upon each enrollment, the authenticatorId for
        // the above sensor should be invalidated.
        for (Integer id : strongSensors) {
            if (id != sensorId) {
                final BiometricTestSession session = mBiometricManager.createTestSession(id);
                biometricSessions.add(session);
                Log.d(TAG, "Sensor " + id + " should request invalidation");
                enrollForSensor(session, id);
                state = getCurrentStateAndClearSchedulerLog();
                assertEquals(1, Utils.numberOfSpecifiedOperations(state, sensorId,
                        BiometricsProto.CM_INVALIDATE));

                // In addition, the sensor that should have enrolled should have been the one that
                // requested invalidation.
                assertEquals(1, Utils.numberOfSpecifiedOperations(state, id,
                        BiometricsProto.CM_INVALIDATION_REQUESTER));
            }
        }

        // Cleanup
        for (BiometricTestSession session : biometricSessions) {
            session.close();
        }
    }

    @Override
    protected SensorStates getSensorStates() throws Exception {
        return getCurrentState().mSensorStates;
    }
}
