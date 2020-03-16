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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityTestActivity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests that AccessibilityNodeInfos from an embedded hierarchy that is present to another
 * hierarchy are properly populated.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityEmbeddedHierarchyTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private final ActivityTestRule<AccessibilityEmbeddedHierarchyActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityEmbeddedHierarchyActivity.class, false, false);

    private static final String HOST_VIEW_RESOURCE_NAME =
            "android.accessibilityservice.cts:id/host_surfaceview";
    private static final String EMBEDDED_VIEW_RESOURCE_NAME =
            "android.accessibilityservice.cts:id/embedded_button";

    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private AccessibilityEmbeddedHierarchyActivity mActivity;

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Throwable {
        mActivity = launchActivityAndWaitForItToBeOnscreen(sInstrumentation, sUiAutomation,
                mActivityRule);
        mActivity.waitForEmbeddedHierarchy();
    }

    @Test
    public void testEmbeddedViewCanBeFound() {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        assertNotNull(target);
    }

    @Test
    public void testEmbeddedViewCanFindItsHostParent() {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        final AccessibilityNodeInfo parent = target.getParent();
        assertTrue(HOST_VIEW_RESOURCE_NAME.equals(parent.getViewIdResourceName()));
    }

    @Test
    public void testEmbeddedViewHasCorrectBound() {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        final AccessibilityNodeInfo parent = target.getParent();

        final Rect hostViewBoundsInScreen = new Rect();
        final Rect embeddedViewBoundsInScreen = new Rect();
        parent.getBoundsInScreen(hostViewBoundsInScreen);
        target.getBoundsInScreen(embeddedViewBoundsInScreen);

        assertTrue("hostViewBoundsInScreen" + hostViewBoundsInScreen.toShortString()
                        + " doesn't contain embeddedViewBoundsInScreen"
                        + embeddedViewBoundsInScreen.toShortString(),
                hostViewBoundsInScreen.contains(embeddedViewBoundsInScreen));
    }

    @Test
    public void testEmbeddedViewHasCorrectBoundAfterHostViewMove() {
        final AccessibilityNodeInfo target =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());

        final Rect hostViewBoundsInScreen = new Rect();
        final Rect newEmbeddedViewBoundsInScreen = new Rect();
        final Rect oldEmbeddedViewBoundsInScreen = new Rect();
        target.getBoundsInScreen(oldEmbeddedViewBoundsInScreen);

        // Move Host SurfaceView from (0, 0) to (100, 100).
        mActivity.requestNewLayoutForTest();

        final AccessibilityNodeInfo newTarget =
                findEmbeddedAccessibilityNodeInfo(sUiAutomation.getRootInActiveWindow());
        final AccessibilityNodeInfo parent = newTarget.getParent();

        newTarget.getBoundsInScreen(newEmbeddedViewBoundsInScreen);
        parent.getBoundsInScreen(hostViewBoundsInScreen);

        assertTrue("hostViewBoundsInScreen" + hostViewBoundsInScreen.toShortString()
                        + " doesn't contain newEmbeddedViewBoundsInScreen"
                        + newEmbeddedViewBoundsInScreen.toShortString(),
                hostViewBoundsInScreen.contains(newEmbeddedViewBoundsInScreen));
        assertFalse("newEmbeddedViewBoundsInScreen" + newEmbeddedViewBoundsInScreen.toShortString()
                        + " shouldn't be the same with oldEmbeddedViewBoundsInScreen"
                        + oldEmbeddedViewBoundsInScreen.toShortString(),
                newEmbeddedViewBoundsInScreen.equals(oldEmbeddedViewBoundsInScreen));
    }

    private AccessibilityNodeInfo findEmbeddedAccessibilityNodeInfo(AccessibilityNodeInfo root) {
        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final AccessibilityNodeInfo info = root.getChild(i);
            if (info == null) {
                continue;
            }
            if (EMBEDDED_VIEW_RESOURCE_NAME.equals(info.getViewIdResourceName())) {
                return info;
            }
            if (info.getChildCount() != 0) {
                return findEmbeddedAccessibilityNodeInfo(info);
            }
        }
        return null;
    }

    /**
     * This class is an dummy {@link android.app.Activity} used to perform embedded hierarchy
     * testing of the accessibility feature by interaction with the UI widgets.
     */
    public static class AccessibilityEmbeddedHierarchyActivity extends
            AccessibilityTestActivity implements SurfaceHolder.Callback {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        private static final int DEFAULT_WIDTH = 150;
        private static final int DEFAULT_HEIGHT = 150;

        private static final int POSITION_X = 50;
        private static final int POSITION_Y = 50;

        private SurfaceView mSurfaceView;
        private SurfaceControlViewHost mViewHost;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.accessibility_embedded_hierarchy_test_host_side);
            mSurfaceView = findViewById(R.id.host_surfaceview);
            mSurfaceView.getHolder().addCallback(this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mViewHost = new SurfaceControlViewHost(this, this.getDisplay(),
                    mSurfaceView.getHostToken());

            mSurfaceView.setChildSurfacePackage(mViewHost.getSurfacePackage());

            View layout = getLayoutInflater().inflate(
                    R.layout.accessibility_embedded_hierarchy_test_embedded_side, null);
            mViewHost.setView(layout, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            mCountDownLatch.countDown();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // No-op
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // No-op
        }

        public void waitForEmbeddedHierarchy() {
            try {
                assertTrue("timed out waiting for embedded hierarchy to init.",
                        mCountDownLatch.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        public void requestNewLayoutForTest() {
            sInstrumentation.runOnMainSync(() -> {
                mSurfaceView.setX(POSITION_X);
                mSurfaceView.setY(POSITION_Y);
                mSurfaceView.requestLayout();
            });
        }
    }
}
