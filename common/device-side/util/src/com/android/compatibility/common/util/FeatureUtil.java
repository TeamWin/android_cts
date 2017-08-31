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

import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;

/**
 * Device-side utility class for detecting system features
 */
public class FeatureUtil {

    /** Returns true if the device has a given system feature */
    public static boolean hasSystemFeature(String feature) {
        return getPackageManager().hasSystemFeature(feature);
    }

    /** Returns true if the device lacks a given system feature */
    public static boolean lacksSystemFeature(String feature) {
        return !hasSystemFeature(feature);
    }

    /** Returns true if the device has any feature in a given collection of system features */
    public static boolean hasAnySystemFeature(String... features) {
        PackageManager pm = getPackageManager();
        for (String feature : features) {
            if (pm.hasSystemFeature(feature)) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if the device lacks any feature in a given collection of system features */
    public static boolean lacksAnySystemFeature(String... features) {
        return !hasAllSystemFeatures(features);
    }

    /** Returns true if the device has all features in a given collection of system features */
    public static boolean hasAllSystemFeatures(String... features) {
        PackageManager pm = getPackageManager();
        for (String feature : features) {
            if (!pm.hasSystemFeature(feature)) {
                return false;
            }
        }
        return true;
    }

    /** Returns true if the device lacks all features in a given collection of system features */
    public static boolean lacksAllSystemFeatures(String... features) {
        return !hasAnySystemFeature(features);
    }

    private static PackageManager getPackageManager() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
    }
}
