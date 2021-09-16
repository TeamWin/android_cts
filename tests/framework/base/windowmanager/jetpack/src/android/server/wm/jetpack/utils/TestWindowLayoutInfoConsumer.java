/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm.jetpack.utils;

import androidx.annotation.Nullable;
import androidx.window.extensions.layout.WindowLayoutInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class TestWindowLayoutInfoConsumer implements Consumer<WindowLayoutInfo> {

    private static final String TAG = "TestWindowLayoutInfoConsumer";
    private static final long WINDOW_LAYOUT_INFO_TIMEOUT_MS = 50;

    CompletableFuture<WindowLayoutInfo> mFuture;

    public TestWindowLayoutInfoConsumer() {
        mFuture = new CompletableFuture<WindowLayoutInfo>();
    }

    @Override
    public void accept(WindowLayoutInfo windowLayoutInfo) {
        mFuture.complete(windowLayoutInfo);
    }

    public @Nullable WindowLayoutInfo waitAndGet()
            throws ExecutionException, InterruptedException, TimeoutException {
        return mFuture.get(WINDOW_LAYOUT_INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
