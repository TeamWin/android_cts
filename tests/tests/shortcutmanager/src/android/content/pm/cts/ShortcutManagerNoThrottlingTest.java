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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertCallbackNotReceived;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertCallbackReceived;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertExpectException;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.checkAssertSuccess;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.resetAll;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.waitUntil;

import static org.mockito.Mockito.mock;

import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Handler;
import android.os.Looper;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.List;

/**
 * Tests for {@link ShortcutManager} and {@link ShortcutInfo}.
 *
 * In this test, we tests the main functionalities of those, without throttling.  We
 */
@SmallTest
public class ShortcutManagerNoThrottlingTest extends ShortcutManagerCtsTestsBase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected String getOverrideConfig() {
        return "reset_interval_sec=999999,"
                + "max_updates_per_interval=999999,"
                + "max_shortcuts=10,"
                + "max_icon_dimension_dp=128,"
                + "max_icon_dimension_dp_lowram=32,"
                + "icon_format=PNG,"
                + "icon_quality=100";
    }

    public void testShortcutInfoMissingMandatoryFields() {
        assertExpectException(
                IllegalArgumentException.class,
                "ID must be provided",
                () -> new ShortcutInfo.Builder(getTestContext()).build());
        assertExpectException(
                IllegalArgumentException.class, "title must be provided", () -> {
            ShortcutInfo si = new ShortcutInfo.Builder(getTestContext()).setId("id").build();
            getManager().setDynamicShortcuts(list(si));
        });
        assertExpectException(
                NullPointerException.class, "Intent must be provided", () -> {
            ShortcutInfo si = new ShortcutInfo.Builder(getTestContext()).setId("id").setTitle("x")
                    .build();
            getManager().setDynamicShortcuts(list(si));
        });
    }

    /**
     * Create shortcuts from different packages and make sure they're really different.
     */
    public void testSpoofingPublisher() {
        runWithCaller(mPackageContext1, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title1");
            getManager().setDynamicShortcuts(list(s1));
        });
        runWithCaller(mPackageContext2, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title2");
            getManager().setDynamicShortcuts(list(s1));
        });
        runWithCaller(mPackageContext3, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title3");
            getManager().setDynamicShortcuts(list(s1));
        });

        runWithCaller(mPackageContext1, () -> {
            final List<ShortcutInfo> list = getManager().getDynamicShortcuts();
            assertEquals(1, list.size());
            assertEquals("title1", list.get(0).getTitle());
        });
        runWithCaller(mPackageContext2, () -> {
            final List<ShortcutInfo> list = getManager().getDynamicShortcuts();
            assertEquals(1, list.size());
            assertEquals("title2", list.get(0).getTitle());
        });
        runWithCaller(mPackageContext3, () -> {
            final List<ShortcutInfo> list = getManager().getDynamicShortcuts();
            assertEquals(1, list.size());
            assertEquals("title3", list.get(0).getTitle());
        });
    }

    public void testSpoofingLauncher() {
        final LauncherApps.Callback c0_1 = mock(LauncherApps.Callback.class);
        final LauncherApps.Callback c0_2 = mock(LauncherApps.Callback.class);
        final LauncherApps.Callback c0_3 = mock(LauncherApps.Callback.class);
        final Handler h = new Handler(Looper.getMainLooper());

        runWithCaller(mLauncherContext1, () -> getLauncherApps().registerCallback(c0_1, h));
        runWithCaller(mLauncherContext2, () -> getLauncherApps().registerCallback(c0_2, h));
        runWithCaller(mLauncherContext3, () -> getLauncherApps().registerCallback(c0_3, h));

        // Change the default launcher
        setDefaultLauncher(getInstrumentation(), mLauncherContext2);

        runWithCaller(mLauncherContext1,
                () -> assertFalse(getLauncherApps().hasShortcutHostPermission()));
        runWithCaller(mLauncherContext2,
                () -> assertTrue(getLauncherApps().hasShortcutHostPermission()));
        runWithCaller(mLauncherContext3,
                () -> assertFalse(getLauncherApps().hasShortcutHostPermission()));

        // Call a publisher API and make sure only launcher2 gets it.

        resetAll(list(c0_1, c0_2, c0_3));

        runWithCaller(mPackageContext1, () -> {
            ShortcutInfo s1 = makeShortcut("s1", "title1");
            getManager().setDynamicShortcuts(list(s1));
        });

        // Because of the handlers, callback calls are not synchronous.
        waitUntil("Launcher 2 didn't receive message", () ->
            checkAssertSuccess(() ->
                assertCallbackReceived(c0_2, android.os.Process.myUserHandle(),
                        mPackageContext1.getPackageName(), "s1")
            )
        );

        assertCallbackNotReceived(c0_1);
        assertCallbackNotReceived(c0_3);
    }
}
