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

import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class ShortcutManagerDynamicCountTest extends ShortcutManagerCtsTestsBase {

    @Override
    protected String getOverrideConfig() {
        return "max_shortcuts=3";
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

    // TODO testAddDynamicShortcuts, update, plus multiple activities.
}
