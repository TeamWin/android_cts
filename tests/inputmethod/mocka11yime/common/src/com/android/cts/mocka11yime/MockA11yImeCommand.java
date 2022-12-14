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

package com.android.cts.mocka11yime;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An immutable data that describes command details sent to MockA11yIme.
 */
public final class MockA11yImeCommand {

    private static final String NAME_KEY = "name";
    private static final String ID_KEY = "id";
    private static final String DISPATCH_TO_MAIN_THREAD_KEY = "dispatchToMainThread";
    private static final String EXTRA_KEY = "extra";

    @NonNull
    private final String mName;
    private final long mId;
    private final boolean mDispatchToMainThread;
    @Nullable
    private final Bundle mExtras;

    MockA11yImeCommand(@NonNull String name, long id, boolean dispatchToMainThread,
            @NonNull Bundle extras) {
        mName = name;
        mId = id;
        mDispatchToMainThread = dispatchToMainThread;
        mExtras = extras;
    }

    private MockA11yImeCommand(@NonNull Bundle bundle) {
        mName = bundle.getString(NAME_KEY);
        mId = bundle.getLong(ID_KEY);
        mDispatchToMainThread = bundle.getBoolean(DISPATCH_TO_MAIN_THREAD_KEY);
        mExtras = bundle.getBundle(EXTRA_KEY);
    }

    static MockA11yImeCommand fromBundle(@NonNull Bundle bundle) {
        return new MockA11yImeCommand(bundle);
    }

    Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(NAME_KEY, mName);
        bundle.putLong(ID_KEY, mId);
        bundle.putBoolean(DISPATCH_TO_MAIN_THREAD_KEY, mDispatchToMainThread);
        bundle.putBundle(EXTRA_KEY, mExtras);
        return bundle;
    }

    /**
     * @return The name of this command.  Usually this is a human-readable string.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * @return The unique ID of this command.
     */
    public long getId() {
        return mId;
    }

    /**
     * @return {@code true} if this command needs to be handled on the main thread of MockA11yIme.
     */
    public boolean shouldDispatchToMainThread() {
        return mDispatchToMainThread;
    }

    /**
     * @return {@link Bundle} that stores extra parameters of this command.
     */
    @Nullable
    public Bundle getExtras() {
        return mExtras;
    }
}
