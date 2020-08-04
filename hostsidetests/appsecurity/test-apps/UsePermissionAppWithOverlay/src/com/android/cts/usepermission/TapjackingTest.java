/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.usepermission;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static junit.framework.Assert.assertEquals;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Until;

import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.Test;

public class TapjackingTest extends BasePermissionsTest {


    private static final String APP_PACKAGE_NAME = "com.android.cts.usepermission";
    private final Context mContext = getInstrumentation().getContext();

    @Test
    public void testTapjackingGrantDialog() throws Exception {
        assertEquals(PackageManager.PERMISSION_DENIED,
                mContext.checkPermission(ACCESS_FINE_LOCATION, Process.myPid(), Process.myUid()));

        requestPermissions(new String[]{ACCESS_FINE_LOCATION}, 0, UsePermissionWithOverlay.class,
                () -> {
                    // Wait for overlay to hide the dialog
                    getUiDevice().wait(Until.findObject(By.res(
                            "com.android.cts.usepermission:id/overlay_description")),
                            10000).click();
                    try {
                        // Try to grant the permission, this should fail
                        eventually(() -> {
                            if (mContext.getPackageManager().checkPermission(ACCESS_FINE_LOCATION,
                                    APP_PACKAGE_NAME) == PackageManager.PERMISSION_DENIED) {
                                clickAllowForegroundButton();
                            }
                            assertEquals(PackageManager.PERMISSION_GRANTED,
                                    mContext.checkPermission(ACCESS_FINE_LOCATION,
                                            Process.myPid(), Process.myUid()));
                        }, 10000);
                    } catch (RuntimeException e) {
                        // expected
                    }
                });

        assertEquals(PackageManager.PERMISSION_DENIED,
                mContext.checkPermission(ACCESS_FINE_LOCATION, Process.myPid(), Process.myUid()));
    }

    /**
     * Make sure that a {@link Runnable} eventually finishes without throwing a {@link
     * Exception}.
     *
     * @param r The {@link Runnable} to run.
     * @param r The number of milliseconds to wait for r to not throw
     */
    public static void eventually(ThrowingRunnable r, long timeoutMillis) {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                r.run();
                return;
            } catch (Throwable e) {
                if (System.currentTimeMillis() - start < timeoutMillis) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
