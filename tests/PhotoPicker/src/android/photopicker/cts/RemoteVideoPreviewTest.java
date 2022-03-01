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
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddButton;
import static android.provider.CloudMediaProvider.CloudMediaSurfaceEventCallback.PLAYBACK_EVENT_READY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.photopicker.cts.PickerProviderMediaGenerator.MediaGenerator;
import android.photopicker.cts.cloudproviders.CloudProviderPrimary;
import android.photopicker.cts.cloudproviders.CloudProviderPrimary.CloudMediaSurfaceControllerImpl;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    private static final String CLOUD_ID2 = "CLOUD_ID2";
    private static final String COLLECTION_1 = "COLLECTION_1";

    private static final long IMAGE_SIZE_BYTES = 107684;
    private static final long VIDEO_SIZE_BYTES = 135600;
    private static final int VIDEO_PIXEL_FORMAT = PixelFormat.RGB_565;

    private CloudMediaSurfaceControllerImpl mSurfaceControllerListener;
    // This is required to assert the order in which the APIs are called.
    private InOrder mAssertInOrder;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        Assume.assumeTrue(getBoolean("sys.photopicker.pickerdb.enabled", true));

        mDevice.executeShellCommand("setprop sys.photopicker.remote_preview true");
        Assume.assumeTrue(getBoolean("sys.photopicker.remote_preview", true));

        mCloudPrimaryMediaGenerator = PickerProviderMediaGenerator.getMediaGenerator(
                mContext, CloudProviderPrimary.AUTHORITY);
        mCloudPrimaryMediaGenerator.resetAll();
        mCloudPrimaryMediaGenerator.setMediaCollectionId(COLLECTION_1);

        setCloudProvider(mContext, CloudProviderPrimary.AUTHORITY);
        assertThat(MediaStore.isCurrentCloudMediaProviderAuthority(mContext.getContentResolver(),
                        CloudProviderPrimary.AUTHORITY)).isTrue();

        mSurfaceControllerListener = CloudProviderPrimary.getMockSurfaceControllerListener();
        mAssertInOrder = Mockito.inOrder(mSurfaceControllerListener);
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();
        mActivity.finish();
        setCloudProvider(mContext, null);
    }

    @Test
    public void testBasicVideoPreview() throws Exception {
        initCloudProviderWithVideo(Pair.create(null, CLOUD_ID1));

        launchPreviewMultiple(/* count */ 1);

        final int surfaceId = 0;
        verifyInitialVideoPreviewSetup(surfaceId, CLOUD_ID1);
        // Remote Preview calls onMediaPlay when PLAYBACK_EVENT_READY is sent by the
        // CloudMediaProvider
        verifyPlaybackEventReady(surfaceId);

        // TODO(b/215187981): Add test for onMediaPause()

        // Exit preview mode
        mDevice.pressBack();

        // Remote Preview calls onSurfaceDestroyed, check if the id is the same (as the
        // CloudMediaProvider is only rendering to one surface id)
        mAssertInOrder.verify(mSurfaceControllerListener).onSurfaceDestroyed(eq(surfaceId));

        // Remote Preview calls onPlayerRelease() and onDestroy() for CMP to release the
        // resources.
        mAssertInOrder.verify(mSurfaceControllerListener).onPlayerRelease();
        mAssertInOrder.verify(mSurfaceControllerListener).onDestroy();

        final UiObject addButton = findAddButton();
        addButton.click();
        // We don't test the result of the picker here because the intention of the test is only to
        // test the remote preview APIs
    }

    @Test
    @Ignore("Re-enable once b/219861183 is fixed")
    public void testSwipeAdjacentVideoPreview() throws Exception {
        initCloudProviderWithVideo(Pair.create(null, CLOUD_ID1), Pair.create(null, CLOUD_ID2));

        launchPreviewMultiple(/* count */ 2);

        final int surfaceIdForFirstVideoPreview = 0;
        verifyInitialVideoPreviewSetup(surfaceIdForFirstVideoPreview, CLOUD_ID2);
        // Remote Preview calls onMediaPlay when PLAYBACK_EVENT_READY is sent by the
        // CloudMediaProvider
        verifyPlaybackEventReady(surfaceIdForFirstVideoPreview);

        // Swipe left preview mode
        swipeLeftAndWait();

        // Remote Preview calls onSurfaceCreated with monotonically increasing surfaceIds
        final int surfaceIdForSecondVideoPreview = 1;

        verifyAdjacentVideoSwipe(surfaceIdForFirstVideoPreview, surfaceIdForSecondVideoPreview,
                CLOUD_ID1);

        // Swipe right in preview mode and go to first video, but the surface id will have
        // increased monotonically
        swipeRightAndWait();

        final int surfaceIdForThirdVideoPreview = 2;
        verifyAdjacentVideoSwipe(surfaceIdForSecondVideoPreview, surfaceIdForThirdVideoPreview,
                CLOUD_ID2);

        final UiObject addButton = findPreviewAddButton();
        addButton.click();
        // We don't test the result of the picker here because the intention of the test is only to
        // test the remote preview APIs
    }

    @Test
    @Ignore("Re-enable once b/219861183 is fixed")
    public void testSwipeImageVideoPreview() throws Exception {
        initCloudProviderWithImage(Pair.create(null, CLOUD_ID1));
        initCloudProviderWithVideo(Pair.create(null, CLOUD_ID2));
        launchPreviewMultiple(/* count */ 2);

        // Remote Preview calls onSurfaceCreated with monotonically increasing surfaceIds
        int surfaceId = 0;
        verifyInitialVideoPreviewSetup(surfaceId, CLOUD_ID2);
        // Remote Preview calls onMediaPlay when PLAYBACK_EVENT_READY is sent by the
        // CloudMediaProvider
        verifyPlaybackEventReady(surfaceId);

        // Swipe left preview mode
        swipeLeftAndWait();

        mAssertInOrder.verify(mSurfaceControllerListener).onSurfaceDestroyed(eq(surfaceId));

        // Remote Preview calls onPlayerRelease() for CMP to release the resources if there is no
        // video to preview
        mAssertInOrder.verify(mSurfaceControllerListener).onPlayerRelease();

        // Swipe right preview mode
        swipeRightAndWait();

        // SurfaceId increases monotonically for each video preview
        surfaceId++;
        verifyInitialVideoPreviewSetup(surfaceId, CLOUD_ID2);

        // TODO (b/218871686): Remote Handler should wait for PLAYBACK_EVENT_READY to be sent.
        // Once the above bug is fixed remove the following and call
        // verifyPlaybackEventReady(surfaceId) instead
        mAssertInOrder.verify(mSurfaceControllerListener).onMediaPlay(eq(surfaceId));

        final UiObject addButton = findPreviewAddButton();
        addButton.click();
        // We don't test the result of the picker here because the intention of the test is only to
        // test the remote preview APIs
    }

    /**
     * Verify surface controller interactions on swiping from one video to another.
     * Note: This test assumes that the first video is in playing state.
     *
     * @param oldSurfaceId the Surface ID which we are swiping away from
     * @param newSurfaceId the Surface ID to which we are swiping
     * @param newMediaId the media ID of the video we are swiping to
     */
    private void verifyAdjacentVideoSwipe(int oldSurfaceId, int newSurfaceId, String newMediaId) {
        mAssertInOrder.verify(mSurfaceControllerListener).onSurfaceCreated(eq(newSurfaceId),
                any(), eq(newMediaId));

        // The surface for the first video is destroyed when it is no longer visible on the screen
        // (swipe is complete).
        mAssertInOrder.verify(mSurfaceControllerListener).onSurfaceDestroyed(eq(oldSurfaceId));

        // Media plays automatically after the previous surface is destroyed (in other words the
        // previous video screen is not visible at all and the swipe has completed). This is because
        // the player is already ready.
        mAssertInOrder.verify(mSurfaceControllerListener).onMediaPlay(eq(newSurfaceId));
    }

    /**
     * The first time video preview is called, the surface controller object should get the
     * following callbacks in the following order:
     * * To prepare media player
     * * Surface related callbacks (onSurfaceCreated and onSurfaceChanged)
     *
     * @param surfaceId Surface ID to set up video preview on
     * @param mediaId Media ID to set up video preview with
     */
    private void verifyInitialVideoPreviewSetup(int surfaceId, String mediaId) {
        // Remote Preview calls onPlayerCreate as the first call to CloudMediaProvider
        mAssertInOrder.verify(mSurfaceControllerListener).onPlayerCreate();

        // Remote Preview calls onSurfaceCreated with surfaceId and mediaId as expected
        mAssertInOrder.verify(mSurfaceControllerListener).onSurfaceCreated(eq(surfaceId), any(),
                eq(mediaId));

        // Remote Preview calls onSurfaceChanged to set the format, width and height
        // corresponding to the video on the same surfaceId
        mAssertInOrder.verify(mSurfaceControllerListener).onSurfaceChanged(eq(surfaceId),
                eq(VIDEO_PIXEL_FORMAT), anyInt(), anyInt());
    }

    private void verifyPlaybackEventReady(int surfaceId) {
        CloudProviderPrimary.sendPlaybackEvent(surfaceId, PLAYBACK_EVENT_READY);
        mAssertInOrder.verify(mSurfaceControllerListener).onMediaPlay(eq(surfaceId));
    }

    private void initCloudProviderWithImage(Pair<String, String>... mediaPairs)
            throws Exception {
        for (Pair<String, String> pair: mediaPairs) {
            addImage(mCloudPrimaryMediaGenerator, pair.first, pair.second);
        }

        syncCloudProvider(mContext);
    }

    private void addImage(MediaGenerator generator, String localId, String cloudId)
            throws Exception {
        generator.addMedia(localId, cloudId, /* albumId */ null, "image/jpeg",
                /* mimeTypeExtension */ 0, IMAGE_SIZE_BYTES, /* isFavorite */ false,
                R.raw.lg_g4_iso_800_jpg);
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

    private void launchPreviewMultiple(int count) throws  Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(count);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(count);

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

    private void swipeLeftAndWait() {
        final int width = mDevice.getDisplayWidth();
        final int height = mDevice.getDisplayHeight();
        mDevice.swipe(width / 2, height / 2, width / 4, height / 2, 10);
        mDevice.waitForIdle();
    }

    private void swipeRightAndWait() {
        final int width = mDevice.getDisplayWidth();
        final int height = mDevice.getDisplayHeight();
        mDevice.swipe(width / 4, height / 2, width / 2, height / 2, 10);
        mDevice.waitForIdle();
    }
}
