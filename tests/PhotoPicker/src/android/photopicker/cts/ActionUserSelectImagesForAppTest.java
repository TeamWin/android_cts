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

import static android.photopicker.cts.PhotoPickerCloudUtils.containsExcept;
import static android.photopicker.cts.PhotoPickerCloudUtils.extractMediaIds;
import static android.photopicker.cts.PhotoPickerCloudUtils.fetchPickerMedia;
import static android.photopicker.cts.PhotoPickerCloudUtils.initCloudProviderWithImage;
import static android.photopicker.cts.PickerProviderMediaGenerator.getMediaGenerator;
import static android.photopicker.cts.PickerProviderMediaGenerator.setCloudProvider;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUris;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.photopicker.cts.PickerProviderMediaGenerator.MediaGenerator;
import android.photopicker.cts.cloudproviders.CloudProviderPrimary;
import android.photopicker.cts.util.PhotoPickerFilesUtils;
import android.photopicker.cts.util.UiAssertionUtils;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** PhotoPicker tests for {@link MediaStore#ACTION_USER_SELECT_IMAGES_FOR_APP} intent. */
@RunWith(AndroidJUnit4.class)
public class ActionUserSelectImagesForAppTest extends PhotoPickerBaseTest {

    @After
    public void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    private static Intent getUserSelectImagesIntent() {
        final Intent intent = new Intent(MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP);
        intent.putExtra(Intent.EXTRA_UID, Process.myUid());
        return intent;
    }

    private static Intent getUserSelectImagesIntent(String mimeType) {
        Intent intent = getUserSelectImagesIntent();
        intent.setType(mimeType);
        return intent;
    }

    @Test
    public void testInvalidMimeTypeFilter() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    Intent intent = getUserSelectImagesIntent("audio/*");
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
    public void testCannotStartActivityWithoutGrantRuntimePermissions() throws Exception {
        assertThrows(
                SecurityException.class,
                () -> mActivity.startActivityForResult(getUserSelectImagesIntent(), REQUEST_CODE));
    }

    @Test
    public void testUserSelectImagesForAppHandledByPhotopicker() throws Exception {
        launchActivityForResult(getUserSelectImagesIntent());
        UiAssertionUtils.assertThatShowsPickerUi();
    }

    @Test
    public void testPhotosMimeTypeFilter() throws Exception {
        Intent intent = getUserSelectImagesIntent("image/*");
        launchActivityForResult(intent);
        UiAssertionUtils.assertThatShowsPickerUi();
    }

    @Test
    public void testVideosMimeTypeFilter() throws Exception {
        Intent intent = getUserSelectImagesIntent("video/*");
        launchActivityForResult(intent);
        UiAssertionUtils.assertThatShowsPickerUi();
    }

    @Test
    public void testNoCloudContent() throws Exception {
        final List<Uri> uriList = new ArrayList<>();
        final String cloudId = "cloud_id1";

        try {
            uriList.addAll(createImagesAndGetUris(1, mContext.getUserId()));
            setupCloudProviderWithImage(cloudId);

            launchActivityForResult(getUserSelectImagesIntent());

            final ClipData clipData = fetchPickerMedia(mActivity, sDevice, 1);
            final List<String> mediaIds = extractMediaIds(clipData, 1);

            containsExcept(mediaIds, uriList.get(0).getLastPathSegment(), cloudId);
        } finally {
            for (Uri uri : uriList) {
                PhotoPickerFilesUtils.deleteMedia(uri, mContext);
            }
            uriList.clear();
            setCloudProvider(mContext, null);
        }
    }

    private void setupCloudProviderWithImage(String cloudId) throws Exception {
        MediaGenerator cloudPrimaryMediaGenerator = getMediaGenerator(
                mContext, CloudProviderPrimary.AUTHORITY);
        cloudPrimaryMediaGenerator.resetAll();
        cloudPrimaryMediaGenerator.setMediaCollectionId("collection_1");
        initCloudProviderWithImage(mContext, cloudPrimaryMediaGenerator,
                CloudProviderPrimary.AUTHORITY, Pair.create(null, cloudId));
    }

    private void launchActivityForResult(Intent intent) throws Exception {
        runWithShellPermissionIdentity(
                () -> mActivity.startActivityForResult(intent, REQUEST_CODE),
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }
}
