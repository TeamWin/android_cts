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

import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
import static android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventType;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitleWithList;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.getActivityTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.supportsMultiDisplay;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.DisplayUtils.VirtualDisplaySession;
import static android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACTIVE;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_FOCUSED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.compatibility.common.util.TestUtils.waitOn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.ProxyConcurrentActivity;
import android.accessibilityservice.cts.activities.ProxyDisplayActivity;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Color;
import android.platform.test.annotations.Presubmit;
import android.util.SparseArray;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityDisplayProxy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ensure AccessibilityDisplayProxy APIs work.
 *
 * AccessibilityDisplayProxy is in android.view.accessibility since apps take advantage of it.
 * AccessibilityDisplayProxyTest is in android.accessibilityservice.cts, not
 * android.view.accessibility.cts, since the proxy behaves likes an a11y service and this package
 * gives access to a suite of helpful utils for testing service-like behavior.
 */
@RunWith(AndroidJUnit4.class)
@Presubmit
public class AccessibilityDisplayProxyTest {
    private static final int TIMEOUT_MS = 5000;
    private static final int INVALID_DISPLAY_ID = 10000;

    public static final int NON_INTERACTIVE_UI_TIMEOUT = 100;
    public static final int INTERACTIVE_UI_TIMEOUT = 200;
    public static final int NOTIFICATION_TIMEOUT = 100;
    public static final String PACKAGE_1 = "package 1";
    public static final String PACKAGE_2 = "package 2";

    private static Instrumentation sInstrumentation;
    private static  UiAutomation sUiAutomation;

    private AccessibilityManager mA11yManager;
    private MyA11yProxy mA11yProxy;
    private int mDisplayId;
    private Activity mActivity;
    private VirtualDisplaySession mVirtualDisplaySession;

