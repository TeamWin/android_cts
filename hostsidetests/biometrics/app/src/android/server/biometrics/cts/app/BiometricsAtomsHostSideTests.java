/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.biometrics.cts.app;

import static android.Manifest.permission.TEST_BIOMETRIC;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FACE_ENROLL_ACQUIRED_MESSAGES;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_ENROLL_ACQUIRED_MESSAGES;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.server.biometrics.SensorStates;
import android.server.biometrics.Utils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class BiometricsAtomsHostSideTests {

    private static final String TAG = "BiometricsAtomsHostSideTests";

    private Instrumentation mInstrumentation;
    private int mUserId;
    private BiometricManager mBiometricManager;
    private List<SensorProperties> mSensorProperties;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(TEST_BIOMETRIC);

        mUserId = mInstrumentation.getContext().getUserId();
        mBiometricManager = mInstrumentation.getContext().getSystemService(BiometricManager.class);
        // ignore the legacy HIDL interface for all tests
        mSensorProperties = filterSensorProperties(mBiometricManager.getSensorProperties());

        assumeTrue(!mSensorProperties.isEmpty());
    }

    private static List<SensorProperties> filterSensorProperties(
            @NonNull List<SensorProperties> properties) {
        final int aidlFpSensorId = Utils.getAidlFingerprintSensorId();
        final int aidlFaceSensorId = Utils.getAidlFaceSensorId();

        return properties.stream().filter(p -> {
            final int id = p.getSensorId();
            try {
                if (isFingerprint(id) && aidlFpSensorId != -1) {
                    return id == aidlFpSensorId;
                } else if (isFace(id) && aidlFaceSensorId != -1) {
                    return id == aidlFaceSensorId;
                }
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to check modality", t);
            }
            return true;
        }).collect(Collectors.toList());
    }

    @After
    public void teardown() {
        mInstrumentation.waitForIdleSync();
        try {
            Utils.waitForIdleService();
        } catch (Throwable t) {
            Log.e(TAG, "Unable to await sensor idle", t);
        }

        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testEnroll() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            final int sensorId = prop.getSensorId();
            try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId)) {
                session.startEnroll(mUserId);
                Utils.waitForBusySensor(sensorId);

                for (int code : getAcquiredCodesForEnroll(sensorId)) {
                    session.notifyAcquired(mUserId, code);
                    mInstrumentation.waitForIdleSync();
                }

                session.finishEnroll(mUserId);
                Utils.waitForIdleService();
            }
        }
        mInstrumentation.waitForIdleSync();
    }

    private static List<Integer> getAcquiredCodesForEnroll(int sensorId) throws Exception {
        if (isFace(sensorId)) {
            return FACE_ENROLL_ACQUIRED_MESSAGES;
        } else if (isFingerprint(sensorId)) {
            return FINGERPRINT_ENROLL_ACQUIRED_MESSAGES;
        }
        return List.of();
    }

    private static boolean isFace(int sensorId) throws Exception {
        return isSensorModality(sensorId, SensorStateProto.FACE);
    }

    private static boolean isFingerprint(int sensorId) throws Exception {
        return isSensorModality(sensorId, SensorStateProto.FINGERPRINT);
    }

    private static boolean isSensorModality(int sensorId, int modality) throws Exception {
        final Map<Integer, SensorStates.SensorState> states =
                Utils.getBiometricServiceCurrentState().mSensorStates.sensorStates;
        if (states.containsKey(sensorId)) {
            return states.get(sensorId).getModality() == modality;
        }
        return false;
    }
}
