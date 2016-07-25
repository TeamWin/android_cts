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

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.test.suitebuilder.annotation.SmallTest;

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
                + "max_shortcuts=10"
                + "max_icon_dimension_dp=96,"
                + "max_icon_dimension_dp_lowram=96,"
                + "icon_format=PNG,"
                + "icon_quality=100";
    }

    public void testShortcutInfoMissingMandatoryFields() {

        final ComponentName mainActivity = new ComponentName(
                getTestContext().getPackageName(), "android.content.pm.cts.shortcutmanager.main");

        assertExpectException(
                RuntimeException.class,
                "id cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), null));

        assertExpectException(
                RuntimeException.class,
                "id cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), ""));

        assertExpectException(
                RuntimeException.class,
                "intents cannot contain null",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setIntent(null));

        assertExpectException(
                RuntimeException.class,
                "action must be set",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setIntent(new Intent()));

        assertExpectException(
                RuntimeException.class,
                "activity cannot be null",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setActivity(null));

        assertExpectException(
                RuntimeException.class,
                "shortLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setShortLabel(null));

        assertExpectException(
                RuntimeException.class,
                "shortLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setShortLabel(""));

        assertExpectException(
                RuntimeException.class,
                "longLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setLongLabel(null));

        assertExpectException(
                RuntimeException.class,
                "longLabel cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setLongLabel(""));

        assertExpectException(
                RuntimeException.class,
                "disabledMessage cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setDisabledMessage(null));

        assertExpectException(
                RuntimeException.class,
                "disabledMessage cannot be empty",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setDisabledMessage(""));

        assertExpectException(NullPointerException.class, "action must be set",
                () -> new ShortcutInfo.Builder(getTestContext(), "id").setIntent(new Intent()));

        assertExpectException(
                IllegalArgumentException.class, "Short label must be provided", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });

        // same for add.
        assertExpectException(
                IllegalArgumentException.class, "Short label must be provided", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(mainActivity)
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });

        assertExpectException(NullPointerException.class, "Intent must be provided", () -> {
            ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                    .setActivity(mainActivity)
                    .setShortLabel("x")
                    .build();
            assertTrue(getManager().setDynamicShortcuts(list(si)));
        });

        // same for add.
        assertExpectException(NullPointerException.class, "Intent must be provided", () -> {
            ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                    .setActivity(mainActivity)
                    .setShortLabel("x")
                    .build();
            assertTrue(getManager().addDynamicShortcuts(list(si)));
        });

        assertExpectException(
                IllegalStateException.class, "does not belong to package", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(new ComponentName("xxx", "s"))
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });

        // same for add.
        assertExpectException(
                IllegalStateException.class, "does not belong to package", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(new ComponentName("xxx", "s"))
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });

        // Not main activity
        final ComponentName nonMainActivity = new ComponentName(
                getTestContext().getPackageName(),
                "android.content.pm.cts.shortcutmanager.non_main");
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(nonMainActivity)
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });
        // For add
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(nonMainActivity)
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });
        // For update
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(nonMainActivity)
                            .build();
                    assertTrue(getManager().updateShortcuts(list(si)));
                });

        // Main activity, but disabled.
        final ComponentName disabledMain = new ComponentName(
                getTestContext().getPackageName(),
                "android.content.pm.cts.shortcutmanager.disabled_main");
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(disabledMain)
                            .build();
                    assertTrue(getManager().setDynamicShortcuts(list(si)));
                });
        // For add
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(disabledMain)
                            .build();
                    assertTrue(getManager().addDynamicShortcuts(list(si)));
                });
        // For update
        assertExpectException(
                IllegalStateException.class, "is not main", () -> {
                    ShortcutInfo si = new ShortcutInfo.Builder(getTestContext(), "id")
                            .setActivity(disabledMain)
                            .build();
                    assertTrue(getManager().updateShortcuts(list(si)));
                });
    }
}
