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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertDynamicShortcutCountExceeded;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;

public class ShortcutManagerDynamicCountTest extends ShortcutManagerCtsTestsBase {

    @Override
    protected String getOverrideConfig() {
        return "reset_interval_sec=999999,"
                + "max_updates_per_interval=999999,"
                + "max_shortcuts=3,"
                + "max_icon_dimension_dp=128,"
                + "max_icon_dimension_dp_lowram=32,"
                + "icon_format=PNG,"
                + "icon_quality=100";
    }

    public void testSetDynamicShortcuts() {
        runWithCaller(mPackageContext1, () -> {
            getManager().setDynamicShortcuts(list(makeShortcut("s1")));
            getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"), makeShortcut("s2"), makeShortcut("s3")));

            assertDynamicShortcutCountExceeded(() -> {
                getManager().setDynamicShortcuts(list(
                        makeShortcut("s1"), makeShortcut("s2"), makeShortcut("s3"),
                        makeShortcut("s4")));
            });
        });
    }
}
