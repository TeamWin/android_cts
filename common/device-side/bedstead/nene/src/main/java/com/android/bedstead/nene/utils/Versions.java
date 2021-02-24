/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.utils;

import static android.os.Build.VERSION.SDK_INT;

import android.os.Build;

/** Version constants used when VERSION_CODES is not final. */
public final class Versions {
    // TODO(scottjonathan): Replace once S version is final
    public static final int S = 31;

    private Versions() {

    }

    /** Require that this is running on Android S or above. */
    public static void requireS() {
        if (!isRunningOn(S, "S")) {
            throw new UnsupportedOperationException(
                    "keepUninstalledPackages is only available on S+ (currently "
                            + Build.VERSION.CODENAME + ")");
        }
    }

    /** True if the app is running on the given Android version or above. */
    public static boolean isRunningOn(int version, String codename) {
        return (SDK_INT >= version || Build.VERSION.CODENAME.equals(codename));
    }
}
