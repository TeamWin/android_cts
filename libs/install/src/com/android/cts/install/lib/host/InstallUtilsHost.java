/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.install.lib.host;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

/**
 * Utilities to facilitate installation in tests on host side.
 */
public class InstallUtilsHost {

    private BaseHostJUnit4Test mTest;

    public InstallUtilsHost(BaseHostJUnit4Test test) {
        mTest = test;
    }

    /**
     * Return {@code true} if and only if device supports updating apex.
     */
    public boolean isApexUpdateSupported() throws Exception {
        return mTest.getDevice().getBooleanProperty("ro.apex.updatable", false);
    }
}
