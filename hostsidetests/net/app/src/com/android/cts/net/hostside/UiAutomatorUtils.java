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
package com.android.cts.net.hostside;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Function;

public class UiAutomatorUtils {

    public static final String WATCH_FEATURE = "android.hardware.type.watch";

    private UiAutomatorUtils() {}

    public static UiObject2 waitFindObject(Context context, UiDevice uiDevice, BySelector selector)
            throws UiObjectNotFoundException {
        return waitFindObject(context, uiDevice, selector, 20_000);
    }

    public static UiObject2 waitFindObject(Context context, UiDevice uiDevice,
            BySelector selector, long timeoutMs) throws UiObjectNotFoundException {
        final UiObject2 view = waitFindObjectOrNull(context, uiDevice, selector, timeoutMs);
        assertNotNull("View not found after waiting for " + timeoutMs + "ms: " + selector,
                view);
        return view;
    }

    public static UiObject2 waitFindObjectOrNull(Context context, UiDevice uiDevice,
            BySelector selector, long timeoutMs) throws UiObjectNotFoundException {
        UiObject2 view = null;
        long start = System.currentTimeMillis();

        boolean isAtEnd = false;
        boolean wasScrolledUpAlready = false;
        while (view == null && start + timeoutMs > System.currentTimeMillis()) {
            view = uiDevice.wait(Until.findObject(selector), 1000);

            if (view == null) {
                UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
                if (!isWatch(context)) {
                    scrollable.setSwipeDeadZonePercentage(0.25);
                }
                if (scrollable.exists()) {
                    if (isAtEnd) {
                        if (wasScrolledUpAlready) {
                            return null;
                        }
                        scrollable.scrollToBeginning(Integer.MAX_VALUE);
                        isAtEnd = false;
                        wasScrolledUpAlready = true;
                    } else {
                        isAtEnd = !scrollable.scrollForward();
                    }
                }
            }
        }
        return view;
    }

    /** Returns true if the device has feature WATCH_FEATURE */
    public static boolean isWatch(Context context) {
        return hasSystemFeature(context, WATCH_FEATURE);
    }

    /** Returns true if the device has a given system feature */
    public static boolean hasSystemFeature(Context context, String feature) {
        return context.getPackageManager().hasSystemFeature(feature);
    }
}
