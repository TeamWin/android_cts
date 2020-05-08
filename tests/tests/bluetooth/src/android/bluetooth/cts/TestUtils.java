/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanRecord;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import junit.framework.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class for Bluetooth CTS test.
 */
public class TestUtils {
    private static final String TAG = "BluetoothTestUtils";

    // ADAPTER_ENABLE_TIMEOUT_MS = AdapterState.BLE_START_TIMEOUT_DELAY +
    //                              AdapterState.BREDR_START_TIMEOUT_DELAY
    private static final int ADAPTER_ENABLE_TIMEOUT_MS = 8000;
    // ADAPTER_DISABLE_TIMEOUT_MS = AdapterState.BLE_STOP_TIMEOUT_DELAY +
    //                                  AdapterState.BREDR_STOP_TIMEOUT_DELAY
    private static final int ADAPTER_DISABLE_TIMEOUT_MS = 5000;

    private static BroadcastReceiver mAdapterIntentReceiver;

    private static Condition mConditionAdapterIsEnabled;
    private static ReentrantLock mAdapterStateEnablinglock;

    private static Condition mConditionAdapterIsDisabled;
    private static ReentrantLock mAdapterStateDisablinglock;
    private static boolean mAdapterVarsInitialized;

    private static class AdapterIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                Log.d(TAG, "Previous state: " + previousState + " New state: " + newState);

                if (newState == BluetoothAdapter.STATE_ON) {
                    mAdapterStateEnablinglock.lock();
                    try {
                        Log.d(TAG, "Signaling to mConditionAdapterIsEnabled");
                        mConditionAdapterIsEnabled.signal();
                    } finally {
                        mAdapterStateEnablinglock.unlock();
                    }
                } else if (newState == BluetoothAdapter.STATE_OFF) {
                    mAdapterStateDisablinglock.lock();
                    try {
                        Log.d(TAG, "Signaling to mConditionAdapterIsDisabled");
                        mConditionAdapterIsDisabled.signal();
                    } finally {
                        mAdapterStateDisablinglock.unlock();
                    }
                }
            }
        }
    }

    // Enables the Bluetooth Adapter. Return true if it is already enabled or is enabled.
    public static boolean enableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!mAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.isEnabled()) return true;

        Log.d(TAG, "Enabling bluetooth adapter");
        bluetoothAdapter.enable();
        mAdapterStateEnablinglock.lock();
        try {
            // Wait for the Adapter to be enabled
            while (!bluetoothAdapter.isEnabled()) {
                if (!mConditionAdapterIsEnabled.await(
                        ADAPTER_ENABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for the Bluetooth Adapter enable");
                    break;
                } // else spurious wakeups
            }
        } catch(InterruptedException e) {
            Log.e(TAG, "enableAdapter: interrrupted");
        } finally {
            mAdapterStateEnablinglock.unlock();
        }
        return bluetoothAdapter.isEnabled();
    }

    // Disable the Bluetooth Adapter. Return true if it is already disabled or is disabled.
    public static boolean disableAdapter(BluetoothAdapter bluetoothAdapter, Context context) {
        if (!mAdapterVarsInitialized) {
            initAdapterStateVariables(context);
        }

        if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) return true;

        Log.d(TAG, "Disabling bluetooth adapter");
        bluetoothAdapter.disable();
        mAdapterStateDisablinglock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (bluetoothAdapter.getState() != BluetoothAdapter.STATE_OFF) {
                if (!mConditionAdapterIsDisabled.await(
                        ADAPTER_DISABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for the Bluetooth Adapter disable");
                    break;
                } // else spurious wakeups
            }
        } catch(InterruptedException e) {
            Log.e(TAG, "enableAdapter: interrrupted");
        } finally {
            mAdapterStateDisablinglock.unlock();
        }
        return bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF;
    }

    // Initialize variables required for TestUtils#enableAdapter and TestUtils#disableAdapter
    private static void initAdapterStateVariables(Context context) {
        Log.d(TAG, "Initializing adapter state variables");
        mAdapterIntentReceiver = new AdapterIntentReceiver();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(mAdapterIntentReceiver, filter);

        mAdapterStateEnablinglock = new ReentrantLock();
        mConditionAdapterIsEnabled = mAdapterStateEnablinglock.newCondition();
        mAdapterStateDisablinglock = new ReentrantLock();
        mConditionAdapterIsDisabled = mAdapterStateDisablinglock.newCondition();

        mAdapterVarsInitialized = true;
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