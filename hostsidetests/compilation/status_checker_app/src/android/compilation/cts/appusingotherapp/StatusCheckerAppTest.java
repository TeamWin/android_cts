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

package android.compilation.cts.statuscheckerapp;

import static dalvik.system.DexFile.OptimizationInfo;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import dalvik.system.ApplicationRuntime;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * An instrumentation test that checks optimization status.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class StatusCheckerAppTest {
    private static final String TAG = "StatusCheckerAppTest";

    @Test
    public void checkStatus() throws Exception {
        Bundle bundle = InstrumentationRegistry.getArguments();
        OptimizationInfo info = ApplicationRuntime.getBaseApkOptimizationInfo();
        assertThat(info.getStatus()).isEqualTo(bundle.getString("compiler-filter"));
        assertThat(info.getReason()).isEqualTo(bundle.getString("compilation-reason"));
        assertThat(info.isVerified()).isEqualTo(bundle.getString("is-verified").equals("true"));
        assertThat(info.isOptimized()).isEqualTo(bundle.getString("is-optimized").equals("true"));
        assertThat(info.isFullyCompiled())
                .isEqualTo(bundle.getString("is-fully-compiled").equals("true"));
    }
}
