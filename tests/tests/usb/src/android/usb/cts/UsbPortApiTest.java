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

package android.usb.cts;

import static android.Manifest.permission.MANAGE_USB;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.content.Context;
import android.hardware.usb.IUsbOperationInternal;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbPort;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link android.hardware.usb.UsbPort}.
 * Note: MUST claimed MANAGE_USB permission in Manifest
 */
@RunWith(AndroidJUnit4.class)
public class UsbPortApiTest {
    private static final String TAG = UsbPortApiTest.class.getSimpleName();

    private Context mContext;

    private UsbManager mUsbManagerSys =
        InstrumentationRegistry.getContext().getSystemService(UsbManager.class);
    private UsbManager mUsbManagerMock;
    @Mock private android.hardware.usb.IUsbManager mMockUsbService;

    private UsbPort mUsbPort;
    private UsbPort mMockUsbPort;

    private UiAutomation mUiAutomation =
        InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        PackageManager pm = mContext.getPackageManager();
        MockitoAnnotations.initMocks(this);

        Assert.assertNotNull(mUsbManagerSys);
        Assert.assertNotNull(mUsbManagerMock =
                new UsbManager(mContext, mMockUsbService));
    }

    /**
     * Verify NO SecurityException.
     */
    @Test
    public void test_UsbApiForResetUsbPort() throws Exception {
        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        mMockUsbPort = new UsbPort(mUsbManagerMock, "1", 0, 0, true, true);
        mUsbPort = new UsbPort(mUsbManagerSys, "1", 0, 0, true, true);
        int result = 0;

        // Should pass with permission.
        when(mMockUsbService.resetUsbPort(anyString(),anyInt(),
                  any(IUsbOperationInternal.class))).thenReturn(true);
        result = mMockUsbPort.resetUsbPort();
        if (result == 0) {
            Log.d(TAG, "resetUsbPort success");
        } else {
            Log.d(TAG, "resetUsbPort fail ,result = " + result);
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();

        try {
            mUsbPort.resetUsbPort();
            Assert.fail("Expected SecurityException on resetUsbPort.");
        } catch (SecurityException secEx) {
            Log.d(TAG, "Expected SecurityException on resetUsbPort.");
        }
    }
}
