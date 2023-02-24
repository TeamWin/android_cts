/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A stub accessibility service to enable when an
 * {@link android.view.accessibility.AccessibilityDisplayProxy} is registered.
 */
public class StubProxyConcurrentAccessibilityService extends InstrumentedAccessibilityService {
    AtomicBoolean mReceivedEvent = new AtomicBoolean();
    Object mWaitObject = new Object();
    UiAutomation.AccessibilityEventFilter mEventFilter;

    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        if (mEventFilter != null) {
            if (mEventFilter.accept(event)) {
                synchronized (mWaitObject) {
                    mReceivedEvent.set(true);
                    mWaitObject.notifyAll();
                }
            }
        }
    }

    public void setEventFilter(@NonNull UiAutomation.AccessibilityEventFilter filter) {
        mEventFilter = filter;
    }

}
