package com.android.compatibility.common.util;
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


import static org.junit.Assert.assertNotNull;

import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;

public class UiAutomatorUtils {
    private UiAutomatorUtils() {}

    public static UiDevice getUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public static UiObject2 waitFindObject(BySelector selector) {
        return waitFindObject(selector, 100_000);
    }

    public static UiObject2 waitFindObject(BySelector selector, long timeoutMs) {
        final UiObject2 view = waitFindObjectOrNull(selector, timeoutMs);
        ExceptionUtils.wrappingExceptions(UiDumpUtils::wrapWithUiDump, () -> {
            assertNotNull("View not found after waiting for " + timeoutMs + "ms: " + selector,
                    view);
        });
        return view;
    }

    public static UiObject2 waitFindObjectOrNull(BySelector selector, long timeoutMs) {
        UiObject2 view = null;
        long start = System.currentTimeMillis();
        while (view == null && start + timeoutMs > System.currentTimeMillis()) {
            view = getUiDevice().wait(Until.findObject(selector), timeoutMs / 10);

            if (view == null) {
                UiObject2 scrollable = getUiDevice().findObject(By.scrollable(true));
                if (scrollable != null) {
                    scrollable.scroll(Direction.DOWN, 1);
                }
            }
        }
        return view;
    }
}
