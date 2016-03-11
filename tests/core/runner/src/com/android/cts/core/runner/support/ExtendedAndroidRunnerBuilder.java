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
package com.android.cts.core.runner.support;

import android.support.test.internal.runner.junit4.AndroidAnnotatedBuilder;
import android.support.test.internal.util.AndroidRunnerParams;
import org.junit.internal.builders.AnnotatedBuilder;
import org.junit.runners.model.RunnerBuilder;

/**
 * Extends {@link AndroidRunnerBuilder} in order to provide alternate {@link RunnerBuilder}
 * implementations.
 */
public class ExtendedAndroidRunnerBuilder extends AndroidRunnerBuilder {

    private final AndroidAnnotatedBuilder mAndroidAnnotatedBuilder;

    /**
     * @param runnerParams {@link AndroidRunnerParams} that stores common runner parameters
     */
    public ExtendedAndroidRunnerBuilder(AndroidRunnerParams runnerParams) {
        super(runnerParams, false /* CTSv1 filtered out Test suite() classes. */);
        mAndroidAnnotatedBuilder = new ExtendedAndroidAnnotatedBuilder(this, runnerParams);
    }

    @Override
    protected AnnotatedBuilder annotatedBuilder() {
        return mAndroidAnnotatedBuilder;
    }
}
