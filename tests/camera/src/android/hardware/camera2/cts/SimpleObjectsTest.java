/*
 * Copyright 2019 The Android Open Source Project
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

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.Capability;
import android.hardware.camera2.params.DeviceStateSensorOrientationMap;
import android.hardware.camera2.params.Face;
import android.util.Range;
import android.util.Size;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test CaptureRequest/Result/CameraCharacteristics.Key objects.
 */
@RunWith(AndroidJUnit4.class)
@SuppressWarnings("EqualsIncompatibleType")
public class SimpleObjectsTest {

    @Test
    public void cameraKeysTest() throws Exception {
        String keyName = "android.testing.Key";
        String keyName2 = "android.testing.Key2";

        CaptureRequest.Key<Integer> testRequestKey =
                new CaptureRequest.Key<>(keyName, Integer.class);
        Assert.assertEquals("Request key name not correct",
                testRequestKey.getName(), keyName);

        CaptureResult.Key<Integer> testResultKey =
                new CaptureResult.Key<>(keyName, Integer.class);
        Assert.assertEquals("Result key name not correct",
                testResultKey.getName(), keyName);

        CameraCharacteristics.Key<Integer> testCharacteristicsKey =
                new CameraCharacteristics.Key<>(keyName, Integer.class);
        Assert.assertEquals("Characteristics key name not correct",
                testCharacteristicsKey.getName(), keyName);

        CaptureRequest.Key<Integer> testRequestKey2 =
                new CaptureRequest.Key<>(keyName, Integer.class);
        Assert.assertEquals("Two request keys with same name/type should be equal",
                testRequestKey, testRequestKey2);
        CaptureRequest.Key<Byte> testRequestKey3 =
                new CaptureRequest.Key<>(keyName, Byte.class);
        Assert.assertTrue("Two request keys with different types should not be equal",
                !testRequestKey.equals(testRequestKey3));
        CaptureRequest.Key<Integer> testRequestKey4 =
                new CaptureRequest.Key<>(keyName2, Integer.class);
        Assert.assertTrue("Two request keys with different names should not be equal",
                !testRequestKey.equals(testRequestKey4));
        CaptureRequest.Key<Byte> testRequestKey5 =
                new CaptureRequest.Key<>(keyName2, Byte.class);
        Assert.assertTrue("Two request keys with different types and names should not be equal",
                !testRequestKey.equals(testRequestKey5));

        CaptureResult.Key<Integer> testResultKey2 =
                new CaptureResult.Key<>(keyName, Integer.class);
        Assert.assertEquals("Two result keys with same name/type should be equal",
                testResultKey, testResultKey2);
        CaptureResult.Key<Byte> testResultKey3 =
                new CaptureResult.Key<>(keyName, Byte.class);
        Assert.assertTrue("Two result keys with different types should not be equal",
                !testResultKey.equals(testResultKey3));
        CaptureResult.Key<Integer> testResultKey4 =
                new CaptureResult.Key<>(keyName2, Integer.class);
        Assert.assertTrue("Two result keys with different names should not be equal",
                !testResultKey.equals(testResultKey4));
        CaptureResult.Key<Byte> testResultKey5 =
                new CaptureResult.Key<>(keyName2, Byte.class);
        Assert.assertTrue("Two result keys with different types and names should not be equal",
                !testResultKey.equals(testResultKey5));

        CameraCharacteristics.Key<Integer> testCharacteristicsKey2 =
                new CameraCharacteristics.Key<>(keyName, Integer.class);
        Assert.assertEquals("Two characteristics keys with same name/type should be equal",
                testCharacteristicsKey, testCharacteristicsKey2);
        CameraCharacteristics.Key<Byte> testCharacteristicsKey3 =
                new CameraCharacteristics.Key<>(keyName, Byte.class);
        Assert.assertTrue("Two characteristics keys with different types should not be equal",
                !testCharacteristicsKey.equals(testCharacteristicsKey3));
        CameraCharacteristics.Key<Integer> testCharacteristicsKey4 =
                new CameraCharacteristics.Key<>(keyName2, Integer.class);
        Assert.assertTrue("Two characteristics keys with different names should not be equal",
                !testCharacteristicsKey.equals(testCharacteristicsKey4));
        CameraCharacteristics.Key<Byte> testCharacteristicsKey5 =
                new CameraCharacteristics.Key<>(keyName2, Byte.class);
        Assert.assertTrue(
                "Two characteristics keys with different types and names should not be equal",
                !testCharacteristicsKey.equals(testCharacteristicsKey5));

    }

