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
 * limitations under the License
 */

package com.android.cts.mockime;

import android.os.Bundle;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.view.inputmethod.EditorInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A utility class that provides basic query operations and wait primitives for a series of
 * {@link ImeEvent} sent from the {@link MockIme}.
 *
 * <p>All public methods are not thread-safe.</p>
 */
public final class ImeEventStream {

    @NonNull
    private final Supplier<ImeEventArray> mEventSupplier;
    private int mCurrentPosition;

    ImeEventStream(@NonNull Supplier<ImeEventArray> supplier) {
        this(supplier, 0 /* position */);
    }

    private ImeEventStream(@NonNull Supplier<ImeEventArray> supplier, int position) {
        mEventSupplier = supplier;
        mCurrentPosition = position;
    }

    /**
     * Create a copy that starts from the same event position of this stream. Once a copy is created
     * further event position change on this stream will not affect the copy.
     *
     * @return A new copy of this stream
     */
    public ImeEventStream copy() {
        return new ImeEventStream(mEventSupplier, mCurrentPosition);
    }

    /**
     * Advances the current event position by skipping events.
     *
     * @param length number of events to be skipped
     * @throws IllegalArgumentException {@code length} is negative
     */
    public void skip(@IntRange(from = 0) int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative: " + length);
        }
        mCurrentPosition += length;
    }

    /**
     * Find the first event that matches the given condition from the current position.
     *
     * <p>If there is such an event, this method returns such an event without moving the current
     * event position.</p>
     *
     * <p>If there is such an event, this method returns {@link Optional#empty()} without moving the
     * current event position.</p>
     *
     * @param condition the event condition to be matched
     * @return {@link Optional#empty()} if there is no such an event. Otherwise the matched event is
     *         returned
     */
    @NonNull
    public Optional<ImeEvent> findFirst(Predicate<ImeEvent> condition) {
        final ImeEventArray latest = mEventSupplier.get();
        int index = mCurrentPosition;
        while (true) {
            if (index >= latest.mLength) {
                return Optional.empty();
            }
            if (condition.test(latest.mArray[index])) {
                return Optional.of(latest.mArray[index]);
            }
            ++index;
        }
    }

    /**
     * Find the first event that matches the given condition from the current position.
     *
     * <p>If there is such an event, this method returns such an event and set the current event
     * position to that event.</p>
     *
     * <p>If there is such an event, this method returns {@link Optional#empty()} without moving the
     * current event position.</p>
     *
     * @param condition the event condition to be matched
     * @return {@link Optional#empty()} if there is no such an event. Otherwise the matched event is
     *         returned
     */
    @NonNull
    public Optional<ImeEvent> seekToFirst(Predicate<ImeEvent> condition) {
        final ImeEventArray latest = mEventSupplier.get();
        while (true) {
            if (mCurrentPosition >= latest.mLength) {
                return Optional.empty();
            }
            if (condition.test(latest.mArray[mCurrentPosition])) {
                return Optional.of(latest.mArray[mCurrentPosition]);
            }
            ++mCurrentPosition;
        }
    }

    /**
     * @return Debug info as a {@link String}.
     */
    public String dump() {
        final ImeEventArray latest = mEventSupplier.get();
        final StringBuilder sb = new StringBuilder();
        final SimpleDateFormat dataFormat =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        sb.append("ImeEventStream:\n");
        sb.append("  latest: array[").append(latest.mArray.length).append("] + {\n");
        for (int i = 0; i < latest.mLength; ++i) {
            final ImeEvent event = latest.mArray[i];
            if (i == mCurrentPosition) {
                sb.append("  ======== CurrentPosition ========  \n");
            }
            sb.append("   ").append(i).append(" :");
            if (event != null) {
                for (int j = 0; j < event.getNestLevel(); ++j) {
                    sb.append(' ');
                }
                sb.append('{');
                sb.append(dataFormat.format(new Date(event.getEnterWallTime())));
                sb.append(" event=").append(event.getEventName());
                sb.append(": args=");
                dumpBundle(sb, event.getArguments());
                sb.append("},\n");
            } else {
                sb.append("{null},\n");
            }
        }
        if (mCurrentPosition >= latest.mLength) {
            sb.append("  ======== CurrentPosition ========  \n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static final void dumpBundle(@NonNull StringBuilder sb, @NonNull Bundle bundle) {
        sb.append('{');
        boolean first = true;
        for (String key : bundle.keySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(' ');
            }
            final Object object = bundle.get(key);
            sb.append(key);
            sb.append('=');
            if (object instanceof EditorInfo) {
                final EditorInfo info = (EditorInfo) object;
                sb.append("EditorInfo{packageName=").append(info.packageName);
                sb.append(" fieldId=").append(info.fieldId);
                sb.append(" hintText=").append(info.hintText);
                sb.append(" privateImeOptions=").append(info.privateImeOptions);
                sb.append("}");
            } else {
                sb.append(object);
            }
        }
        sb.append('}');
    }

    final static class ImeEventArray {
        @NonNull
        public final ImeEvent[] mArray;
        public final int mLength;
        public ImeEventArray(ImeEvent[] array, int length) {
            mArray = array;
            mLength = length;
        }
    }
}
