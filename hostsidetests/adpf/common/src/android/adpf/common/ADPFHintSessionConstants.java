/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adpf.common;

public class ADPFHintSessionConstants {
    public static final String LOG_KEY_VALUE_SPLITTER = " - ";
    public static final String LOG_TARGET_DURATION_PREFFIX = "Target" + LOG_KEY_VALUE_SPLITTER;
    public static final String LOG_ACTUAL_DURATION_PREFIX = "Actual" + LOG_KEY_VALUE_SPLITTER;
    public static final String LOG_TEST_APP_FAILED_PREFIX = "Test_Failed" + LOG_KEY_VALUE_SPLITTER;

    public static final String TEST_NAME_KEY = "Test_Name";

    public static final int MINIMUM_VALID_SDK = 32; // T
}
