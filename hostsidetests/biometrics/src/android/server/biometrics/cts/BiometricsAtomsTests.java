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

package android.server.biometrics.cts;

import static android.server.biometrics.cts.FingerprintHostsideConstants.FACE_ENROLL_ACQUIRED_MESSAGES_AIDL;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_ENROLL_ACQUIRED_MESSAGES;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_ENROLL_ACQUIRED_MESSAGES_AIDL;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.hardware.biometrics.ActionEnum;
import android.hardware.biometrics.ModalityEnum;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for biometric atom logging.
 *
 * Run via: atest CtsBiometricsHostTestCases -c
 */
public class BiometricsAtomsTests extends BiometricDeviceTestCase {

    private static final String TEST_PKG = "android.server.biometrics.cts.app";
    private static final String TEST_CLASS = ".BiometricsAtomsHostSideTests";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    public void testEnrollAtom() throws Exception {
        if (!hasBiometrics()) {
            return;
        }

        final List<StatsLog.EventMetricData> data = runEnrollmentTestOnDevice();

        if (hasFeatureFingerprint()) {
            final ModalityEnum modality = ModalityEnum.MODALITY_FINGERPRINT;

            final List<AtomsProto.BiometricEnrolled> enrolledAtoms =
                    filterEnrollmentAtoms(data, modality);
            assertThat(enrolledAtoms).hasSize(1);
            assertEnrollmentAtomData(enrolledAtoms.get(0));

            final List<AtomsProto.BiometricAcquired> acquiredAtoms =
                    filterAcquiredAtoms(data, modality);
            assertEnrollmentAcquiredAtomsData(acquiredAtoms, modality);
        }

        if (hasFeatureFace()) {
            final ModalityEnum modality = ModalityEnum.MODALITY_FACE;

            final List<AtomsProto.BiometricEnrolled> enrolledAtoms =
                    filterEnrollmentAtoms(data, modality);
            assertThat(enrolledAtoms).hasSize(1);
            assertEnrollmentAtomData(enrolledAtoms.get(0));

            final List<AtomsProto.BiometricAcquired> acquiredAtoms =
                    filterAcquiredAtoms(data, modality);
            assertEnrollmentAcquiredAtomsData(acquiredAtoms, modality);
        }
    }

    private List<StatsLog.EventMetricData> runEnrollmentTestOnDevice() throws Exception {
        ConfigUtils.uploadConfigForPushedAtoms(getDevice(), TEST_PKG,
                new int[]{AtomsProto.Atom.BIOMETRIC_ENROLLED_FIELD_NUMBER,
                        AtomsProto.Atom.BIOMETRIC_ACQUIRED_FIELD_NUMBER});
        DeviceUtils.runDeviceTests(
                getDevice(),
                TEST_PKG,
                TEST_PKG + TEST_CLASS,
                "testEnroll");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
        return ReportUtils.getEventMetricDataList(getDevice());
    }

    private void assertEnrollmentAtomData(AtomsProto.BiometricEnrolled atom) throws Exception {
        assertThat(atom.hasSuccess() && atom.getSuccess()).isTrue();
        assertThat(atom.getUser()).isEqualTo(getDevice().getCurrentUser());
        assertThat(atom.hasAmbientLightLux()).isTrue();
    }

    // check enrollment acquired messages match the fixed values in the test
    private void assertEnrollmentAcquiredAtomsData(
            List<AtomsProto.BiometricAcquired> atoms, ModalityEnum modality) throws Exception {
        assertThat(atoms).isNotEmpty();

        for (AtomsProto.BiometricAcquired atom : atoms) {
            assertThat(atom.hasModality() && atom.getModality() == modality).isTrue();
            assertThat(atom.hasAction() && atom.getAction() == ActionEnum.ACTION_ENROLL).isTrue();
            assertThat(atom.hasUser() && atom.getUser() == getDevice().getCurrentUser()).isTrue();
        }

        if (modality == ModalityEnum.MODALITY_FINGERPRINT) {
            assertThat(atoms.stream().map(d -> d.getAcquireInfo()).collect(Collectors.toList()))
                    .containsExactlyElementsIn(hasAidlFingerprintSensorId()
                            ? FINGERPRINT_ENROLL_ACQUIRED_MESSAGES_AIDL
                            : FINGERPRINT_ENROLL_ACQUIRED_MESSAGES)
                    .inOrder();
        }

        if (modality == ModalityEnum.MODALITY_FACE) {
            assertThat(atoms.stream().map(d -> d.getAcquireInfo()).collect(Collectors.toList()))
                    .containsExactlyElementsIn(FACE_ENROLL_ACQUIRED_MESSAGES_AIDL)
                    .inOrder();
        }
    }

    private static List<AtomsProto.BiometricEnrolled> filterEnrollmentAtoms(
            List<StatsLog.EventMetricData> data, ModalityEnum modality) {
        return data.stream()
                .filter(d -> d.getAtom().hasBiometricEnrolled())
                .map(d -> d.getAtom().getBiometricEnrolled())
                .filter(d -> d.hasModality() && d.getModality() == modality)
                .collect(Collectors.toList());
    }

    private static List<AtomsProto.BiometricAcquired> filterAcquiredAtoms(
            List<StatsLog.EventMetricData> data, ModalityEnum modality) {
        return data.stream()
                .filter(d -> d.getAtom().hasBiometricAcquired())
                .map(d -> d.getAtom().getBiometricAcquired())
                .filter(d -> d.hasModality() && d.getModality() == modality)
                .collect(Collectors.toList());
    }
}
