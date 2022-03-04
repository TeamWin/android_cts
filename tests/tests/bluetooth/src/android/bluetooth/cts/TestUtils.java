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

package android.bluetooth.cts;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Utility class for Bluetooth CTS test.
 */
class TestUtils {
    /**
     * Bluetooth package name
     */
    static final String BLUETOOTH_PACKAGE_NAME = "com.android.bluetooth";

    /**
     * Get the Config.xml name tag for a particular Bluetooth profile
     * @param profile profile id from {@link BluetoothProfile}
     * @return config name tag, or null if the tag name is not available
     */
    @Nullable static String profileIdToConfigTag(int profile) {
        switch (profile) {
            case BluetoothProfile.A2DP:
                return "profile_supported_a2dp";
            case BluetoothProfile.A2DP_SINK:
                return "profile_supported_a2dp_sink";
            case BluetoothProfile.HEADSET:
                return "profile_supported_hs_hfp";
            case BluetoothProfile.HEADSET_CLIENT:
                return "profile_supported_hfpclient";
            case BluetoothProfile.HID_HOST:
                return "profile_supported_hid_host";
            case BluetoothProfile.OPP:
                return "profile_supported_opp";
            case BluetoothProfile.PAN:
                return "profile_supported_pan";
            case BluetoothProfile.PBAP:
                return "profile_supported_pbap";
            case BluetoothProfile.GATT:
                return "profile_supported_gatt";
            case BluetoothProfile.MAP:
                return "profile_supported_map";
            // Hidden profile
            // case BluetoothProfile.AVRCP:
            //    return "profile_supported_avrcp_target";
            case BluetoothProfile.AVRCP_CONTROLLER:
                return "profile_supported_avrcp_controller";
            case BluetoothProfile.SAP:
                return "profile_supported_sap";
            case BluetoothProfile.PBAP_CLIENT:
                return "profile_supported_pbapclient";
            case BluetoothProfile.MAP_CLIENT:
                return "profile_supported_mapmce";
            case BluetoothProfile.HID_DEVICE:
                return "profile_supported_hid_device";
            case BluetoothProfile.LE_AUDIO:
                return "profile_supported_le_audio";
            case BluetoothProfile.LE_AUDIO_BROADCAST:
                return "profile_supported_le_audio_broadcast";
            case BluetoothProfile.VOLUME_CONTROL:
                return "profile_supported_vc";
            // Hidden profile
            // case BluetoothProfile.MCP_SERVER:
            //    return "profile_supported_mcp_server";
            case BluetoothProfile.CSIP_SET_COORDINATOR:
                return "profile_supported_csip_set_coordinator";
            // Hidden profile
            // case BluetoothProfile.LE_CALL_CONTROL:
            //    return "profile_supported_le_call_control";
            case BluetoothProfile.HAP_CLIENT:
                return "profile_supported_hap_client";
            case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT:
                return "profile_supported_bass_client";
            default:
                return null;
        }
    }

    /**
     * Checks if a particular Bluetooth profile is configured for this device
     * Fail the test if profile config status cannot be obtained
     */
    static boolean getProfileConfigValueOrDie(int profile) {
        String profileConfigValueTag = profileIdToConfigTag(profile);
        assertNotNull(profileConfigValueTag);
        assertNotEquals("profile tag cannot be empty", 0, profileConfigValueTag.length());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Resources bluetoothResources = null;
        try {
            bluetoothResources = context.getPackageManager().getResourcesForApplication(
                    BLUETOOTH_PACKAGE_NAME);
        } catch (PackageManager.NameNotFoundException e) {
            fail("Cannot get Bluetooth package resource");
        }
        int resourceId = bluetoothResources.getIdentifier(
                profileConfigValueTag, "bool", BLUETOOTH_PACKAGE_NAME);
        if (resourceId == 0) {
            return false;
        }
        return bluetoothResources.getBoolean(resourceId);
    }

    /**
     * Checks whether this device has Bluetooth feature
     * @return true if this device has Bluetooth feature
     */
    static boolean hasBluetooth() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
    }

    /**
     * Adopt shell UID's permission via {@link android.app.UiAutomation}
     * @param permission permission to adopt
     */
    static void adoptPermissionAsShellUid(@NonNull String permission) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(permission);
    }

    /**
     * Drop all permissions adopted as shell UID
     */
    static void dropPermissionAsShellUid() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Get {@link BluetoothAdapter} via {@link android.bluetooth.BluetoothManager}
     * Fail the test if {@link BluetoothAdapter} is null
     * @return instance of {@link BluetoothAdapter}
     */
    @NonNull static BluetoothAdapter getBluetoothAdapterOrDie() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        assertNotNull(manager);
        BluetoothAdapter adapter = manager.getAdapter();
        assertNotNull(adapter);
        return adapter;
    }

    /**
     * Utility method to call hidden ScanRecord.parseFromBytes method.
     */
    static ScanRecord parseScanRecord(byte[] bytes) {
        Class<?> scanRecordClass = ScanRecord.class;
        try {
            Method method = scanRecordClass.getDeclaredMethod("parseFromBytes", byte[].class);
            return (ScanRecord) method.invoke(null, bytes);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            return null;
        }
    }

    /**
     * Assert two byte arrays are equal.
     */
    static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            Assert.fail("expected:<" + Arrays.toString(expected) +
                    "> but was:<" + Arrays.toString(actual) + ">");
        }
    }

    /**
     * Get current location mode settings.
     */
    static int getLocationMode(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
    }

    /**
     * Set location settings mode.
     */
    static void setLocationMode(Context context, int mode) {
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE,
                mode);
    }

    /**
     * Return true if location is on.
     */
    static boolean isLocationOn(Context context) {
        return getLocationMode(context) != Settings.Secure.LOCATION_MODE_OFF;
    }

    /**
     * Enable location and set the mode to GPS only.
     */
    static void enableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
    }

    /**
     * Disable location.
     */
    static void disableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_OFF);
    }

    /**
     * Check if BLE is supported by this platform
     * @param context current device context
     * @return true if BLE is supported, false otherwise
     */
    static boolean isBleSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Put the current thread to sleep.
     * @param sleepMillis number of milliseconds to sleep for
     */
    static void sleep(int sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Log.e(TestUtils.class.getSimpleName(), "interrupted", e);
        }
    }
}