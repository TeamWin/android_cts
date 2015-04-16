/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.inputmethod.cts;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.inputmethodservice.InputMethodService;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A mock implementation of {@link InputMethodService} for testing purpose.
 */
public class MockInputMethodService extends InputMethodService {
    private static AtomicReference<MockInputMethodService> sCurrentInstance =
            new AtomicReference<>();
    private static final HashMap<String, Integer> mCallCounter = new HashMap<>();

    /**
     * @return The instance of {@code MockInputMethodService}. If the service has not been created
     * yet or already been destroyed, returns {@code null}.
     */
    @Nullable
    public static MockInputMethodService getInstance() {
        return sCurrentInstance.get();
    }

    public static void resetCounter() {
        synchronized (mCallCounter) {
            mCallCounter.clear();
        }
    }

    private static void incrementCallCount(@NonNull final String methodName) {
        synchronized (mCallCounter) {
            if (!mCallCounter.containsKey(methodName)) {
                mCallCounter.put(methodName, 0);
            }
            mCallCounter.put(methodName, mCallCounter.get(methodName) + 1);
        }
    }

    public static int getCallCount(@NonNull final String methodName) {
        synchronized (mCallCounter) {
            if (!mCallCounter.containsKey(methodName)) {
                return 0;
            }
            return mCallCounter.get(methodName);
        }
    }

    public MockInputMethodService() {
        incrementCallCount("<init>");
    }

    @Override
    public void onCreate() {
        if (!sCurrentInstance.compareAndSet(null, this)) {
            throw new IllegalStateException("New MockInputMethodService instance is being created "
                    + "before the existing instance is destroyed.");
        }

        super.onCreate();
        incrementCallCount("onCreate");
    }

    @Override
    public void onDestroy() {
        sCurrentInstance.lazySet(null);
        super.onDestroy();
        incrementCallCount("onDestroy");
    }
}

