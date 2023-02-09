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
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A stub accessibility service to enable when an
 * {@link android.view.accessibility.AccessibilityDisplayProxy} is registered.
 */
public class StubProxyConcurrentAccessibilityService extends InstrumentedAccessibilityService {
    private AccessibilityEvent mUnwantedEvent;
    AtomicBoolean mReceivedUnwantedEvent = new AtomicBoolean();

    private AccessibilityEvent mExpectedEvent;
    AtomicBoolean mReceivedEvent = new AtomicBoolean();

    Object mWaitObject = new Object();

    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        if (mUnwantedEvent != null) {
            if (event.getEventType() == mUnwantedEvent.getEventType()
                    && event.getClassName().equals(mUnwantedEvent.getClassName())
                    && event.getDisplayId() == mUnwantedEvent.getDisplayId()
                    && event.getText().equals(mUnwantedEvent.getText())) {
                synchronized (mWaitObject) {
                    mReceivedUnwantedEvent.set(true);
                    mWaitObject.notifyAll();
                }
            }
        } else if (mExpectedEvent != null) {
            if (event.getEventType() == mExpectedEvent.getEventType()) {
                synchronized (mWaitObject) {
                    mReceivedEvent.set(true);
                    mWaitObject.notifyAll();
                }
            }
        }
    }

    public void setUnwantedEvent(@NonNull AccessibilityEvent event) {
        mUnwantedEvent = event;
    }

    public void setExpectedEvent(@NonNull AccessibilityEvent event) {
        mExpectedEvent = event;
    }
}
