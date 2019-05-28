/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.usepermission;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BasePermissionActivity extends Activity {
    private static final long OPERATION_TIMEOUT_MILLIS = 5000;

    /**
     * Static to ensure correct behavior if {@link Activity} instance was recreated before
     * result delivery.
     *
     * requestCode -> Future<Result>
     */
    private static Map<Integer, CompletableFuture<Result>> sPendingResults =
            new ConcurrentHashMap<>();
    private static AtomicInteger sNextRequestCode = new AtomicInteger(0);

    private final CountDownLatch mOnCreateSync = new CountDownLatch(1);

    public static class Result {
        public final String[] permissions;
        public final int[] grantResults;

        public Result(String[] permissions, int[] grantResults) {
            this.permissions = permissions;
            this.grantResults = grantResults;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        mOnCreateSync.countDown();
    }

    public CompletableFuture<Result> requestPermissions(String[] permissions) {
        int requestCode = sNextRequestCode.getAndIncrement();
        CompletableFuture<Result> future = new CompletableFuture<>();
        sPendingResults.put(requestCode, future);
        requestPermissions(permissions, requestCode);
        return future;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        sPendingResults.get(requestCode).complete(new Result(permissions, grantResults));
    }

    public void waitForOnCreate() {
        try {
            mOnCreateSync.await(OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
