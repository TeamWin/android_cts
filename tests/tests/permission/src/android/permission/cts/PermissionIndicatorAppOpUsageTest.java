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

package android.permission.cts;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.MICROPHONE;
import static android.app.AppOpsManager.OPSTR_CAMERA;
import static android.app.AppOpsManager.OPSTR_PHONE_CALL_CAMERA;
import static android.app.AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE;
import static android.app.AppOpsManager.OPSTR_RECORD_AUDIO;
import static android.provider.DeviceConfig.NAMESPACE_PRIVACY;

import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.permission.PermGroupUsage;
import android.permission.PermissionManager;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot talk to permission manager")
public class PermissionIndicatorAppOpUsageTest {

    private static Context sContext = InstrumentationRegistry.getInstrumentation().getContext();
    private static UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static UiDevice sUiDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static AppOpsManager sOpManager = sContext.getSystemService(AppOpsManager.class);
    private static final String RECENT_ACCESS_TIME_MS_CONFIG = "recent_access_time_ms";
    private static final String RUNNING_ACCESS_TIME_MS_CONFIG = "running_access_time_ms";
    private static final String PKG = sContext.getPackageName();
    private static final int UID = Process.myUid();
    private static final String OTHER_APK =
            "/data/local/tmp/cts/permissions/CtsAppThatRequestsMicCamera.apk";
    private static final String OTHER_PKG = "android.permission.cts.appthatrequestsmiccamera";
    private static final String ATTR_TAG = "cts_attribution_tag";
    private static final String OTHER_ATTR_TAG = "other_attribution_tag";
    private static final String OTHER_ACTIVITY = ".TestActivity";
    private static final String OTHER_LABEL = "PermissionUsage";
    private static int sOtherUid = -1;
    private static final long UNSET_CONFIG_VAL = -1;
    private static final long RUNNING_APP_TIME_THRESHOLD_MS = 200;
    private static final long RECENT_APP_TIME_DELAY_MS = 500;

    private HashMap<String, Long> mSavedThresholds = new HashMap<>();

    private enum Action {
        START,
        NOTE,
        FINISH
    }

    @Before
    public void installApp() {
        if (sOtherUid != -1) {
            return;
        }
        SystemUtil.runShellCommand("pm install -r " + OTHER_APK);
        try {
            sOtherUid = sContext.getPackageManager().getPackageUid(OTHER_PKG, 0);
        } catch (PackageManager.NameNotFoundException e) {
            // assert below will fail
        }
        assertNotEquals("Could not find package " + OTHER_PKG, -1, sOtherUid);
        sUiAutomation.grantRuntimePermission(OTHER_PKG, RECORD_AUDIO);
        sUiAutomation.grantRuntimePermission(PKG, RECORD_AUDIO);
        sUiAutomation.grantRuntimePermission(OTHER_PKG, Manifest.permission.CAMERA);
        sUiAutomation.grantRuntimePermission(PKG, Manifest.permission.CAMERA);
    }

    private static class UsageWithRange {
        public PermGroupUsage usage;
        public long startTime;
        public long endTime;

        UsageWithRange(PermGroupUsage usage, long startTime, long endTime) {
            this.usage = usage;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        boolean equalsUsage(PermGroupUsage other) {
            return usage.getPackageName().equals(other.getPackageName())
                    && usage.getUid() == other.getUid()
                    && usage.getPermGroupName().equals(other.getPermGroupName())
                    && startTime <= other.getLastAccess() && endTime >= other.getLastAccess()
                    && usage.isActive() == other.isActive()
                    && usage.isPhoneCall() == other.isPhoneCall()
                    && Objects.equals(usage.getAttribution(), other.getAttribution());
        }

        @Override
        public String toString() {
            return usage.toString() + " range is " + startTime + " to " + endTime;
        }
    }

    private void getAndVerifyUsages(List<UsageWithRange> expectedUsages) {
        List<PermGroupUsage> usages;
        sUiAutomation.adoptShellPermissionIdentity();
        usages = sContext.getSystemService(PermissionManager.class).getIndicatorAppOpUsageData();
        sUiAutomation.dropShellPermissionIdentity();

        assertTrue("expected at least " + expectedUsages.size() + ", got " + usages.size(),
                expectedUsages.size() <= usages.size());
        for (UsageWithRange expected : expectedUsages) {
            boolean anyEqual = false;
            for (PermGroupUsage other : usages) {
                if (expected.equalsUsage(other)) {
                    anyEqual = true;
                    break;
                }
            }
            assertTrue("could not find " + expected + " in " + usages, anyEqual);
        }

    }

