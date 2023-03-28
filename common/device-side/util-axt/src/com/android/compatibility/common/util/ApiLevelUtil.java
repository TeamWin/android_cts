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
import static android.os.Build.VERSION.SDK_INT;

import android.os.Build;
import android.os.SystemProperties;

import java.lang.reflect.Field;

/**
 * Device-side compatibility utility class for reading device API level.
 */
public class ApiLevelUtil {

    // Build.VERSION.DEVICE_INITIAL_SDK_INT was introduced in Android S, it was called
    // Build.VERSION.FIRST_SDK_INT before that. FIRST_SDK_INT can't be used here for older versions
    // as that has been removed from Build.VERSION, so ro.product.first_api_level is used for
    // versions Android R and before
    private static final int FIRST_API_LEVEL = Sdk.isAfterR()
            ? Build.VERSION.DEVICE_INITIAL_SDK_INT
            : SystemProperties.getInt("ro.product.first_api_level", 0);

    private static final boolean IS_REL_VERSION = Build.VERSION.CODENAME.equals("REL");
    private static final String CODENAME_Q = "Q";
    private static final String CODENAME_R = "R";
    private static final String CODENAME_S = "S";
    private static final String CODENAME_S_V2 = "Sv2";
    private static final String CODENAME_T = "Tiramisu";
    private static final String CODENAME_U = "UpsideDownCake";


    private static void verifyVersion(int version) {
        if (version == Build.VERSION_CODES.CUR_DEVELOPMENT) {
            throw new RuntimeException("Invalid version: " + version);
        }
    }

    public static boolean isBefore(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT < version;
    }

    public static boolean isBefore(String version) {
        return isBefore(resolveVersionString(version));
    }

    public static boolean isAfter(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT > version;
    }

    public static boolean isAfter(String version) {
        return isAfter(resolveVersionString(version));
    }

    public static boolean isAtLeast(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT >= version;
    }

    public static boolean isAtLeast(String version) {
        return isAtLeast(resolveVersionString(version));
    }

    public static boolean isAtMost(int version) {
        verifyVersion(version);
        return Build.VERSION.SDK_INT <= version;
    }

    public static boolean isAtMost(String version) {
        return isAtMost(resolveVersionString(version));
    }

    public static int getApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    public static boolean isFirstApiBefore(int version) {
        verifyVersion(version);
        return FIRST_API_LEVEL < version;
    }

    public static boolean isFirstApiBefore(String version) {
        return isFirstApiBefore(resolveVersionString(version));
    }

    public static boolean isFirstApiAfter(int version) {
        verifyVersion(version);
        return FIRST_API_LEVEL > version;
    }

    public static boolean isFirstApiAfter(String version) {
        return isFirstApiAfter(resolveVersionString(version));
    }

    public static boolean isFirstApiAtLeast(int version) {
        return FIRST_API_LEVEL >= version;
    }

    public static boolean isFirstApiAtLeast(String version) {
        return isFirstApiAtLeast(resolveVersionString(version));
    }

    public static boolean isFirstApiAtMost(int version) {
        return FIRST_API_LEVEL <= version;
    }

    public static boolean isFirstApiAtMost(String version) {
        return isFirstApiAtMost(resolveVersionString(version));
    }

    public static int getFirstApiLevel() {
        return FIRST_API_LEVEL;
    }

    public static boolean codenameEquals(String name) {
        return Build.VERSION.CODENAME.equalsIgnoreCase(name.trim());
    }

    public static boolean codenameStartsWith(String prefix) {
        return Build.VERSION.CODENAME.startsWith(prefix);
    }

    public static String getCodename() {
        return Build.VERSION.CODENAME;
    }

    protected static int resolveVersionString(String versionString) {
        // Attempt 1: Parse version string as an integer, e.g. "23" for M
        try {
            return Integer.parseInt(versionString);
        } catch (NumberFormatException e) { /* ignore for alternate approaches below */ }
        // Attempt 2: Find matching field in VersionCodes utility class, return value
        try {
            Field versionField = VersionCodes.class.getField(versionString.toUpperCase());
            return versionField.getInt(null); // no instance for VERSION_CODES, use null
        } catch (IllegalAccessException | NoSuchFieldException e) { /* ignore */ }
        // Attempt 3: Find field within android.os.Build.VERSION_CODES
        try {
            Field versionField = Build.VERSION_CODES.class.getField(versionString.toUpperCase());
            return versionField.getInt(null); // no instance for VERSION_CODES, use null
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(
                    String.format("Failed to parse version string %s", versionString), e);
        }
    }

