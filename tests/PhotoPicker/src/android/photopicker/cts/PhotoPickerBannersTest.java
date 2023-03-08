/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.photopicker.cts.PickerProviderMediaGenerator.setCloudProvider;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImage;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findBannerActionButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findBannerDismissButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.getBannerPrimaryText;
import static android.photopicker.cts.util.PhotoPickerUiUtils.isPhotoPickerVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsActivityIsVisible;
import static android.provider.MediaStore.ACTION_PICK_IMAGES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.photopicker.cts.cloudproviders.CloudProviderPrimary;

import androidx.test.filters.SdkSuppress;
import androidx.test.uiautomator.UiObject;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Photo Picker Banner Tests for common flows.
 */
// TODO(b/195009187): Enabling the banners requires setting allowed_cloud_providers device config.
//  We currently can't do this in R.
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
public class PhotoPickerBannersTest extends PhotoPickerBaseTest {

    private static String sPreviouslyAllowedCloudProviders;
    private Uri mLocalMediaFileUri;

    @BeforeClass
    public static void setUpBeforeClass() {
        // Store the current allowed cloud providers for reset at the end of tests.
        sPreviouslyAllowedCloudProviders = PhotoPickerCloudUtils.getAllowedProvidersDeviceConfig();

        // Override the allowed cloud providers config to enable the banners.
        final String allowedCloudProviders = CloudProviderPrimary.AUTHORITY;
        PhotoPickerCloudUtils.setAllowedProvidersDeviceConfig(allowedCloudProviders);
    }

    @AfterClass
    public static void tearDownClass() {
        // Reset the allowed cloud providers device config.
        PhotoPickerCloudUtils.setAllowedProvidersDeviceConfig(sPreviouslyAllowedCloudProviders);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        setCloudProvider(mContext, /* authority */ null);

        // Create a local media file because if there's no media items for the picker grids,
        // the recycler view gets hidden along with the banners.
        mLocalMediaFileUri = createImage(mContext.getUserId(), /* isFavorite */ false).first;
    }

    @After
    public void tearDown() throws Exception {
        if (!isHardwareSupported()) {
            // No-op, skip tear down if hardware is not supported.
            return;
        }

        if (mActivity != null) {
            mActivity.finish();
        }

        deleteMedia(mLocalMediaFileUri, mContext);

        setCloudProvider(mContext, /* authority */ null);
    }

    @Test
    public void testChooseAppBannerOnDismiss() throws Exception {
        // 1. Setting up the 'Choose App' banner.
        setCloudMediaInfoForChooseAppBanner();

        // 2. Assert that the 'Choose App' banner is visible.
        assertThat(getBannerPrimaryText()).isEqualTo("Choose cloud media app");

        // 3. Click the banner 'Dismiss' button.
        final UiObject dismissButton = findBannerDismissButton();
        dismissButton.click();

        // 4. Assert that the Banner disappeared while the Picker is still visible.
        assertWithMessage("Timed out waiting for the banner to disappear")
                .that(dismissButton.waitUntilGone(TIMEOUT))
                .isTrue();
        assertThatPhotoPickerActivityIsVisible();
    }

    @Test
    public void testChooseAppBannerOnActionButtonClick() throws Exception {
        // 1. Setting up the 'Choose App' banner.
        setCloudMediaInfoForChooseAppBanner();

        // 2. Assert that the 'Choose App' banner is visible.
        assertThat(getBannerPrimaryText()).isEqualTo("Choose cloud media app");

        // 3. Click the banner 'Action' button.
        findBannerActionButton().click();

        // 4. Assert that Settings page is visible.
        verifySettingsActivityIsVisible();
        sDevice.pressBack();
    }

    private void setCloudMediaInfoForChooseAppBanner() {
        // 1. Set a non-null cloud provider and launch the photo picker.
        setCloudProvider(mContext, CloudProviderPrimary.AUTHORITY);
        launchPickerActivity();
        // 2. Close the photo picker.
        mActivity.finish();
        // 3. Set the cloud provider as None and launch the photo picker.
        setCloudProvider(mContext, /* authority */ null);
        launchPickerActivity();
    }

    private void launchPickerActivity() {
        final Intent intent = new Intent(ACTION_PICK_IMAGES);
        mActivity.startActivity(intent);
        assertThatPhotoPickerActivityIsVisible();
    }

    private void assertThatPhotoPickerActivityIsVisible() {
        assertWithMessage("Timed out waiting for the photo picker activity to appear")
                .that(isPhotoPickerVisible())
                .isTrue();
    }
}
