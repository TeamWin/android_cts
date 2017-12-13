/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.transferowner;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.Before;

@SmallTest
public class DeviceAndProfileOwnerTransferIncomingTest {
    public static class BasicAdminReceiver extends DeviceAdminReceiver {
        public BasicAdminReceiver() {}
    }

    protected ComponentName mIncomingComponentName;
    protected DevicePolicyManager mDevicePolicyManager;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        mDevicePolicyManager = context.getSystemService(DevicePolicyManager.class);
        mIncomingComponentName = new ComponentName(context, BasicAdminReceiver.class.getName());
    }
}
