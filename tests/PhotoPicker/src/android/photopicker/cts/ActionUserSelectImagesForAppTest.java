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

package android.photopicker.cts;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Process;
import android.photopicker.cts.util.UiAssertionUtils;
import android.provider.MediaStore;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** PhotoPicker tests for {@link MediaStore#ACTION_USER_SELECT_IMAGES_FOR_APP} intent. */
@RunWith(AndroidJUnit4.class)
public class ActionUserSelectImagesForAppTest extends PhotoPickerBaseTest {

    @After
    public void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    @Test
    public void testInvalidMimeTypeFilter() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    final Intent intent = new Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP);
                    intent.putExtra(Intent.EXTRA_UID, Process.myUid());
                    intent.setType("audio/*");
                    assertThrows(
                            ActivityNotFoundException.class,
                            () -> mActivity.startActivityForResult(intent, REQUEST_CODE));
                },
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    @Test
    public void testActivityCancelledWithMissingAppUid() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    final Intent intent = new Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP);
                    mActivity.startActivityForResult(intent, REQUEST_CODE);
                    final GetResultActivity.Result res = mActivity.getResult();
                    assertThat(res.resultCode).isEqualTo(Activity.RESULT_CANCELED);
                },
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    @Test
    public void testUserSelectImagesForAppHandledByPhotopicker() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    final Intent intent = new Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP);
                    intent.putExtra(Intent.EXTRA_UID, Process.myUid());
                    mActivity.startActivityForResult(intent, REQUEST_CODE);

                    UiAssertionUtils.assertThatShowsPickerUi();
                },
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    @Test
    public void testPhotosMimeTypeFilter() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    final Intent intent = new Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP);
                    intent.putExtra(Intent.EXTRA_UID, Process.myUid());
                    intent.setType("image/*");
                    mActivity.startActivityForResult(intent, REQUEST_CODE);
                    UiAssertionUtils.assertThatShowsPickerUi();
                },
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    @Test
    public void testVideosMimeTypeFilter() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    final Intent intent = new Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP);
                    intent.putExtra(Intent.EXTRA_UID, Process.myUid());
                    intent.setType("video/*");
                    mActivity.startActivityForResult(intent, REQUEST_CODE);
                    UiAssertionUtils.assertThatShowsPickerUi();
                },
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    @Test
    public void testCannotStartActivityWithoutGrantRuntimePermissions() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP);
        intent.putExtra(Intent.EXTRA_UID, Process.myUid());
        assertThrows(
                SecurityException.class,
                () -> mActivity.startActivityForResult(intent, REQUEST_CODE));
    }
}
