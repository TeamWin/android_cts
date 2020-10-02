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

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_MEDIA_LOCATION;
import static android.Manifest.permission.BACKGROUND_CAMERA;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.RECORD_BACKGROUND_AUDIO;
import static android.Manifest.permission.WRITE_CALL_LOG;
import static android.Manifest.permission.WRITE_CONTACTS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Build;
import android.permission.PermissionManager;
import android.permission.PermissionManager.SplitPermissionInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SplitPermissionsSystemTest {

    private static final int NO_TARGET = Build.VERSION_CODES.CUR_DEVELOPMENT + 1;

    private List<SplitPermissionInfo> mSplitPermissions;

    @Before
    public void before() {
        Context context = InstrumentationRegistry.getContext();
        PermissionManager permissionManager = (PermissionManager) context.getSystemService(
                Context.PERMISSION_SERVICE);
        mSplitPermissions = permissionManager.getSplitPermissions();
    }

    @Test
    public void validateAndroidSystem() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q));

        Set<SplitPermissionInfo> seenSplits = new HashSet<>(6);

        for (SplitPermissionInfo split : mSplitPermissions) {
            String splitPermission = split.getSplitPermission();
            boolean isAndroid = splitPermission.startsWith("android");

            if (!isAndroid) {
                continue;
            }

            assertThat(seenSplits).doesNotContain(split);
            seenSplits.add(split);

            List<String> newPermissions = split.getNewPermissions();

            switch (splitPermission) {
                case ACCESS_FINE_LOCATION:
                    // Q declares multiple for ACCESS_FINE_LOCATION, so assert both exist
                    if (newPermissions.contains(ACCESS_COARSE_LOCATION)) {
                        assertSplit(split, ACCESS_COARSE_LOCATION, NO_TARGET);
                    } else {
                        assertSplit(split, ACCESS_BACKGROUND_LOCATION, Build.VERSION_CODES.Q);
                    }
                    break;
                case WRITE_EXTERNAL_STORAGE:
                    assertSplit(split, READ_EXTERNAL_STORAGE, NO_TARGET);
                    break;
                case READ_CONTACTS:
                    assertSplit(split, READ_CALL_LOG, Build.VERSION_CODES.JELLY_BEAN);
                    break;
                case WRITE_CONTACTS:
                    assertSplit(split, WRITE_CALL_LOG, Build.VERSION_CODES.JELLY_BEAN);
                    break;
                case ACCESS_COARSE_LOCATION:
                    assertSplit(split, ACCESS_BACKGROUND_LOCATION, Build.VERSION_CODES.Q);
                    break;
                case READ_EXTERNAL_STORAGE:
                    assertSplit(split, ACCESS_MEDIA_LOCATION, Build.VERSION_CODES.Q);
                    break;
                case READ_PRIVILEGED_PHONE_STATE:
                    assertSplit(split, READ_PHONE_STATE, NO_TARGET);
                    break;
                case RECORD_AUDIO:
                    // Written this way so that when the sdk int is set for S the test fails
                    // When this fails verify that the targetSdk is correct in platform.xml
                    assertSplit(split, RECORD_BACKGROUND_AUDIO, Build.VERSION_CODES.S - 9969);
                    break;
                case CAMERA:
                    assertSplit(split, BACKGROUND_CAMERA, Build.VERSION_CODES.S - 9969);
                    break;
            }
        }

        assertEquals(10, seenSplits.size());
    }

    private void assertSplit(SplitPermissionInfo split, String permission, int targetSdk) {
        // For now, all system splits have 1 permission
        assertThat(split.getNewPermissions()).containsExactly(permission);
        assertThat(split.getTargetSdk()).isEqualTo(targetSdk);
    }
}
