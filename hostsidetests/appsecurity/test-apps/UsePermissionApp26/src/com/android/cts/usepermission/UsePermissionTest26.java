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

package com.android.cts.usepermission;

import static com.android.compatibility.common.util.UiAutomatorUtils.getUiDevice;

import android.Manifest;
import android.content.pm.PackageManager;

import org.junit.Test;

/**
 * Runtime permission behavior tests for apps targeting API 26
 */
public class UsePermissionTest26 extends BasePermissionsTest {
    private static final int REQUEST_CODE_PERMISSIONS = 42;

    @Test
    public void testRuntimeGroupGrantNoExpansion() throws Exception {
        // Start out without permission
        assertPermissionState(Manifest.permission.RECEIVE_SMS, PackageManager.PERMISSION_DENIED);
        assertPermissionState(Manifest.permission.SEND_SMS, PackageManager.PERMISSION_DENIED);

        String[] permissions = new String[]{Manifest.permission.RECEIVE_SMS};

        // request only one permission from the 'SMS' permission group at runtime,
        // but two from this group are <uses-permission> in the manifest
        // request only one permission from the 'contacts' permission group
        BasePermissionActivity.Result result = requestPermissions(permissions, () -> {
            try {
                clickAllowButton();
                getUiDevice().waitForIdle();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Expect the permission is granted
        assertPermissionRequestResult(result, permissions, new boolean[]{true});

        assertPermissionState(Manifest.permission.SEND_SMS, PackageManager.PERMISSION_DENIED);
    }
}
