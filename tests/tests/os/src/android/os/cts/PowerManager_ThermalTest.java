/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.os.cts;

import static junit.framework.TestCase.assertEquals;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.ThermalStatusCallback;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.android.compatibility.common.util.ThermalUtils;

import org.junit.After;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PowerManager_ThermalTest {
    private static final long CALLBACK_TIMEOUT_MILLI_SEC = 5000;
    private UiDevice mUiDevice;
    private Context mContext;
    private PowerManager mPowerManager;
    private Executor mExec = Executors.newSingleThreadExecutor();
    @Mock
    private ThermalStatusCallback mCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mPowerManager = mContext.getSystemService(PowerManager.class);
        ThermalUtils.overrideThermalNotThrottling();
    }

    @After
    public void tearDown() throws Exception {
        ThermalUtils.resetThermalStatus();
    }

    @Test
    public void testGetThermalStatus() throws Exception {
        int status = 0; // Temperature.THROTTLING_NONE
        assertEquals(status, mPowerManager.getCurrentThermalStatus());
        status = 3; // Temperature.THROTTLING_SEVERE
        ThermalUtils.overrideThermalStatus(status);
        assertEquals(status, mPowerManager.getCurrentThermalStatus());
    }

    @Test
    public void testThermalStatusCallback() throws Exception {
        // Initial override status is Temperature.THROTTLING_NONE (0)
        int status = 0; // Temperature.THROTTLING_NONE
        mPowerManager.registerThermalStatusCallback(mCallback, mExec);
        verify(mCallback, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(1)).onStatusChange(status);
        reset(mCallback);
        status = 3; // Temperature.THROTTLING_SEVERE
        ThermalUtils.overrideThermalStatus(status);
        verify(mCallback, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(1)).onStatusChange(status);
        reset(mCallback);
        mPowerManager.unregisterThermalStatusCallback(mCallback);
        status = 2; // THROTTLING_MODERATE
        ThermalUtils.overrideThermalStatus(status);
        verify(mCallback, timeout(CALLBACK_TIMEOUT_MILLI_SEC)
                .times(0)).onStatusChange(status);
    }
}