    /**
     * Utils to check SDK version (Based on Build.VERSION.SDK_INT and Build.VERSION.CODENAME)
     */
    public static class Sdk {
        /**
         * Checks if the device is running a version before Android Q.
         */
        public static boolean isBeforeQ() {
            return isBefore(Build.VERSION_CODES.Q, CODENAME_Q);
        }

        /**
         * Checks if the device is running Android Q or older.
         */
        public static boolean isAtMostQ() {
            return isAtMost(Build.VERSION_CODES.Q, CODENAME_Q);
        }

        /**
         * Checks if the device is running Android Q.
         */
        public static boolean isQ() {
            return is(Build.VERSION_CODES.Q, CODENAME_Q);
        }

        /**
         * Checks if the device is running Android Q or newer.
         */
        public static boolean isAtLeastQ() {
            return isAtLeast(Build.VERSION_CODES.Q, CODENAME_Q);
        }

        /**
         * Checks if the device is running a version after Android Q.
         */
        public static boolean isAfterQ() {
            return isAfter(Build.VERSION_CODES.Q, CODENAME_Q);
        }

        /**
         * Checks if the device is running a version before Android R.
         */
        public static boolean isBeforeR() {
            return isBefore(Build.VERSION_CODES.R, CODENAME_R);
        }

        /**
         * Checks if the device is running Android R or older.
         */
        public static boolean isAtMostR() {
            return isAtMost(Build.VERSION_CODES.R, CODENAME_R);
        }

        /**
         * Checks if the device is running Android R.
         */
        public static boolean isR() {
            return is(Build.VERSION_CODES.R, CODENAME_R);
        }

        /**
         * Checks if the device is running Android R or newer.
         */
        public static boolean isAtLeastR() {
            return isAtLeast(Build.VERSION_CODES.R, CODENAME_R);
        }

        /**
         * Checks if the device is running a version after Android R.
         */
        public static boolean isAfterR() {
            return isAfter(Build.VERSION_CODES.R, CODENAME_R);
        }

        /**
         * Checks if the device is running a version before Android S.
         */
        public static boolean isBeforeS() {
            return isBefore(Build.VERSION_CODES.S, CODENAME_S);
        }

        /**
         * Checks if the device is running Android S or older.
         */
        public static boolean isAtMostS() {
            return isAtMost(Build.VERSION_CODES.S, CODENAME_S);
        }

        /**
         * Checks if the device is running Android S.
         */
        public static boolean isS() {
            return is(Build.VERSION_CODES.S, CODENAME_S);
        }

        /**
         * Checks if the device is running Android S or newer.
         */
        public static boolean isAtLeastS() {
            return isAtLeast(Build.VERSION_CODES.S, CODENAME_S);
        }

        /**
         * Checks if the device is running a version after Android S.
         */
        public static boolean isAfterS() {
            return isAfter(Build.VERSION_CODES.S, CODENAME_S);
        }

        /**
         * Checks if the device is running a version before Android S_V2.
         */
        public static boolean isBeforeSv2() {
            return isBefore(Build.VERSION_CODES.S_V2, CODENAME_S_V2);
        }

        /**
         * Checks if the device is running Android S_V2 or older.
         */
        public static boolean isAtMostSv2() {
            return isAtMost(Build.VERSION_CODES.S_V2, CODENAME_S_V2);
        }

        /**
         * Checks if the device is running Android S_V2.
         */
        public static boolean isSv2() {
            return is(Build.VERSION_CODES.S_V2, CODENAME_S_V2);
        }

        /**
         * Checks if the device is running Android S_V2 or newer.
         */
        public static boolean isAtLeastSv2() {
            return isAtLeast(Build.VERSION_CODES.S_V2, CODENAME_S_V2);
        }

        /**
         * Checks if the device is running a version after Android S_V2.
         */
        public static boolean isAfterSv2() {
            return isAfter(Build.VERSION_CODES.S_V2, CODENAME_S_V2);
        }

