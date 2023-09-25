/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.virtualdevice.cts.applaunch;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.common.util.VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.util.EmptyActivity;
import android.virtualdevice.cts.common.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceHomeTest {

    private static final VirtualDisplayConfig HOME_DISPLAY_CONFIG =
            createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS)
                    .build();

    private static final ComponentName EMPTY_ACTIVITY = new ComponentName(
            "android.virtualdevice.cts.applaunch",
            "android.virtualdevice.cts.applaunch.util.EmptyActivity");

    private static final ComponentName DEFAULT_HOME_ACTIVITY = new ComponentName(
            "android.virtualdevice.streamedtestapp",
            "android.virtualdevice.streamedtestapp.HomeActivity");

    private static final ComponentName CUSTOM_HOME_ACTIVITY = new ComponentName(
            "android.virtualdevice.streamedtestapp",
            "android.virtualdevice.streamedtestapp.CustomHomeActivity");

    private ComponentName mHomeComponent = null;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_TRUSTED_DISPLAY,
            CHANGE_COMPONENT_ENABLED_STATE,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private Context mContext;
    @Mock
    private VirtualDeviceManager.ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        assumeTrue(Flags.vdmCustomHome());

        MockitoAnnotations.initMocks(this);
        mContext = getApplicationContext();
        final PackageManager packageManager = mContext.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mVirtualDeviceManager = mContext.getSystemService(VirtualDeviceManager.class);
        assumeTrue(mVirtualDeviceManager != null);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void virtualDeviceHome_noCustomHomeComponent() {
        try (HomeActivitySession session = new HomeActivitySession(DEFAULT_HOME_ACTIVITY)) {
            final ComponentName homeComponent = session.getCurrentSecondaryHomeComponent();
            createVirtualDeviceAndHomeDisplay();
            assertActivityOnVirtualDisplay(homeComponent);

            EmptyActivity activity = launchTestActivity();

            activity.finish();
            assertActivityOnVirtualDisplay(homeComponent, 2);
        }
    }

    @Test
    public void virtualDeviceHome_noCustomHomeComponent_sendHomeIntent() {
        try (HomeActivitySession session = new HomeActivitySession(DEFAULT_HOME_ACTIVITY)) {
            final ComponentName homeComponent = session.getCurrentSecondaryHomeComponent();
            createVirtualDeviceAndHomeDisplay();
            assertActivityOnVirtualDisplay(homeComponent);

            launchTestActivity();

            sendHomeIntentOnVirtualDisplay();
            assertActivityOnVirtualDisplay(homeComponent, 2);
        }
    }

    @Test
    public void virtualDeviceHome_invalidCustomHomeComponent_fallbackToDefaultSecondaryHome() {
        try (HomeActivitySession session = new HomeActivitySession(DEFAULT_HOME_ACTIVITY)) {
            mHomeComponent = new ComponentName("foo.bar", "foo.bar.Baz");
            final ComponentName homeComponent = session.getCurrentSecondaryHomeComponent();
            createVirtualDeviceAndHomeDisplay();
            assertActivityOnVirtualDisplay(homeComponent);
        }
    }

    @Test
    public void virtualDeviceHome_withCustomHomeComponent() {
        mHomeComponent = CUSTOM_HOME_ACTIVITY;

        createVirtualDeviceAndHomeDisplay();
        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY);

        EmptyActivity activity = launchTestActivity();

        activity.finish();
        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY, 2);
    }

    @Test
    public void virtualDeviceHome_withCustomHomeComponent_sendHomeIntent() {
        mHomeComponent = CUSTOM_HOME_ACTIVITY;

        createVirtualDeviceAndHomeDisplay();
        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY);

        launchTestActivity();

        sendHomeIntentOnVirtualDisplay();
        assertActivityOnVirtualDisplay(CUSTOM_HOME_ACTIVITY, 2);
    }

    private void createVirtualDeviceAndHomeDisplay() {
        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder().setHomeComponent(mHomeComponent).build());
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                HOME_DISPLAY_CONFIG, null, null);
    }

    private EmptyActivity launchTestActivity() {
        EmptyActivity activity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(mContext, EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(mVirtualDisplay));
        assertActivityOnVirtualDisplay(EMPTY_ACTIVITY);
        return activity;
    }

    private void sendHomeIntentOnVirtualDisplay() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent, createActivityOptions(mVirtualDisplay));
    }

    private void assertActivityOnVirtualDisplay(ComponentName componentName) {
        assertActivityOnVirtualDisplay(componentName, 1);
    }

    private void assertActivityOnVirtualDisplay(ComponentName componentName, int times) {
        verify(mActivityListener, timeout(3000).times(times)).onTopActivityChanged(
                eq(mVirtualDisplay.getDisplay().getDisplayId()),
                eq(componentName),
                eq(mContext.getUserId()));

    }

    /**
     * HomeActivitySession is used to replace the default home component, so that you can use
     * your preferred home for testing within the session. The original default home will be
     * restored automatically afterward.
     */
    private class HomeActivitySession implements AutoCloseable {
        private final PackageManager mPackageManager;
        private final ComponentName mOrigHome;
        private final ComponentName mSessionHome;

        HomeActivitySession(ComponentName sessionHome) {
            mSessionHome = sessionHome;
            mPackageManager = mContext.getPackageManager();
            mOrigHome = getDefaultHomeComponent();

            mPackageManager.setComponentEnabledSetting(mSessionHome,
                    COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
            setDefaultHome(mSessionHome);
        }

        @Override
        public void close() {
            mPackageManager.setComponentEnabledSetting(mSessionHome,
                    COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
            if (mOrigHome != null) {
                setDefaultHome(mOrigHome);
            }
        }

        private void setDefaultHome(ComponentName componentName) {
            SystemUtil.runShellCommand("cmd package set-home-activity --user "
                    + android.os.Process.myUserHandle().getIdentifier() + " "
                    + componentName.flattenToString());
        }

        ComponentName getCurrentSecondaryHomeComponent() {
            boolean useSystemProvidedLauncher = mContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_useSystemProvidedLauncherForSecondary",
                            "bool", "android"));
            return useSystemProvidedLauncher ? getDefaultSecondaryHomeComponent() : mSessionHome;
        }

        private ComponentName getDefaultHomeComponent() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return resolveHomeIntent(intent);
        }

        private ComponentName getDefaultSecondaryHomeComponent() {
            int resId = Resources.getSystem().getIdentifier(
                    "config_secondaryHomePackage", "string", "android");
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_SECONDARY_HOME);
            intent.setPackage(mContext.getResources().getString(resId));
            return resolveHomeIntent(intent);
        }

        private ComponentName resolveHomeIntent(Intent intent) {
            final ResolveInfo resolveInfo =
                    mContext.getPackageManager().resolveActivity(intent, MATCH_DEFAULT_ONLY);
            assumeTrue(resolveInfo != null);
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            return new ComponentName(activityInfo.packageName, activityInfo.name);
        }
    }
}
