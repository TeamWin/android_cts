/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.accessibility.cts.common.InstrumentedAccessibilityService.TIMEOUT_SERVICE_ENABLE;
import static android.accessibility.cts.common.ServiceControlUtils.waitForConditionWithServiceStateChange;
import static android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;
import static android.provider.Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Service;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests accessibility shortcut related functionality
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class AccessibilityShortcutTest {
    private static final int ACCESSIBILITY_BUTTON = 0;
    private static final int ACCESSIBILITY_SHORTCUT_KEY = 1;

    private static final String ACCESSIBILITY_BUTTON_TARGET_COMPONENT =
            "accessibility_button_target_component";

    private static final char DELIMITER = ':';
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private final InstrumentedAccessibilityServiceTestRule<SpeakingAccessibilityService>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    SpeakingAccessibilityService.class, false);

    private final InstrumentedAccessibilityServiceTestRule<AccessibilityButtonService>
            mA11yButtonServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            AccessibilityButtonService.class, false);

    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mServiceRule)
            .around(mA11yButtonServiceRule)
            .around(mDumpOnFailureRule);

    private Context mTargetContext;
    private ContentResolver mContentResolver;
    private AccessibilityManager mAccessibilityManager;

    private ActivityMonitor mActivityMonitor;
    private Activity mShortcutTargetActivity;

    private String mSpeakingA11yServiceName;
    private String mShortcutTargetActivityName;
    private String mA11yButtonServiceName;

    // These are the current shortcut states before doing the tests. Roll back them after the tests.
    private String[] mA11yShortcutTargets;
    private String[] mA11yButtonTargets;
    private List<String> mA11yShortcutTargetList;
    private List<String> mA11yButtonTargetList;

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() {
        mTargetContext = sInstrumentation.getTargetContext();
        mContentResolver = mTargetContext.getContentResolver();
        mAccessibilityManager = (AccessibilityManager) mTargetContext.getSystemService(
                Service.ACCESSIBILITY_SERVICE);
        mSpeakingA11yServiceName = new ComponentName(mTargetContext,
                SpeakingAccessibilityService.class).flattenToString();
        mShortcutTargetActivityName = new ComponentName(mTargetContext,
                AccessibilityShortcutTargetActivity.class).flattenToString();
        mA11yButtonServiceName = new ComponentName(mTargetContext,
                AccessibilityButtonService.class).flattenToString();
        mActivityMonitor = new ActivityMonitor(
                AccessibilityShortcutTargetActivity.class.getName(), null, false);
        sInstrumentation.addMonitor(mActivityMonitor);

        // Reads current shortcut states.
        readShortcutStates();
    }

    @After
    public void tearDown() {
        if (mActivityMonitor != null) {
            sInstrumentation.removeMonitor(mActivityMonitor);
        }
        if (mShortcutTargetActivity != null) {
            sInstrumentation.runOnMainSync(() -> mShortcutTargetActivity.finish());
        }

        // Rollback default shortcut states.
        if (configureShortcut(ACCESSIBILITY_SHORTCUT_KEY, mA11yShortcutTargets)) {
            waitForShortcutStateChange(ACCESSIBILITY_SHORTCUT_KEY, mA11yShortcutTargetList);
        }
        if (configureShortcut(ACCESSIBILITY_BUTTON, mA11yButtonTargets)) {
            waitForShortcutStateChange(ACCESSIBILITY_BUTTON, mA11yButtonTargetList);
        }
    }

    @Test
    public void performAccessibilityShortcut_withoutPermission_throwsSecurityException() {
        try {
            mAccessibilityManager.performAccessibilityShortcut();
            fail("No security exception thrown when performing shortcut without permission");
        } catch (SecurityException e) {
            // Expected
        }
    }

    @Test
    public void performAccessibilityShortcut_launchAccessibilityService() {
        configureShortcut(ACCESSIBILITY_SHORTCUT_KEY, mSpeakingA11yServiceName);
        waitForShortcutStateChange(ACCESSIBILITY_SHORTCUT_KEY,
                Arrays.asList(mSpeakingA11yServiceName));

        runWithShellPermissionIdentity(sUiAutomation,
                () -> mAccessibilityManager.performAccessibilityShortcut());

        // Make sure the service starts up
        final SpeakingAccessibilityService service = mServiceRule.getService();
        assertTrue("Speaking accessibility service starts up", service != null);
    }

    @Test
    public void performAccessibilityShortcut_launchShortcutTargetActivity() {
        configureShortcut(ACCESSIBILITY_SHORTCUT_KEY, mShortcutTargetActivityName);
        waitForShortcutStateChange(ACCESSIBILITY_SHORTCUT_KEY,
                Arrays.asList(mShortcutTargetActivityName));

        runWithShellPermissionIdentity(sUiAutomation,
                () -> mAccessibilityManager.performAccessibilityShortcut());

        // Make sure the activity starts up
        mShortcutTargetActivity = mActivityMonitor.waitForActivityWithTimeout(
                TIMEOUT_SERVICE_ENABLE);
        assertTrue("Accessibility shortcut target starts up",
                mShortcutTargetActivity != null);
    }

    @Test
    public void performAccessibilityShortcut_withReqA11yButtonService_a11yButtonCallback() {
        mA11yButtonServiceRule.enableService();
        configureShortcut(ACCESSIBILITY_SHORTCUT_KEY, mA11yButtonServiceName);
        waitForShortcutStateChange(ACCESSIBILITY_SHORTCUT_KEY,
                Arrays.asList(mA11yButtonServiceName));

        performShortcutAndWaitForA11yButtonClicked(mA11yButtonServiceRule.getService());
    }

    @Test
    public void getAccessibilityShortcut_withoutPermission_throwsSecurityException() {
        try {
            mAccessibilityManager.getAccessibilityShortcutTargets(ACCESSIBILITY_BUTTON);
            fail("No security exception thrown when get shortcut without permission");
        } catch (SecurityException e) {
            // Expected
        }
    }

    @Test
    public void getAccessibilityShortcut_assignedShortcutTarget_returnAssignedTarget() {
        configureShortcut(ACCESSIBILITY_BUTTON, mSpeakingA11yServiceName);
        waitForShortcutStateChange(ACCESSIBILITY_BUTTON, Arrays.asList(mSpeakingA11yServiceName));
    }

    @Test
    public void getAccessibilityShortcut_multipleTargets_returnMultipleTargets() {
        configureShortcut(ACCESSIBILITY_BUTTON,
                mSpeakingA11yServiceName, mShortcutTargetActivityName);
        waitForShortcutStateChange(ACCESSIBILITY_BUTTON,
                Arrays.asList(mSpeakingA11yServiceName, mShortcutTargetActivityName));
    }

    /**
     * Reads current shortcut states.
     */
    private void readShortcutStates() {
        mA11yShortcutTargets = getComponentIdArray(Settings.Secure.getString(mContentResolver,
                ACCESSIBILITY_SHORTCUT_TARGET_SERVICE));
        mA11yButtonTargets = getComponentIdArray(Settings.Secure.getString(mContentResolver,
                ACCESSIBILITY_BUTTON_TARGET_COMPONENT));
        runWithShellPermissionIdentity(sUiAutomation, () -> {
            mA11yShortcutTargetList = mAccessibilityManager
                    .getAccessibilityShortcutTargets(ACCESSIBILITY_SHORTCUT_KEY);
            mA11yButtonTargetList = mAccessibilityManager
                    .getAccessibilityShortcutTargets(ACCESSIBILITY_BUTTON);
        });
    }

    /**
     * Returns an array of component names by given colon-separated component name string.
     *
     * @param componentIds The colon-separated component name string.
     * @return The array of component names.
     */
    @NonNull
    private String[] getComponentIdArray(String componentIds) {
        final List<String> nameList = getComponentIdList(componentIds);
        if (nameList.isEmpty()) {
            return EMPTY_STRING_ARRAY;
        }
        final String[] result = new String[nameList.size()];
        return nameList.toArray(result);
    }

    /**
     * Return a list of component names by given colon-separated component name string.
     *
     * @param componentIds The colon-separated component name string.
     * @return The list.
     */
    @NonNull
    private List<String> getComponentIdList(String componentIds) {
        final ArrayList<String> componentIdList = new ArrayList<>();
        if (TextUtils.isEmpty(componentIds)) {
            return componentIdList;
        }

        final TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(DELIMITER);
        splitter.setString(componentIds);
        for (String name : splitter) {
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            componentIdList.add(name);
        }
        return componentIdList;
    }

    /**
     * Return a colon-separated component name string by given string array.
     *
     * @param componentIds The array of component names.
     * @return A colon-separated component name string.
     */
    @Nullable
    private String getComponentIdString(String ... componentIds) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (String componentId : componentIds) {
            if (TextUtils.isEmpty(componentId)) {
                continue;
            }
            if (stringBuilder.length() != 0) {
                stringBuilder.append(DELIMITER);
            }
            stringBuilder.append(componentId);
        }

        if (stringBuilder.length() == 0) {
            return null;
        }
        return stringBuilder.toString();
    }

    /**
     * Update the shortcut settings.
     *
     * @param shortcutType The shortcut type.
     * @param newUseShortcutList The component names which use the shortcut.
     * @return true if the new states updated.
     */
    private boolean configureShortcut(int shortcutType, String ... newUseShortcutList) {
        final String useShortcutList = getComponentIdString(newUseShortcutList);
        if (shortcutType == ACCESSIBILITY_SHORTCUT_KEY) {
            return updateAccessibilityShortcut(useShortcutList);
        } else {
            return updateAccessibilityButton(useShortcutList);
        }
    }

    /**
     * Update the setting keys of the accessibility shortcut.
     *
     * @param newUseShortcutList The value of ACCESSIBILITY_SHORTCUT_TARGET_SERVICE
     * @return true if the new states updated.
     */
    private boolean updateAccessibilityShortcut(String newUseShortcutList) {
        final ShellCommandBuilder command = ShellCommandBuilder.create(sUiAutomation);
        final String useShortcutList = Settings.Secure.getString(mContentResolver,
                ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);
        boolean changes = false;
        if (!TextUtils.equals(useShortcutList, newUseShortcutList)) {
            if (TextUtils.isEmpty(newUseShortcutList)) {
                command.deleteSecureSetting(ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);
            } else {
                command.putSecureSetting(ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, newUseShortcutList);
            }
            changes = true;
        }
        if (changes) {
            command.run();
        }
        return changes;
    }

    /**
     * Update the setting keys of the accessibility button.
     *
     * @param newUseShortcutList The value of ACCESSIBILITY_BUTTON_TARGET_COMPONENT
     * @return true if the new states updated.
     */
    private boolean updateAccessibilityButton(String newUseShortcutList) {
        final ShellCommandBuilder command = ShellCommandBuilder.create(sUiAutomation);
        final String useShortcutList = Settings.Secure.getString(mContentResolver,
                ACCESSIBILITY_BUTTON_TARGET_COMPONENT);
        boolean changes = false;
        if (!TextUtils.equals(useShortcutList, newUseShortcutList)) {
            if (TextUtils.isEmpty(newUseShortcutList)) {
                command.deleteSecureSetting(ACCESSIBILITY_BUTTON_TARGET_COMPONENT);
            } else {
                command.putSecureSetting(ACCESSIBILITY_BUTTON_TARGET_COMPONENT, newUseShortcutList);
            }
            changes = true;
        }
        if (changes) {
            command.run();
        }
        return changes;
    }

    /**
     * Waits for the shortcut state changed, and gets current shortcut list is the same with
     * expected one.
     *
     * @param shortcutType The shortcut type.
     * @param expectedList The expected shortcut targets returned from
     *        {@link AccessibilityManager#getAccessibilityShortcutTargets(int)}.
     */
    private void waitForShortcutStateChange(int shortcutType, List<String> expectedList) {
        final StringBuilder message = new StringBuilder();
        if (shortcutType == ACCESSIBILITY_SHORTCUT_KEY) {
            message.append("Accessibility Shortcut, ");
        } else {
            message.append("Accessibility Button, ");
        }
        message.append("expect:").append(expectedList);
        runWithShellPermissionIdentity(sUiAutomation, () ->
                waitForConditionWithServiceStateChange(mTargetContext, () -> {
                    final List<String> currentShortcuts =
                            mAccessibilityManager.getAccessibilityShortcutTargets(shortcutType);
                    if (currentShortcuts.size() != expectedList.size()) {
                        return false;
                    }
                    for (String expect : expectedList) {
                        if (!currentShortcuts.contains(expect)) {
                            return false;
                        }
                    }
                    return true;
                }, TIMEOUT_SERVICE_ENABLE, message.toString()));
    }

    /**
     * Perform shortcut and wait for accessibility button clicked call back.
     *
     * @param service The accessibility service
     */
    private void performShortcutAndWaitForA11yButtonClicked(AccessibilityService service) {
        final AtomicBoolean clicked = new AtomicBoolean();
        final AccessibilityButtonCallback callback = new AccessibilityButtonCallback() {
            @Override
            public void onClicked(AccessibilityButtonController controller) {
                synchronized (clicked) {
                    clicked.set(true);
                    clicked.notifyAll();
                }
            }

            @Override
            public void onAvailabilityChanged(AccessibilityButtonController controller,
                    boolean available) {
                /* do nothing */
            }
        };
        try {
            service.getAccessibilityButtonController()
                    .registerAccessibilityButtonCallback(callback);
            runWithShellPermissionIdentity(sUiAutomation,
                    () -> mAccessibilityManager.performAccessibilityShortcut());
            TestUtils.waitOn(clicked, () -> clicked.get(), TIMEOUT_SERVICE_ENABLE,
                    "Wait for a11y button clicked");
        } finally {
            service.getAccessibilityButtonController()
                    .unregisterAccessibilityButtonCallback(callback);
        }
    }
}
