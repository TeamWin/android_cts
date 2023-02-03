/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.edi.cts;

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Before;

import java.io.IOException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Biometrics device info collector
 */
public class BiometricsDeviceInfo extends DeviceInfo {
    private static final String TAG = "BiometricsDeviceInfo";

    private static final String FEATURE_FINGERPRINT = "android.hardware.fingerprint";
    private static final String FEATURE_FACE = "android.hardware.biometrics.face";
    private static final String DUMPSYS_BIOMETRIC = "dumpsys biometric";

    // BiometricAuthenticator.Modality.TYPE_FINGERPRINT
    private static final int AUTHENTICATOR_TYPE_FINGERPRINT = 1 << 1;
    // BiometricAuthenticator.Modality.TYPE_IRIS
    private static final int AUTHENTICATOR_TYPE_IRIS = 1 << 2;
    // BiometricAuthenticator.Modality.TYPE_FACE
    private static final int AUTHENTICATOR_TYPE_FACE = 1 << 3;

    // BiometricManager.Authenticators.BIOMETRIC_CONVENIENCE
    private static final int AUTHENTICATOR_BIOMETRIC_CONVENIENCE = 0x0FFF;
    // BiometricManager.Authenticators.BIOMETRIC_WEAK
    private static final int AUTHENTICATOR_BIOMETRIC_WEAK = 0x00FF;
    // BiometricManager.Authenticators.BIOMETRIC_STRONG
    private static final int AUTHENTICATOR_BIOMETRIC_STRONG = 0x000F;

    private static final String BIOMETRIC_PROPERTIES = "biometric_properties";
    private static final String SENSOR_ID = "sensor_id";

    private static final String SENSOR_MODALITY = "modality";
    private static final int MODALITY_UNKNOWN = 0;
    private static final int MODALITY_FINGERPRINT = 1;
    private static final int MODALITY_IRIS = 2;
    private static final int MODALITY_FACE = 4;

    private static final String SENSOR_STRENGTH = "sensor_strength";
    private static final int STRENGTH_UNKNOWN = 0;
    private static final int STRENGTH_CONVENIENCE = 1;
    private static final int STRENGTH_WEAK = 2;
    private static final int STRENGTH_STRONG = 3;

    private ITestDevice mDevice;
    private DeviceSdkLevel mDeviceSdkLevel;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        mDeviceSdkLevel = new DeviceSdkLevel(mDevice);
        //TODO: may change to U if ComponentInfo is added
        assumeTrue("Skipping test for devices not launching with at least Android S",
                mDeviceSdkLevel.isDeviceAtLeastS());
        assumeTrue("Skipping test for devices not supporting fingerprint and/or face "
                + "authentication", mDevice.hasFeature(FEATURE_FINGERPRINT)
                || mDevice.hasFeature(FEATURE_FACE));
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        String output = mDevice.executeShellCommand(DUMPSYS_BIOMETRIC);
        if (output == null) output = "";
        output = output.trim();
        if (output.isEmpty()) {
            CLog.e("dumpsys biometric does not generate anything");
            return;
        }
        parseSensorInfo(store, output);
    }

    private void parseSensorInfo(HostInfoStore store, String output) throws IOException {
        Pattern pattern = Pattern.compile(".*ID\\((?<ID>\\d+)\\).*updatedStrength:[ \\t]*"
                + "(?<strength>\\d+).*modality[ \\t]*(?<modality>\\d+).*");
        try (Scanner scanner = new Scanner(output)) {
            store.startArray(BIOMETRIC_PROPERTIES);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    store.startGroup();
                    store.addResult(SENSOR_ID, Integer.parseInt(matcher.group("ID")));
                    store.addResult(SENSOR_MODALITY,
                            toModality(Integer.parseInt(matcher.group("modality"))));
                    store.addResult(SENSOR_STRENGTH,
                            toSensorStrength(Integer.parseInt(matcher.group("strength"))));
                    store.endGroup();
                }
            }
            store.endArray(); // "biometric_properties"
        }
    }

    /**
     * Convert a modality (BiometricAuthenticator.Modality) to the corresponding enum to be stored.
     *
     * @param modality See BiometricAuthenticator.Modality
     * @return The enum to be stored
     */
    private int toModality(int modality) {
        switch (modality) {
            case AUTHENTICATOR_TYPE_FINGERPRINT:
                return MODALITY_FINGERPRINT;
            case AUTHENTICATOR_TYPE_IRIS:
                return MODALITY_IRIS;
            case AUTHENTICATOR_TYPE_FACE:
                return MODALITY_FACE;
            default:
                return MODALITY_UNKNOWN;
        }
    }

    /**
     * Convert a sensor strength (BiometricManager.Authenticators.Types) to the corresponding enum
     * to be stored.
     *
     * @param sensorStrength See BiometricManager.Authenticators.Types
     * @return The enum to be stored
     */
    private int toSensorStrength(int sensorStrength) {
        switch (sensorStrength) {
            case AUTHENTICATOR_BIOMETRIC_CONVENIENCE:
                return STRENGTH_CONVENIENCE;
            case AUTHENTICATOR_BIOMETRIC_WEAK:
                return STRENGTH_WEAK;
            case AUTHENTICATOR_BIOMETRIC_STRONG:
                return STRENGTH_STRONG;
            default:
                return STRENGTH_UNKNOWN;
        }
    }
}
