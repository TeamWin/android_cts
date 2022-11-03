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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.supportsMultiDisplay;
import static android.accessibilityservice.cts.utils.DisplayUtils.VirtualDisplaySession;
import static android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.ProxyDisplayActivity;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.view.Display;
import android.view.accessibility.AccessibilityDisplayProxy;
import android.view.accessibility.AccessibilityManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/** Ensure {@link AccessibilityDisplayProxy} APIs work.
 *
 * AccessibilityDisplayProxy is in android.view.accessibility since apps take advantage of it.
 * AccessibilityDisplayProxyTest is in android.accessibilityservice.cts, not
 * android.view.accessibility.cts, since the proxy behaves likes an a11y service and this package
 * gives access to a suite of helpful utils for testing service-like behavior.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityDisplayProxyTest {
    private static Instrumentation sInstrumentation;
    private static  UiAutomation sUiAutomation;

    private AccessibilityManager mA11yManager;
    private MyA11yProxy mA11yProxy;
    private int mDisplayId;
    private Activity mActivity;
    private VirtualDisplaySession mVirtualDisplaySession;

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation =
                sInstrumentation.getUiAutomation(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        final AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        final Context context = sInstrumentation.getContext();
        assumeTrue(supportsMultiDisplay(context));
        mA11yManager = context.getSystemService(AccessibilityManager.class);
        mVirtualDisplaySession = createVirtualDisplay();
        mActivity = launchActivityOnVirtualDisplay(mDisplayId);
        final List<AccessibilityServiceInfo> infos = new ArrayList<>();
        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        infos.add(info);
        mA11yProxy = new MyA11yProxy(mDisplayId, Executors.newSingleThreadExecutor(), infos);
    }

    @After
    public void tearDown() throws TimeoutException {
        if (mA11yProxy != null) {
            runWithShellPermissionIdentity(sUiAutomation, () ->
                    mA11yManager.unregisterDisplayProxy(mA11yProxy));
        }
        if (mActivity != null) {
            mActivity.runOnUiThread(() -> mActivity.finish());
        }
        if (mVirtualDisplaySession != null) {
            mVirtualDisplaySession.close();
        }
    }

    private VirtualDisplaySession createVirtualDisplay() {
        final VirtualDisplaySession displaySession = new VirtualDisplaySession();
        final int virtualDisplayId =
                displaySession.createDisplayWithDefaultDisplayMetricsAndWait(
                        sInstrumentation.getContext(), false).getDisplayId();
        mDisplayId = virtualDisplayId;
        return displaySession;
    }
    private Activity launchActivityOnVirtualDisplay(int virtualDisplayId) throws Exception {
        final Activity activityOnVirtualDisplay =
                launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(sInstrumentation,
                        sUiAutomation,
                        ProxyDisplayActivity.class,
                        virtualDisplayId);
        return activityOnVirtualDisplay;
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_withPermission_successfullyRegisters() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThat(mA11yManager.registerDisplayProxy(mA11yProxy)).isTrue());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_withoutPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                mA11yManager.registerDisplayProxy(mA11yProxy));
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_alreadyProxyed_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThat(mA11yManager.registerDisplayProxy(mA11yProxy)).isTrue());

        runWithShellPermissionIdentity(sUiAutomation, () ->
                assertThrows(IllegalArgumentException.class, () ->
                        mA11yManager.registerDisplayProxy(mA11yProxy)));
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterAccessibilityProxy_withDefaultDisplay_throwsIllegalArgException() {
        final MyA11yProxy invalidProxy = new MyA11yProxy(
                Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(), new ArrayList<>());
        try {
            runWithShellPermissionIdentity(sUiAutomation, () ->
                    assertThrows(IllegalArgumentException.class, () ->
                    mA11yManager.registerDisplayProxy(invalidProxy)));
        } finally {
            runWithShellPermissionIdentity(sUiAutomation, () ->
                    mA11yManager.unregisterDisplayProxy(invalidProxy));
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#unregisterProxy"})
    public void testUnregisterAccessibilityProxy_withPermission_successfullyUnregisters() {
        runWithShellPermissionIdentity(sUiAutomation, () -> {
            assertThat(mA11yManager.registerDisplayProxy(mA11yProxy)).isTrue();
            assertThat(mA11yManager.unregisterDisplayProxy(mA11yProxy)).isTrue();
        });
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#unregisterProxy"})
    public void testUnregisterAccessibilityProxy_withPermission_failsToUnregister() {
        //TODO(241429275): Implement test when ProxyManager can check if the given id has a service
        // connection (It currently returns true).
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#getDisplayId"})
    public void testGetDisplayId() {
        assertThat(mA11yProxy.getDisplayId()).isEqualTo(mDisplayId);
    }

    /**
     * Class for testing AccessibilityProxy.
     * TODO(241429275): A subclass is not technically needed now, but will be used to implement and
     * test onProxyConnected, onAccessibilityEvent, and onInterrupted when available.
     */
    class MyA11yProxy extends AccessibilityDisplayProxy {
        MyA11yProxy(int displayId, Executor executor, List<AccessibilityServiceInfo> infos) {
            super(displayId, executor, infos);
        }
    }
}
