/*
 * iCopyright 2021 The Android Open Source Project
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

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.MultiResolutionImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.MultiResolutionStreamConfigurationMap;
import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import org.junit.Test;

/**
 * Tests for multi-resolution size reprocessing.
 */

@RunWith(Parameterized.class)
public class MultiResolutionReprocessCaptureTest extends Camera2AndroidTestCase  {
    private static final String TAG = "MultiResolutionReprocessCaptureTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int CAPTURE_TIMEOUT_FRAMES = 100;
    private static final int CAPTURE_TIMEOUT_MS = 3000;
    private static final int WAIT_FOR_SURFACE_CHANGE_TIMEOUT_MS = 1000;
    private static final int CAPTURE_TEMPLATE = CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG;
    private int mDumpFrameCount = 0;

    // The image reader for the regular captures
    private MultiResolutionImageReader mMultiResImageReader;
    // The image reader for the reprocess capture
    private MultiResolutionImageReader mSecondMultiResImageReader;
    // A flag indicating whether the regular capture and the reprocess capture share the same
    // multi-resolution image reader. If it's true, the mMultiResImageReader should be used for
    // both regular and reprocess outputs.
    private boolean mShareOneReader;
    private SimpleMultiResolutionImageReaderListener mMultiResImageReaderListener;
    private SimpleMultiResolutionImageReaderListener mSecondMultiResImageReaderListener;
    private Surface mInputSurface;
    private ImageWriter mImageWriter;
    private SimpleImageWriterListener mImageWriterListener;

