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

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.*;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.Suppress;

import java.util.List;
import java.util.function.BooleanSupplier;

public class ShortcutManagerThrottlingTest extends ShortcutManagerCtsTestsBase {
    private static final String CONFIG_BASE =
            "max_updates_per_interval=5,"
            + "max_shortcuts=10,"
            + "max_icon_dimension_dp=128,"
            + "max_icon_dimension_dp_lowram=32,"
            + "icon_format=PNG,"
            + "icon_quality=100";

    private static final String CONFIG_3SEC = CONFIG_BASE + ",reset_interval_sec=3";

    @Override
    protected String getOverrideConfig() {
        return CONFIG_3SEC;
    }

    private void resetThrottling() {
        resetAllThrottling(getInstrumentation());
        assertEquals(5, getManager().getRemainingCallCount());
    }

    private void waitUntilReset() {
        final long resetTime = getManager().getRateLimitResetTime();
        final long sleepTime = resetTime - System.currentTimeMillis();

        if (sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void clearDynamicShortcuts() {
        getManager().removeAllDynamicShortcuts();
        assertEquals(0, getManager().getDynamicShortcuts().size());
    }

    /**
     * Run the given test, except for making it's okay to get an AssertionError, when the test took
     * longer and the reset has happened.
     */
    private void runButOkayIfItTakesLonger(Runnable r) {
        final long nextResetTime = getManager().getRateLimitResetTime();

        try {
            r.run();
        } catch (AssertionError e) {
            if (System.currentTimeMillis() < nextResetTime) {
                throw e;
            }
        }
    }

    private void checkThrottled(BooleanSupplier apiCall, Runnable nonThrottledAssert,
            Runnable throttledAssert) {
        resetThrottling();

        runButOkayIfItTakesLonger(() -> {
            // Can call 5 times successfully.
            assertTrue(apiCall.getAsBoolean());
            nonThrottledAssert.run();
            assertEquals(4, getManager().getRemainingCallCount());

            assertTrue(apiCall.getAsBoolean());
            nonThrottledAssert.run();
            assertEquals(3, getManager().getRemainingCallCount());

            assertTrue(apiCall.getAsBoolean());
            nonThrottledAssert.run();
            assertEquals(2, getManager().getRemainingCallCount());

            assertTrue(apiCall.getAsBoolean());
            nonThrottledAssert.run();
            assertEquals(1, getManager().getRemainingCallCount());

            assertTrue(apiCall.getAsBoolean());
            nonThrottledAssert.run();
            assertEquals(0, getManager().getRemainingCallCount());

            // Now throttled.
            assertFalse(apiCall.getAsBoolean());
            throttledAssert.run();
            assertEquals(0, getManager().getRemainingCallCount());

            // Still throttled.
            assertFalse(apiCall.getAsBoolean());
            throttledAssert.run();

            assertEquals(0, getManager().getRemainingCallCount());
        });

        // However, it shouldn't affect other packages.
        runWithCaller(mPackageContext3, () -> {
            assertEquals(5, getManager().getRemainingCallCount());
        });

        // Wait until reset, then retry.
        waitUntilReset();
        assertEquals(5, getManager().getRemainingCallCount());

        runButOkayIfItTakesLonger(() -> {

            assertTrue(apiCall.getAsBoolean());
            nonThrottledAssert.run();
            assertEquals(4, getManager().getRemainingCallCount());
        });
    }

    /**
     * Make sure {@link android.content.pm.ShortcutManager#setDynamicShortcuts(List)} correctly
     * throttles calls.
     *
     * <p>Suppressed for now -- because when an instrumentation test is running, the process
     * will be in the PROCESS_STATE_FOREGROUND_SERVICE state, so none of calls will be throttled.
     * Need a different way to verify this.
     */
    @Suppress
    @LargeTest
    public void testThrottled_setDynamicShortcuts() {
        runWithCaller(mPackageContext1, () -> {
            checkThrottled(
                    () -> {
                        clearDynamicShortcuts();
                        return getManager().setDynamicShortcuts(list(makeShortcut("s1")));
                    }, () -> { // Non-throttled assert.
                        assertEquals(1, getManager().getDynamicShortcuts().size());
                    }, () -> { // Throttled assert.
                        assertEquals(0, getManager().getDynamicShortcuts().size());
                    });
        });
    }

    private void checkNotThrottled(Runnable apiCall) {
        resetThrottling();

        // Can call more than 5 times.
        apiCall.run();
        assertEquals(5, getManager().getRemainingCallCount());

        apiCall.run();
        assertEquals(5, getManager().getRemainingCallCount());

        apiCall.run();
        assertEquals(5, getManager().getRemainingCallCount());

        apiCall.run();
        assertEquals(5, getManager().getRemainingCallCount());

        apiCall.run();
        assertEquals(5, getManager().getRemainingCallCount());

        apiCall.run();
        assertEquals(5, getManager().getRemainingCallCount());

        apiCall.run();
        assertEquals(5, getManager().getRemainingCallCount());
    }

    public void testNotThrottled_delete() {
        runWithCaller(mPackageContext1, () -> {
            checkNotThrottled(() -> getManager().removeAllDynamicShortcuts());

            checkNotThrottled(() -> getManager().removeDynamicShortcuts(list("s1")));
        });
    }

    public void testNotThrottled_getShortcuts() {
        // Preparation: Create some shortcuts, and pin some.
        runWithCaller(mPackageContext1, () -> {
            getManager().setDynamicShortcuts(list(makeShortcut("s1"), makeShortcut("s2")));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext2);
        runWithCaller(mLauncherContext2, () -> {
            getLauncherApps().pinShortcuts(
                    mPackageContext1.getPackageName(), list("s2"), getUserHandle());
        });

        // Then check.
        runWithCaller(mPackageContext1, () -> {
            resetThrottling();
            checkNotThrottled(() ->
                assertShortcutIds(getManager().getDynamicShortcuts(), "s1", "s2"));

            checkNotThrottled(() ->
                assertShortcutIds(getManager().getPinnedShortcuts(), "s2"));
        });
    }

    public void testNotThrottled_misc() {
        runWithCaller(mPackageContext1, () -> {
            checkNotThrottled(() -> getManager().getIconMaxDimensions());

            checkNotThrottled(() -> getManager().getRateLimitResetTime());

            checkNotThrottled(() ->
                    assertEquals(10, getManager().getMaxDynamicShortcutCount()));
        });
    }
}
