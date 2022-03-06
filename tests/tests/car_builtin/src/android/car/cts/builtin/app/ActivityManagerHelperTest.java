/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin.app;

import static com.google.common.truth.Truth.assertThat;

import android.car.builtin.app.ActivityManagerHelper;
import android.content.pm.PackageManager;
import android.os.Process;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerHelperTest {

    private static final String TAG = ActivityManagerHelperTest.class.getSimpleName();

    private static final String NOT_REQUESTED_PERMISSION_CAR_MILEAGE =
            "android.car.permission.CAR_MILEAGE";
    private static final String NOT_REQUESTED_PERMISSION_READ_CAR_POWER_POLICY =
            "android.car.permission.READ_CAR_POWER_POLICY";

    private static final String GRANTED_PERMISSION_INTERACT_ACROSS_USERS =
            "android.permission.INTERACT_ACROSS_USERS";

    private static final int OWNING_UID = -1;

    @Test
    public void testCheckComponentPermission() throws Exception {
        // not requested from Manifest
        assertComponentPermissionNotGranted(NOT_REQUESTED_PERMISSION_CAR_MILEAGE);
        assertComponentPermissionNotGranted(NOT_REQUESTED_PERMISSION_READ_CAR_POWER_POLICY);

        // requested from Manifest and granted
        assertComponentPermissionGranted(GRANTED_PERMISSION_INTERACT_ACROSS_USERS);
    }

    @Test
    public void testSetFocusedRootTask() throws Exception {
        //TODO(b/201005730): implement the test case to test setFocusedRootTask API.
    }

    @Test
    public void testRemoveTask() throws Exception {
        //TODO(b/201005730): implement the test case to test setRemoveTask API.
    }

    @Test
    public void testProcessObserverCallback() throws Exception {
        //TODO(b/201005730): implement the test case to test processObserverCallback API.
    }

    private void assertComponentPermissionGranted(String permission) throws Exception {
        assertThat(ActivityManagerHelper.checkComponentPermission(permission,
                Process.myUid(), /* owningUid= */ OWNING_UID, /* exported= */ true))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    private void assertComponentPermissionNotGranted(String permission) throws Exception {
        assertThat(ActivityManagerHelper.checkComponentPermission(permission,
                Process.myUid(), /* owningUid= */ OWNING_UID, /* exported= */ true))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }
}