    private void useAppOp(PermGroupUsage usage, Action action, boolean proxy, String attributionTag,
            String proxyAttributionTag) {
        String op;
        if (usage.getPermGroupName().equals(CAMERA) && usage.isPhoneCall()) {
            op = OPSTR_PHONE_CALL_CAMERA;
        } else if (usage.isPhoneCall()) {
            op = OPSTR_PHONE_CALL_MICROPHONE;
        } else if (usage.getPermGroupName().equals(CAMERA)) {
            op = OPSTR_CAMERA;
        } else {
            op = OPSTR_RECORD_AUDIO;
        }

        AppOpsManager manager = sOpManager;
        if (proxy && proxyAttributionTag != null) {
            manager = sContext.createAttributionContext(proxyAttributionTag)
                    .getSystemService(AppOpsManager.class);
        } else if (attributionTag != null) {
            manager = sContext.createAttributionContext(attributionTag)
                    .getSystemService(AppOpsManager.class);
        }
        assertNotNull(manager);

        int resultMode = -1;
        if (proxy) {
            sUiAutomation.adoptShellPermissionIdentity();
            switch(action) {
                case START: resultMode = manager.startProxyOpNoThrow(op, usage.getUid(),
                        usage.getPackageName(), attributionTag, null);
                break;
                case NOTE: resultMode = manager.noteProxyOpNoThrow(op, usage.getPackageName(),
                        usage.getUid(), attributionTag, null);
                break;
                case FINISH: manager.finishProxyOp(op, usage.getUid(),
                        usage.getPackageName(), attributionTag);
            }
            sUiAutomation.dropShellPermissionIdentity();
        } else {
            switch(action) {
                case START: resultMode = manager.startOpNoThrow(op, usage.getUid(),
                        usage.getPackageName(), attributionTag, null);
                    break;
                case NOTE: resultMode = manager.noteOpNoThrow(op, usage.getUid(),
                        usage.getPackageName(), attributionTag, null);
                    break;
                case FINISH: manager.finishOp(op, usage.getUid(), usage.getPackageName(),
                        attributionTag);
            }
        }

        if (action != Action.FINISH) {
            assertEquals("mode expected to be ALLOWED", AppOpsManager.MODE_ALLOWED, resultMode);
        }
    }

    private void setConfig(String property, Long amount) {
        runWithShellPermissionIdentity(() -> {
            String value = null;
            if (amount != null && amount != UNSET_CONFIG_VAL) {
                value = amount.toString();
            }
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, property, value,
                    value == null);
        });
    }

