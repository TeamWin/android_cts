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
package android.time.cts.host;

import static android.time.cts.host.LocationManager.SHELL_COMMAND_IS_LOCATION_ENABLED;
import static android.time.cts.host.LocationManager.SHELL_COMMAND_SET_LOCATION_ENABLED;
import static android.time.cts.host.LocationTimeZoneManager.DeviceConfig.NAMESPACE;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_IS_GEO_DETECTION_ENABLED;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_SET_AUTO_DETECTION_ENABLED;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_SET_GEO_DETECTION_ENABLED;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.ITestDevice;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A helper class that helps host tests interact with the time_zone_detector and location_manager
 * services via adb.
 */
final class TimeZoneDetectorHostHelper {

    private final ITestDevice mDevice;

    TimeZoneDetectorHostHelper(ITestDevice mDevice) {
        this.mDevice = Objects.requireNonNull(mDevice);
    }

    boolean isLocationEnabledForCurrentUser() throws Exception {
        byte[] result = executeLocationManagerCommand(SHELL_COMMAND_IS_LOCATION_ENABLED);
        return parseShellCommandBytesAsBoolean(result);
    }

    void setLocationEnabledForCurrentUser(boolean enabled) throws Exception {
        executeLocationManagerCommand(
                "%s %s", SHELL_COMMAND_SET_LOCATION_ENABLED, enabled);
    }

    boolean isAutoDetectionEnabled() throws Exception {
        byte[] result = executeTimeZoneDetectorCommand(SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED);
        return parseShellCommandBytesAsBoolean(result);
    }

    void setAutoDetectionEnabled(boolean enabled) throws Exception {
        executeTimeZoneDetectorCommand("%s %s", SHELL_COMMAND_SET_AUTO_DETECTION_ENABLED, enabled);
    }

    boolean isGeoDetectionEnabled() throws Exception {
        byte[] result = executeTimeZoneDetectorCommand(SHELL_COMMAND_IS_GEO_DETECTION_ENABLED);
        return parseShellCommandBytesAsBoolean(result);
    }

    void setGeoDetectionEnabled(boolean enabled) throws Exception {
        executeTimeZoneDetectorCommand("%s %s", SHELL_COMMAND_SET_GEO_DETECTION_ENABLED, enabled);
    }

    void assumeLocationTimeZoneManagerIsPresent() throws Exception {
        assumeTrue(isLocationTimeZoneManagerPresent());
    }

    private boolean isLocationTimeZoneManagerPresent() throws Exception {
        // Look for the service name in "cmd -l".
        byte[] serviceListBytes = executeShellCommandReturnBytes("cmd -l");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(serviceListBytes), StandardCharsets.UTF_8))) {
            String serviceName;
            while ((serviceName = reader.readLine()) != null) {
                serviceName = serviceName.trim();
                if (LocationTimeZoneManager.SHELL_COMMAND_SERVICE_NAME.equals(serviceName)) {
                    return true;
                }
            }
            return false;
        }
    }

    boolean isGeoDetectionSupported() throws Exception {
        byte[] result = executeTimeZoneDetectorCommand(
                TimeZoneDetector.SHELL_COMMAND_IS_GEO_DETECTION_SUPPORTED);
        return parseShellCommandBytesAsBoolean(result);
    }

    boolean isTelephonyDetectionSupported() throws Exception {
        byte[] result = executeTimeZoneDetectorCommand(
                TimeZoneDetector.SHELL_COMMAND_IS_TELEPHONY_DETECTION_SUPPORTED);
        return parseShellCommandBytesAsBoolean(result);
    }

    void clearSystemTimeDeviceConfigKey(String deviceConfigKey) throws Exception {
        executeDeviceConfigCommand("delete %s %s", NAMESPACE, deviceConfigKey);
    }

    void setSystemTimeDeviceConfigKey(String deviceConfigKey, String value) throws Exception {
        executeDeviceConfigCommand("put %s %s %s", NAMESPACE, deviceConfigKey, value);
    }

    void resetSystemTimeDeviceConfigKeys() throws Exception {
        executeDeviceConfigCommand("reset trusted_defaults %s", NAMESPACE);
    }

    private byte[] executeDeviceConfigCommand(String cmd, Object... args) throws Exception {
        String command = String.format(cmd, args);
        return executeShellCommandReturnBytes("cmd %s %s",
                LocationTimeZoneManager.DeviceConfig.SHELL_COMMAND_SERVICE_NAME, command);
    }

    private byte[] executeLocationManagerCommand(String cmd, Object... args)
            throws Exception {
        String command = String.format(cmd, args);
        return executeShellCommandReturnBytes("cmd %s %s",
                LocationManager.SHELL_COMMAND_SERVICE_NAME, command);
    }

    private byte[] executeTimeZoneDetectorCommand(String cmd, Object... args) throws Exception {
        String command = String.format(cmd, args);
        return executeShellCommandReturnBytes("cmd %s %s",
                TimeZoneDetector.SHELL_COMMAND_SERVICE_NAME, command);
    }

    byte[] executeShellCommandReturnBytes(String cmd, Object... args) throws Exception {
        CollectingByteOutputReceiver bytesReceiver = new CollectingByteOutputReceiver();
        mDevice.executeShellCommand(String.format(cmd, args), bytesReceiver);
        return bytesReceiver.getOutput();
    }

    static boolean parseShellCommandBytesAsBoolean(byte[] result) {
        String resultString = new String(result, 0, result.length, StandardCharsets.ISO_8859_1);
        if (resultString.startsWith("true")) {
            return true;
        } else if (resultString.startsWith("false")) {
            return false;
        } else {
            throw new AssertionError("Command returned unexpected result: " + resultString);
        }
    }
}
