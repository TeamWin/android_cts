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

package android.virtualdevice.cts;

import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.isVirtualDeviceManagerConfigEnabled;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceManagerFlaggingTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = getApplicationContext();
    }

    @Test
    public void getSystemService_enableVirtualDeviceManagerFalse_returnsNull() {
        // Continue this test only if this flag is set to false.
        assumeFalse(isVirtualDeviceManagerConfigEnabled(mContext));

        // config_enableVirtualDeviceManager is false so VirtualDeviceManager shouldn't be started.
        assertNull(
                "VirtualDeviceManager.class started in spite of setting "
                        + "config_enableVirtualDeviceManager false.",
                mContext.getSystemService(VirtualDeviceManager.class));
    }

    @Test
    public void getSystemService_enableVirtualDeviceManagerTrue_returnsNonNull() {
        // Continue this test only if this flag is set to true.
        assumeTrue(isVirtualDeviceManagerConfigEnabled(mContext));

        // config_enableVirtualDeviceManager is true so VirtualDeviceManager should be started.
        assertNotNull(
                "VirtualDeviceManager.class not started in spite of setting "
                        + "config_enableVirtualDeviceManager true.",
                mContext.getSystemService(VirtualDeviceManager.class));
    }
}