    @Test
    public void blackLevelPatternConstructionTest() {
        int[] offsets = { 5, 5, 5, 5 };
        BlackLevelPattern blackLevelPattern = new BlackLevelPattern(offsets);

        try {
            blackLevelPattern = new BlackLevelPattern(/*offsets*/ null);
            Assert.fail("BlackLevelPattern did not throw a NullPointerException for null offsets");
        } catch (NullPointerException e) {
            // Do nothing
        }

        try {
            int[] badOffsets = { 5 };
            blackLevelPattern = new BlackLevelPattern(badOffsets);
            Assert.fail("BlackLevelPattern did not throw an IllegalArgumentException for incorrect"
                    + " offsets length");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

    @Test
    public void capabilityConstructionTest() {
        Capability capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, 128),
                /*zoomRatioRange*/ new Range<Float>(0.2f, 2.0f));

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(-1, 128),
                    /*zoomRatioRange*/ new Range<Float>(0.2f, 2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for negative "
                    + "maxStreamingWidth");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, -1),
                     /*zoomRatioRange*/ new Range<Float>(0.2f, 2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for negative "
                    + "maxStreamingHeight");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, 128),
                    /*zoomRatioRange*/ new Range<Float>(-3.0f, -2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for negative "
                    + "zoom ratios");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            capability = new Capability(/*mode*/ 0, /*maxStreamingSize*/ new Size(128, 128),
                    /*zoomRatioRange*/ new Range<Float>(3.0f, 2.0f));
            Assert.fail("Capability did not throw an IllegalArgumentException for minZoomRatio "
                    + "> maxZoomRatio");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

    @Test
    public void deviceStateSensorOrientationMapConstructionTest() {
        DeviceStateSensorOrientationMap.Builder builder =
                new DeviceStateSensorOrientationMap.Builder();
        builder.addOrientationForState(/*deviceState*/ 0, /*angle*/ 90);
        builder.addOrientationForState(/*deviceState*/ 1, /*angle*/ 180);
        builder.addOrientationForState(/*deviceState*/ 2, /*angle*/ 270);
        DeviceStateSensorOrientationMap deviceStateSensorOrientationMap = builder.build();

        try {
            builder = new DeviceStateSensorOrientationMap.Builder();
            deviceStateSensorOrientationMap = builder.build();
            Assert.fail("DeviceStateSensorOrientationMap did not throw an IllegalStateException for"
                    + " zero elements");
        } catch (IllegalStateException e) {
            // Do nothing
        }

        try {
            builder = new DeviceStateSensorOrientationMap.Builder();
            builder.addOrientationForState(/*deviceState*/ 0, 55);
            deviceStateSensorOrientationMap = builder.build();
            Assert.fail("DeviceStateSensorOrientationMap did not throw an IllegalArgumentException "
                    + "for incorrect elements values");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

    @Test
    public void faceConstructionTest() {
        Rect bounds = new Rect();
        int score = 50;
        int id = 1;
        Point leftEyePosition = new Point();
        Point rightEyePosition = new Point();
        Point mouthPosition = new Point();
        Face face = new Face(bounds, score, id, leftEyePosition, rightEyePosition, mouthPosition);
        face = new Face(bounds, score);

        try {
            face = new Face(/*bounds*/ null, score, id, leftEyePosition, rightEyePosition,
                    mouthPosition);
            Assert.fail("Face did not throw an IllegalArgumentException for null bounds");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            face = new Face(bounds, /*score*/ Face.SCORE_MIN - 1, id, leftEyePosition,
                    rightEyePosition, mouthPosition);
            Assert.fail("Face did not throw an IllegalArgumentException for score below range");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            face = new Face(bounds, /*score*/ Face.SCORE_MAX + 1, id, leftEyePosition,
                    rightEyePosition, mouthPosition);
            Assert.fail("Face did not throw an IllegalArgumentException for score above range");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }

        try {
            face = new Face(bounds, score, /*id*/ Face.ID_UNSUPPORTED, leftEyePosition,
                    rightEyePosition, mouthPosition);
            Assert.fail("Face did not throw an IllegalArgumentException for non-null positions when"
                    + " id is ID_UNSUPPORTED.");
        } catch (IllegalArgumentException e) {
            // Do nothing
        }
    }

}
