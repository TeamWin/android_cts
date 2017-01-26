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
 * limitations under the License.
 */

package com.android.compatibility.common.util;

import android.os.SystemProperties;

/**
 * Device-side utility class for reading properties and gathering information for testing
 * Android device compatibility.
 */
public class PropertyUtil {

    /**
     * Name of read-only property detailing the first API level for which the product was
     * shipped. Property should be undefined for factory ROM products.
     */
    public static String FIRST_API_LEVEL = "ro.product.first_api_level";

    /** Value to be returned by SystemProperties.getInt() if property is not found */
    public static int INT_VALUE_IF_UNSET = -1;

    /** Returns whether the device build is the factory ROM */
    public static boolean isFactoryROM() {
        // property should be undefined if and only if the product is factory ROM.
        return getFirstApiLevel() == INT_VALUE_IF_UNSET;
    }

    /** Return value of read-only property "ro.product.first_api_level" */
    public static int getFirstApiLevel() {
        return SystemProperties.getInt(FIRST_API_LEVEL, INT_VALUE_IF_UNSET);
    }
}
