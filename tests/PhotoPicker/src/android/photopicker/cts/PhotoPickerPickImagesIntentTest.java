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

import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertPersistedGrant;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImages;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker tests for {@link MediaStore#ACTION_PICK_IMAGES} intent exclusively
 */
@RunWith(AndroidJUnit4.class)
public class PhotoPickerPickImagesIntentTest extends PhotoPickerBaseTest {

    private List<Uri> mUriList = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();

        if (mActivity != null) {
            mActivity.finish();
        }
    }

    @Test
    public void testMultiSelect_invalidParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit() + 1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertThat(res.resultCode).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void testMultiSelect_invalidNegativeParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, -1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertThat(res.resultCode).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void testMultiSelect_returnsNotMoreThanMax() throws Exception {
        final int maxCount = 2;
        final int imageCount = maxCount + 1;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxCount);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        // Select maxCount + 1 item
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        UiObject snackbarTextView = mDevice.findObject(new UiSelector().text(
                "Select up to 2 items"));
        assertWithMessage("Timed out while waiting for snackbar to appear").that(
                snackbarTextView.waitForExists(SHORT_TIMEOUT)).isTrue();

        assertWithMessage("Timed out waiting for snackbar to disappear").that(
                snackbarTextView.waitUntilGone(SHORT_TIMEOUT)).isTrue();

        clickAndWait(mDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(maxCount);
    }

    @Test
    public void testDoesNotRespectExtraAllowMultiple() throws Exception {
        final int imageCount = 2;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        // Select 1 item
        clickAndWait(mDevice, itemList.get(0));

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertPersistedGrant(uri, mContext.getContentResolver());
        assertRedactedReadOnlyAccess(uri);
    }
}
