/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * Stub accessibility service for testing APIs to show/hide Soft Keyboard.
 */
public class StubSoftKeyboardModesAccessibilityService extends AccessibilityService {

    public static Object sWaitObjectForConnecting = new Object();

    public static StubSoftKeyboardModesAccessibilityService sInstance = null;

    @Override
    protected void onServiceConnected() {
        synchronized (sWaitObjectForConnecting) {
            sInstance = this;
            sWaitObjectForConnecting.notifyAll();
        }
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        /* do nothing */
    }

    @Override
    public void onInterrupt() {
        /* do nothing */
    }

    public SoftKeyboardController getTestSoftKeyboardController() {
        return sInstance.getSoftKeyboardController();
    }

    public int getTestWindowsListSize() {
        return sInstance.getWindows().size();
    }
}
