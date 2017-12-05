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

    ImeEvent(@NonNull String eventName, int nestLevel, @NonNull String threadName, int threadId,
            boolean isMainThread, long enterTimestamp, long exitTimestamp, long enterWallTime,
            long exitWallTime, @NonNull Bundle arguments) {
        mEventName = eventName;
        mNestLevel = nestLevel;
        mThreadName = threadName;
        mThreadId = threadId;
        mIsMainThread = isMainThread;
        mEnterTimestamp = enterTimestamp;
        mExitTimestamp = exitTimestamp;
        mEnterWallTime = enterWallTime;
        mExitWallTime = exitWallTime;
        mArguments = arguments;
    }

    @NonNull
    final Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString("mEventName", mEventName);
        bundle.putInt("mNestLevel", mNestLevel);
        bundle.putString("mThreadName", mThreadName);
        bundle.putInt("mThreadId", mThreadId);
        bundle.putBoolean("mIsMainThread", mIsMainThread);
        bundle.putLong("mEnterTimestamp", mEnterTimestamp);
        bundle.putLong("mExitTimestamp", mExitTimestamp);
        bundle.putLong("mEnterWallTime", mEnterWallTime);
        bundle.putLong("mExitWallTime", mExitWallTime);
        bundle.putBundle("mArguments", mArguments);
        return bundle;
    }

    @NonNull
    static ImeEvent fromBundle(@NonNull Bundle bundle) {
        final String eventName = bundle.getString("mEventName");
        final int nestLevel = bundle.getInt("mNestLevel");
        final String threadName = bundle.getString("mThreadName");
        final int threadId = bundle.getInt("mThreadId");
        final boolean isMainThread = bundle.getBoolean("mIsMainThread");
        final long enterTimestamp = bundle.getLong("mEnterTimestamp");
        final long exitTimestamp = bundle.getLong("mExitTimestamp");
        final long enterWallTime = bundle.getLong("mEnterWallTime");
        final long exitWallTime = bundle.getLong("mExitWallTime");
        final Bundle arguments = bundle.getBundle("mArguments");
        return new ImeEvent(eventName, nestLevel, threadName,
                threadId, isMainThread, enterTimestamp, exitTimestamp, enterWallTime, exitWallTime,
                arguments);
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
     * Returns the nest level of this event.
     *
     * <p>For instance, when &quot;showSoftInput&quot; internally calls
     * &quot;onStartInputView&quot;, the event for &quot;onStartInputView&quot; has 1 level higher
     * nest level than &quot;showSoftInput&quot;.</p>
     */
    public int getNestLevel() {
        return mNestLevel;
    }

    /**
     * @return Name of the thread, where the event was consumed.
     */
    @NonNull
    public String getThreadName() {
        return mThreadName;
    }

    /**
     * @return Thread ID (TID) of the thread, where the event was consumed.
     */
    public int getThreadId() {
        return mThreadId;
    }

    /**
     * @return {@code true} if the event was being consumed in the main thread.
     */
    public boolean isMainThread() {
        return mIsMainThread;
    }

    /**
     * @return Monotonic time measured by {@link android.os.SystemClock#elapsedRealtimeNanos()} when
     *         the corresponding event handler was called back.
     */
    public long getEnterTimestamp() {
        return mEnterTimestamp;
    }

    /**
     * @return Monotonic time measured by {@link android.os.SystemClock#elapsedRealtimeNanos()} when
     *         the corresponding event handler finished.
     */
    public long getExitTimestamp() {
        return mExitTimestamp;
    }

    /**
     * @return Wall-clock time measured by {@link System#currentTimeMillis()} when the corresponding
     *         event handler was called back.
     */
    public long getEnterWallTime() {
        return mEnterWallTime;
    }

    /**
     * @return Wall-clock time measured by {@link System#currentTimeMillis()} when the corresponding
     *         event handler finished.
     */
    public long getExitWallTime() {
        return mExitWallTime;
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
    private final int mNestLevel;
    @NonNull
    private final String mThreadName;
    private final int mThreadId;
    private final boolean mIsMainThread;
    private final long mEnterTimestamp;
    private final long mExitTimestamp;
    private final long mEnterWallTime;
    private final long mExitWallTime;
    @NonNull
    private final Bundle mArguments;
}
