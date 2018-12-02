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

package android.permission.cts;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.UsesPermissionInfo;
import android.test.InstrumentationTestCase;

import org.junit.Before;

public final class PermissionUsageTest extends InstrumentationTestCase {
    private PackageManager mPm;
    private Context mContext;

    @Override
    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getTargetContext();
        mPm = mContext.getPackageManager();
        assertNotNull(mPm);
    }

    private UsesPermissionInfo getUsesPermissionInfo(String permission) throws Exception {
        PackageInfo info = mPm.getPackageInfo(mContext.getPackageName(),
                PackageManager.GET_PERMISSIONS);
        assertNotNull(info);
        assertNotNull(info.usesPermissions);
        for (UsesPermissionInfo upi : info.usesPermissions) {
            if (permission.equals(upi.getPermission())) {
                return upi;
            }
        }
        return null;
    }

    public void testBasic() throws Exception {
        UsesPermissionInfo upi = getUsesPermissionInfo(Manifest.permission.READ_CONTACTS);
        assertEquals(UsesPermissionInfo.USAGE_NO, upi.getDataSentOffDevice());
        assertEquals(UsesPermissionInfo.USAGE_NO, upi.getDataSharedWithThirdParty());
        assertEquals(UsesPermissionInfo.USAGE_NO, upi.getDataUsedForMonetization());
        assertEquals(UsesPermissionInfo.RETENTION_NOT_RETAINED, upi.getDataRetention());
    }

    public void testRetentionWeeks() throws Exception {
        UsesPermissionInfo upi
                = getUsesPermissionInfo(Manifest.permission.READ_PHONE_STATE);
        assertEquals(UsesPermissionInfo.USAGE_YES, upi.getDataSentOffDevice());
        assertEquals(UsesPermissionInfo.USAGE_YES, upi.getDataSharedWithThirdParty());
        assertEquals(UsesPermissionInfo.USAGE_YES, upi.getDataUsedForMonetization());
        assertEquals(UsesPermissionInfo.RETENTION_SPECIFIED, upi.getDataRetention());
        assertEquals(32, upi.getDataRetentionWeeks());
    }

    public void testUserTriggered() throws Exception {
        UsesPermissionInfo upi
                = getUsesPermissionInfo(Manifest.permission.RECORD_AUDIO);
        assertEquals(UsesPermissionInfo.USAGE_USER_TRIGGERED, upi.getDataSentOffDevice());
        assertEquals(UsesPermissionInfo.USAGE_USER_TRIGGERED,
                upi.getDataSharedWithThirdParty());
        assertEquals(UsesPermissionInfo.USAGE_USER_TRIGGERED,
                upi.getDataUsedForMonetization());
        assertEquals(UsesPermissionInfo.RETENTION_UNLIMITED, upi.getDataRetention());
    }

    public void testUndefined() throws Exception {
        UsesPermissionInfo upi
                = getUsesPermissionInfo(Manifest.permission.READ_CALENDAR);
        assertEquals(UsesPermissionInfo.USAGE_UNDEFINED, upi.getDataSentOffDevice());
        assertEquals(UsesPermissionInfo.USAGE_UNDEFINED, upi.getDataSharedWithThirdParty());
        assertEquals(UsesPermissionInfo.USAGE_UNDEFINED, upi.getDataUsedForMonetization());
        assertEquals(UsesPermissionInfo.RETENTION_UNDEFINED, upi.getDataRetention());
    }

    public void testUsageInfoRequired() throws Exception {
        PermissionInfo pi = mPm.getPermissionInfo("android.permission.cts.D", 0);
        assertTrue(pi.usageInfoRequired);
    }
}
