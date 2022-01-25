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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityCacheActivity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.annotations.AppModeFull;
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

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class AccessibilityCacheTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private InstrumentedAccessibilityService mService;
    private AccessibilityCacheActivity mActivity;

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private final ActivityTestRule<AccessibilityCacheActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityCacheActivity.class, false, false);

    private InstrumentedAccessibilityServiceTestRule<InstrumentedAccessibilityService>
            mInstrumentedAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            InstrumentedAccessibilityService.class, false);

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mInstrumentedAccessibilityServiceRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mService = mInstrumentedAccessibilityServiceRule.enableService();
        AccessibilityServiceInfo info = mService.getServiceInfo();
        info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        mService.setServiceInfo(info);
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
    }

    @Test
    public void enable_cacheEnabled() {
        assertTrue(mService.setCacheEnabled(false));
        assertFalse("Failed to disable", mService.isCacheEnabled());

        assertTrue(mService.setCacheEnabled(true));
        assertTrue("Failed to enable", mService.isCacheEnabled());
    }

    @Test
    public void disable_cacheDisabled() {
        assertTrue(mService.setCacheEnabled(false));
        assertFalse("Failed to disable", mService.isCacheEnabled());
    }

    @Test
    public void queryNode_nodeIsInCache() {
        AccessibilityNodeInfo info = mService.getRootInActiveWindow();
        assertTrue("Node is not in cache", mService.isNodeInCache(info));
    }

    @Test
    public void invalidateNode_nodeInCacheInvalidated() {
        AccessibilityNodeInfo info = mService.getRootInActiveWindow();
        assertTrue(mService.clearCachedSubtree(info));
        assertFalse("Node is still in cache", mService.isNodeInCache(info));
    }

    @Test
    public void invalidateNode_subtreeInCacheInvalidated() {
        // Tree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        assertThat(root.getChildCount(), is(2));
        AccessibilityNodeInfo child0 = root.getChild(0);
        AccessibilityNodeInfo child1 = root.getChild(1);
        AccessibilityNodeInfo grandChild = child1.getChild(0);

        assertTrue(mService.clearCachedSubtree(root));

        assertFalse("Root is in cache", mService.isNodeInCache(root));
        assertFalse("Child0 is in cache", mService.isNodeInCache(child0));
        assertFalse("Child1 is in cache", mService.isNodeInCache(child1));
        assertFalse("Grandchild is in cache", mService.isNodeInCache(grandChild));
    }

    @Test
    public void clear_cacheInvalidated() {
        // Tree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        assertThat(root.getChildCount(), is(2));
        AccessibilityNodeInfo child0 = root.getChild(0);
        AccessibilityNodeInfo child1 = root.getChild(1);
        AccessibilityNodeInfo grandChild = child1.getChild(0);

        assertTrue(mService.clearCache());

        assertFalse("Root is in cache", mService.isNodeInCache(root));
        assertFalse("Child0 is in cache", mService.isNodeInCache(child0));
        assertFalse("Child1 is in cache", mService.isNodeInCache(child1));
        assertFalse("Grandchild is in cache", mService.isNodeInCache(grandChild));
    }

    @Test
    public void getChild_descendantNotPrefetched() throws Throwable {
        // Tree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo frameRoot = mService.getRootInActiveWindow();
        assertThat(frameRoot.getChildCount(), is(2));
        AccessibilityNodeInfo textViewChild = frameRoot.getChild(0);
        AccessibilityNodeInfo linearLayoutChild = frameRoot.getChild(1);
        AccessibilityNodeInfo frameGrandChild = linearLayoutChild.getChild(0);

        // clear cache
        assertTrue(mService.clearCachedSubtree(frameRoot));
        frameRoot.getChild(1, AccessibilityNodeInfo.FLAG_PREFETCH_SIBLINGS
                | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE);
        assertTrue("Root is not in cache", mService.isNodeInCache(frameRoot));
        assertTrue("TextView is not in cache", mService.isNodeInCache(textViewChild));
        assertTrue("linearLayout is not in cache", mService.isNodeInCache(linearLayoutChild));
        // No descendant prefetching
        assertFalse("root grandchild is in cache", mService.isNodeInCache(frameGrandChild));
    }

    @Test
    public void getChild_descendantPrefetched() throws Throwable {
        // Tree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo frameRoot = mService.getRootInActiveWindow();
        assertThat(frameRoot.getChildCount(), is(2));
        AccessibilityNodeInfo textViewChild = frameRoot.getChild(0);
        AccessibilityNodeInfo linearLayoutChild = frameRoot.getChild(1);
        AccessibilityNodeInfo frameGrandChild = linearLayoutChild.getChild(0);

        // clear cache
        assertTrue(mService.clearCachedSubtree(frameRoot));

        frameRoot.getChild(1, AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST
                | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE);

        assertFalse("Root is in cache", mService.isNodeInCache(frameRoot));
        assertFalse("TextView is in cache", mService.isNodeInCache(textViewChild));
        assertTrue("linearLayout is not in cache", mService.isNodeInCache(linearLayoutChild));
        // Descendant prefetching
        assertTrue("root grandchild is not in cache", mService.isNodeInCache(frameGrandChild));
    }

    /**
     * Tests a request that prefetches descendants with multiple strategies. This throws an
     * exception.
     */
    @Test
    public void testRequest_withMultiplePrefetchingStrategies_throwsException() {
        // Tree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        assertThat(root.getChildCount(), is(2));

        assertThrows(IllegalArgumentException.class, () -> {
            root.getChild(0,
                    AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST
                            | AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID);
        });
    }
}
