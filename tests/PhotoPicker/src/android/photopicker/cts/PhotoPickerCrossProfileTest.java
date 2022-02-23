/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImages;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findProfileButton;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker Device only tests for cross profile interaction flows.
 */
@RunWith(BedsteadJUnit4.class)
public class PhotoPickerCrossProfileTest extends PhotoPickerBaseTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private List<Uri> mUriList = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();
        mActivity.finish();
    }

    @Test
    @RequireRunOnWorkProfile
    public void testWorkApp_canAccessPersonalProfileContents() throws Exception {
        final int imageCount = 2;
        createImages(imageCount, sDeviceState.primaryUser().id(), mUriList);

        Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, imageCount);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Click the profile button to change to personal profile
        final UiObject profileButton = findProfileButton();
        profileButton.click();
        mDevice.waitForIdle();

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        final UiObject addButton = findAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(imageCount);
        for (int i = 0; i < count; i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, sDeviceState.primaryUser().id());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    @EnsureHasWorkProfile
    public void testPersonalApp_cannotAccessWorkProfile_default() throws Exception {
        Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Click the profile button to change to work profile
        final UiObject profileButton = findProfileButton();
        profileButton.click();
        mDevice.waitForIdle();

        // By default, ACTION_PICK_IMAGES is only allowlisted to access personal
        // content from work profile, but not vice-a-versa.
        assertBlockedByAdminDialog();
    }

    private void assertBlockedByAdminDialog() {
        final String dialogTitle = "Blocked by your admin";
        assertWithMessage("Timed out while waiting for blocked by admin dialog to appear")
                .that(new UiObject(new UiSelector().textContains(dialogTitle))
                        .waitForExists(SHORT_TIMEOUT))
                .isTrue();

        final String dialogDescription = "Accessing work data from a personal app is not permitted";
        assertWithMessage("Blocked by admin description is not as expected")
                .that(new UiObject(new UiSelector().textContains(dialogDescription)).exists())
                .isTrue();
    }
}