    private void startHelperApp() throws Exception {
        sUiDevice.wakeUp();
        runShellCommand(InstrumentationRegistry.getInstrumentation(), "wm dismiss-keyguard");
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(OTHER_PKG, OTHER_PKG + OTHER_ACTIVITY));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sContext.startActivity(intent);
        eventually(() -> {
            UiObject view = sUiDevice.findObject(new UiSelector().textContains(OTHER_LABEL));
            assertTrue("Couldn't find view with text " + OTHER_LABEL, view.exists());
        });
    }

    /**
     * Ensure that we get unique timestamps by waiting 1 ms before returning.
     */
    private long getUniqueTime() throws InterruptedException {
        Thread.sleep(1);
        return System.currentTimeMillis();
    }

    @Before()
    public void saveThresholdsAndSet() {
        String running = RUNNING_ACCESS_TIME_MS_CONFIG;
        String recent = RECENT_ACCESS_TIME_MS_CONFIG;
        runWithShellPermissionIdentity(() -> {
            mSavedThresholds.put(running, DeviceConfig.getLong(NAMESPACE_PRIVACY, running,
                    UNSET_CONFIG_VAL));
            mSavedThresholds.put(recent, DeviceConfig.getLong(NAMESPACE_PRIVACY, recent,
                    UNSET_CONFIG_VAL));
            setConfig(running, RUNNING_APP_TIME_THRESHOLD_MS);
            setConfig(recent, RECENT_APP_TIME_DELAY_MS);
        });
    }

    @After
    public void resetThresholds() {
        String running = RUNNING_ACCESS_TIME_MS_CONFIG;
        String recent = RECENT_ACCESS_TIME_MS_CONFIG;
        setConfig(running, mSavedThresholds.get(running));
        setConfig(recent, mSavedThresholds.get(recent));
    }

    @Test
    public void testBasicUsage() throws Exception {
        long before = getUniqueTime();
        PermGroupUsage camera = new PermGroupUsage(PKG, UID, CAMERA, 0, true, false, null);
        PermGroupUsage mic = new PermGroupUsage(PKG, UID, MICROPHONE, 0, true, false, null);
        useAppOp(camera, Action.NOTE, false, null, null);

        long afterCamera = getUniqueTime();
        useAppOp(mic, Action.NOTE, false, null, null);

        long afterMic = getUniqueTime();
        UsageWithRange c = new UsageWithRange(camera, before, afterCamera);
        UsageWithRange m = new UsageWithRange(mic, afterCamera, afterMic);
        getAndVerifyUsages(List.of(c, m));
    }

    @Test
    public void testRunningAndRecentStartUsage() throws Exception {
        long before = getUniqueTime();
        PermGroupUsage base = new PermGroupUsage(PKG, UID, CAMERA, 0, true, false, null);
        PermGroupUsage base2 = new PermGroupUsage(PKG, UID, CAMERA, 0, false, false, null);
        useAppOp(base, Action.START, false, null, null);

        UsageWithRange running = new UsageWithRange(base, before, Long.MAX_VALUE);
        getAndVerifyUsages(List.of(running));
        useAppOp(base, Action.FINISH, false, null, null);

        long after = getUniqueTime();
        UsageWithRange recent = new UsageWithRange(base, before, after);
        getAndVerifyUsages(List.of(recent));
        Thread.sleep(RUNNING_APP_TIME_THRESHOLD_MS);

        UsageWithRange recent2 = new UsageWithRange(base2, before, after);
        getAndVerifyUsages(List.of(recent2));
    }

    @Test
    public void testRunningAndRecentNoteUsage() throws Exception {
        long before = getUniqueTime();
        PermGroupUsage base = new PermGroupUsage(PKG, UID, CAMERA, 0, true, false, null);
        PermGroupUsage base2 = new PermGroupUsage(PKG, UID, CAMERA, 0, false, false, null);
        useAppOp(base, Action.NOTE, false, null, null);

        long after = getUniqueTime();
        UsageWithRange usage = new UsageWithRange(base, before, after);
        getAndVerifyUsages(List.of(usage));
        Thread.sleep(RUNNING_APP_TIME_THRESHOLD_MS);

        UsageWithRange usage2 = new UsageWithRange(base2, before, after);
        getAndVerifyUsages(List.of(usage2));
    }

    @Test
    public void testMultipleUsageOnlyMostRecentShown() throws Exception {
        PermGroupUsage base = new PermGroupUsage(PKG, UID, CAMERA, 0, true, false, null);
        useAppOp(base, Action.NOTE, false, null, null);

        long after1 = getUniqueTime();
        useAppOp(base, Action.NOTE, false, ATTR_TAG, null);

        long after2 = getUniqueTime();
        getAndVerifyUsages(List.of(new UsageWithRange(base, after1, after2)));
    }

    @Test
    public void testProxyUsageStart() throws Exception {
        startHelperApp();
        long before = getUniqueTime();
        PermGroupUsage base = new PermGroupUsage(OTHER_PKG, sOtherUid, MICROPHONE, 0, true,
                false, PKG);
        useAppOp(base, Action.START, true, null, null);

        long after = getUniqueTime();
        getAndVerifyUsages(List.of(new UsageWithRange(base, before, after)));
        useAppOp(base, Action.FINISH, true, null, null);
        sUiDevice.pressHome();
    }

    @Test
    public void testSelfBlameProxyDoesntShowAttribution() throws Exception {
        PermGroupUsage base = new PermGroupUsage(PKG, UID, MICROPHONE, 0, true, false, null);
        long before = getUniqueTime();
        useAppOp(base, Action.NOTE, true, ATTR_TAG, null);
        long after = getUniqueTime();
        getAndVerifyUsages(List.of(new UsageWithRange(base, before, after)));
    }
}
