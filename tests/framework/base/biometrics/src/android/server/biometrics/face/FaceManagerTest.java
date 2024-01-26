/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.biometrics.face;

import static android.hardware.biometrics.Flags.FLAG_FACE_BACKGROUND_AUTHENTICATION;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.hardware.face.FaceManager;
import android.os.CancellationSignal;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.biometrics.SensorStates;
import android.server.biometrics.TestSessionList;
import android.server.biometrics.Utils;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.UiDeviceUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;
import com.android.server.biometrics.nano.SensorServiceStateProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

@Presubmit
public class FaceManagerTest extends ActivityManagerTestBase implements TestSessionList.Idler {

    private static final String TAG = "FaceServiceTest";
    private static final String DUMPSYS_FACE = "dumpsys face --proto --state";
    private static final long WAIT_MS = 2000;

    @Nullable
    private FaceManager mFaceManager;
    @NonNull
    private List<? extends SensorProperties> mSensorProperties;

    private AuthCallBack mAuthCallback = new AuthCallBack();

    @Before
    public void setUp() throws Exception {
        mFaceManager = mInstrumentation.getContext()
                .getSystemService(FaceManager.class);

        // Tests can be skipped on devices without FaceManager
        assertThat(mFaceManager != null).isTrue();

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();

        mSensorProperties = mFaceManager.getSensorProperties();

        // Tests can be skipped on devices without face sensors
        assertThat(!mSensorProperties.isEmpty()).isTrue();

        // Turn screen on and dismiss keyguard
        UiDeviceUtils.pressWakeupButton();
        UiDeviceUtils.pressUnlockButton();
    }

    @After
    public void cleanup() throws Exception {
        if (mFaceManager == null) {
            return;
        }
        mInstrumentation.waitForIdleSync();
        Utils.waitForIdleService(this::getSensorStates);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @ApiTest(apis = {
            "android.hardware.face."
                    + "FaceManager#authenticateInBackground"})
    @RequiresFlagsEnabled(FLAG_FACE_BACKGROUND_AUTHENTICATION)
    @Test
    public void testAuthenticateInBackground() throws Exception {
        final int userId = Utils.getUserId();
        try (TestSessionList testSessions = createTestSessionsWithEnrollments(userId)) {

            mFaceManager.authenticateInBackground(null, null, new CancellationSignal(),
                    mAuthCallback);

            // At least one sensor should be authenticating
            assertThat(getSensorStates().areAllSensorsIdle()).isFalse();

            // Nothing happened yet
            assertThat(mAuthCallback.acquiredReceived).isEqualTo(0);
            assertThat(mAuthCallback.errorsReceived).isEqualTo(0);
            assertThat(mAuthCallback.numAuthRejected).isEqualTo(0);
            assertThat(mAuthCallback.numAuthAccepted).isEqualTo(0);

            testSessions.first().acceptAuthentication(userId);
            Utils.waitFor("Waiting for authentication callback",
                    () -> mAuthCallback.numAuthAccepted == 1);

            assertThat(mAuthCallback.acquiredReceived).isEqualTo(0);
            assertThat(mAuthCallback.errorsReceived).isEqualTo(0);
            assertThat(mAuthCallback.numAuthRejected).isEqualTo(0);
            assertThat(mAuthCallback.numAuthAccepted).isEqualTo(1);
        }
    }

    @ApiTest(apis = {
            "android.hardware.face."
                    + "FaceManager#hasEnrolledTemplates"})
    @RequiresFlagsEnabled(FLAG_FACE_BACKGROUND_AUTHENTICATION)
    @Test
    public void testHasEnrolledTemplates() throws Exception {
        final int userId = Utils.getUserId();
        try (TestSessionList testSessions = createTestSessions(userId)) {
            assertThat(mFaceManager.hasEnrolledTemplates()).isFalse();
            enrollTestSession(userId, testSessions);
            assertThat(mFaceManager.hasEnrolledTemplates()).isTrue();
        }
    }

    @Override
    public void waitForIdleSensors() {
        try {
            Utils.waitForIdleService(this::getSensorStates);
        } catch (Exception e) {
            Log.e(TAG, "Exception when waiting for idle", e);
        }
    }

    private SensorStates getSensorStates() throws Exception {
        final byte[] dump = Utils.executeShellCommand(DUMPSYS_FACE);
        SensorServiceStateProto proto = SensorServiceStateProto.parseFrom(dump);
        return SensorStates.parseFrom(proto);
    }

    private TestSessionList createTestSessions(int userId) {
        final TestSessionList testSessions = new TestSessionList(this);
        for (SensorProperties prop : mSensorProperties) {
            BiometricTestSession session =
                    mFaceManager.createTestSession(prop.getSensorId());
            testSessions.put(prop.getSensorId(), session);
        }
        return testSessions;
    }

    private void enrollTestSession(int userId, TestSessionList testSessions) {
        for (SensorProperties prop : mSensorProperties) {
            BiometricTestSession session = testSessions.find(prop.getSensorId());

            session.startEnroll(userId);
            mInstrumentation.waitForIdleSync();
            waitForIdleSensors();

            session.finishEnroll(userId);
            mInstrumentation.waitForIdleSync();
            waitForIdleSensors();
        }
    }

    private TestSessionList createTestSessionsWithEnrollments(int userId) {
        final TestSessionList testSessions = createTestSessions(userId);
        enrollTestSession(userId, testSessions);
        return testSessions;
    }

    private static class AuthCallBack extends BiometricPrompt.AuthenticationCallback {
        public int acquiredReceived = 0;
        public int numAuthAccepted = 0;
        public int errorsReceived = 0;
        public int numAuthRejected = 0;

        @Override
        public void onAuthenticationAcquired(int acquireInfo) {
            acquiredReceived++;
        }

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            errorsReceived++;
        }

        @Override
        public void onAuthenticationFailed() {
            numAuthRejected++;
        }

        @Override
        public void onAuthenticationSucceeded(
                BiometricPrompt.AuthenticationResult result) {
            numAuthAccepted++;
        }
    }
}
