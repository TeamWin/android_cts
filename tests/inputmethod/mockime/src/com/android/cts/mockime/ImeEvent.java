/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.mockime;

import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * An immutable object that stores event happened in the {@link MockIme}.
 */
public final class ImeEvent {

    ImeEvent(@NonNull String eventName, @NonNull Bundle arguments) {
        mEventName = eventName;
        mArguments = arguments;
    }

    @NonNull
    final Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString("mEventName", mEventName);
        bundle.putBundle("mArguments", mArguments);
        return bundle;
    }

    @NonNull
    static ImeEvent fromBundle(@NonNull Bundle bundle) {
        final String eventName = bundle.getString("mEventName");
        final Bundle arguments = bundle.getBundle("mArguments");
        return new ImeEvent(eventName, arguments);
    }

    /**
     * Returns a string that represents the type of this event.
     *
     * <p>Examples: &quot;onCreate&quot;, &quot;onStartInput&quot;, ...</p>
     *
     * <p>TODO: Use enum type or something like that instead of raw String type.</p>
     * @return A string that represents the type of this event.
     */
    @NonNull
    public String getEventName() {
        return mEventName;
    }

    /**
     * @return {@link Bundle} that stores parameters passed to the corresponding event handler.
     */
    @NonNull
    public Bundle getArguments() {
        return mArguments;
    }

    @NonNull
    private final String mEventName;
    @NonNull
    private final Bundle mArguments;
}
