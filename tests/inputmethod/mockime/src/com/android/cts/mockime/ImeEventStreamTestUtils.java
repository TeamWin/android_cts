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

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.inputmethod.InputBinding;

import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * A set of utility methods to avoid boilerplate code when writing end-to-end tests.
 */
public final class ImeEventStreamTestUtils {
    private static final long TIME_SLICE = 50;  // msec

    /**
     * Cannot be instantiated
     */
    private ImeEventStreamTestUtils() {}

    /**
     * Wait until an event that matches the given {@code condition} is found in the stream.
     *
     * <p>When this method succeeds to find an event that matches the given {@code condition}, the
     * stream position will be set to the next to the found object then the event found is returned.
     * </p>
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param timeout timeout in millisecond
     * @return {@link ImeEvent} found
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    @NonNull
    public static ImeEvent expectEvent(@NonNull ImeEventStream stream,
            @NonNull Predicate<ImeEvent> condition, long timeout) throws TimeoutException {
        try {
            Optional<ImeEvent> result;
            while (true) {
                if (timeout < 0) {
                    throw new TimeoutException(
                            "event not found within the timeout: " + stream.dump());
                }
                result = stream.seekToFirst(condition);
                if (result.isPresent()) {
                    break;
                }
                Thread.sleep(TIME_SLICE);
                timeout -= TIME_SLICE;
            }
            final ImeEvent event = result.get();
            if (event == null) {
                throw new NullPointerException("found event is null: " + stream.dump());
            }
            stream.skip(1);
            return event;
        } catch (InterruptedException e) {
            throw new RuntimeException("expectEvent failed: " + stream.dump(), e);
        }
    }

    /**
     * Assert that an event that matches the given {@code condition} will no be found in the stream
     * within the given {@code timeout}.
     *
     * <p>Fails with {@link junit.framework.Assert#fail} if such an event is  found within the given
     * {@code timeout}.</p>
     *
     * <p>When this method succeeds, the stream position will not change.</p>
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param timeout timeout in millisecond
     */
    public static void notExpectEvent(@NonNull ImeEventStream stream,
            @NonNull Predicate<ImeEvent> condition, long timeout) {
        try {
            while (true) {
                if (timeout < 0) {
                    return;
                }
                if (stream.findFirst(condition).isPresent()) {
                    throw new AssertionError("notExpectEvent failed: " + stream.dump());
                }
                Thread.sleep(TIME_SLICE);
                timeout -= TIME_SLICE;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("notExpectEvent failed: " + stream.dump(), e);
        }
    }

    /**
     * A specialized version of {@link #expectEvent(ImeEventStream, Predicate, long)} to wait for
     * {@link android.view.inputmethod.InputMethod#bindInput(InputBinding)}.
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param targetProcessPid PID to be matched to {@link InputBinding#getPid()}
     * @param timeout timeout in millisecond
     * @throws TimeoutException when "bindInput" is not called within {@code timeout} msec
     */
    public static void expectBindInput(@NonNull ImeEventStream stream, int targetProcessPid,
            long timeout) throws TimeoutException {
        expectEvent(stream, event -> {
            if (!TextUtils.equals("bindInput", event.getEventName())) {
                return false;
            }
            final InputBinding binding = event.getArguments().getParcelable("binding");
            return binding.getPid() == targetProcessPid;
        }, timeout);
    }

}
