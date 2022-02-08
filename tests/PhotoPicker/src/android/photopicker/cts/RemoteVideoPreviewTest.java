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

import static android.os.SystemProperties.getBoolean;
import static android.photopicker.cts.PickerProviderMediaGenerator.setCloudProvider;
import static android.photopicker.cts.PickerProviderMediaGenerator.syncCloudProvider;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideos;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.photopicker.cts.PickerProviderMediaGenerator.MediaGenerator;
import android.photopicker.cts.cloudproviders.CloudProviderPrimary;
import android.photopicker.cts.cloudproviders.CloudProviderPrimary.SurfaceControllerImpl;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/**
 * PhotoPicker test coverage for remote video preview APIs.
 * End-to-end coverage for video preview controls is present in {@link PhotoPickerTest}
 */
@RunWith(AndroidJUnit4.class)
public class RemoteVideoPreviewTest extends PhotoPickerBaseTest {

    private MediaGenerator mCloudPrimaryMediaGenerator;
    private final List<Uri> mUriList = new ArrayList<>();

    private static final String CLOUD_ID1 = "CLOUD_ID1";
    private static final String VERSION_1 = "VERSION_1";

    private static final long VIDEO_SIZE_BYTES = 135600;
    private static final int VIDEO_PIXEL_FORMAT = PixelFormat.RGB_565;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        Assume.assumeTrue(getBoolean("sys.photopicker.pickerdb.enabled", true));

        mDevice.executeShellCommand("setprop sys.photopicker.remote_preview true");
        Assume.assumeTrue(getBoolean("sys.photopicker.remote_preview", true));

        mCloudPrimaryMediaGenerator = PickerProviderMediaGenerator.getMediaGenerator(
                mContext, CloudProviderPrimary.AUTHORITY);
        mCloudPrimaryMediaGenerator.resetAll();
        mCloudPrimaryMediaGenerator.setVersion(VERSION_1);

        setCloudProvider(mContext, CloudProviderPrimary.AUTHORITY);
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(CloudProviderPrimary.AUTHORITY);
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext.getUserId());
        }
        mUriList.clear();
        mActivity.finish();
        setCloudProvider(mContext, null);
    }

    @Test
    public void testBasicVideoPreview() throws Exception {
        initCloudProviderWithVideo(Pair.create(null, CLOUD_ID1));

        launchPreviewMultipleWithVideos(1);

        final SurfaceControllerImpl mockSurfaceController =
                CloudProviderPrimary.getMockSurfaceControllerListener();
        // This is required to assert the order in which the methods are called.
        final InOrder order = Mockito.inOrder(mockSurfaceController);

        // 1. Remote Preview calls onPlayerCreate as the first call to CloudMediaProvider
        order.verify(mockSurfaceController).onPlayerCreate();

        // 2. Remote Preview calls onSurfaceCreated with monotonically increasing surfaceIds
        final ArgumentCaptor<Integer> surfaceIdArgCaptor = ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<String> mediaIdArgCaptor = ArgumentCaptor.forClass(String.class);
        order.verify(mockSurfaceController).onSurfaceCreated(surfaceIdArgCaptor.capture(), any(),
                mediaIdArgCaptor.capture());

        // Verify surfaceId and mediaId
        final int surfaceId = surfaceIdArgCaptor.getValue();
        assertThat(surfaceId).isEqualTo(0);
        final String mediaId = mediaIdArgCaptor.getValue();
        assertThat(mediaId).isEqualTo(CLOUD_ID1);

        // 3. Remote Preview calls onSurfaceChanged to set the format, width and height
        // corresponding to the video on the same surfaceId
        order.verify(mockSurfaceController).onSurfaceChanged(eq(surfaceId), eq(VIDEO_PIXEL_FORMAT),
                anyInt(), anyInt());

        // 4. TODO(b/215187981): check why onMediaPlay() is not called
        // 5. TODO(b/215187981): Add test for onMediaPause()

        // Exit preview mode
        mDevice.pressBack();

        // 6. Remote Preview calls onSurfaceDestroyed, check if the id is the same (as the
        // CloudMediaProvider is only rendering to one surface id)
        order.verify(mockSurfaceController).onSurfaceDestroyed(eq(surfaceId));

        // 7. Remote Preview calls onPlayerRelease() and onDestroy() for CMP to release the
        // resources.
        order.verify(mockSurfaceController).onPlayerRelease();
        order.verify(mockSurfaceController).onDestroy();
    }

    private void initCloudProviderWithVideo(Pair<String, String>... mediaPairs)
            throws Exception {
        for (Pair<String, String> pair: mediaPairs) {
            addVideo(mCloudPrimaryMediaGenerator, pair.first, pair.second);
        }

        syncCloudProvider(mContext);
    }

    private void addVideo(MediaGenerator generator, String localId, String cloudId)
            throws Exception {
        generator.addMedia(localId, cloudId, /* albumId */ null, "video/mp4",
                /* mimeTypeExtension */ 0, VIDEO_SIZE_BYTES, /* isFavorite */ false,
                R.raw.test_video);
    }

    private void launchPreviewMultipleWithVideos(int videoCount) throws  Exception {
        createVideos(videoCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
        intent.setType("video/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(videoCount);

        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        final UiObject viewSelectedButton = findViewSelectedButton();
        viewSelectedButton.click();
        mDevice.waitForIdle();
    }

    private static UiObject findViewSelectedButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_view_selected"));
    }
}
