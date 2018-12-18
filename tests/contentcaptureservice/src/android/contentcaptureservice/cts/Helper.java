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

import static android.contentcaptureservice.cts.common.ShellHelper.runShellCommand;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper for common funcionalities.
 */
final class Helper {

    public static final String TAG = "ContentCaptureTest";

    public static final long GENERIC_TIMEOUT_MS = 2000;

    public static final String MY_PACKAGE = "android.contentcaptureservice.cts";

    public static final long MY_EPOCH = SystemClock.uptimeMillis();

    /**
     * Awaits for a latch to be counted down.
     */
    public static void await(@NonNull CountDownLatch latch, @NonNull String fmt,
            @Nullable Object... args)
            throws InterruptedException {
        final boolean called = latch.await(GENERIC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!called) {
            throw new IllegalStateException(String.format(fmt, args)
                    + " in " + GENERIC_TIMEOUT_MS + "ms");
        }
    }

    /**
     * Sets the content capture service.
     */
    public static void setService(@NonNull String service) {
        Log.d(TAG, "Setting service to " + service);
        // TODO(b/119638958): use @TestingAPI for max duration constant
        runShellCommand("cmd content_capture set temporary-service 0 " + service + " 12000");
        // TODO(b/119638958): add a more robust mechanism to wait for service to be set.
        // For example, when the service is set using a shell cmd, block until the
        // IntelligencePerUserService is cached (or use a @TestingApi instead of shell cmd)
        SystemClock.sleep(GENERIC_TIMEOUT_MS);
    }

    /**
     * Resets the content capture service.
     */
    public static void resetService() {
        Log.d(TAG, "Resetting back to default service");
        runShellCommand("cmd content_capture set temporary-service 0");
    }

    /**
     * Sets {@link CtsContentCaptureService} as the service for the current user.
     */
    public static void enableService() {
        setService(CtsContentCaptureService.SERVICE_NAME);
    }

    private Helper() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
