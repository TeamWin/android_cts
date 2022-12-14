/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.slice.cts;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import android.app.slice.SliceManager;
import android.app.slice.SliceProvider;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Process;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SlicePermissionsTest {

    private static final Uri BASE_URI = Uri.parse("content://android.slice.cts.local/");
    private final Context mContext = InstrumentationRegistry.getContext();
    private String mTestPkg;
    private int mTestUid;
    private int mTestPid;
    private SliceManager mSliceManager;
    private boolean isSliceDisabled = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SLICES_DISABLED);

    @Before
    public void setup() throws NameNotFoundException {
        assumeFalse(isSliceDisabled);
        mSliceManager = mContext.getSystemService(SliceManager.class);
        mTestPkg = mContext.getPackageName();
        mTestUid = mContext.getPackageManager().getPackageUid(mTestPkg, 0);
        mTestPid = Process.myPid();
    }

    @After
    public void tearDown() {
        if (isSliceDisabled) {
            return;
        }
        mSliceManager.revokeSlicePermission(mTestPkg, BASE_URI);
    }

    @Test
    public void testGrant() {
        assumeFalse(isSliceDisabled);
        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, BASE_URI);

        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));
    }

    @Test
    public void testGrantParent() {
        assumeFalse(isSliceDisabled);
        Uri uri = BASE_URI.buildUpon()
                .appendPath("something")
                .build();

        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, BASE_URI);

        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));
    }

    @Test
    public void testGrantParentExpands() {
        assumeFalse(isSliceDisabled);
        Uri uri = BASE_URI.buildUpon()
                .appendPath("something")
                .build();

        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, uri);

        // Only sub-path granted.
        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));
        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, BASE_URI);

        // Now all granted.
        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));
        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));
    }

    @Test
    public void testGrantChild() {
        assumeFalse(isSliceDisabled);
        Uri uri = BASE_URI.buildUpon()
                .appendPath("something")
                .build();

        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, uri);

        // Still no permission because only a child was granted
        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));
    }

    @Test
    public void testRevoke() {
        assumeFalse(isSliceDisabled);
        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, BASE_URI);

        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));

        mSliceManager.revokeSlicePermission(mTestPkg, BASE_URI);

        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));
    }

    @Test
    public void testRevokeParent() {
        assumeFalse(isSliceDisabled);
        Uri uri = BASE_URI.buildUpon()
                .appendPath("something")
                .build();
        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, uri);

        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));

        mSliceManager.revokeSlicePermission(mTestPkg, BASE_URI);

        // Revoked because parent was revoked
        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(uri, mTestPid, mTestUid));
    }

    @Test
    public void testPermissionIntent() {
        Intent intent = SliceProvider.createPermissionIntent(mContext, BASE_URI, mTestPkg);
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        assertEquals(1, activities.size());
    }

    @Test
    public void testRevokeChild() {
        assumeFalse(isSliceDisabled);
        Uri uri = BASE_URI.buildUpon()
                .appendPath("something")
                .build();
        assertEquals(PERMISSION_DENIED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));

        mSliceManager.grantSlicePermission(mTestPkg, BASE_URI);

        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));

        mSliceManager.revokeSlicePermission(mTestPkg, uri);

        // Not revoked because child was revoked.
        assertEquals(PERMISSION_GRANTED,
                mSliceManager.checkSlicePermission(BASE_URI, mTestPid, mTestUid));
    }

}
