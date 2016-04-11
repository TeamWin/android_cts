/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.content.pm.cts;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertExpectException;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.Looper;

public class ShortcutManagerSpoofDetectionTest extends ShortcutManagerCtsTestsBase {

    @Override
    protected String getOverrideConfig() {
        return "reset_interval_sec=999999,"
                + "max_daily_updates=999999,"
                + "max_shortcuts=10,"
                + "max_icon_dimension_dp=128,"
                + "max_icon_dimension_dp_lowram=32,"
                + "icon_format=PNG,"
                + "icon_quality=100";
    }

    public void assertCallingPackageMismatch(String method, Context callerContext, Runnable r) {
        assertExpectException(
                "Caller=" + callerContext.getPackageName() + ", method=" + method,
                SecurityException.class, "Calling package name mismatch",
                () -> runWithCaller(callerContext, () -> r.run())
        );
    }

    public void testPublisherSpoofing() {
        assertCallingPackageMismatch("setDynamicShortcuts", mPackageContext4, () -> {
            getManager().setDynamicShortcuts(list(makeShortcut("s1")));
        });
        assertCallingPackageMismatch("addDynamicShortcut", mPackageContext4, () -> {
            getManager().addDynamicShortcut(makeShortcut("s1"));
        });
        assertCallingPackageMismatch("deleteDynamicShortcut", mPackageContext4, () -> {
            getManager().deleteDynamicShortcut("s1");
        });
        assertCallingPackageMismatch("deleteAllDynamicShortcuts", mPackageContext4, () -> {
            getManager().deleteAllDynamicShortcuts();
        });
        assertCallingPackageMismatch("getDynamicShortcuts", mPackageContext4, () -> {
            getManager().getDynamicShortcuts();
        });
        assertCallingPackageMismatch("getPinnedShortcuts", mPackageContext4, () -> {
            getManager().getPinnedShortcuts();
        });
        assertCallingPackageMismatch("updateShortcuts", mPackageContext4, () -> {
            getManager().updateShortcuts(list(makeShortcut("s1")));
        });
        assertCallingPackageMismatch("getMaxDynamicShortcutCount", mPackageContext4, () -> {
            getManager().getMaxDynamicShortcutCount();
        });
        assertCallingPackageMismatch("getRemainingCallCount", mPackageContext4, () -> {
            getManager().getRemainingCallCount();
        });
        assertCallingPackageMismatch("getRateLimitResetTime", mPackageContext4, () -> {
            getManager().getRateLimitResetTime();
        });
        assertCallingPackageMismatch("getIconMaxDimensions", mPackageContext4, () -> {
            getManager().getIconMaxDimensions();
        });
    }

    public void testLauncherSpoofing() {
        assertCallingPackageMismatch("hasShortcutHostPermission", mLauncherContext4, () -> {
            getLauncherApps().hasShortcutHostPermission();
        });

        assertCallingPackageMismatch("registerCallback", mLauncherContext4, () -> {
            final LauncherApps.Callback c = mock(LauncherApps.Callback.class);
            getLauncherApps().registerCallback(c, new Handler(Looper.getMainLooper()));
        });

        assertCallingPackageMismatch("getShortcuts", mLauncherContext4, () -> {
            ShortcutQuery q = new ShortcutQuery();
            getLauncherApps().getShortcuts(q, getUserHandle());
        });

        assertCallingPackageMismatch("pinShortcuts", mLauncherContext4, () -> {
            getLauncherApps().pinShortcuts(
                    mPackageContext1.getPackageName(), list(), getUserHandle());
        });

        assertCallingPackageMismatch("getShortcutIconResId 1", mLauncherContext4, () -> {
            getLauncherApps().getShortcutIconResId(makeShortcut("s"));
        });
        assertCallingPackageMismatch("getShortcutIconResId 2", mLauncherContext4, () -> {
            getLauncherApps().getShortcutIconResId(mPackageContext1.getPackageName(), "s1",
                    getUserHandle());
        });
        assertCallingPackageMismatch("getShortcutIconResId 1", mLauncherContext4, () -> {
            getLauncherApps().getShortcutIconResId(makeShortcut("s"));
        });
        assertCallingPackageMismatch("getShortcutIconResId 2", mLauncherContext4, () -> {
            getLauncherApps().getShortcutIconResId(mPackageContext1.getPackageName(), "s1",
                    getUserHandle());
        });

        assertCallingPackageMismatch("startShortcut 1", mLauncherContext4, () -> {
            getLauncherApps().startShortcut(makeShortcut("s"), null, null);
        });
        assertCallingPackageMismatch("startShortcut 2", mLauncherContext4, () -> {
            getLauncherApps().startShortcut(mPackageContext1.getPackageName(), "s1",
                    null, null, getUserHandle());
        });
    }
}
