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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementMethodTest {
    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private boolean mIsDistanceMeasurementSupported;

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
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mIsDistanceMeasurementSupported =
                mAdapter.isDistanceMeasurementSupported() == FEATURE_SUPPORTED;
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
    public void testCreateFromParcel() {
        if (shouldSkipTest()) {
            return;
        }
        final Parcel parcel = Parcel.obtain();
        try {
            DistanceMeasurementMethod method = new DistanceMeasurementMethod
                    .Builder(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                    .setAzimuthAngleSupported(true)
                    .setAltitudeAngleSupported(true).build();
            method.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DistanceMeasurementMethod methodFromParcel =
                    DistanceMeasurementMethod.CREATOR.createFromParcel(parcel);
            assertMethodEquals(method, methodFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testSetGetId() {
        if (shouldSkipTest()) {
            return;
        }
        DistanceMeasurementMethod method = new DistanceMeasurementMethod
                    .Builder(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI).build();
        assertEquals(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI,
                method.getId(), 0.0);
    }

    @Test
    public void testIsAzimuthAngleSupported() {
        if (shouldSkipTest()) {
            return;
        }
        DistanceMeasurementMethod method = new DistanceMeasurementMethod
                    .Builder(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                    .setAzimuthAngleSupported(true)
                    .build();
        assertEquals(true, method.isAzimuthAngleSupported());
    }

    @Test
    public void testIsAltitudeAngleSupported() {
        if (shouldSkipTest()) {
            return;
        }
        DistanceMeasurementMethod method = new DistanceMeasurementMethod
                    .Builder(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                    .setAltitudeAngleSupported(true)
                    .build();
        assertEquals(true, method.isAltitudeAngleSupported());
    }

    @Test
    public void testHashCode() {
        if (shouldSkipTest()) {
            return;
        }
        DistanceMeasurementMethod method = new DistanceMeasurementMethod
                    .Builder(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                    .setAltitudeAngleSupported(true)
                    .build();
        assertEquals(Objects.hash(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI),
                method.hashCode());
    }

    private void assertMethodEquals(DistanceMeasurementMethod p, DistanceMeasurementMethod other) {
        if (p == null && other == null) {
            return;
        }

        if (p == null || other == null) {
            fail("Cannot compare null with non-null value: p=" + p + ", other=" + other);
        }

        assertEquals(p.getId(), other.getId(), 0.0);
        assertEquals(p.isAzimuthAngleSupported(), other.isAzimuthAngleSupported());
        assertEquals(p.isAltitudeAngleSupported(), other.isAltitudeAngleSupported());
    }

    private boolean shouldSkipTest() {
        return !mHasBluetooth || !mIsDistanceMeasurementSupported;
    }
}
