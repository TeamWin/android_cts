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

package android.backup.cts.backuprestoreapp;

final class Constants {
    // Names of raw data files used by the tests
    static final String TEST_FILE_1 = "test-file-1";
    static final String TEST_FILE_2 = "test-file-2";

    static final String TEST_PREFS_1 = "test-prefs-1";
    static final String INT_PREF = "int-pref";
    static final String BOOL_PREF = "bool-pref";

    static final String TEST_PREFS_2 = "test-prefs-2";
    static final String FLOAT_PREF = "float-pref";
    static final String LONG_PREF = "long-pref";
    static final String STRING_PREF = "string-pref";

    static final int DEFAULT_INT_VALUE = 0;
    static final boolean DEFAULT_BOOL_VALUE = false;

    static final float DEFAULT_FLOAT_VALUE = 0.0f;
    static final long DEFAULT_LONG_VALUE = 0L;
    static final String DEFAULT_STRING_VALUE = null;

    // Shared prefs test activity actions
    static final String INIT_ACTION = "android.backup.cts.backuprestore.INIT";
    static final String UPDATE_ACTION = "android.backup.cts.backuprestore.UPDATE";
    static final String TEST_ACTION = "android.backup.cts.backuprestore.TEST";
}
