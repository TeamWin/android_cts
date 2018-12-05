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
package android.contentcaptureservice.cts;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper for common funcionalities.
 */
final class Helper {

    public static final String TAG = "ContentCaptureTest";
    public static final long GENERIC_TIMEOUT_MS = 2000;

    public static void await(@NonNull CountDownLatch latch, @NonNull String errorMsg)
            throws InterruptedException {
        final boolean called = latch.await(GENERIC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!called) {
            throw new IllegalStateException(errorMsg + " in " + GENERIC_TIMEOUT_MS + "ms");
        }
    }

    private Helper() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
