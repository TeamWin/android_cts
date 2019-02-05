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

import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper for common funcionalities.
 */
final class Helper {

    public static final String TAG = "ContentCaptureTest";

    public static final long GENERIC_TIMEOUT_MS = 10_000;

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
        // TODO(b/123540602): use @TestingAPI to get max duration constant
        runShellCommand("cmd content_capture set temporary-service 0 " + service + " 12000");
    }

    /**
     * Resets the content capture service.
     */
    public static void resetService() {
        Log.d(TAG, "Resetting back to default service");
        runShellCommand("cmd content_capture set temporary-service 0");
    }

    /**
     * Enables / disables the default service.
     */
    public static void setDefaultServiceEnabled(boolean enabled) {
        Log.d(TAG, "setDefaultServiceEnabled(): " + enabled);
        runShellCommand("cmd content_capture set default-service-enabled 0 %s",
                Boolean.toString(enabled));
    }

    /**
     * Gets the component name for a given class.
     */
    public static ComponentName componentNameFor(@NonNull Class<?> clazz) {
        return new ComponentName(MY_PACKAGE, clazz.getName());
    }

    /**
     * Creates a view that can be added to a parent and is important for content capture
     */
    public static TextView newImportantView(@NonNull Context context, @NonNull String text) {
        final TextView child = new TextView(context);
        child.setText(text);
        child.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_YES);
        return child;
    }

    private Helper() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
