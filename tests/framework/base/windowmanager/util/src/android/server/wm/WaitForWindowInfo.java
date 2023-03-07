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

package android.server.wm;

import android.os.IBinder;
import android.window.WindowInfosListenerForTest;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class WaitForWindowInfo {
    /**
     * Calls the provided predicate each time window information changes.
     *
     * <p>
     * <strong>Note:</strong>The caller must have
     * android.permission.ACCESS_SURFACE_FLINGER permissions.
     * </p>
     *
     * @param predicate The predicate tested each time window infos change.
     * @param timeout   The amount of time to wait for the predicate to be satisfied.
     * @param unit      The units associated with timeout.
     * @return True if the provided predicate is true for any invocation before
     * the timeout is reached. False otherwise.
     */
    public static boolean waitForWindowInfos(@NonNull Predicate<List<WindowInfo>> predicate,
            int timeout, @NonNull TimeUnit unit) throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        Consumer<List<WindowInfo>> checkPredicate = windowInfos -> {
            if (satisfied.get()) {
                return;
            }
            if (predicate.test(windowInfos)) {
                satisfied.set(true);
                latch.countDown();
            }
        };

        var listener = new WindowInfosListenerForTest();
        try {
            listener.addWindowInfosListener(checkPredicate);
            latch.await(timeout, unit);
        } finally {
            listener.removeWindowInfosListener(checkPredicate);
        }

        return satisfied.get();
    }

    /**
     * Calls the provided predicate each time window information changes if a
     * window is found that matches the supplied window token.
     *
     * <p>
     * <strong>Note:</strong>The caller must have the
     * android.permission.ACCESS_SURFACE_FLINGER permissions.
     * </p>
     *
     * @param predicate           The predicate tested each time window infos change.
     * @param timeout             The amount of time to wait for the predicate to be satisfied.
     * @param unit                The units associated with timeout.
     * @param windowTokenSupplier Supplies the window token for the window to
     *                            call the predicate on. The supplier is called each time window
     *                            info change. If the supplier returns null, the predicate is
     *                            assumed false for the current invocation.
     * @return True if the provided predicate is true for any invocation before the timeout is
     * reached. False otherwise.
     * @hide
     */
    public static boolean waitForWindowInfo(@NonNull Predicate<WindowInfo> predicate, int timeout,
            @NonNull TimeUnit unit, @NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        Predicate<List<WindowInfo>> wrappedPredicate = windowInfos -> {
            IBinder windowToken = windowTokenSupplier.get();
            if (windowToken == null) {
                return false;
            }

            for (var windowInfo : windowInfos) {
                if (windowInfo.windowToken == windowToken) {
                    return predicate.test(windowInfo);
                }
            }

            return false;
        };
        return waitForWindowInfos(wrappedPredicate, timeout, unit);
    }

    /**
     * Waits until the window specified by windowTokenSupplier is visible or times out.
     *
     * <p>
     * <strong>Note:</strong>The caller must have the
     * android.permission.ACCESS_SURFACE_FLINGER permissions.
     * </p>
     *
     * @param timeout             The amount of time to wait for the window to be visible.
     * @param unit                The units associated with timeout.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @return True if the window is visible before the timeout is reached. False otherwise.
     * @hide
     */
    public boolean waitForWindowVisible(int timeout, @NonNull TimeUnit unit,
            @NonNull Supplier<IBinder> windowTokenSupplier) throws InterruptedException {
        return waitForWindowInfo(windowInfo -> true, timeout, unit, windowTokenSupplier);
    }
}
