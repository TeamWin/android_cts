/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.view.accessibility.cts;

import static android.view.accessibility.cts.ServiceControlUtils.TIMEOUT_FOR_SERVICE_ENABLE;
import static android.view.accessibility.cts.ServiceControlUtils.getEnabledServices;
import static android.view.accessibility.cts.ServiceControlUtils.waitForConditionWithServiceStateChange;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.compatibility.common.util.TestUtils.waitOn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.Service;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;

import com.android.compatibility.common.util.PollingCheck;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Class for testing {@link AccessibilityManager}.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityManagerTest {

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private static final String SPEAKING_ACCESSIBLITY_SERVICE_NAME =
        "android.view.accessibility.cts.SpeakingAccessibilityService";

    private static final String VIBRATING_ACCESSIBLITY_SERVICE_NAME =
        "android.view.accessibility.cts.VibratingAccessibilityService";

    private static final String MULTIPLE_FEEDBACK_TYPES_ACCESSIBILITY_SERVICE_NAME =
        "android.view.accessibility.cts.SpeakingAndVibratingAccessibilityService";

    private static final String ACCESSIBILITY_MINIMUM_UI_TIMEOUT_ENABLED =
            "accessibility_minimum_ui_timeout_enabled";

    private static final String ACCESSIBILITY_MINIMUM_UI_TIMEOUT_MS =
            "accessibility_minimum_ui_timeout_ms";

    private AccessibilityManager mAccessibilityManager;

    private Context mTargetContext;

    private Handler mHandler;

    @Before
    public void setUp() throws Exception {
        mAccessibilityManager = (AccessibilityManager)
                sInstrumentation.getContext().getSystemService(Service.ACCESSIBILITY_SERVICE);
        mTargetContext = sInstrumentation.getTargetContext();
        mHandler = new Handler(mTargetContext.getMainLooper());
        // In case the test runner started a UiAutomation, destroy it to start with a clean slate.
        sInstrumentation.getUiAutomation().destroy();
        ServiceControlUtils.turnAccessibilityOff(sInstrumentation);
    }

    @After
    public void tearDown() throws Exception {
        ServiceControlUtils.turnAccessibilityOff(sInstrumentation);
    }

    @Test
    public void testAddAndRemoveAccessibilityStateChangeListener() throws Exception {
        AccessibilityStateChangeListener listener = (state) -> {
                /* do nothing */
        };
        assertTrue(mAccessibilityManager.addAccessibilityStateChangeListener(listener));
        assertTrue(mAccessibilityManager.removeAccessibilityStateChangeListener(listener));
        assertFalse(mAccessibilityManager.removeAccessibilityStateChangeListener(listener));
    }

    @Test
    public void testAddAndRemoveTouchExplorationStateChangeListener() throws Exception {
        TouchExplorationStateChangeListener listener = (boolean enabled) -> {
            // Do nothing.
        };
        assertTrue(mAccessibilityManager.addTouchExplorationStateChangeListener(listener));
        assertTrue(mAccessibilityManager.removeTouchExplorationStateChangeListener(listener));
        assertFalse(mAccessibilityManager.removeTouchExplorationStateChangeListener(listener));
    }

    @Test
    public void testIsTouchExplorationEnabled() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mAccessibilityManager.isTouchExplorationEnabled();
            }
        }.run();
    }

    @Test
    public void testGetInstalledAccessibilityServicesList() throws Exception {
        List<AccessibilityServiceInfo> installedServices =
            mAccessibilityManager.getInstalledAccessibilityServiceList();
        assertFalse("There must be at least one installed service.", installedServices.isEmpty());
        boolean speakingServiceInstalled = false;
        boolean vibratingServiceInstalled = false;
        final int serviceCount = installedServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo installedService = installedServices.get(i);
            ServiceInfo serviceInfo = installedService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceInstalled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceInstalled = true;
            }
        }
        assertTrue("The speaking service should be installed.", speakingServiceInstalled);
        assertTrue("The vibrating service should be installed.", vibratingServiceInstalled);
    }

    @Test
    public void testGetEnabledAccessibilityServiceList() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        List<AccessibilityServiceInfo> enabledServices =
            mAccessibilityManager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        boolean speakingServiceEnabled = false;
        boolean vibratingServiceEnabled = false;
        final int serviceCount = enabledServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo enabledService = enabledServices.get(i);
            ServiceInfo serviceInfo = enabledService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceEnabled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceEnabled = true;
            }
        }
        assertTrue("The speaking service should be enabled.", speakingServiceEnabled);
        assertTrue("The vibrating service should be enabled.", vibratingServiceEnabled);
    }

    @Test
    public void testGetEnabledAccessibilityServiceListForType() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        List<AccessibilityServiceInfo> enabledServices =
            mAccessibilityManager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_SPOKEN);
        assertSame("There should be only one enabled speaking service.", 1, enabledServices.size());
        final int serviceCount = enabledServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo enabledService = enabledServices.get(i);
            ServiceInfo serviceInfo = enabledService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                return;
            }
        }
        fail("The speaking service is not enabled.");
    }

    @Test
    public void testGetEnabledAccessibilityServiceListForTypes() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        // For this test, also enable a service with multiple feedback types
        ServiceControlUtils.enableMultipleFeedbackTypesService(sInstrumentation);

        List<AccessibilityServiceInfo> enabledServices =
                mAccessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_SPOKEN
                                | AccessibilityServiceInfo.FEEDBACK_HAPTIC);
        assertSame("There should be 3 enabled accessibility services.", 3, enabledServices.size());
        boolean speakingServiceEnabled = false;
        boolean vibratingServiceEnabled = false;
        boolean multipleFeedbackTypesServiceEnabled = false;
        final int serviceCount = enabledServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo enabledService = enabledServices.get(i);
            ServiceInfo serviceInfo = enabledService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceEnabled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceEnabled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && MULTIPLE_FEEDBACK_TYPES_ACCESSIBILITY_SERVICE_NAME.equals(
                    serviceInfo.name)) {
                multipleFeedbackTypesServiceEnabled = true;
            }
        }
        assertTrue("The speaking service should be enabled.", speakingServiceEnabled);
        assertTrue("The vibrating service should be enabled.", vibratingServiceEnabled);
        assertTrue("The multiple feedback types service should be enabled.",
                multipleFeedbackTypesServiceEnabled);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetAccessibilityServiceList() throws Exception {
        List<ServiceInfo> services = mAccessibilityManager.getAccessibilityServiceList();
        boolean speakingServiceInstalled = false;
        boolean vibratingServiceInstalled = false;
        final int serviceCount = services.size();
        for (int i = 0; i < serviceCount; i++) {
            ServiceInfo serviceInfo = services.get(i);
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceInstalled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceInstalled = true;
            }
        }
        assertTrue("The speaking service should be installed.", speakingServiceInstalled);
        assertTrue("The vibrating service should be installed.", vibratingServiceInstalled);
    }

    @Test
    public void testInterrupt() throws Exception {
        // The APIs are heavily tested in the android.accessibilityservice package.
        // This just makes sure the call does not throw an exception.
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        waitForAccessibilityEnabled();
        mAccessibilityManager.interrupt();
    }

    @Test
    public void testSendAccessibilityEvent() throws Exception {
        // The APIs are heavily tested in the android.accessibilityservice package.
        // This just makes sure the call does not throw an exception.
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        waitForAccessibilityEnabled();
        mAccessibilityManager.sendAccessibilityEvent(AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_CLICKED));
    }

    @Test
    public void testTouchExplorationListenerNoHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        TouchExplorationStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addTouchExplorationStateChangeListener(listener);
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Touch exploration state listener not called when services enabled");
        assertTrue("Listener told that touch exploration is enabled, but manager says disabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        ServiceControlUtils.turnAccessibilityOff(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Touch exploration state listener not called when services disabled");
        assertFalse("Listener told that touch exploration is disabled, but manager says it enabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        mAccessibilityManager.removeTouchExplorationStateChangeListener(listener);
    }

    @Test
    public void testTouchExplorationListenerWithHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        TouchExplorationStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addTouchExplorationStateChangeListener(listener, mHandler);
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Touch exploration state listener not called when services enabled");
        assertTrue("Listener told that touch exploration is enabled, but manager says disabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        ServiceControlUtils.turnAccessibilityOff(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Touch exploration state listener not called when services disabled");
        assertFalse("Listener told that touch exploration is disabled, but manager says it enabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        mAccessibilityManager.removeTouchExplorationStateChangeListener(listener);
    }

    @Test
    public void testAccessibilityStateListenerNoHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        AccessibilityStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addAccessibilityStateChangeListener(listener);
        ServiceControlUtils.enableMultipleFeedbackTypesService(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Accessibility state listener not called when services enabled");
        assertTrue("Listener told that accessibility is enabled, but manager says disabled",
                mAccessibilityManager.isEnabled());
        ServiceControlUtils.turnAccessibilityOff(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Accessibility state listener not called when services disabled");
        assertFalse("Listener told that accessibility is disabled, but manager says enabled",
                mAccessibilityManager.isEnabled());
        mAccessibilityManager.removeAccessibilityStateChangeListener(listener);
    }

    @Test
    public void testAccessibilityStateListenerWithHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        AccessibilityStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addAccessibilityStateChangeListener(listener, mHandler);
        ServiceControlUtils.enableMultipleFeedbackTypesService(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Accessibility state listener not called when services enabled");
        assertTrue("Listener told that accessibility is enabled, but manager says disabled",
                mAccessibilityManager.isEnabled());
        ServiceControlUtils.turnAccessibilityOff(sInstrumentation);
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Accessibility state listener not called when services disabled");
        assertFalse("Listener told that accessibility is disabled, but manager says enabled",
                mAccessibilityManager.isEnabled());
        mAccessibilityManager.removeAccessibilityStateChangeListener(listener);
    }

    @Test
    public void testGetMinimumUiTimeoutMs() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(sInstrumentation);
        waitForAccessibilityEnabled();
        UiAutomation automan = sInstrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        turnOffMinimumUiTimoutSettings(automan);
        PollingCheck.waitFor(() -> mAccessibilityManager.getMinimumUiTimeoutMillis() == 2000);
        turnOnMinimumUiTimoutSettings(automan, 5000);
        PollingCheck.waitFor(() -> mAccessibilityManager.getMinimumUiTimeoutMillis() == 5000);
        turnOnMinimumUiTimoutSettings(automan, 6000);
        PollingCheck.waitFor(() -> mAccessibilityManager.getMinimumUiTimeoutMillis() == 6000);
        turnOffMinimumUiTimoutSettings(automan);
        PollingCheck.waitFor(() -> mAccessibilityManager.getMinimumUiTimeoutMillis() == 2000);
        automan.destroy();
    }

    @Test
    public void performShortcut_withoutPermission_fails() {
        UiAutomation uiAutomation = sInstrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);

        String originalShortcut = configureShortcut(
                uiAutomation, SpeakingAccessibilityService.COMPONENT_NAME.flattenToString());
        try {
            mAccessibilityManager.performAccessibilityShortcut();
            fail("No security exception thrown when performing shortcut without permission");
        } catch (SecurityException e) {
            // Expected
        } finally {
            configureShortcut(uiAutomation, originalShortcut);
            uiAutomation.destroy();
        }
        assertTrue(TextUtils.isEmpty(getEnabledServices(mTargetContext.getContentResolver())));
    }

    @Test
    public void performShortcut_withPermission_succeeds() {
        UiAutomation uiAutomation = sInstrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);

        String originalShortcut = configureShortcut(
                uiAutomation, SpeakingAccessibilityService.COMPONENT_NAME.flattenToString());
        try {
            runWithShellPermissionIdentity(uiAutomation,
                    () -> mAccessibilityManager.performAccessibilityShortcut());
            // Make sure the service starts up
            waitOn(SpeakingAccessibilityService.sWaitObjectForConnecting,
                    () -> SpeakingAccessibilityService.sConnectedInstance != null,
                    TIMEOUT_FOR_SERVICE_ENABLE, "Speaking accessibility service starts up");
        } finally {
            configureShortcut(uiAutomation, originalShortcut);
            uiAutomation.destroy();
        }
    }

    private String configureShortcut(UiAutomation uiAutomation, String shortcutService) {
        String currentService = Settings.Secure.getString(mTargetContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);
        putSecureSetting(uiAutomation, Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,
                shortcutService);
        if (shortcutService != null) {
            runWithShellPermissionIdentity(uiAutomation, () ->
                    waitForConditionWithServiceStateChange(mTargetContext, () -> TextUtils.equals(
                            mAccessibilityManager.getAccessibilityShortcutService(),
                            shortcutService),
                            TIMEOUT_FOR_SERVICE_ENABLE,
                            "accessibility shortcut set to test service"));
        }
        return currentService;
    }

    private void assertAtomicBooleanBecomes(AtomicBoolean atomicBoolean,
            boolean expectedValue, Object waitObject, String message)
            throws Exception {
        long timeoutTime =
                System.currentTimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        synchronized (waitObject) {
            while ((atomicBoolean.get() != expectedValue)
                    && (System.currentTimeMillis() < timeoutTime)) {
                waitObject.wait(timeoutTime - System.currentTimeMillis());
            }
        }
        assertTrue(message, atomicBoolean.get() == expectedValue);
    }

    private void waitForAccessibilityEnabled() throws InterruptedException {
        final Object waitObject = new Object();

        AccessibilityStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addAccessibilityStateChangeListener(listener);
        long timeoutTime =
                System.currentTimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        synchronized (waitObject) {
            while (!mAccessibilityManager.isEnabled()
                    && (System.currentTimeMillis() < timeoutTime)) {
                waitObject.wait(timeoutTime - System.currentTimeMillis());
            }
        }
        mAccessibilityManager.removeAccessibilityStateChangeListener(listener);
        assertTrue("Timed out enabling accessibility", mAccessibilityManager.isEnabled());
    }

    private void turnOffMinimumUiTimoutSettings(UiAutomation automan) {
        putSecureSetting(automan, ACCESSIBILITY_MINIMUM_UI_TIMEOUT_ENABLED, null);
        putSecureSetting(automan, ACCESSIBILITY_MINIMUM_UI_TIMEOUT_MS, null);
    }

    private void turnOnMinimumUiTimoutSettings(UiAutomation automan, int timeout) {
        putSecureSetting(automan, ACCESSIBILITY_MINIMUM_UI_TIMEOUT_ENABLED, "1");
        putSecureSetting(automan, ACCESSIBILITY_MINIMUM_UI_TIMEOUT_MS, Integer.toString(timeout));
    }

    private void putSecureSetting(UiAutomation automan, String name, String value) {
        ContentResolver cr = mTargetContext.getContentResolver();
        automan.adoptShellPermissionIdentity();
        try {
            Settings.Secure.putString(cr, name, value);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }
}
