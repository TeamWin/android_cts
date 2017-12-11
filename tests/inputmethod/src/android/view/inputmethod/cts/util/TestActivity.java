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

package android.view.inputmethod.cts.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.view.View;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class TestActivity extends Activity {

    private static final AtomicReference<Function<TestActivity, View>> sInitializer =
            new AtomicReference<>();

    private Function<TestActivity, View> mInitializer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mInitializer == null) {
            mInitializer = sInitializer.get();
            sInitializer.set(null);
        }
        setContentView(mInitializer.apply(this));
    }

    /**
     * Launches {@link TestActivity} with the given initialization logic for content view.
     *
     * <p>As long as you are using {@link android.support.test.runner.AndroidJUnitRunner}, the test
     * runner automatically calls {@link Activity#finish()} for the {@link Activity} launched when
     * the test finished.  You do not need to explicitly call {@link Activity#finish()}.</p>
     *
     * @param activityInitializer An initializer to supply {@link View} to be passed to
     *                           {@link Activity#setContentView(View)}
     * @return {@link TestActivity} launched
     */
    public static TestActivity startSync(
            @NonNull Function<TestActivity, View> activityInitializer) {
        sInitializer.set(activityInitializer);
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_MAIN)
                .setClass(InstrumentationRegistry.getContext(), TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return (TestActivity) InstrumentationRegistry
                .getInstrumentation().startActivitySync(intent);
    }
}
