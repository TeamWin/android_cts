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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.DistanceMeasurementManager;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.bluetooth.le.DistanceMeasurementParams;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementManagerTest {
    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private boolean mIsDistanceMeasurementSupported;
    private BluetoothDevice mDevice;
    private DistanceMeasurementManager mDistanceMeasurementManager;

    private DistanceMeasurementSession.Callback mTestcallback =
            new DistanceMeasurementSession.Callback() {
        public void onStarted(DistanceMeasurementSession session) {}

        public void onStartFail(int reason) {}

        public void onStopped(DistanceMeasurementSession session, int reason) {}

        public void onResult(BluetoothDevice device, DistanceMeasurementResult result) {}
    };

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }
        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) {
            return;
        }
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        mIsDistanceMeasurementSupported =
                mAdapter.isDistanceMeasurementSupported() == FEATURE_SUPPORTED;
        mDevice = mAdapter.getRemoteDevice("11:22:33:44:55:66");
        if (mIsDistanceMeasurementSupported) {
            mDistanceMeasurementManager = mAdapter.getDistanceMeasurementManager();
        }
    }

    @After
    public void tearDown() {
        if (!mHasBluetooth) {
            return;
        }
        if (mAdapter != null) {
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        }
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @Test
    public void testStartMeasurementSession() {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice)
                .setDurationSeconds(15)
                .setFrequency(DistanceMeasurementParams.REPORT_FREQUENCY_LOW)
                .build();
        CancellationSignal signal = mDistanceMeasurementManager.startMeasurementSession(params,
                mContext.getMainExecutor(), mTestcallback);
        assertNotNull(signal);
        signal.cancel();
    }

    @Test
    public void testGetSupportedMethods() {
        List<DistanceMeasurementMethod> list = mDistanceMeasurementManager.getSupportedMethods();
        assertNotNull(list);
    }

    private boolean shouldSkipTest() {
        return !mHasBluetooth || !mIsDistanceMeasurementSupported;
    }
}
