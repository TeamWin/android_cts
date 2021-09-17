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

import android.graphics.Rect;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;

import java.util.regex.Pattern;

public class UiAutomatorUtils {
    private UiAutomatorUtils() {}

    private static Pattern sCollapsingToolbarResPattern =
            Pattern.compile(".*:id/collapsing_toolbar");

    public static UiDevice getUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public static UiObject2 waitFindObject(BySelector selector) throws UiObjectNotFoundException {
        return waitFindObject(selector, 20_000);
    }

    public static UiObject2 waitFindObject(BySelector selector, long timeoutMs)
            throws UiObjectNotFoundException {
        final UiObject2 view = waitFindObjectOrNull(selector, timeoutMs);
        ExceptionUtils.wrappingExceptions(UiDumpUtils::wrapWithUiDump, () -> {
            assertNotNull("View not found after waiting for " + timeoutMs + "ms: " + selector,
                    view);
        });
        return view;
    }

    public static UiObject2 waitFindObjectOrNull(BySelector selector)
            throws UiObjectNotFoundException {
        return waitFindObjectOrNull(selector, 20_000);
    }

    public static UiObject2 waitFindObjectOrNull(BySelector selector, long timeoutMs)
            throws UiObjectNotFoundException {
        UiObject2 view = null;
        long start = System.currentTimeMillis();

        boolean isAtEnd = false;
        boolean wasScrolledUpAlready = false;
        boolean scrolledPastCollapsibleToolbar = false;

        while (view == null && start + timeoutMs > System.currentTimeMillis()) {
            view = getUiDevice().wait(Until.findObject(selector), 1000);

            if (view == null) {
                UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
                if (!FeatureUtil.isWatch() && !FeatureUtil.isTV()) {
                    scrollable.setSwipeDeadZonePercentage(0.25);
                }
                if (scrollable.exists()) {
                    if (!scrolledPastCollapsibleToolbar) {
                        scrollPastCollapsibleToolbar(scrollable);
                        scrolledPastCollapsibleToolbar = true;
                        continue;
                    }
                    if (isAtEnd) {
                        if (wasScrolledUpAlready) {
                            return null;
                        }
                        scrollable.scrollToBeginning(Integer.MAX_VALUE);
                        isAtEnd = false;
                        wasScrolledUpAlready = true;
                        scrolledPastCollapsibleToolbar = false;
                    } else {
                        Rect boundsBeforeScroll = scrollable.getBounds();
                        boolean scrollAtStartOrEnd = !scrollable.scrollForward();
                        Rect boundsAfterScroll = scrollable.getBounds();
                        isAtEnd = scrollAtStartOrEnd && boundsBeforeScroll.equals(
                                boundsAfterScroll);
                    }
                }
            }
        }
        return view;
    }

    private static void scrollPastCollapsibleToolbar(UiScrollable scrollable)
            throws UiObjectNotFoundException {
        final UiObject2 collapsingToolbar = getUiDevice().findObject(
                By.res(sCollapsingToolbarResPattern));
        if (collapsingToolbar == null) {
            return;
        }

        final Rect scrollableBounds = scrollable.getVisibleBounds();
        final int distanceToSwipe = collapsingToolbar.getVisibleBounds().height() / 2;
        final int steps = 55; // == UiScrollable.SCROLL_STEPS
        getUiDevice().drag(scrollableBounds.centerX(), scrollableBounds.centerY(),
                scrollableBounds.centerX(), scrollableBounds.centerY() - distanceToSwipe, steps);
    }
}