        /**
         * Checks if the device is running a version before Android Tiramisu.
         */
        public static boolean isBeforeT() {
            return isBefore(Build.VERSION_CODES.TIRAMISU, CODENAME_T);
        }

        /**
         * Checks if the device is running Android Tiramisu or older.
         */
        public static boolean isAtMostT() {
            return isAtMost(Build.VERSION_CODES.TIRAMISU, CODENAME_T);
        }

        /**
         * Checks if the device is running Android Tiramisu.
         */
        public static boolean isT() {
            return is(Build.VERSION_CODES.TIRAMISU, CODENAME_T);
        }

        /**
         * Checks if the device is running Android Tiramisu or newer.
         */
        public static boolean isAtLeastT() {
            return isAtLeast(Build.VERSION_CODES.TIRAMISU, CODENAME_T);
        }

        /**
         * Checks if the device is running a version after Android Tiramisu.
         */
        public static boolean isAfterT() {
            return isAfter(Build.VERSION_CODES.TIRAMISU, CODENAME_T);
        }

        /**
         * Checks if the device is running a version before Android UpsideDownCake.
         */
        public static boolean isBeforeU() {
            return isBefore(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, CODENAME_U);
        }

        /**
         * Checks if the device is running Android UpsideDownCake or older.
         */
        public static boolean isAtMostU() {
            return isAtMost(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, CODENAME_U);
        }

        /**
         * Checks if the device is running Android UpsideDownCake.
         */
        public static boolean isU() {
            return is(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, CODENAME_U);
        }

        /**
         * Checks if the device is running Android UpsideDownCake or newer.
         */
        public static boolean isAtLeastU() {
            return isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, CODENAME_U);
        }

