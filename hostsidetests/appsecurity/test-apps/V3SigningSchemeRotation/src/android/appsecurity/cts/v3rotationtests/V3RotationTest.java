/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.appsecurity.cts.v3rotationtests;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.test.AndroidTestCase;

import java.lang.Override;

/**
 * On-device tests for APK Signature Scheme v3 based signing certificate rotation
 */
public class V3RotationTest extends AndroidTestCase {

    private static final String COMPANION_PKG = "android.appsecurity.cts.tinyapp_companion";
    private static final String PERMISSION_NAME = "android.appsecurity.cts.tinyapp.perm";

    public void testHasPerm() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        assertTrue(PERMISSION_NAME + " not granted to " + COMPANION_PKG,
                pm.checkPermission(PERMISSION_NAME, COMPANION_PKG)
                        == PackageManager.PERMISSION_GRANTED);
    }

    public void testHasNoPerm() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        assertFalse(PERMISSION_NAME + " granted to " + COMPANION_PKG,
                pm.checkPermission(PERMISSION_NAME, COMPANION_PKG)
                        == PackageManager.PERMISSION_GRANTED);
    }
}
