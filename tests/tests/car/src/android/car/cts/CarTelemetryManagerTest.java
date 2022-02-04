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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.telemetry.CarTelemetryManager;
import android.platform.test.annotations.RequiresDevice;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.telemetry.TelemetryProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.Semaphore;

@RequiresDevice
@RunWith(AndroidJUnit4.class)
public class CarTelemetryManagerTest extends CarApiTestBase {

    /* Test MetricsConfig that does nothing. */
    private static final TelemetryProto.MetricsConfig TEST_CONFIG =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("test_config")
                    .setVersion(1)
                    .setScript("no-op")
                    .build();
    private static final String TEST_CONFIG_NAME = TEST_CONFIG.getName();

    private CarTelemetryManager mCarTelemetryManager;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue("CarTelemetryService is not enabled, skipping test",
                getCar().isFeatureEnabled(Car.CAR_TELEMETRY_SERVICE));

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mUiAutomation = instrumentation.getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(
                "android.car.permission.USE_CAR_TELEMETRY_SERVICE");
        mCarTelemetryManager = (CarTelemetryManager) Car.createCar(
                instrumentation.getContext()).getCarManager(Car.CAR_TELEMETRY_SERVICE);
        assertThat(mCarTelemetryManager).isNotNull();
    }

    @After
    public void tearDown() throws Exception {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testAddRemoveMetricsConfig() throws Exception {
        // Test: add new MetricsConfig. Expect: SUCCESS
        AddMetricsConfigCallbackImpl callback = new AddMetricsConfigCallbackImpl();
        mCarTelemetryManager.addMetricsConfig(TEST_CONFIG_NAME, TEST_CONFIG.toByteArray(),
                Runnable::run, callback);
        callback.mSemaphore.acquire();
        assertThat(callback.mAddConfigStatusMap.get(TEST_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_METRICS_CONFIG_SUCCESS);

        // Test: add a duplicate MetricsConfig. Expect: ALREADY_EXISTS status code
        mCarTelemetryManager.addMetricsConfig(TEST_CONFIG_NAME, TEST_CONFIG.toByteArray(),
                Runnable::run, callback);
        callback.mSemaphore.acquire();
        assertThat(callback.mAddConfigStatusMap.get(TEST_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_METRICS_CONFIG_ALREADY_EXISTS);

        // Test: remove a MetricsConfig. Expect: the next add should return SUCCESS
        mCarTelemetryManager.removeMetricsConfig(TEST_CONFIG_NAME);
        mCarTelemetryManager.addMetricsConfig(TEST_CONFIG_NAME, TEST_CONFIG.toByteArray(),
                Runnable::run, callback);
        callback.mSemaphore.acquire();
        assertThat(callback.mAddConfigStatusMap.get(TEST_CONFIG_NAME))
                .isEqualTo(CarTelemetryManager.STATUS_METRICS_CONFIG_SUCCESS);
    }

    private final class AddMetricsConfigCallbackImpl
            implements CarTelemetryManager.AddMetricsConfigCallback {

        private final Semaphore mSemaphore = new Semaphore(0);
        private final Map<String, Integer> mAddConfigStatusMap = new ArrayMap<>();

        @Override
        public void onAddMetricsConfigStatus(@NonNull String metricsConfigName, int statusCode) {
            mAddConfigStatusMap.put(metricsConfigName, statusCode);
            mSemaphore.release();
        }
    }
}
