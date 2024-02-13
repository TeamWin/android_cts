/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.cts.CameraTestUtils.MockStateCallback;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.internal.camera.flags.Flags;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tests the functionality of {@link CameraDevice.CameraDeviceSetup} APIs.
 * <p>
 * NOTE: Functionality of {@link CameraDevice.CameraDeviceSetup#createCaptureRequest} and
 * {@link CameraDevice.CameraDeviceSetup#isSessionConfigurationSupported} is tested by
 * {@link FeatureCombinationTest}
 */
@RunWith(Parameterized.class)
public class CameraDeviceSetupTest extends Camera2AndroidTestCase {
    private static final String TAG = CameraDeviceSetupTest.class.getSimpleName();
    public static final int CAMERA_STATE_TIMEOUT_MS = 3000;

    @Rule
    public final CheckFlagsRule mFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_CAMERA_DEVICE_SETUP, Flags.FLAG_FEATURE_COMBINATION_QUERY})
    public void testCameraDeviceSetupSupport() throws Exception {
        for (String cameraId : getCameraIdsUnderTest()) {
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
            Integer queryVersion = chars.get(
                    CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION);
            mCollector.expectNotNull(CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION
                    + " must not be null", queryVersion);
            if (queryVersion == null) {
                continue;
            }
            boolean cameraDeviceSetupSupported = mCameraManager.isCameraDeviceSetupSupported(
                    cameraId);

            // CameraDeviceSetup must be supported for all CameraDevices that report
            // INFO_SESSION_CONFIGURATION_QUERY_VERSION > U, and false for those that don't.
            mCollector.expectEquals(CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION
                            + " and CameraManager.isCameraDeviceSetupSupported give differing "
                            + "answers for camera id" + cameraId,
                    queryVersion > Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                    cameraDeviceSetupSupported);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testCameraDeviceSetupCreationSuccessful() throws Exception {
        for (String cameraId : getCameraIdsUnderTest()) {
            if (!mCameraManager.isCameraDeviceSetupSupported(cameraId)) {
                Log.i(TAG, "CameraDeviceSetup not supported for camera id " + cameraId);
                continue;
            }

            CameraDevice.CameraDeviceSetup cameraDeviceSetup =
                    mCameraManager.getCameraDeviceSetup(cameraId);
            mCollector.expectEquals("Camera ID of created object is not the same as that "
                            + "passed to CameraManager.",
                    cameraId, cameraDeviceSetup.getId());
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testCameraDeviceSetupCreationFailure() throws Exception {
        try {
            mCameraManager.getCameraDeviceSetup(/*cameraId=*/ null);
            Assert.fail("Calling getCameraDeviceSetup with null should have raised an "
                    + "IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected. Don't do anything.
        }

        // Try to get an invalid camera ID by randomly generating 100 integers.
        // NOTE: We don't actually expect to generate more than an int or two.
        // Not being able to find an invalid camera within 100 random ints
        // is astronomical.
        HashSet<String> cameraIds = new HashSet<>(List.of(getCameraIdsUnderTest()));
        Random rng = new Random();
        int invalidId = rng.ints(/*streamSize=*/ 100, /*randomNumberOrigin=*/ 0,
                        /*randomNumberBound=*/ Integer.MAX_VALUE)
                .filter(i -> !cameraIds.contains(String.valueOf(i)))
                .findAny()
                .orElseThrow(() -> new AssertionError(
                        "Could not find an invalid cameraID within 100 randomly generated "
                                + "numbers."));
        String invalidCameraId = String.valueOf(invalidId);
        Log.i(TAG, "Using invalid Camera ID: " + invalidCameraId);
        try {
            mCameraManager.getCameraDeviceSetup(invalidCameraId);
            Assert.fail("Calling getCameraDeviceSetup with a cameraId not in getCameraIdList()"
                    + "should have raised an IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected. Don't do anything.
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testOpenSuccessful() throws Exception {
        ExecutorService callbackExecutor = Executors.newCachedThreadPool();
        for (String cameraId : getCameraIdsUnderTest()) {
            // mock listener to capture the CameraDevice from callbacks
            MockStateCallback mockListener = MockStateCallback.mock();
            BlockingStateCallback callback = new BlockingStateCallback(mockListener);

            CameraDevice.CameraDeviceSetup cameraDeviceSetup =
                    mCameraManager.getCameraDeviceSetup(cameraId);
            cameraDeviceSetup.openCamera(callbackExecutor, callback);

            callback.waitForState(BlockingStateCallback.STATE_OPENED, CAMERA_STATE_TIMEOUT_MS);
            CameraDevice cameraDevice = CameraTestUtils.verifyCameraStateOpened(
                    cameraId, mockListener);

            mCollector.expectEquals("CameraDeviceSetup and created CameraDevice must have "
                            + "the same ID",
                    cameraDeviceSetup.getId(), cameraDevice.getId());

            cameraDevice.close();
            callback.waitForState(BlockingStateCallback.STATE_CLOSED, CAMERA_STATE_TIMEOUT_MS);
        }
    }
}