    @Test
    public void testMultiResolutionReprocessCharacteristics() {
        for (String id : mCameraIdsUnderTest) {
            if (VERBOSE) {
                Log.v(TAG, "Testing multi-resolution reprocess characteristics for Camera " + id);
            }
            StaticMetadata info = mAllStaticInfo.get(id);
            CameraCharacteristics c = info.getCharacteristics();
            StreamConfigurationMap config = c.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int[] inputFormats = config.getInputFormats();
            int[] capabilities = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean isLogicalCamera = CameraTestUtils.contains(capabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
            Set<String> physicalCameraIds = c.getPhysicalCameraIds();

            MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                    CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
            if (multiResolutionMap == null) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution reprocessing.");
                continue;
            }
            if (VERBOSE) {
                Log.v(TAG, "MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP: "
                        + multiResolutionMap.toString());
            }

            // Find multi-resolution input and output formats
            int[] multiResolutionInputFormats = multiResolutionMap.getInputFormats();
            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();

            //TODO: Handle ultra high resolution sensor camera
            assertTrue("Camera " + id + " must be a logical multi-camera "
                    + "to support multi-resolution reprocessing.", isLogicalCamera);

            for (int format : multiResolutionInputFormats) {
                assertTrue(String.format("Camera %s: multi-resolution input format %d "
                        + "isn't a supported format", id, format),
                        CameraTestUtils.contains(inputFormats, format));

                Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                        multiResolutionMap.getInputInfo(format);
                assertTrue(String.format("Camera %s supports %d multi-resolution "
                        + "input stream info, expected at least 2", id,
                        multiResolutionStreams.size()),
                        multiResolutionStreams.size() >= 2);

                // Make sure that each multi-resolution input stream info has the maximum size
                // for that format.
                for (MultiResolutionStreamInfo streamInfo : multiResolutionStreams) {
                    String physicalCameraId = streamInfo.getPhysicalCameraId();
                    int width = streamInfo.getWidth();
                    int height = streamInfo.getHeight();
                    assertTrue("Camera " + id + "'s multi-resolution input info "
                            + "physical camera id " + physicalCameraId + "isn't valid",
                            physicalCameraIds.contains(physicalCameraId));

                    StaticMetadata pInfo = mAllStaticInfo.get(physicalCameraId);
                    CameraCharacteristics pChar = pInfo.getCharacteristics();
                    StreamConfigurationMap pConfig = pChar.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] sizes = pConfig.getInputSizes(format);

                    assertTrue(String.format("Camera %s physical camera %s must "
                            + "support at least one input size for multi-resolution input "
                            + "format %d.", id, physicalCameraId, format),
                             sizes != null && sizes.length > 0);

                    Size maxSize = CameraTestUtils.getMaxSize(sizes);
                    assertTrue(String.format("Camera %s's supported multi-resolution"
                           + " input size [%d, %d] for physical camera %s is not the largest "
                           + "supported input size [%d, %d] for format %d", id, width, height,
                           physicalCameraId, maxSize.getWidth(), maxSize.getHeight(), format),
                           width == maxSize.getWidth() && height == maxSize.getHeight());
                }
            }

            // YUV reprocessing capabilities check
            if (CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.YUV_422_888) &&
                    CameraTestUtils.contains(multiResolutionInputFormats,
                    ImageFormat.YUV_420_888)) {
                assertTrue("The camera device must have YUV_REPROCESSING capability if it "
                        + "supports multi-resolution YUV input and YUV output",
                        CameraTestUtils.contains(capabilities,
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING));

                assertTrue("The camera device must supports multi-resolution JPEG output if "
                        + "supports multi-resolution YUV input and YUV output",
                        CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.JPEG));
            }

            // OPAQUE reprocessing capabilities check
            if (CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.PRIVATE) &&
                    CameraTestUtils.contains(multiResolutionInputFormats, ImageFormat.PRIVATE)) {
                assertTrue("The camera device must have PRIVATE_REPROCESSING capability if it "
                        + "supports multi-resolution PRIVATE input and PRIVATE output",
                        CameraTestUtils.contains(capabilities,
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING));

                assertTrue("The camera device must supports multi-resolution JPEG output if "
                        + "supports multi-resolution PRIVATE input and PRIVATE output",
                        CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.JPEG));
                assertTrue("The camera device must supports multi-resolution YUV output if "
                        + "supports multi-resolution PRIVATE input and PRIVATE output",
                        CameraTestUtils.contains(multiResolutionOutputFormats,
                        ImageFormat.YUV_420_888));
            }
        }
    }

    /**
     * Test YUV_420_888 -> YUV_420_888 multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionYuvToYuvReprocessing() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            testMultiResolutionReprocessing(id, ImageFormat.YUV_420_888, ImageFormat.YUV_420_888);
        }
    }

    /**
     * Test YUV_420_888 -> JPEG multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionYuvToJpegReprocessing() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            testMultiResolutionReprocessing(id, ImageFormat.YUV_420_888, ImageFormat.JPEG);
        }
    }

    /**
     * Test OPAQUE -> YUV_420_888 multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionOpaqueToYuvReprocessing() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            // Opaque -> YUV_420_888 must be supported.
            testMultiResolutionReprocessing(id, ImageFormat.PRIVATE, ImageFormat.YUV_420_888);
        }
    }

    /**
     * Test OPAQUE -> JPEG multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionOpaqueToJpegReprocessing() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            // OPAQUE -> JPEG must be supported.
            testMultiResolutionReprocessing(id, ImageFormat.PRIVATE, ImageFormat.JPEG);
        }
    }

    /**
     * Test multi-resolution reprocessing from the input format to the output format
     */
    private void testMultiResolutionReprocessing(String cameraId, int inputFormat,
            int outputFormat) throws Exception {
        if (VERBOSE) {
            Log.v(TAG, "testMultiResolutionReprocessing: cameraId: " + cameraId + " inputFormat: "
                    + inputFormat + " outputFormat: " + outputFormat);
        }

        Collection<MultiResolutionStreamInfo> inputStreamInfo =
                getMultiResReprocessInfo(cameraId, inputFormat, /*input*/ true);
        Collection<MultiResolutionStreamInfo> regularOutputStreamInfo =
                getMultiResReprocessInfo(cameraId, inputFormat, /*input*/ false);
        Collection<MultiResolutionStreamInfo> reprocessOutputStreamInfo =
                getMultiResReprocessInfo(cameraId, outputFormat, /*input*/ false);
        if (inputStreamInfo == null || regularOutputStreamInfo == null ||
                reprocessOutputStreamInfo == null) {
            return;
        }
        assertTrue("The multi-resolution stream info for format " + inputFormat
                + " must be equal between input and output",
                inputStreamInfo.containsAll(regularOutputStreamInfo)
                && regularOutputStreamInfo.containsAll(inputStreamInfo));

        try {
            openDevice(cameraId);

            testMultiResolutionReprocessWithStreamInfo(cameraId, inputFormat, inputStreamInfo,
                    outputFormat, reprocessOutputStreamInfo);
        } finally {
            closeDevice(cameraId);
        }
    }

    /**
     * Test multi-resolution reprocess with multi-resolution stream info lists for a particular
     * format combination.
     */
    private void testMultiResolutionReprocessWithStreamInfo(String cameraId,
            int inputFormat, Collection<MultiResolutionStreamInfo> inputInfo,
            int outputFormat, Collection<MultiResolutionStreamInfo> outputInfo)
            throws Exception {
        try {
            setupMultiResImageReaders(inputFormat, inputInfo, outputFormat, outputInfo,
                    /*maxImages*/1);
            setupReprocessableSession(inputFormat, inputInfo, outputInfo,
                    /*numImageWriterImages*/1);

            List<Float> zoomRatioList = CameraTestUtils.getCandidateZoomRatios(mStaticInfo);
            for (Float zoomRatio :  zoomRatioList) {
                ImageResultSizeHolder imageResultSizeHolder = null;

                try {
                    imageResultSizeHolder = doMultiResReprocessCapture(zoomRatio);
                    Image reprocessedImage = imageResultSizeHolder.getImage();
                    Size outputSize = imageResultSizeHolder.getExpectedSize();
                    TotalCaptureResult result = imageResultSizeHolder.getTotalCaptureResult();

                    mCollector.expectImageProperties("testMultiResolutionReprocess",
                            reprocessedImage, outputFormat, outputSize,
                            result.get(CaptureResult.SENSOR_TIMESTAMP));

                    if (DEBUG) {
                        Log.d(TAG, String.format("camera %s %d zoom %f out %dx%d %d",
                                cameraId, inputFormat, zoomRatio,
                                outputSize.getWidth(), outputSize.getHeight(),
                                outputFormat));

                        dumpImage(reprocessedImage,
                                "/testMultiResolutionReprocess_camera" + cameraId
                                + "_" + mDumpFrameCount);
                        mDumpFrameCount++;
                    }
                } finally {
                    if (imageResultSizeHolder != null) {
                        imageResultSizeHolder.getImage().close();
                    }
                }
            }
        } finally {
            closeReprossibleSession();
            closeMultiResImageReaders();
        }
    }

    /**
     * Set up multi-resolution image readers for regular and reprocess output
     *
     * <p>If the reprocess input format is equal to output format, share one multi-resolution
     * image reader.</p>
     */
    private void setupMultiResImageReaders(int inputFormat,
            Collection<MultiResolutionStreamInfo> inputInfo, int outputFormat,
            Collection<MultiResolutionStreamInfo> outputInfo, int maxImages) {

        mShareOneReader = false;
        // If the regular output and reprocess output have the same format,
        // they can share one MultiResolutionImageReader.
        if (inputFormat == outputFormat) {
            maxImages *= 2;
            mShareOneReader = true;
        }

        // create an MultiResolutionImageReader for the regular capture
        mMultiResImageReader = MultiResolutionImageReader.newInstance(inputInfo,
                inputFormat, maxImages);
        mMultiResImageReaderListener = new SimpleMultiResolutionImageReaderListener(
                mMultiResImageReader, 1, /*repeating*/false);
        mMultiResImageReader.setOnImageAvailableListener(mMultiResImageReaderListener,
                new HandlerExecutor(mHandler));

        if (!mShareOneReader) {
            // create an MultiResolutionImageReader for the reprocess capture
            mSecondMultiResImageReader = MultiResolutionImageReader.newInstance(
                    outputInfo, outputFormat, maxImages);
            mSecondMultiResImageReaderListener = new SimpleMultiResolutionImageReaderListener(
                    mSecondMultiResImageReader, maxImages, /*repeating*/ false);
            mSecondMultiResImageReader.setOnImageAvailableListener(
                    mSecondMultiResImageReaderListener, new HandlerExecutor(mHandler));
        }
    }

    /**
     * Close two multi-resolution image readers.
     */
    private void closeMultiResImageReaders() {
        mMultiResImageReader.close();
        mMultiResImageReader = null;

        if (!mShareOneReader) {
            mSecondMultiResImageReader.close();
            mSecondMultiResImageReader = null;
        }
    }

    /**
     * Get the MultiResolutionImageReader for reprocess output.
     */
    private MultiResolutionImageReader getOutputMultiResImageReader() {
        if (mShareOneReader) {
            return mMultiResImageReader;
        } else {
            return mSecondMultiResImageReader;
        }
    }

    /**
     * Get the MultiResolutionImageReaderListener for reprocess output.
     */
    private SimpleMultiResolutionImageReaderListener getOutputMultiResImageReaderListener() {
        if (mShareOneReader) {
            return mMultiResImageReaderListener;
        } else {
            return mSecondMultiResImageReaderListener;
        }
    }

    /**
     * Set up a reprocessable session and create an ImageWriter with the session's input surface.
     */
    private void setupReprocessableSession(int inputFormat,
            Collection<MultiResolutionStreamInfo> inputInfo,
            Collection<MultiResolutionStreamInfo> outputInfo,
            int numImageWriterImages) throws Exception {
        // create a reprocessable capture session
        Collection<OutputConfiguration> outConfigs =
                OutputConfiguration.createInstancesForMultiResolutionOutput(
                        mMultiResImageReader);
        ArrayList<OutputConfiguration> outputConfigsList = new ArrayList<OutputConfiguration>(
                outConfigs);

        if (!mShareOneReader) {
            Collection<OutputConfiguration> secondOutputConfigs =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(
                            mSecondMultiResImageReader);
            outputConfigsList.addAll(secondOutputConfigs);
        }

        InputConfiguration inputConfig = new InputConfiguration(inputInfo, inputFormat);
        if (VERBOSE) {
            String inputConfigString = inputConfig.toString();
            Log.v(TAG, "InputConfiguration: " + inputConfigString);
        }

        mCameraSessionListener = new BlockingSessionCallback();
        mCameraSession = configureReprocessableCameraSessionWithConfigurations(
                mCamera, inputConfig, outputConfigsList, mCameraSessionListener, mHandler);

        // create an ImageWriter
        mInputSurface = mCameraSession.getInputSurface();
        mImageWriter = ImageWriter.newInstance(mInputSurface,
                numImageWriterImages);

        mImageWriterListener = new SimpleImageWriterListener(mImageWriter);
        mImageWriter.setOnImageReleasedListener(mImageWriterListener, mHandler);
    }

    /**
     * Close the reprocessable session and ImageWriter.
     */
    private void closeReprossibleSession() {
        mInputSurface = null;

        if (mCameraSession != null) {
            mCameraSession.close();
            mCameraSession = null;
        }

        if (mImageWriter != null) {
            mImageWriter.close();
            mImageWriter = null;
        }
    }

    /**
     * Do one multi-resolution reprocess capture for the specified zoom ratio
     */
    private ImageResultSizeHolder doMultiResReprocessCapture(float zoomRatio) throws Exception {
        // submit a regular capture and get the result
        TotalCaptureResult totalResult = submitCaptureRequest(
                zoomRatio, mMultiResImageReader.getSurface(), /*inputResult*/null);
        Map<String, TotalCaptureResult> physicalResults =
                totalResult.getPhysicalCameraTotalResults();

        ImageAndMultiResStreamInfo inputImageAndInfo =
                mMultiResImageReaderListener.getAnyImageAndInfoAvailable(CAPTURE_TIMEOUT_MS);
        assertNotNull("Failed to capture input image", inputImageAndInfo);
        Image inputImage = inputImageAndInfo.image;
        MultiResolutionStreamInfo inputStreamInfo = inputImageAndInfo.streamInfo;
        TotalCaptureResult inputSettings =
                physicalResults.get(inputStreamInfo.getPhysicalCameraId());
        assertTrue("Regular capture's TotalCaptureResult doesn't contain capture result for "
                + "physical camera id " + inputStreamInfo.getPhysicalCameraId(),
                inputSettings != null);

        // Submit a reprocess capture and get the result
        mImageWriter.queueInputImage(inputImage);

        TotalCaptureResult finalResult = submitCaptureRequest(zoomRatio,
                getOutputMultiResImageReader().getSurface(), inputSettings);

        ImageAndMultiResStreamInfo outputImageAndInfo =
                getOutputMultiResImageReaderListener().getAnyImageAndInfoAvailable(
                CAPTURE_TIMEOUT_MS);
        Image outputImage = outputImageAndInfo.image;
        MultiResolutionStreamInfo outputStreamInfo = outputImageAndInfo.streamInfo;

        assertTrue("The regular output and reprocess output's stream info must be the same",
                outputStreamInfo.equals(inputStreamInfo));

        ImageResultSizeHolder holder = new ImageResultSizeHolder(outputImageAndInfo.image,
                finalResult, new Size(outputStreamInfo.getWidth(), outputStreamInfo.getHeight()));

        return holder;
    }

    /**
     * Issue a capture request and return the result for a particular zoom ratio.
     *
     * <p>If inputResult is null, it's a regular request. Otherwise, it's a reprocess request.</p>
     */
    private TotalCaptureResult submitCaptureRequest(float zoomRatio,
            Surface output, TotalCaptureResult inputResult) throws Exception {

        SimpleCaptureCallback captureCallback = new SimpleCaptureCallback();

        // Prepare a list of capture requests. Whether it's a regular or reprocess capture request
        // is based on inputResult.
        CaptureRequest.Builder builder;
        boolean isReprocess = (inputResult != null);
        if (isReprocess) {
            builder = mCamera.createReprocessCaptureRequest(inputResult);
        } else {
            builder = mCamera.createCaptureRequest(CAPTURE_TEMPLATE);
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
        }
        builder.addTarget(output);
        CaptureRequest request = builder.build();
        assertTrue("Capture request reprocess type " + request.isReprocess() + " is wrong.",
            request.isReprocess() == isReprocess);

        mCameraSession.capture(request, captureCallback, mHandler);

        TotalCaptureResult result = captureCallback.getTotalCaptureResultForRequest(
                request, CAPTURE_TIMEOUT_FRAMES);

        // make sure all input surfaces are released.
        if (isReprocess) {
            mImageWriterListener.waitForImageReleased(CAPTURE_TIMEOUT_MS);
        }

        return result;
    }

    private Size getMaxSize(int format, StaticMetadata.StreamDirection direction) {
        Size[] sizes = mStaticInfo.getAvailableSizesForFormatChecked(format, direction);
        return getAscendingOrderSizes(Arrays.asList(sizes), /*ascending*/false).get(0);
    }

    private Collection<MultiResolutionStreamInfo> getMultiResReprocessInfo(String cameraId,
            int format, boolean input) throws Exception {
        StaticMetadata staticInfo = mAllStaticInfo.get(cameraId);
        CameraCharacteristics characteristics = staticInfo.getCharacteristics();
        MultiResolutionStreamConfigurationMap configs = characteristics.get(
                CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
        if (configs == null) {
            Log.i(TAG, "Camera " + cameraId + " doesn't support multi-resolution streams");
            return null;
        }

        String streamType = input ? "input" : "output";
        int[] formats = input ? configs.getInputFormats() :
                configs.getOutputFormats();
        if (!CameraTestUtils.contains(formats, format)) {
            Log.i(TAG, "Camera " + cameraId + " doesn't support multi-resolution "
                    + streamType + " stream for format " + format + ". Supported formats are "
                    + Arrays.toString(formats));
            return null;
        }
        Collection<MultiResolutionStreamInfo> streams =
                input ? configs.getInputInfo(format) : configs.getOutputInfo(format);
        mCollector.expectTrue(String.format("Camera %s supported 0 multi-resolution "
                + streamType + " stream info, expected at least 1", cameraId),
                streams.size() > 0);

        return streams;
    }

    private void dumpImage(Image image, String name) {
        String filename = mDebugFileNameBase + name;
        switch(image.getFormat()) {
            case ImageFormat.JPEG:
                filename += ".jpg";
                break;
            case ImageFormat.YUV_420_888:
                filename += ".yuv";
                break;
            default:
                filename += "." + image.getFormat();
                break;
        }

        Log.d(TAG, "dumping an image to " + filename);
        dumpFile(filename , getDataFromImage(image));
    }

    /**
     * A class that holds an Image, a TotalCaptureResult, and expected image size.
     */
    public static class ImageResultSizeHolder {
        private final Image mImage;
        private final TotalCaptureResult mResult;
        private final Size mExpectedSize;

        public ImageResultSizeHolder(Image image, TotalCaptureResult result, Size expectedSize) {
            mImage = image;
            mResult = result;
            mExpectedSize = expectedSize;
        }

        public Image getImage() {
            return mImage;
        }

        public TotalCaptureResult getTotalCaptureResult() {
            return mResult;
        }

        public Size getExpectedSize() {
            return mExpectedSize;
        }
    }

}
