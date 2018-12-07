/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.webkit.cts;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.Assert;

/**
 * Helper methods for common webkit test tasks.
 *
 * <p>
 * This should remain functionally equivalent to androidx.webkit.WebkitUtils.
 * Modifications to this class should be reflected in that class as necessary. See
 * http://go/modifying-webview-cts.
 */
public final class WebkitUtils {

    private static final long TEST_TIMEOUT_MS = 20000L; // 20s.

    /**
     * Waits for {@code future} and returns its value (or times out).
     */
    public static <T> T waitForFuture(Future<T> future) throws InterruptedException,
             ExecutionException,
             TimeoutException {
        // TODO(ntfschr): consider catching ExecutionException and throwing e.getCause().
        return future.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Takes an element out of the {@link BlockingQueue} (or times out).
     */
    public static <T> T waitForNextQueueElement(BlockingQueue<T> queue) throws InterruptedException,
             TimeoutException {
        T value = queue.poll(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (value == null) {
            // {@code null} is the special value which means {@link BlockingQueue#poll} has timed
            // out (also: there's no risk for collision with real values, because BlockingQueue does
            // not allow null entries). Instead of returning this special value, let's throw a
            // proper TimeoutException to stay consistent with {@link #waitForFuture}.
            throw new TimeoutException(
                    "Timeout while trying to take next entry from BlockingQueue");
        }
        return value;
    }

    // Do not instantiate this class.
    private WebkitUtils() {}
}
