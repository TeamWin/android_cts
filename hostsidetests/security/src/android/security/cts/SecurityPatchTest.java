/**
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

package android.security.cts;

import com.android.compatibility.common.util.ApiLevelUtil;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class SecurityPatchTest extends SecurityTestCase {
    private static final String TAG = SecurityPatchTest.class.getSimpleName();
    private static final String SECURITY_PATCH_ERROR =
            "security_patch should be in the format \"YYYY-MM-DD\". Found \"%s\"";
    private static final String SECURITY_PATCH_DATE_ERROR =
            "security_patch should be \"%d-%02d\" or later. Found \"%s\"";
    private static final int SECURITY_PATCH_YEAR = 2016;
    private static final int SECURITY_PATCH_MONTH = 12;

    private int first_api_level;
    private int o_api_level;
    private String mVendorSecurityPatch;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        first_api_level = Integer.parseInt(getDevice().executeShellCommand("getprop ro.product.first_api_level").trim());
        o_api_level = 27;
        mVendorSecurityPatch = getDevice().executeShellCommand("getprop ro.vendor.build.security_patch").trim();
    }

    /** Security patch should be of the form YYYY-MM-DD in M or higher */
    @SecurityTest
    private void testSecurityPatchFormat(String patch, String error) {
        assertEquals(error, 10, patch.length());
        assertTrue(error, Character.isDigit(patch.charAt(0)));
        assertTrue(error, Character.isDigit(patch.charAt(1)));
        assertTrue(error, Character.isDigit(patch.charAt(2)));
        assertTrue(error, Character.isDigit(patch.charAt(3)));
        assertEquals(error, '-', patch.charAt(4));
        assertTrue(error, Character.isDigit(patch.charAt(5)));
        assertTrue(error, Character.isDigit(patch.charAt(6)));
        assertEquals(error, '-', patch.charAt(7));
        assertTrue(error, Character.isDigit(patch.charAt(8)));
        assertTrue(error, Character.isDigit(patch.charAt(9)));
    }

    /** Security patch should no older than the month this test was updated in M or higher **/
    @SecurityTest
    private void testSecurityPatchDate(String patch, String error) {
        int declaredYear = 0;
        int declaredMonth = 0;

        try {
            declaredYear = Integer.parseInt(patch.substring(0,4));
            declaredMonth = Integer.parseInt(patch.substring(5,7));
        } catch (Exception e) {
            assertTrue(error, false);
        }

        assertTrue(error, declaredYear >= SECURITY_PATCH_YEAR);
        assertTrue(error, (declaredYear > SECURITY_PATCH_YEAR) ||
                          (declaredMonth >= SECURITY_PATCH_MONTH));
    }

    @SecurityTest
    public void testVendorSecurityPatchFound() throws Exception {
        if (first_api_level <= o_api_level) {
            // Skip P+ Test"
            return;
        }
        assertTrue(!mVendorSecurityPatch.isEmpty());
    }

    @SecurityTest
    public void testSecurityPatchesFormat() throws Exception {
        if (first_api_level <= o_api_level) {
            // Skip P+ Test"
            return;
        }
        String error = String.format(SECURITY_PATCH_ERROR, mVendorSecurityPatch);
        testSecurityPatchFormat(mVendorSecurityPatch, error);
    }

    public void testSecurityPatchDates() {
        if (first_api_level <= o_api_level) {
            // Skip P+ Test"
            return;
        }
        String error = String.format(SECURITY_PATCH_DATE_ERROR,
                                     SECURITY_PATCH_YEAR,
                                     SECURITY_PATCH_MONTH,
                                     mVendorSecurityPatch);
        testSecurityPatchDate(mVendorSecurityPatch, error);
    }
}