        /**
         * Checks if the device is running a version after Android UpsideDownCake.
         */
        public static boolean isAfterU() {
            return isAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, CODENAME_U);
        }

        /**
         * Checks if the device is running a version before the given SDK version.
         */
        private static boolean isBefore(int version, String codename) {
            if (IS_REL_VERSION) {
                return SDK_INT < version;
            }
            // lexically compare and return true if the build codename is equal to or
            // greater than the requested codename
            return Build.VERSION.CODENAME.compareTo(codename) < 0;
        }

        /**
         * Checks if the device is running the given or older SDK version.
         */
        private static boolean isAtMost(int version, String codename) {
            if (IS_REL_VERSION) {
                return SDK_INT <= version;
            }
            // lexically compare and return true if the build codename is equal to or
            // greater than the requested codename
            return Build.VERSION.CODENAME.compareTo(codename) <= 0;
        }

        /**
         * Checks if the device is running the given SDK version.
         */
        private static boolean is(int version, String codename) {
            if (IS_REL_VERSION) {
                return SDK_INT == version;
            }
            return Build.VERSION.CODENAME.equals(codename);
        }

        /**
         * Checks if the device is running the given or newer SDK version.
         */
        private static boolean isAtLeast(int version, String codename) {
            if (IS_REL_VERSION) {
                return SDK_INT >= version;
            }
            if (Build.VERSION.CODENAME.compareTo("Z") >= 0) {
                throw new RuntimeException("Lexical comparison doesn't work for Android Z. Fix "
                        + "these functions.");
            }
            // lexically compare and return true if the build codename is equal to or
            // greater than the requested codename
            return Build.VERSION.CODENAME.compareTo(codename) >= 0;
        }

        /**
         * Checks if the device is running a version after the given SDK version.
         */
        private static boolean isAfter(int version, String codename) {
            if (IS_REL_VERSION) {
                return SDK_INT > version;
            }
            if (Build.VERSION.CODENAME.compareTo("Z") >= 0) {
                throw new RuntimeException("Lexical comparison doesn't work for Android Z. Fix "
                        + "these functions.");
            }
            // lexically compare and return true if the build codename is equal to or
            // greater than the requested codename
            return Build.VERSION.CODENAME.compareTo(codename) > 0;
        }
    }

    /**
     * Utils to check Initial SDK version (Based on ro.product.first_api_level)
     * Currently only isAtLeast*() functions are added as only these variants are used in the tests.
     * If other variants are needed in the tests, then those functions need to be added here.
     */
    public static class InitialSdk {
        // Build.VERSION.DEVICE_INITIAL_SDK_INT was introduced in Android S, it was called
        // Build.VERSION.FIRST_SDK_INT before that. FIRST_SDK_INT can't be used here for older
        // versions as that has been removed from Build.VERSION, so ro.product.first_api_level is
        // used for versions Android R and before
        private static final int DEVICE_INITIAL_SDK_INT = Sdk.isAfterR()
                ? Build.VERSION.DEVICE_INITIAL_SDK_INT
                : SystemProperties.getInt("ro.product.first_api_level", 0);
        private static final String DEVICE_INITIAL_SDK_CODENAME =
                SystemProperties.get("ro.product.first_api_level", "");

        /**
         * Checks if the device was launched on Android Q or newer.
         */
        public static boolean isAtLeastQ() {
            return isAtLeast(Build.VERSION_CODES.Q, CODENAME_Q);
        }

        /**
         * Checks if the device was launched on Android R or newer.
         */
        public static boolean isAtLeastR() {
            return isAtLeast(Build.VERSION_CODES.R, CODENAME_R);
        }

        /**
         * Checks if the device was launched on Android S or newer.
         */
        public static boolean isAtLeastS() {
            return isAtLeast(Build.VERSION_CODES.S, CODENAME_S);
        }

        /**
         * Checks if the device was launched on Android S_V2 or newer.
         */
        public static boolean isAtLeastSv2() {
            return isAtLeast(Build.VERSION_CODES.S_V2, CODENAME_S_V2);
        }

        /**
         * Checks if the device was launched on Android Tiramisu or newer.
         */
        public static boolean isAtLeastT() {
            return isAtLeast(Build.VERSION_CODES.TIRAMISU, CODENAME_T);
        }

        /**
         * Checks if the device was launched on Android UpsideDownCake or newer.
         */
        public static boolean isAtLeastU() {
            return isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, CODENAME_U);
        }

        /**
         * Checks if the device device was launched on the given or newer SDK version.
         */
        private static boolean isAtLeast(int version, String codename) {
            if (IS_REL_VERSION) {
                return DEVICE_INITIAL_SDK_INT >= version;
            }
            // lexically compare and return true if the vndk codename is equal to or
            // greater than the requested codename
            return DEVICE_INITIAL_SDK_CODENAME.compareTo(codename) >= 0;
        }
    }

    /**
     * Utils to check VNDK version (Based on ro.vndk.version)
     * Currently only isAtLeast*() functions are added as only these variants are used in the tests.
     * If other variants are needed in the tests, then those functions need to be added here.
     */
    public static class Vndk {
        static final int VNDK_INT = SystemProperties.getInt("ro.vndk.version", 0);
        static final String VNDK_CODENAME = SystemProperties.get("ro.vndk.version", "");

        /**
         * Checks if the VNDK version is Android Q or newer.
         */
        public static boolean isAtLeastQ() {
            return isAtLeast(Build.VERSION_CODES.Q, CODENAME_Q);
        }

        /**
         * Checks if the VNDK version is Android R or newer.
         */
        public static boolean isAtLeastR() {
            return isAtLeast(Build.VERSION_CODES.R, CODENAME_R);
        }

        /**
         * Checks if the VNDK version is Android S or newer.
         */
        public static boolean isAtLeastS() {
            return isAtLeast(Build.VERSION_CODES.S, CODENAME_S);
        }

        /**
         * Checks if the VNDK version is Android S_V2 or newer.
         */
        public static boolean isAtLeastSv2() {
            return isAtLeast(Build.VERSION_CODES.S_V2, CODENAME_S_V2);
        }

        /**
         * Checks if the VNDK version is Android Tiramisu or newer.
         */
        public static boolean isAtLeastT() {
            return isAtLeast(Build.VERSION_CODES.TIRAMISU, CODENAME_T);
        }

        /**
         * Checks if the VNDK version is Android UpsideDownCake or newer.
         */
        public static boolean isAtLeastU() {
            return isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, CODENAME_U);
        }

        /**
         * Checks if the device is running the given or newer VNDK version.
         */
        private static boolean isAtLeast(int version, String codename) {
            if (IS_REL_VERSION) {
                return VNDK_INT >= version;
            }
            // lexically compare and return true if the vndk codename is equal to or
            // greater than the requested codename
            return VNDK_CODENAME.compareTo(codename) >= 0;
        }
    }
}
