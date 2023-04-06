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

package android.localeconfig.cts.util;

/**
 * Common constants used across {@link com.android.cts.localeconfig.LocaleConfigAppUpdateTest}
 * and the test apps.
 */
public final class LocaleConstants {

    private LocaleConstants() {}

    public static final String APP_CREATION_INFO_PROVIDER_ACTION =
            "android.localeconfig.cts.action.APP_CREATION_INFO_PROVIDER";

    public static final String EXTRA_QUERY_LOCALES = "query_locales";

    public static final String EXTRA_SET_LOCALES = "set_locales";

    public static final String EXTRA_SET_LOCALECONFIG = "set_localeconfig";
}
