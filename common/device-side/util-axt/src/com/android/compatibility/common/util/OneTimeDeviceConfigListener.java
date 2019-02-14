/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.compatibility.common.util;

import android.os.SystemClock;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.OnPropertyChangedListener;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper used to block tests until a device config value has been updated.
 */
public final class OneTimeDeviceConfigListener implements OnPropertyChangedListener {

    public static final long DEFAULT_TIMEOUT_MS = 5_000;

    private static final String TAG = OneTimeDeviceConfigListener.class.getSimpleName();

    private final String mNamespace;
    private final String mKey;
    private final long mTimeoutMs;
    private final long mStarted = SystemClock.elapsedRealtime();

    private final CountDownLatch mLatch = new CountDownLatch(1);

    public OneTimeDeviceConfigListener(@NonNull String namespace, @NonNull String key) {
        this(namespace, key, DEFAULT_TIMEOUT_MS);
    }

    public OneTimeDeviceConfigListener(@NonNull String namespace, @NonNull String key,
            long timeoutMs) {
        mNamespace = Preconditions.checkNotNull(namespace);
        mKey = Preconditions.checkNotNull(key);
        mTimeoutMs = timeoutMs;
    }

    @Override
    public void onPropertyChanged(String namespace, String key, String value) {
        if (!mNamespace.equals(namespace) || !mKey.equals(key)) {
            Log.d(TAG, "ignoring " + namespace + "." + key);
            return;
        }
        mLatch.countDown();
        DeviceConfig.removeOnPropertyChangedListener(this);
    }

    /**
     * Blocks for a few seconds until it's called.
     *
     * @throws IllegalStateException if it's not called.
     */
    public void assertCalled() {
        try {
            final boolean updated = mLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS);
            if (!updated) {
                throw new RetryableException(
                        "Settings " + mKey + " not called in " + mTimeoutMs + "ms");
            }
            final long delta = SystemClock.elapsedRealtime() - mStarted;
            Log.v(TAG, TestNameUtils.getCurrentTestName() + "/" + mKey + ": " + delta + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }
}
