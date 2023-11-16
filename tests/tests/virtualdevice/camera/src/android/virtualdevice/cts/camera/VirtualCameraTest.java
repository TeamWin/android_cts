/*
 * Copyright 2023 The Android Open Source Project
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

package android.virtualdevice.cts.camera;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RequiresFlagsEnabled({android.companion.virtual.flags.Flags.FLAG_VIRTUAL_CAMERA,
        android.companion.virtualdevice.flags.Flags.FLAG_VIRTUAL_CAMERA_SERVICE_DISCOVERY})
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraTest {
    private static final long TIMEOUT_MILLIS = 2000L;
    private static final int CAMERA_DISPLAY_NAME_RES_ID = 10;
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_FORMAT = ImageFormat.YUV_420_888;

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Mock
    private CameraManager.AvailabilityCallback mMockCameraAvailabilityCallback;
    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;

    private CameraManager mCameraManager;
    private VirtualDevice mVirtualDevice;
    private VirtualCamera mVirtualCamera;
    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        mCameraManager = context.getSystemService(CameraManager.class);
        mVirtualDevice = mRule.createManagedVirtualDevice();
        VirtualCameraConfig config = VirtualCameraUtils.createVirtualCameraConfig(CAMERA_WIDTH,
                CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_DISPLAY_NAME_RES_ID, mExecutor,
                mVirtualCameraCallback);
        mVirtualCamera = mVirtualDevice.createVirtualCamera(config);
    }

    @Test
    public void virtualCamera_getConfig_returnsCorrectConfig() {
        VirtualCameraConfig config = mVirtualCamera.getConfig();
        VirtualCameraUtils.assertVirtualCameraConfig(config, CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_DISPLAY_NAME_RES_ID);
    }

    @Test
    public void virtualCamera_triggersCameraAvailabilityCallbacks() throws Exception {
        try {
            mCameraManager.registerAvailabilityCallback(mExecutor, mMockCameraAvailabilityCallback);

            String virtualCameraId = mVirtualCamera.getId();
            verify(mMockCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                    .onCameraAvailable(virtualCameraId);

            mVirtualCamera.close();
            verify(mMockCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                    .onCameraUnavailable(virtualCameraId);
        } finally {
            mCameraManager.unregisterAvailabilityCallback(mMockCameraAvailabilityCallback);
        }
    }
}