    private InstrumentedAccessibilityServiceTestRule<StubProxyConcurrentAccessibilityService>
            mProxyConcurrentServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubProxyConcurrentAccessibilityService.class, false);

    private final ActivityTestRule<ProxyConcurrentActivity>
            mConcurrentAccessibilityServiceActivityRule =
            new ActivityTestRule<>(ProxyConcurrentActivity.class, false, false);


    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mProxyConcurrentServiceRule)
            .around(mConcurrentAccessibilityServiceActivityRule)
            .around(mDumpOnFailureRule);

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
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        infos.add(info);
        mA11yProxy = new MyA11yProxy(mDisplayId, Executors.newSingleThreadExecutor(), infos);

        final AccessibilityServiceInfo automationInfo = sUiAutomation.getServiceInfo();
        assert automationInfo != null;
        automationInfo.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        sUiAutomation.setServiceInfo(automationInfo);
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
        final MyA11yProxy invalidProxy = new MyA11yProxy(
                INVALID_DISPLAY_ID, Executors.newSingleThreadExecutor(), new ArrayList<>());
        runWithShellPermissionIdentity(sUiAutomation, () -> {
            assertThat(mA11yManager.unregisterDisplayProxy(invalidProxy)).isFalse();
        });
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#getDisplayId"})
    public void testGetDisplayId() {
        assertThat(mA11yProxy.getDisplayId()).isEqualTo(mDisplayId);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testTypeViewClickedAccessibilityEvent_proxyReceivesEvent() {
        registerProxyAndWaitForConnection();
        // Create and populate the expected event
        AccessibilityEvent clickEvent = getProxyClickAccessibilityEvent();
        mA11yProxy.setExpectedEvent(clickEvent);

        final Button button = mActivity.findViewById(R.id.button);
        mActivity.runOnUiThread(() -> button.performClick());

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(), TIMEOUT_MS,
                "Expected event was not received within " + TIMEOUT_MS + " ms");
    }

    @Test
    public void testAccessibilityServiceDoesNotGetProxyEvents() {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();

        try {
            registerProxyAndWaitForConnection();
            // Create and populate the expected event.
            AccessibilityEvent clickEvent = getProxyClickAccessibilityEvent();
            service.setUnwantedEvent(clickEvent);

            final Button button = mActivity.findViewById(R.id.button);
            mActivity.runOnUiThread(() -> button.performClick());
            assertThrows(AssertionError.class, () ->
                    waitOn(service.mWaitObject, ()-> service.mReceivedUnwantedEvent.get(),
                            TIMEOUT_MS,
                    "Expected event was not received within " + TIMEOUT_MS + " ms"));
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#getWindows"})
    public void testGetWindows_proxyReceivesActivityAppOnDisplay() {
        registerProxyAndWaitForConnection();

        final List<AccessibilityWindowInfo> windows = mA11yProxy.getWindows();
        assertThat(findWindowByTitleWithList(
                getActivityTitle(sInstrumentation, mActivity), windows)).isNotNull();
    }

    @Test
    public void testAccessibilityServiceDoesNotGetProxyWindows() {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays =
                    service.getWindowsOnAllDisplays();
            assertThat(windowsOnAllDisplays.contains(mDisplayId)).isFalse();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxyGetsAccessibilityFocus() throws TimeoutException {
        registerProxyAndWaitForConnection();
        final List<AccessibilityWindowInfo> windows = mA11yProxy.getWindows();
        // Validity check: activity window exists.
        assertThat(findWindowByTitleWithList(
                getActivityTitle(sInstrumentation, mActivity), windows)).isNotNull();

        final EditText editText = mActivity.findViewById(R.id.edit_text);
        setAccessibilityFocus(editText);

        final AccessibilityNodeInfo a11yFocusedNode = mA11yProxy.findFocus(
                AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        assertThat(a11yFocusedNode).isEqualTo(editText.createAccessibilityNodeInfo());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxyDoesNotGetServiceAccessibilityFocus() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            // Launch an activity on the default display.
            final ProxyConcurrentActivity concurrentToProxyActivity =
                    launchActivityAndWaitForItToBeOnscreen(
                    sInstrumentation, sUiAutomation, mConcurrentAccessibilityServiceActivityRule);

            final List<AccessibilityWindowInfo> windows = service.getWindows();
            // Validity check: activity window exists.
            assertThat(findWindowByTitleWithList(getActivityTitle(sInstrumentation,
                    concurrentToProxyActivity), windows)).isNotNull();

            final EditText editText = concurrentToProxyActivity.findViewById(R.id.editText);
            setAccessibilityFocus(editText);

            final AccessibilityNodeInfo a11yFocusedNode = service.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            assertThat(a11yFocusedNode).isEqualTo(editText.createAccessibilityNodeInfo());
            assertThat(mA11yProxy.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)).isNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxyGetsInputFocus() throws TimeoutException {
        registerProxyAndWaitForConnection();
        final List<AccessibilityWindowInfo> windows = mA11yProxy.getWindows();
        // Validity check: activity window exists.
        assertThat(findWindowByTitleWithList(
                getActivityTitle(sInstrumentation, mActivity), windows)).isNotNull();
        // Make sure that the proxy display is the top-focused display.
        setTopFocusedDisplayIfNeeded(mDisplayId, mActivity, windows);

        final EditText editText = mActivity.findViewById(R.id.edit_text);
        setInputFocusIfNeeded(editText);

        final AccessibilityNodeInfo inputFocus = mA11yProxy.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT);
        assertThat(inputFocus).isEqualTo(editText.createAccessibilityNodeInfo());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxyDoesNotGetServiceInputFocus() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            // Launch an activity on the default display.
            final ProxyConcurrentActivity concurrentToProxyActivity =
                    launchActivityAndWaitForItToBeOnscreen(
                    sInstrumentation, sUiAutomation, mConcurrentAccessibilityServiceActivityRule);
            final List<AccessibilityWindowInfo> windows = service.getWindows();
            // Validity check: activity window exists.
            assertThat(findWindowByTitleWithList(getActivityTitle(sInstrumentation,
                    concurrentToProxyActivity), windows)).isNotNull();
            // Make sure that the default display is the top-focused display.
            setTopFocusedDisplayIfNeeded(Display.DEFAULT_DISPLAY, concurrentToProxyActivity,
                    windows);

            final EditText editText = concurrentToProxyActivity.findViewById(R.id.editText);
            setInputFocusIfNeeded(editText);

            final AccessibilityNodeInfo inputFocus = service.findFocus(
                    AccessibilityNodeInfo.FOCUS_INPUT);
            assertThat(inputFocus).isEqualTo(editText.createAccessibilityNodeInfo());
            assertThat(mA11yProxy.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)).isNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityDisplayProxy#setInstalledAndEnabledServices",
            "android.view.accessibility.AccessibilityDisplayProxy#getInstalledAndEnabledServices"})
    public void testSetAndGetInstalledAndEnabledAccessibilityServices_proxySetsAndGetsList() {
        mA11yProxy = new MyA11yProxy(mDisplayId, Executors.newSingleThreadExecutor(),
                new ArrayList<>());
        registerProxyAndWaitForConnection();

        mA11yProxy.setInstalledAndEnabledServices(getTestAccessibilityServiceInfoAsList());

        assertTestAccessibilityServiceInfo(mA11yProxy.getInstalledAndEnabledServices());
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityDisplayProxy#getInstalledAndEnabledServices"})
    public void testGetInstalledAndEnabledAccessibilityServices_proxyConstructorMatchesGet() {
        mA11yProxy = new MyA11yProxy(mDisplayId, Executors.newSingleThreadExecutor(),
                getTestAccessibilityServiceInfoAsList());
        registerProxyAndWaitForConnection();

        assertTestAccessibilityServiceInfo(mA11yProxy.getInstalledAndEnabledServices());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onInterrupt"})
    public void testOnInterrupted() {
        registerProxyAndWaitForConnection();
        final AccessibilityManager activityA11yManager =
                mActivity.getSystemService(AccessibilityManager.class);

        activityA11yManager.interrupt();

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mInterrupted.get(), TIMEOUT_MS,
                "Proxy was not interrupted");
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onProxyConnected"})
    public void testOnProxyConnected() {
        registerProxyAndWaitForConnection();
    }

    private void registerProxyAndWaitForConnection() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> mA11yManager.registerDisplayProxy(mA11yProxy));

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mConnected.get(), TIMEOUT_MS,
                "Proxy was not connected");
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#setFocusAppearance"})
    public void testSetFocusAppearanceDataAfterProxyEnabled() {
        registerProxyAndWaitForConnection();
        // This test verifies that the proxy can set the user's focus appearance, which affects all
        // apps. Ideally this should only affect the apps that are proxy-ed.
        // TODO(264594384): Test that a non-proxy activity does not get a changed focus appearance.
        final AccessibilityManager activityA11yManager =
                mActivity.getSystemService(AccessibilityManager.class);
        final int width = activityA11yManager.getAccessibilityFocusStrokeWidth();
        final int color = activityA11yManager.getAccessibilityFocusColor();
        final int updatedWidth = width + 10;
        final int updatedColor = color == Color.BLUE ? Color.RED : Color.BLUE;

        try {
            setFocusAppearanceDataAndCheckItCorrect(mA11yProxy, updatedWidth, updatedColor);
        } finally {
            setFocusAppearanceDataAndCheckItCorrect(mA11yProxy, width, color);
        }
    }

    private void setFocusAppearanceDataAndCheckItCorrect(AccessibilityDisplayProxy proxy,
            int focusStrokeWidthValue, int focusColorValue) {
        proxy.setAccessibilityFocusAppearance(focusStrokeWidthValue,
                focusColorValue);
        final AccessibilityManager activityA11yManager =
                mActivity.getSystemService(AccessibilityManager.class);
        // Checks if the color and the stroke values from AccessibilityManager are
        // updated as expected.
        PollingCheck.waitFor(()->
                activityA11yManager.getAccessibilityFocusStrokeWidth() == focusStrokeWidthValue
                && activityA11yManager.getAccessibilityFocusColor() == focusColorValue);
    }

    AccessibilityEvent getProxyClickAccessibilityEvent() {
        final AccessibilityEvent clickEvent = new AccessibilityEvent();
        clickEvent.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);
        clickEvent.setClassName(Button.class.getName());
        clickEvent.setDisplayId(mA11yProxy.getDisplayId());
        clickEvent.getText().add(mActivity.getString(R.string.button_title));
        return clickEvent;
    }

    private List<AccessibilityServiceInfo> getTestAccessibilityServiceInfoAsList() {
        final List<AccessibilityServiceInfo> infos = new ArrayList<>();
        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.setAccessibilityTool(true);
        info.packageNames = new String[]{PACKAGE_1, PACKAGE_2};
        info.setInteractiveUiTimeoutMillis(INTERACTIVE_UI_TIMEOUT);
        info.setNonInteractiveUiTimeoutMillis(NON_INTERACTIVE_UI_TIMEOUT);
        info.notificationTimeout = NOTIFICATION_TIMEOUT;
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = FEEDBACK_AUDIBLE;
        info.flags = FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        infos.add(info);
        return infos;
    }

    private void assertTestAccessibilityServiceInfo(List<AccessibilityServiceInfo> infos) {
        assertThat(infos.size()).isEqualTo(1);
        final AccessibilityServiceInfo retrievedInfo = infos.get(0);
        // Assert individual properties, since info.equals checks the ComponentName, which is
        // populated in the info instances belonging to the system process but not in those held by
        // the proxy.
        assertThat(retrievedInfo.isAccessibilityTool()).isTrue();
        assertThat(retrievedInfo.packageNames).isEqualTo(new String[]{PACKAGE_1, PACKAGE_2});
        assertThat(retrievedInfo.getInteractiveUiTimeoutMillis()).isEqualTo(
                INTERACTIVE_UI_TIMEOUT);
        assertThat(retrievedInfo.getNonInteractiveUiTimeoutMillis()).isEqualTo(
                NON_INTERACTIVE_UI_TIMEOUT);
        assertThat(retrievedInfo.notificationTimeout).isEqualTo(NOTIFICATION_TIMEOUT);
        assertThat(retrievedInfo.eventTypes).isEqualTo(AccessibilityEvent.TYPE_VIEW_CLICKED);
        assertThat(retrievedInfo.feedbackType).isEqualTo(FEEDBACK_AUDIBLE);
        assertThat(retrievedInfo.flags).isEqualTo(FLAG_INCLUDE_NOT_IMPORTANT_VIEWS);
    }

    private void setAccessibilityFocus(View view) throws TimeoutException {
        sUiAutomation.executeAndWaitForEvent(
                () -> mActivity.runOnUiThread(() -> view.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)),
                filterForEventType(TYPE_VIEW_ACCESSIBILITY_FOCUSED), DEFAULT_TIMEOUT_MS);
    }

    private void setInputFocusIfNeeded(View view) throws TimeoutException {
        if (!view.isFocused()) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> mActivity.runOnUiThread(() -> view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_FOCUS, null)),
                    filterForEventType(TYPE_VIEW_FOCUSED), DEFAULT_TIMEOUT_MS);
        }
    }

    private void setTopFocusedDisplayIfNeeded(int displayId, Activity activity,
            List<AccessibilityWindowInfo> windows) throws TimeoutException {
        boolean isTopFocusedDisplay = false;
        for (AccessibilityWindowInfo window : windows) {
            // A focused window of the display means this display is the top-focused display.
            if (window.isFocused()) {
                isTopFocusedDisplay = true;
                break;
            }
        }
        if (!isTopFocusedDisplay) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> DisplayUtils.touchDisplay(sUiAutomation, displayId, activity.getTitle()),
                    filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_FOCUSED
                            | WINDOWS_CHANGE_ACTIVE), DEFAULT_TIMEOUT_MS);
        }
    }

    /**
     * Class for testing AccessibilityDisplayProxy.
     */
    class MyA11yProxy extends AccessibilityDisplayProxy {
        AtomicBoolean mReceivedEvent = new AtomicBoolean();
        AtomicBoolean mConnected = new AtomicBoolean();
        AtomicBoolean mInterrupted = new AtomicBoolean();

        private AccessibilityEvent mExpectedEvent;
        Object mWaitObject = new Object();

        MyA11yProxy(int displayId, Executor executor, List<AccessibilityServiceInfo> infos) {
            super(displayId, executor, infos);
        }

        @Override
        public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
            if (mExpectedEvent != null) {
                if (event.getEventType() == mExpectedEvent.getEventType()
                        && event.getClassName().equals(mExpectedEvent.getClassName())
                        && event.getDisplayId() == mExpectedEvent.getDisplayId()
                        && event.getText().equals(mExpectedEvent.getText())) {
                    synchronized (mWaitObject) {
                        mReceivedEvent.set(true);
                        mWaitObject.notifyAll();
                    }
                }
            }
        }

        @Override
        public void onProxyConnected() {
            synchronized (mWaitObject) {
                mConnected.set(true);
                mWaitObject.notifyAll();
            }
        }

        @Override
        public void interrupt() {
            synchronized (mWaitObject) {
                mInterrupted.set(true);
                mWaitObject.notifyAll();
            }
        }
        public void setExpectedEvent(@NonNull AccessibilityEvent event) {
            mExpectedEvent = event;
        }
    }
}
