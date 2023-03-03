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

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.MANAGE_ACCESSIBILITY;
import static android.Manifest.permission.WAKE_LOCK;
import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
import static android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventType;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWaitForAll;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitleWithList;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.getActivityTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.supportsMultiDisplay;
import static android.accessibilityservice.cts.utils.WindowCreationUtils.TOP_WINDOW_TITLE;
import static android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACTIVE;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_FOCUSED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.compatibility.common.util.TestUtils.waitOn;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.ProxyConcurrentActivity;
import android.accessibilityservice.cts.activities.ProxyDisplayActivity;
import android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.accessibilityservice.cts.utils.WindowCreationUtils;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityDisplayProxy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.virtualdevice.cts.common.FakeAssociationRule;
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
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
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
    private static String sEnabledServices;

    // The manager representing the app registering/unregistering the proxy.
    private AccessibilityManager mA11yManager;

    // This is technically the same manager as mA11yManager, since there is one manager per process,
    // but add separation for readability.
    private AccessibilityManager mProxyActivityA11yManager;
    private MyA11yProxy mA11yProxy;
    private int mVirtualDisplayId;
    private VirtualDisplay mVirtualDisplay;
    private Activity mProxyActivity;
    private CharSequence mProxyActivityTitle;

    // Virtual Device variables.
    private VirtualDeviceManager mVirtualDeviceManager;
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();
    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

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
        // Save enabled accessibility services before disabling them so they can be re-enabled after
        // the test.
        sEnabledServices = Settings.Secure.getString(
                sInstrumentation.getContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        // Disable all services before enabling Accessibility service to prevent flakiness
        // that depends on which services are enabled.
        InstrumentedAccessibilityService.disableAllServices();

        sUiAutomation =
                sInstrumentation.getUiAutomation(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        final AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        ShellCommandBuilder.create(sInstrumentation)
                .putSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, sEnabledServices)
                .run();
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        final Context context = sInstrumentation.getContext();
        assumeTrue(supportsMultiDisplay(context));
        mA11yManager = context.getSystemService(AccessibilityManager.class);
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        mVirtualDisplay = createVirtualDeviceAndLaunchVirtualDisplay();
        assertThat(mVirtualDisplay).isNotNull();
        mVirtualDisplayId = mVirtualDisplay.getDisplay().getDisplayId();
        final List<AccessibilityServiceInfo> infos = new ArrayList<>();
        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        infos.add(info);
        mA11yProxy = new MyA11yProxy(mVirtualDisplayId, Executors.newSingleThreadExecutor(), infos);
        mProxyActivity = launchActivityOnVirtualDisplay(
                mVirtualDisplay.getDisplay().getDisplayId());
        mProxyActivityTitle = getActivityTitle(sInstrumentation, mProxyActivity);
        mProxyActivityA11yManager = mProxyActivity.getSystemService(AccessibilityManager.class);

        final AccessibilityServiceInfo automationInfo = sUiAutomation.getServiceInfo();
        assertThat(automationInfo).isNotNull();
        automationInfo.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        sUiAutomation.setServiceInfo(automationInfo);
    }

    @After
    public void tearDown() throws TimeoutException {
        sUiAutomation.adoptShellPermissionIdentity(
                MANAGE_ACCESSIBILITY, CREATE_VIRTUAL_DEVICE, WAKE_LOCK);
        if (mA11yProxy != null) {
            mA11yManager.unregisterDisplayProxy(mA11yProxy);
        }
        if (mProxyActivity != null) {
            mProxyActivity.runOnUiThread(() -> mProxyActivity.finish());
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_withPermission_successfullyRegisters() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThat(mA11yManager.registerDisplayProxy(mA11yProxy)).isTrue(),
                CREATE_VIRTUAL_DEVICE, MANAGE_ACCESSIBILITY);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_withoutPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                mA11yManager.registerDisplayProxy(mA11yProxy));
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_withoutA11yPermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                mA11yManager.registerDisplayProxy(mA11yProxy)), CREATE_VIRTUAL_DEVICE);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_withoutDevicePermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                        mA11yManager.registerDisplayProxy(mA11yProxy)), MANAGE_ACCESSIBILITY);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerProxy"})
    public void testRegisterProxyForDisplay_alreadyProxyed_throwsIllegalArgumentException() {
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
    public void testUnregisterAccessibilityProxy_withoutDevicePermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                        mA11yManager.unregisterDisplayProxy(mA11yProxy)), MANAGE_ACCESSIBILITY);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#unregisterProxy"})
    public void testUnregisterAccessibilityProxy_withoutA11yPermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                        mA11yManager.unregisterDisplayProxy(mA11yProxy)), CREATE_VIRTUAL_DEVICE);
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
        assertThat(mA11yProxy.getDisplayId()).isEqualTo(mVirtualDisplayId);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testTypeViewClickedAccessibilityEvent_proxyReceivesEvent() {
        registerProxyAndWaitForConnection();
        // Create and populate the expected event
        AccessibilityEvent clickEvent = getProxyClickAccessibilityEvent();

        mA11yProxy.setEventFilter(getClickEventFilter(clickEvent));

        final Button button = mProxyActivity.findViewById(R.id.button);
        mProxyActivity.runOnUiThread(() -> button.performClick());

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
            service.setEventFilter(getClickEventFilter(clickEvent));

            final Button button = mProxyActivity.findViewById(R.id.button);
            mProxyActivity.runOnUiThread(() -> button.performClick());
            assertThrows(AssertionError.class, () ->
                    waitOn(service.mWaitObject, ()-> service.mReceivedEvent.get(),
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
        assertVirtualDisplayActivityExistsToProxy();
    }

    @Test
    public void testAccessibilityServiceDoesNotGetProxyWindows() {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays =
                    service.getWindowsOnAllDisplays();
            assertThat(windowsOnAllDisplays.contains(mVirtualDisplayId)).isFalse();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxyGetsAccessibilityFocus() throws TimeoutException {
        registerProxyAndWaitForConnection();
        assertVirtualDisplayActivityExistsToProxy();

        final EditText editText = mProxyActivity.findViewById(R.id.edit_text);
        setAccessibilityFocus(editText);

        final AccessibilityNodeInfo a11yFocusedNode = mA11yProxy.findFocus(
                AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        assertThat(a11yFocusedNode).isEqualTo(editText.createAccessibilityNodeInfo());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxyDoesNotGetServiceAccessibilityFocus_getsNull() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            assertVirtualDisplayActivityExistsToProxy();
            // Launch an activity on the default display.
            final ProxyConcurrentActivity concurrentToProxyActivity =
                    launchProxyConcurrentActivityOnDefaultDisplay(service);

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
    public void testGetFocus_serviceDoesNotGetProxyAccessibilityFocus_getsNull() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            assertVirtualDisplayActivityExistsToProxy();

            final EditText editText = mProxyActivity.findViewById(R.id.edit_text);
            setAccessibilityFocus(editText);

            final AccessibilityNodeInfo a11yFocusedNode = mA11yProxy.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            assertThat(a11yFocusedNode).isEqualTo(editText.createAccessibilityNodeInfo());

            assertThat(service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)).isNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testGetFocus_proxyDoesNotGetWindowFocusChangeEvent_withinWindow() throws Exception {
        registerProxyAndWaitForConnection();
        assertVirtualDisplayActivityExistsToProxy();

        final EditText editText = mProxyActivity.findViewById(R.id.edit_text);
        setAccessibilityFocus(editText);

        final AccessibilityNodeInfo a11yFocusedNode = mA11yProxy.findFocus(
                AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        assertThat(a11yFocusedNode).isEqualTo(editText.createAccessibilityNodeInfo());

        final Button button = mProxyActivity.findViewById(R.id.button);
        mProxyActivity.runOnUiThread(() -> button.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));

        mA11yProxy.setEventFilter(
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED));
        assertThrows(AssertionError.class, () ->
                waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(),
                        TIMEOUT_MS,
                        "Unwanted event was received within " + TIMEOUT_MS + " ms"));
        assertThat(button.isAccessibilityFocused()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testGetFocus_proxyGetsWindowFocusChangeEvent_initialFocusInWindow() {
        registerProxyAndWaitForConnection();
        assertVirtualDisplayActivityExistsToProxy();

        final EditText editText = mProxyActivity.findViewById(R.id.edit_text);
        mProxyActivity.runOnUiThread(() -> editText.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        mA11yProxy.setEventFilter(
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED));

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(), TIMEOUT_MS,
                "Expected event was not received within " + TIMEOUT_MS + " ms");
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testGetFocus_proxyGetsWindowFocusChangeEvent_betweenWindows() throws Exception {
        registerProxyAndWaitForConnection();
        assertVirtualDisplayActivityExistsToProxy();

        final EditText editText = mProxyActivity.findViewById(R.id.edit_text);
        setAccessibilityFocus(editText);

        showTopWindowAndWaitForItToShowUp();
        final AccessibilityWindowInfo topWindow = findWindowByTitleWithList(TOP_WINDOW_TITLE,
                mA11yProxy.getWindows());
        assertThat(topWindow).isNotNull();

        final AccessibilityNodeInfo buttonNode =
                topWindow.getRoot().findAccessibilityNodeInfosByText(
                        sInstrumentation.getContext().getString(R.string.button1)).get(0);

        mProxyActivity.runOnUiThread(() -> buttonNode.performAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS));
        mA11yProxy.setEventFilter(filterWaitForAll(
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED),
                filterForEventType(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)));

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(), TIMEOUT_MS,
                "Expected event was not received within " + TIMEOUT_MS + " ms");
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_serviceAndProxyGetsSeparateAccessibilityFocus() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            assertVirtualDisplayActivityExistsToProxy();
            // TODO(268752827): Investigate why the proxy window is invisible to to accessibility
            //  services nce the activity on the default display is launched. (Launching the default
            //  display activity will cause the windows of the virtual display to be cleared from
            // A11yWindowManager.)

            final EditText proxyEditText = mProxyActivity.findViewById(R.id.edit_text);
            setAccessibilityFocus(proxyEditText);

            final AccessibilityNodeInfo proxyA11yFocusedNode =
                    mA11yProxy.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            assertThat(proxyA11yFocusedNode).isEqualTo(proxyEditText.createAccessibilityNodeInfo());

            // Launch an activity on the default display.
            final Activity concurrentToProxyActivity =
                    launchProxyConcurrentActivityOnDefaultDisplay(service);

            final EditText serviceEditText = concurrentToProxyActivity.findViewById(R.id.editText);
            setAccessibilityFocus(serviceEditText);
            final AccessibilityNodeInfo a11yFocusedNode = service.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

            assertThat(a11yFocusedNode).isEqualTo(serviceEditText.createAccessibilityNodeInfo());
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxyGetsInputFocus() throws TimeoutException {
        registerProxyAndWaitForConnection();
        assertVirtualDisplayActivityExistsToProxy();

        // Make sure that the proxy display is the top-focused display.
        setTopFocusedDisplayIfNeeded(mVirtualDisplayId, mProxyActivity, mA11yProxy.getWindows());

        final EditText editText = mProxyActivity.findViewById(R.id.edit_text);
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
                    launchProxyConcurrentActivityOnDefaultDisplay(service);
            // Make sure that the default display is the top-focused display.
            setTopFocusedDisplayIfNeeded(Display.DEFAULT_DISPLAY, concurrentToProxyActivity,
                    service.getWindows());

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
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testA11yInputFilter_onProxyDisplay_interactionEventNotReceived() {
        registerProxyAndWaitForConnection();
        mA11yProxy.setEventFilter(filterForEventType(
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START));

        // Try to trigger touch exploration, but fail.
        final MotionEvent downEvent = getDownMotionEvent(mProxyActivityTitle,
                mA11yProxy.getWindows(), mVirtualDisplayId);
        sUiAutomation.injectInputEventToInputFilter(downEvent);

        assertThrows(AssertionError.class, () ->
                waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(),
                        TIMEOUT_MS,
                        "Unwanted event was received within " + TIMEOUT_MS + " ms"));
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testA11yInputFilter_onDefaultDisplay_interactionEventReceived() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mProxyConcurrentServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            // Launch an activity on the default display.
            final ProxyConcurrentActivity concurrentToProxyActivity =
                    launchProxyConcurrentActivityOnDefaultDisplay(service);
            service.setEventFilter(filterForEventType(
                    AccessibilityEvent.TYPE_TOUCH_INTERACTION_START));

            // Trigger touch exploration.
            final MotionEvent downEvent = getDownMotionEvent(getActivityTitle(sInstrumentation,
                            concurrentToProxyActivity),
                    service.getWindows(), concurrentToProxyActivity.getDisplayId());
            sUiAutomation.injectInputEventToInputFilter(downEvent);

            waitOn(service.mWaitObject, ()-> service.mReceivedEvent.get(), TIMEOUT_MS,
                    "Expected event was not received within " + TIMEOUT_MS + " ms");
        } finally {
            service.disableSelfAndRemove();
        }
    }

    private static MotionEvent getDownMotionEvent(CharSequence activityTitle,
            List<AccessibilityWindowInfo> windows, int displayId) {
        final Rect areaOfActivityWindowOnDisplay = new Rect();
        final AccessibilityWindowInfo window = findWindowByTitleWithList(activityTitle, windows);
        // Validity check: activity window exists.
        assertThat(window).isNotNull();

        window.getBoundsInScreen(areaOfActivityWindowOnDisplay);
        final int xOnScreen =
                areaOfActivityWindowOnDisplay.centerX();
        final int yOnScreen =
                areaOfActivityWindowOnDisplay.centerY();
        final long downEventTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = MotionEvent.obtain(downEventTime,
                downEventTime, MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        downEvent.setDisplayId(displayId);
        return downEvent;
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityDisplayProxy#setInstalledAndEnabledServices",
            "android.view.accessibility.AccessibilityDisplayProxy#getInstalledAndEnabledServices"})
    public void testSetAndGetInstalledAndEnabledAccessibilityServices_proxySetsAndGetsList() {
        mA11yProxy = new MyA11yProxy(mVirtualDisplayId, Executors.newSingleThreadExecutor(),
                new ArrayList<>());
        registerProxyAndWaitForConnection();

        mA11yProxy.setInstalledAndEnabledServices(getTestAccessibilityServiceInfoAsList());

        assertTestAccessibilityServiceInfo(mA11yProxy.getInstalledAndEnabledServices());
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityDisplayProxy#getInstalledAndEnabledServices"})
    public void testGetInstalledAndEnabledAccessibilityServices_proxyConstructorMatchesGet() {
        mA11yProxy = new MyA11yProxy(mVirtualDisplayId, Executors.newSingleThreadExecutor(),
                getTestAccessibilityServiceInfoAsList());
        registerProxyAndWaitForConnection();

        assertTestAccessibilityServiceInfo(mA11yProxy.getInstalledAndEnabledServices());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onInterrupt"})
    public void testOnInterrupted() {
        registerProxyAndWaitForConnection();

        mProxyActivityA11yManager.interrupt();

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
        final int width = mProxyActivityA11yManager.getAccessibilityFocusStrokeWidth();
        final int color = mProxyActivityA11yManager.getAccessibilityFocusColor();
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
        // Checks if the color and the stroke values from AccessibilityManager are
        // updated as expected.
        PollingCheck.waitFor(()->
                mProxyActivityA11yManager.getAccessibilityFocusStrokeWidth()
                        == focusStrokeWidthValue
                        && mProxyActivityA11yManager.getAccessibilityFocusColor()
                        == focusColorValue);
    }

    private AccessibilityEvent getProxyClickAccessibilityEvent() {
        final AccessibilityEvent clickEvent = new AccessibilityEvent();
        clickEvent.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);
        clickEvent.setClassName(Button.class.getName());
        clickEvent.setDisplayId(mA11yProxy.getDisplayId());
        clickEvent.getText().add(mProxyActivity.getString(R.string.button_title));
        return clickEvent;
    }

    private UiAutomation.AccessibilityEventFilter getClickEventFilter(
            AccessibilityEvent clickEvent) {
        return allOf(
                new AccessibilityEventFilterUtils.AccessibilityEventTypeMatcher(TYPE_VIEW_CLICKED),
                AccessibilityEventFilterUtils.matcherForDisplayId(clickEvent.getDisplayId()),
                AccessibilityEventFilterUtils.matcherForClassName(clickEvent.getClassName()),
                AccessibilityEventFilterUtils.matcherForFirstText(clickEvent.getText().get(0)))
                ::matches;
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
        if (!view.isAccessibilityFocused()) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> mProxyActivity.runOnUiThread(() -> view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)),
                    filterForEventType(TYPE_VIEW_ACCESSIBILITY_FOCUSED), TIMEOUT_MS);
        }
    }

    private void setInputFocusIfNeeded(View view) throws TimeoutException {
        if (!view.isFocused()) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> mProxyActivity.runOnUiThread(() -> {
                        // Ensure state for taking input focus.
                        view.setVisibility(View.VISIBLE);
                        view.setFocusable(true);
                        view.performAccessibilityAction(
                                AccessibilityNodeInfo.ACTION_FOCUS, null);
                    }) ,
                    filterForEventType(TYPE_VIEW_FOCUSED), TIMEOUT_MS);
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
                            | WINDOWS_CHANGE_ACTIVE), TIMEOUT_MS);
        }
    }

    private void assertVirtualDisplayActivityExistsToProxy() {
        final List<AccessibilityWindowInfo> proxyWindows = mA11yProxy.getWindows();
        assertThat(findWindowByTitleWithList(mProxyActivityTitle, proxyWindows)).isNotNull();
    }

    private ProxyConcurrentActivity launchProxyConcurrentActivityOnDefaultDisplay(
            InstrumentedAccessibilityService service)
            throws Exception {
        final ProxyConcurrentActivity concurrentToProxyActivity =
                launchActivityAndWaitForItToBeOnscreen(
                        sInstrumentation, sUiAutomation,
                        mConcurrentAccessibilityServiceActivityRule);
        final List<AccessibilityWindowInfo> serviceWindows = service.getWindows();
        assertThat(findWindowByTitleWithList(getActivityTitle(sInstrumentation,
                concurrentToProxyActivity), serviceWindows)).isNotNull();
        return concurrentToProxyActivity;
    }

    private View showTopWindowAndWaitForItToShowUp() throws TimeoutException {
        final WindowManager.LayoutParams paramsForTop =
                WindowCreationUtils.layoutParamsForWindowOnTop(
                        sInstrumentation, mProxyActivity, TOP_WINDOW_TITLE);
        final Button button = new Button(mProxyActivity);
        button.setText(sInstrumentation.getContext().getString(R.string.button1));
        WindowCreationUtils.addWindowAndWaitForEvent(sUiAutomation, sInstrumentation,
                mProxyActivity,
                button, paramsForTop, (event) -> (event.getEventType() == TYPE_WINDOWS_CHANGED)
                        && (findWindowByTitleWithList(mProxyActivityTitle, mA11yProxy.getWindows())
                        != null)
                        && (findWindowByTitleWithList(TOP_WINDOW_TITLE, mA11yProxy.getWindows())
                        != null));
        return button;
    }

    private VirtualDisplay createVirtualDeviceAndLaunchVirtualDisplay() {
        sUiAutomation.adoptShellPermissionIdentity(ADD_TRUSTED_DISPLAY, CREATE_VIRTUAL_DEVICE);
        VirtualDisplay display;
        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        // Values taken from StreamedAppClipboardTest
        ImageReader reader = ImageReader.newInstance(/* width= */ 100, /* height= */ 100,
                PixelFormat.RGBA_8888, /* maxImages= */ 1);
        display = mVirtualDevice.createVirtualDisplay(
                /* width= */ reader.getWidth(),
                /* height= */ reader.getHeight(),
                /* densityDpi= */ 240,
                reader.getSurface(),
                0,
                Runnable::run,
                new VirtualDisplay.Callback(){});
        return display;
    }

    private Activity launchActivityOnVirtualDisplay(int virtualDisplayId) throws Exception {
        final Activity activityOnVirtualDisplay =
                launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(sInstrumentation,
                        sUiAutomation,
                        ProxyDisplayActivity.class,
                        virtualDisplayId);
        return activityOnVirtualDisplay;
    }

    /**
     * Class for testing AccessibilityDisplayProxy.
     */
    class MyA11yProxy extends AccessibilityDisplayProxy {
        AtomicBoolean mReceivedEvent = new AtomicBoolean();
        AtomicBoolean mConnected = new AtomicBoolean();
        AtomicBoolean mInterrupted = new AtomicBoolean();
        UiAutomation.AccessibilityEventFilter mEventFilter;
        final Object mWaitObject = new Object();
        MyA11yProxy(int displayId, Executor executor, List<AccessibilityServiceInfo> infos) {
            super(displayId, executor, infos);
        }

        @Override
        public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
            if (mEventFilter != null) {
                if (mEventFilter.accept(event)) {
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

        public void setEventFilter(@NonNull UiAutomation.AccessibilityEventFilter filter) {
            mEventFilter = filter;
        }

    }
}
