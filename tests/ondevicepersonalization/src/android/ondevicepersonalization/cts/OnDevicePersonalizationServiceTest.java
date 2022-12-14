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

package android.ondevicepersonalization.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.ondevicepersonalization.OnDevicePersonalizationManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test of {@link OnDevicePersonalizationManager}
 */
@RunWith(JUnit4.class)
public class OnDevicePersonalizationServiceTest {
    private Context mContext;
    private OnDevicePersonalizationManager mService;

    @Before
    public void setup() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mService = new OnDevicePersonalizationManager(mContext);
    }

    @Test
    public void testVersion() throws Exception {
        assertEquals(mService.getVersion(), "1.0");
    }
}
