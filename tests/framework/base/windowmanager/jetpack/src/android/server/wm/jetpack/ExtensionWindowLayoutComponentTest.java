/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.jetpack;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.server.wm.jetpack.utils.ExtensionUtil.assertEqualWindowLayoutInfo;
import static android.server.wm.jetpack.utils.ExtensionUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.utils.ExtensionUtil.assumeHasDisplayFeatures;
import static android.server.wm.jetpack.utils.ExtensionUtil.getExtensionWindowLayoutComponent;
import static android.server.wm.jetpack.utils.ExtensionUtil.getExtensionWindowLayoutInfo;
import static android.server.wm.jetpack.utils.SidecarUtil.assumeSidecarSupportedDevice;
import static android.server.wm.jetpack.utils.SidecarUtil.getSidecarInterface;

import static androidx.window.extensions.layout.FoldingFeature.STATE_FLAT;
import static androidx.window.extensions.layout.FoldingFeature.STATE_HALF_OPENED;
import static androidx.window.extensions.layout.FoldingFeature.TYPE_FOLD;
import static androidx.window.extensions.layout.FoldingFeature.TYPE_HINGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import android.graphics.Rect;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.server.wm.jetpack.utils.TestValueCountConsumer;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.window.extensions.layout.DisplayFeature;
import androidx.window.extensions.layout.FoldingFeature;
import androidx.window.extensions.layout.WindowLayoutComponent;
import androidx.window.extensions.layout.WindowLayoutInfo;
import androidx.window.sidecar.SidecarDisplayFeature;
import androidx.window.sidecar.SidecarInterface;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Tests for the {@link androidx.window.extensions.layout.WindowLayoutComponent} implementation
 * provided on the device (and only if one is available).
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ExtensionWindowLayoutComponentTest
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ExtensionWindowLayoutComponentTest extends WindowManagerJetpackTestBase {

    private TestActivity mActivity;
    private WindowLayoutComponent mWindowLayoutComponent;
    private WindowLayoutInfo mWindowLayoutInfo;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        assumeExtensionSupportedDevice();
        mActivity = (TestActivity) startActivityNewTask(TestActivity.class);
        mWindowLayoutComponent = getExtensionWindowLayoutComponent();
        assumeNotNull(mWindowLayoutComponent);
    }

    /**
     * Test adding and removing a window layout change listener.
     */
    @Test
    public void testWindowLayoutComponent_onWindowLayoutChangeListener() throws Exception {
        // Set activity to portrait
        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_PORTRAIT);

        // Create the callback, onWindowLayoutChanged should only be called twice in this
        // test, not the third time when the orientation will change because the listener will be
        // removed.
        TestValueCountConsumer<WindowLayoutInfo> windowLayoutInfoConsumer =
                new TestValueCountConsumer<>();
        windowLayoutInfoConsumer.setCount(2);

        // Add window layout listener for mWindowToken - onWindowLayoutChanged should be called
        mWindowLayoutComponent.addWindowLayoutInfoListener(mActivity, windowLayoutInfoConsumer);

        // Change the activity orientation - onWindowLayoutChanged should be called
        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_LANDSCAPE);

        // Remove the listener
        mWindowLayoutComponent.removeWindowLayoutInfoListener(windowLayoutInfoConsumer);

        // Change the activity orientation - onWindowLayoutChanged should NOT be called
        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_PORTRAIT);

        // Check that the countdown is zero
        WindowLayoutInfo lastValue = windowLayoutInfoConsumer.waitAndGet();
        assertNotNull(lastValue);
    }

    @Test
    public void testWindowLayoutComponent_WindowLayoutInfoListener() {
        TestValueCountConsumer<WindowLayoutInfo> windowLayoutInfoConsumer =
                new TestValueCountConsumer<>();
        // Test that adding and removing callback succeeds
        mWindowLayoutComponent.addWindowLayoutInfoListener(mActivity, windowLayoutInfoConsumer);
        mWindowLayoutComponent.removeWindowLayoutInfoListener(windowLayoutInfoConsumer);
    }

    @Test
    public void testDisplayFeatures()
            throws ExecutionException, InterruptedException, TimeoutException {
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);
        for (DisplayFeature displayFeature : mWindowLayoutInfo.getDisplayFeatures()) {
            // Check that the feature bounds are valid
            final Rect featureRect = displayFeature.getBounds();
            // Feature cannot have negative width or height
            assertHasNonNegativeDimensions(featureRect);
            // The feature cannot have zero area
            assertNotBothDimensionsZero(featureRect);
            // The feature cannot be outside the activity bounds
            assertTrue(getActivityBounds(mActivity).contains(featureRect));

            if (displayFeature instanceof FoldingFeature) {
                // Check that the folding feature has a valid type and state
                final FoldingFeature foldingFeature = (FoldingFeature) displayFeature;
                final int featureType = foldingFeature.getType();
                assertThat(featureType).isIn(Range.range(
                        TYPE_FOLD, BoundType.CLOSED,
                        TYPE_HINGE, BoundType.CLOSED));
                final int featureState = foldingFeature.getState();
                assertThat(featureState).isIn(Range.range(
                        STATE_FLAT, BoundType.CLOSED,
                        STATE_HALF_OPENED, BoundType.CLOSED));
            }
        }
    }

    @Test
    public void testGetWindowLayoutInfo_configChanged_windowLayoutUpdates()
            throws ExecutionException, InterruptedException, TimeoutException {
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);

        TestConfigChangeHandlingActivity configHandlingActivity
                = (TestConfigChangeHandlingActivity) startActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        setActivityOrientationActivityHandlesOrientationChanges(configHandlingActivity,
                ORIENTATION_PORTRAIT);
        final WindowLayoutInfo portraitWindowLayoutInfo = getExtensionWindowLayoutInfo(
                configHandlingActivity);
        final Rect portraitBounds = getActivityBounds(configHandlingActivity);
        final Rect portraitMaximumBounds = getMaximumActivityBounds(configHandlingActivity);

        setActivityOrientationActivityHandlesOrientationChanges(configHandlingActivity,
                ORIENTATION_LANDSCAPE);
        final WindowLayoutInfo landscapeWindowLayoutInfo = getExtensionWindowLayoutInfo(
                configHandlingActivity);
        final Rect landscapeBounds = getActivityBounds(configHandlingActivity);
        final Rect landscapeMaximumBounds = getMaximumActivityBounds(configHandlingActivity);

        final boolean doesDisplayRotateForOrientation = doesDisplayRotateForOrientation(
                portraitMaximumBounds, landscapeMaximumBounds);
        assertEqualWindowLayoutInfo(portraitWindowLayoutInfo, landscapeWindowLayoutInfo,
                portraitBounds, landscapeBounds, doesDisplayRotateForOrientation);
    }

    @Test
    public void testGetWindowLayoutInfo_windowRecreated_windowLayoutUpdates()
            throws ExecutionException, InterruptedException, TimeoutException {
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);

        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_PORTRAIT);
        final WindowLayoutInfo portraitWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        final Rect portraitBounds = getActivityBounds(mActivity);
        final Rect portraitMaximumBounds = getMaximumActivityBounds(mActivity);

        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_LANDSCAPE);
        final WindowLayoutInfo landscapeWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        final Rect landscapeBounds = getActivityBounds(mActivity);
        final Rect landscapeMaximumBounds = getMaximumActivityBounds(mActivity);

        final boolean doesDisplayRotateForOrientation = doesDisplayRotateForOrientation(
                portraitMaximumBounds, landscapeMaximumBounds);
        assertEqualWindowLayoutInfo(portraitWindowLayoutInfo, landscapeWindowLayoutInfo,
                portraitBounds, landscapeBounds, doesDisplayRotateForOrientation);
    }

    /**
     * Tests that if sidecar is also present, then it returns the same display features as
     * extensions.
     */
    @Test
    public void testSidecarHasSameDisplayFeatures()
            throws ExecutionException, InterruptedException, TimeoutException {
        assumeSidecarSupportedDevice(mActivity);
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);

        // Retrieve and sort the extension folding features
        final List<FoldingFeature> extensionFoldingFeatures = new ArrayList<>(
                mWindowLayoutInfo.getDisplayFeatures())
                .stream()
                .filter(d -> d instanceof FoldingFeature)
                .map(d -> (FoldingFeature) d)
                .collect(Collectors.toList());

        // Retrieve and sort the sidecar display features in the same order as the extension
        // display features
        final SidecarInterface sidecarInterface = getSidecarInterface(mActivity);
        final List<SidecarDisplayFeature> sidecarDisplayFeatures = sidecarInterface
                .getWindowLayoutInfo(getActivityWindowToken(mActivity)).displayFeatures;

        // Check that the display features are the same
        assertEquals(extensionFoldingFeatures.size(), sidecarDisplayFeatures.size());
        final int nFeatures = extensionFoldingFeatures.size();
        if (nFeatures == 0) {
            return;
        }
        final boolean[] extensionDisplayFeatureMatched = new boolean[nFeatures];
        final boolean[] sidecarDisplayFeatureMatched = new boolean[nFeatures];
        for (int extensionIndex = 0; extensionIndex < nFeatures; extensionIndex++) {
            if (extensionDisplayFeatureMatched[extensionIndex]) {
                // A match has already been found for this extension folding feature
                continue;
            }
            final FoldingFeature extensionFoldingFeature = extensionFoldingFeatures
                    .get(extensionIndex);
            for (int sidecarIndex = 0; sidecarIndex < nFeatures; sidecarIndex++) {
                if (sidecarDisplayFeatureMatched[sidecarIndex]) {
                    // A match has already been found for this sidecar display feature
                    continue;
                }
                final SidecarDisplayFeature sidecarDisplayFeature = sidecarDisplayFeatures
                        .get(sidecarIndex);
                // Check that the bounds, type, and state match
                if (extensionFoldingFeature.getBounds().equals(sidecarDisplayFeature.getRect())
                        && extensionFoldingFeature.getType() == sidecarDisplayFeature.getType()
                        && areExtensionAndSidecarDeviceStateEqual(
                                extensionFoldingFeature.getState(),
                                sidecarInterface.getDeviceState().posture)) {
                    // Match found
                    extensionDisplayFeatureMatched[extensionIndex] = true;
                    sidecarDisplayFeatureMatched[sidecarIndex] = true;
                }
            }
        }

        // Check that a match was found for each display feature
        for (int i = 0; i < nFeatures; i++) {
            assertTrue(extensionDisplayFeatureMatched[i] && sidecarDisplayFeatureMatched[i]);
        }
    }

    /**
     * Tests that the public API for {@link DisplayFeature} matches the API
     * provided on device. The window-extensions artifact is provided by OEMs
     * and may not match the API defined in the androidx repository.
     */
    @Test
    public void testDisplayFeature_publicApi() throws NoSuchMethodException {
        Class<DisplayFeature> displayFeatureClass = DisplayFeature.class;

        /* DisplayFeature Method Validation */
        validateClassInfo(displayFeatureClass,
                "getBounds", new Class<?>[]{}, Rect.class);
    }

    /**
     * Tests that the public API of {@link FoldingFeature} matches the implementation provided.
     */
    @Test
    public void testFoldingFeature_publicApi() throws NoSuchMethodException {
        Class<DisplayFeature> displayFeatureClass = DisplayFeature.class;
        Class<FoldingFeature> foldingFeatureClass = FoldingFeature.class;
        // Assert Instance Hierarchy. Reason: OEMs should not change the class hierarchy.
        assertTrue(displayFeatureClass.isAssignableFrom(foldingFeatureClass));

        // Validate the signature of public methods in FoldingFeature
        validateClassInfo(foldingFeatureClass,
                "toString", new Class<?>[]{}, String.class);
        validateClassInfo(foldingFeatureClass,
                "equals", new Class<?>[]{Object.class}, boolean.class);
        validateClassInfo(foldingFeatureClass,
                "hashCode", new Class<?>[]{}, int.class);

        // Create a FoldingFeature instance with any constructor (just checking it works).
        final int foldType = TYPE_FOLD;
        final int foldState = STATE_FLAT;
        final Rect foldBoundaries = new Rect(0, 1, 1, 0);
        FoldingFeature foldingFeatureInstance =
                new FoldingFeature(foldBoundaries, foldType, foldState);

        /* FoldingFeature Instance Validation */
        assertEquals(foldBoundaries, foldingFeatureInstance.getBounds());
        assertEquals(foldType, foldingFeatureInstance.getType());
        assertEquals(foldState, foldingFeatureInstance.getState());
    }

    /**
     * Tests that the public API of {@link WindowLayoutInfo} matches the implementation provided.
     */
    @Test
    public void testWindowLayoutInfo_publicApi() throws NoSuchMethodException {
        // Create a FoldingFeature that will be added to WindowLayoutInfo as a DisplayFeature
        final Rect foldBoundaries = new Rect(0, 1, 1, 0);
        FoldingFeature foldingFeature = new FoldingFeature(foldBoundaries, TYPE_FOLD, STATE_FLAT);
        // Add the FoldingFeature to a list of DisplayFeatures
        List<DisplayFeature> displayFeatures = new ArrayList<>();
        displayFeatures.add(foldingFeature);
        // Create a WindowLayoutInfo that holds the DisplayFeatures
        WindowLayoutInfo windowLayoutInfo = new WindowLayoutInfo(displayFeatures);

        /* WindowLayoutInfo Instance Validation */
        assertEquals(displayFeatures, windowLayoutInfo.getDisplayFeatures());

        Class<WindowLayoutInfo> windowLayoutInfoClass = WindowLayoutInfo.class;
        // Validate the signature of public methods in WindowLayoutInfo
        validateClassInfo(windowLayoutInfoClass,
                "toString", new Class<?>[]{}, String.class);
        validateClassInfo(windowLayoutInfoClass,
                "equals", new Class<?>[]{Object.class}, boolean.class);
        validateClassInfo(windowLayoutInfoClass,
                "hashCode", new Class<?>[]{}, int.class);
    }

    /**
     * Verifies that the inputClass has the expected access modifiers, function name,
     * input parameters, and return type. Fails if any are not what is expected.
     * @param inputClass: The Class in Question; Type: Class.
     * @param methodName: The Name of the Method to Check; Type: String.
     * @param parameterList: A list of classes representing the input parameter's Data Types.
     * @param returnType: The return type of the method; Type: Class.
     * @throws NoSuchMethodException: If No Class found, throws a NoSuchMethodException error.
     */
    public void validateClassInfo(Class<?> inputClass, String methodName, Class<?>[] parameterList,
            Class<?> returnType) throws NoSuchMethodException {
        // Get the specified method from the class.
        // Fails if it cannot find the methodName with the Correct input parameters.
        Method classMethod = inputClass.getMethod(methodName, parameterList);

        assertEquals(returnType, classMethod.getReturnType());
        // This should not fail if getMethod did not fail.
        assertTrue(Modifier.isPublic(classMethod.getModifiers()));
    }
}
