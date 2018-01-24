/*
 * Copyright 2018 The Android Open Source Project
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

import static android.hardware.camera2.cts.CameraTestUtils.*;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.util.ArraySet;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;


import com.android.compatibility.common.util.Stat;
import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Tests exercising logical camera setup, configuration, and usage.
 */
public final class LogicalCameraDeviceTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "LogicalCameraTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int CONFIGURE_TIMEOUT = 5000; //ms

    private static final double NS_PER_MS = 1000000.0;
    private static final int MAX_IMAGE_COUNT = 3;
    private static final int NUM_FRAMES_CHECKED = 30;

    private static final double FRAME_DURATION_THRESHOLD = 0.1;

    /**
     * Test that passing in invalid physical camera ids in OutputConfiguragtion behaves as expected
     * for logical multi-camera and non-logical multi-camera.
     */
    public void testInvalidPhysicalCameraIdInOutputConfiguration() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                Size yuvSize = mOrderedPreviewSizes.get(0);
                // Create a YUV image reader.
                ImageReader imageReader = ImageReader.newInstance(yuvSize.getWidth(),
                        yuvSize.getHeight(), ImageFormat.YUV_420_888, /*maxImages*/1);

                CameraCaptureSession.StateCallback sessionListener =
                        mock(CameraCaptureSession.StateCallback.class);
                List<OutputConfiguration> outputs = new ArrayList<>();
                OutputConfiguration outputConfig = new OutputConfiguration(
                        imageReader.getSurface());
                outputConfig.setPhysicalCameraId(id);

                // Regardless of logical camera or non-logical camera, create a session of an
                // output configuration with invalid physical camera id, verify that the
                // createCaptureSession fails.
                outputs.add(outputConfig);
                CameraCaptureSession session =
                        CameraTestUtils.configureCameraSessionWithConfig(mCamera, outputs,
                                sessionListener, mHandler);
                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce()).
                        onConfigureFailed(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onConfigured(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onReady(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onActive(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onClosed(any(CameraCaptureSession.class));
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test for making sure that streaming from physical streams work as expected, and
     * FPS isn't slowed down.
     */
    public void testBasicPhysicalStreaming() throws Exception {

        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                if (!mStaticInfo.isLogicalMultiCamera()) {
                    Log.i(TAG, "Camera " + id + " is not a logical multi-camera, skipping");
                    continue;
                }

                assertTrue("Logical multi-camera must be LIMITED or higher",
                        mStaticInfo.isHardwareLevelAtLeastLimited());

                // Figure out yuv size to use.
                Size yuvSize = findMaxPhysicalYuvSize(id);
                if (yuvSize == null) {
                    Log.i(TAG, "Camera " + id + ": No matching physical YUV streams, skipping");
                    continue;
                }

                if (VERBOSE) {
                    Log.v(TAG, "Camera " + id + ": Testing YUV size of " + yuvSize.getWidth() +
                        " x " + yuvSize.getHeight());
                }
                List<String> physicalCameraIds =
                        mStaticInfo.getCharacteristics().getPhysicalCameraIds();
                assertTrue("Logical camera must contain at least 2 physical camera ids",
                        physicalCameraIds.size() >= 2);

                List<String> noPhysicalIds = new ArrayList<>();
                double avgLogicalDurationsMs = measureYuvFrameDuration(id, noPhysicalIds, yuvSize);

                List<String> onePhysicalIds = new ArrayList<>();
                onePhysicalIds.add(physicalCameraIds.get(0));
                double avg1PhysicalDurationsMs = measureYuvFrameDuration(id,
                        onePhysicalIds, yuvSize);
                mCollector.expectLessOrEqual("The average frame duration increase of a physical "
                        + "stream is larger than threshold: "
                        + String.format("increase = %.2f, threshold = %.2f",
                          (avg1PhysicalDurationsMs - avgLogicalDurationsMs)/avgLogicalDurationsMs,
                          FRAME_DURATION_THRESHOLD),
                        avgLogicalDurationsMs*(1+FRAME_DURATION_THRESHOLD),
                        avg1PhysicalDurationsMs);

                double avgAllPhysicalDurationsMs = measureYuvFrameDuration(
                        id, physicalCameraIds, yuvSize);

                mCollector.expectLessOrEqual("The average frame duration increase of all physical "
                        + "streams is larger than threshold: "
                        + String.format("increase = %.2f, threshold = %.2f",
                          (avgAllPhysicalDurationsMs - avgLogicalDurationsMs)/avgLogicalDurationsMs,
                          FRAME_DURATION_THRESHOLD),
                        avgLogicalDurationsMs*(1+FRAME_DURATION_THRESHOLD),
                        avgAllPhysicalDurationsMs);

            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test for making sure that multiple requests for physical cameras work as expected.
     */
    public void testBasicPhysicalRequests() throws Exception {

        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                if (!mStaticInfo.isLogicalMultiCamera()) {
                    Log.i(TAG, "Camera " + id + " is not a logical multi-camera, skipping");
                    continue;
                }

                assertTrue("Logical multi-camera must be LIMITED or higher",
                        mStaticInfo.isHardwareLevelAtLeastLimited());

                // Figure out yuv size to use.
                Size yuvSize= findMaxPhysicalYuvSize(id);
                if (yuvSize == null) {
                    Log.i(TAG, "Camera " + id + ": No matching physical YUV streams, skipping");
                    continue;
                }

                if (VERBOSE) {
                    Log.v(TAG, "Camera " + id + ": Testing YUV size of " + yuvSize.getWidth() +
                        " x " + yuvSize.getHeight());
                }
                List<CaptureRequest.Key<?>> physicalRequestKeys =
                    mStaticInfo.getCharacteristics().getAvailablePhysicalCameraRequestKeys();
                if (physicalRequestKeys == null) {
                    Log.i(TAG, "Camera " + id + ": no available physical request keys, skipping");
                    continue;
                }

                List<String> physicalCameraIds =
                        mStaticInfo.getCharacteristics().getPhysicalCameraIds();
                assertTrue("Logical camera must contain at least 2 physical camera ids",
                        physicalCameraIds.size() >= 2);
                ArraySet<String> physicalIdSet = new ArraySet<String>(physicalCameraIds.size());
                physicalIdSet.addAll(physicalCameraIds);

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                List<ImageReader> imageReaders = new ArrayList<>();
                SimpleImageReaderListener readerListener = new SimpleImageReaderListener();
                ImageReader yuvTarget = CameraTestUtils.makeImageReader(yuvSize,
                        ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                        readerListener, mHandler);
                imageReaders.add(yuvTarget);
                OutputConfiguration config = new OutputConfiguration(yuvTarget.getSurface());
                outputConfigs.add(new OutputConfiguration(yuvTarget.getSurface()));

                CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW, physicalIdSet);
                requestBuilder.addTarget(config.getSurface());

                mSessionListener = new BlockingSessionCallback();
                mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                        mSessionListener, mHandler);

                for (int i = 0; i < MAX_IMAGE_COUNT; i++) {
                    mSession.capture(requestBuilder.build(), new SimpleCaptureCallback(), mHandler);
                    readerListener.getImage(WAIT_FOR_RESULT_TIMEOUT_MS);
                }

                if (mSession != null) {
                    mSession.close();
                }

            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Tests invalid/incorrect multiple physical capture request cases.
     */
    public void testInvalidPhysicalCameraRequests() throws Exception {

        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                assertTrue("Logical multi-camera must be LIMITED or higher",
                        mStaticInfo.isHardwareLevelAtLeastLimited());

                // Figure out yuv size to use.
                Size yuvSize= findMaxPhysicalYuvSize(id);
                if (yuvSize == null) {
                    Log.i(TAG, "Camera " + id + ": No matching physical YUV streams, skipping");
                    continue;
                }

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                List<ImageReader> imageReaders = new ArrayList<>();
                SimpleImageReaderListener readerListener = new SimpleImageReaderListener();
                ImageReader yuvTarget = CameraTestUtils.makeImageReader(yuvSize,
                        ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                        readerListener, mHandler);
                imageReaders.add(yuvTarget);
                OutputConfiguration config = new OutputConfiguration(yuvTarget.getSurface());
                outputConfigs.add(new OutputConfiguration(yuvTarget.getSurface()));

                ArraySet<String> physicalIdSet = new ArraySet<String>();
                // Invalid physical id
                physicalIdSet.add("-2");

                CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW, physicalIdSet);
                requestBuilder.addTarget(config.getSurface());

                // Check for invalid setting get/set
                try {
                    requestBuilder.getPhysicalCameraKey(CaptureRequest.CONTROL_CAPTURE_INTENT, "-1");
                    fail("No exception for invalid physical camera id");
                } catch (IllegalArgumentException e) {
                    //expected
                }

                try {
                    requestBuilder.setPhysicalCameraKey(CaptureRequest.CONTROL_CAPTURE_INTENT,
                            new Integer(0), "-1");
                    fail("No exception for invalid physical camera id");
                } catch (IllegalArgumentException e) {
                    //expected
                }

                mSessionListener = new BlockingSessionCallback();
                mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                        mSessionListener, mHandler);

                try {
                    mSession.capture(requestBuilder.build(), new SimpleCaptureCallback(),
                            mHandler);
                    fail("No exception for invalid physical camera id");
                } catch (IllegalArgumentException e) {
                    //expected
                }

                if (mStaticInfo.isLogicalMultiCamera()) {
                    List<String> physicalCameraIds =
                        mStaticInfo.getCharacteristics().getPhysicalCameraIds();
                    assertTrue("Logical camera must contain at least 2 physical camera ids",
                            physicalCameraIds.size() >= 2);

                    physicalIdSet.clear();
                    physicalIdSet.addAll(physicalCameraIds);
                    requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW, physicalIdSet);
                    requestBuilder.addTarget(config.getSurface());
                    CaptureRequest request = requestBuilder.build();

                    // Streaming requests with individual physical camera settings are not
                    // supported.
                    try {
                        mSession.setRepeatingRequest(request, new SimpleCaptureCallback(),
                                mHandler);
                        fail("Streaming requests that include physical camera settings are " +
                                "supported");
                    } catch (IllegalArgumentException e) {
                        //expected
                    }

                    try {
                        ArrayList<CaptureRequest> requestList = new ArrayList<CaptureRequest>();
                        requestList.add(request);
                        mSession.setRepeatingBurst(requestList, new SimpleCaptureCallback(),
                                mHandler);
                        fail("Streaming requests that include physical camera settings are " +
                                "supported");
                    } catch (IllegalArgumentException e) {
                        //expected
                    }
                }

                if (mSession != null) {
                    mSession.close();
                }
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Find the maximum YUV stream size that's supported by physical cameras.
     */
    private Size findMaxPhysicalYuvSize(String cameraId) throws Exception {
        List<String> physicalCameras =
                mStaticInfo.getCharacteristics().getPhysicalCameraIds();
        List<Size> yuvSizes = CameraTestUtils.getSortedSizesForFormat(
                cameraId, mCameraManager, ImageFormat.YUV_420_888, /*bound*/null);
        Size bestYuvSize = null;
        for (Size yuvSize : yuvSizes) {
            boolean physicalSizeSupported = true;
            for (String physicalCameraId : physicalCameras) {
                List<Size> yuvSizesForPhysicalCamera =
                        CameraTestUtils.getSortedSizesForFormat(physicalCameraId,
                        mCameraManager, ImageFormat.YUV_420_888, /*bound*/null);
                if (!yuvSizesForPhysicalCamera.contains(yuvSize)) {
                    physicalSizeSupported = false;
                    break;
                }
            }
            if (physicalSizeSupported) {
                bestYuvSize = yuvSize;
                break;
            }
        }
        return bestYuvSize;
    }

    /**
     * Measure the average frame duration of logical YUV stream.
     */
    private double measureYuvFrameDuration(String logicalCameraId,
            List<String> physicalCameraIds, Size yuvSize) throws Exception {
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        List<ImageReader> imageReaders = new ArrayList<>();
        if (physicalCameraIds.size() == 0) {
            ImageReader yuvTarget = CameraTestUtils.makeImageReader(yuvSize,
                    ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                    new ImageDropperListener(), mHandler);
            imageReaders.add(yuvTarget);
            OutputConfiguration config = new OutputConfiguration(yuvTarget.getSurface());
            outputConfigs.add(new OutputConfiguration(yuvTarget.getSurface()));
        } else {
            for (String physicalCameraId : physicalCameraIds) {
                ImageReader yuvTarget = CameraTestUtils.makeImageReader(yuvSize,
                        ImageFormat.YUV_420_888, MAX_IMAGE_COUNT,
                        new ImageDropperListener(), mHandler);
                OutputConfiguration config = new OutputConfiguration(yuvTarget.getSurface());
                config.setPhysicalCameraId(physicalCameraId);
                outputConfigs.add(config);
                imageReaders.add(yuvTarget);
            }
        }

        // Stream YUV size and note down the FPS
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        for (OutputConfiguration c : outputConfigs) {
            requestBuilder.addTarget(c.getSurface());
        }

        mSessionListener = new BlockingSessionCallback();
        mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                mSessionListener, mHandler);

        SimpleCaptureCallback simpleResultListener =
                new SimpleCaptureCallback();
        StreamConfigurationMap config = mStaticInfo.getCharacteristics().get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final long minFrameDuration = config.getOutputMinFrameDuration(
                ImageFormat.YUV_420_888, yuvSize);
        if (minFrameDuration > 0) {
            Range<Integer> targetRange = getSuitableFpsRangeForDuration(logicalCameraId,
                    minFrameDuration);
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetRange);
        }
        mSession.setRepeatingRequest(requestBuilder.build(),
                simpleResultListener, mHandler);

        // Converge AE
        waitForAeStable(simpleResultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

        if (mStaticInfo.isAeLockSupported()) {
            // Lock AE if supported.
            requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            mSession.setRepeatingRequest(requestBuilder.build(), simpleResultListener,
                    mHandler);
            waitForResultValue(simpleResultListener, CaptureResult.CONTROL_AE_STATE,
                    CaptureResult.CONTROL_AE_STATE_LOCKED, NUM_RESULTS_WAIT_TIMEOUT);
        }

        long prevTimestamp = -1;
        double[] frameDurationMs = new double[NUM_FRAMES_CHECKED-1];
        for (int i = 0; i < NUM_FRAMES_CHECKED; i++) {
            CaptureResult captureResult =
                    simpleResultListener.getCaptureResult(
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
            long timestamp = captureResult.get(
                    CaptureResult.SENSOR_TIMESTAMP);
            if (prevTimestamp != -1) {
                frameDurationMs[i-1] = (double)(timestamp - prevTimestamp)/NS_PER_MS;
            }
            prevTimestamp = timestamp;
        }
        double avgDurationMs = Stat.getAverage(frameDurationMs);
        if (VERBOSE) {
            Log.v(TAG, "average Duration is " + avgDurationMs + " ms");
        }

        // Stop preview
        if (mSession != null) {
            mSession.close();
        }

        return avgDurationMs;
    }
}
