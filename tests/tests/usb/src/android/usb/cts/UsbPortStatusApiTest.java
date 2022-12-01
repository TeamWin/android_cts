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
import android.hardware.usb.UsbPortStatus;
import android.util.Log;

import android.os.Build;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.util.function.Consumer;
import java.util.concurrent.Executor;
import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link android.hardware.usb.UsbPortStatus}.
 * Note: MUST claimed MANAGE_USB permission in Manifest
 */
@RunWith(AndroidJUnit4.class)
public class UsbPortStatusApiTest {
    private static final String TAG = UsbPortStatusApiTest.class.getSimpleName();

    private Context mContext;

    private UsbManager mUsbManagerSys =
            InstrumentationRegistry.getContext().getSystemService(UsbManager.class);
    private UsbManager mUsbManagerMock;
    @Mock private android.hardware.usb.IUsbManager mMockUsbService;

    private UsbPort mUsbPort;
    private UsbPort mMockUsbPort;
    private UsbPortStatus mUsbPortStatus;

    private UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private Executor mExecutor;
    private Consumer<Integer> mConsumer;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mExecutor = mContext.getMainExecutor();
        PackageManager pm = mContext.getPackageManager();
        MockitoAnnotations.initMocks(this);

        Assert.assertNotNull(mUsbManagerSys);
        Assert.assertNotNull(mUsbManagerMock =
                new UsbManager(mContext, mMockUsbService));

        mUsbPort = new UsbPort(mUsbManagerSys, "1", 0, 0, true, true, true);
        mUsbPortStatus = new UsbPortStatus(0, 0, 0, 0, 0, 0);
    }

    /**
     * Verify that getComplianceWarnings is initialized to empty array on older
     * version of UsbPortStatus constructors.
     */
    @Test
    public void test_UsbApiForGetComplianceWarnings() throws Exception {
        int[] complianceWarnings;

        // Adopt MANAGE_USB permission.
        mUiAutomation.adoptShellPermissionIdentity(MANAGE_USB);

        // Check to see that build version is valid
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                complianceWarnings = mUsbPortStatus.getComplianceWarnings();
                Assert.assertTrue(complianceWarnings.length == 0);
            } catch (Exception e) {
                Assert.fail("Unexpected Exception on getNonCompliantReasons");
            }
        }

        // Drop MANAGE_USB permission.
        mUiAutomation.dropShellPermissionIdentity();
    }
}
