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

import static android.photopicker.cts.PhotoPickerCloudUtils.disableCloudMediaAndClearAllowedCloudProviders;
import static android.photopicker.cts.PhotoPickerCloudUtils.enableCloudMediaAndSetAllowedCloudProviders;
import static android.photopicker.cts.PhotoPickerCloudUtils.getAllowedProvidersDeviceConfig;
import static android.photopicker.cts.PhotoPickerCloudUtils.isCloudMediaEnabled;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.isPhotoPickerVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsActionBarIsVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsActivityIsVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsDescriptionIsVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsFragmentContainerExists;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsTitleIsVisible;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.os.Build;
import android.os.UserHandle;
import android.photopicker.cts.util.PhotoPickerUiUtils;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Photo Picker tests for settings page launched from the overflow menu in PhotoPickerActivity or
 * the Settings app.
 */
// TODO(b/195009187): Enabling settings page requires setting allowed_cloud_providers device config.
//  We currently can't do this in R.
@RunWith(BedsteadJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
public class PhotoPickerSettingsTest extends PhotoPickerBaseTest {

    private static boolean sCloudMediaPreviouslyEnabled;
    private static String sPreviouslyAllowedCloudProviders;
    private static final String EXTRA_TAB_USER_ID = "user_id";
    private static final String TAB_CONTAINER_RESOURCE_ID =
            REGEX_PACKAGE_NAME + ":id/tab_container";
    private static final String TAB_LAYOUT_RESOURCE_ID = REGEX_PACKAGE_NAME + ":id/tabs";
    private static final String PERSONAL_TAB_TITLE_ENGLISH = "Personal";
    private static final String WORK_TAB_TITLE_ENGLISH = "Work";

    @BeforeClass
    public static void setUpBeforeClass() {
        // Store the current CMP configs, so that we can reset them at the end of the test.
        sCloudMediaPreviouslyEnabled = isCloudMediaEnabled();
        if (sCloudMediaPreviouslyEnabled) {
            sPreviouslyAllowedCloudProviders = getAllowedProvidersDeviceConfig();
        }

        // Enable Settings menu item in PhotoPickerActivity's overflow menu.
        PhotoPickerCloudUtils.enableCloudMediaAndSetAllowedCloudProviders(
                /* allowedCloudProviders */ sTargetPackageName);
    }

    @AfterClass
    public static void tearDownClass() {
        // Reset CloudMedia configs.
        if (sCloudMediaPreviouslyEnabled) {
            enableCloudMediaAndSetAllowedCloudProviders(sPreviouslyAllowedCloudProviders);
        } else {
            disableCloudMediaAndClearAllowedCloudProviders();
        }
    }

    @After
    public void tearDown() {
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    @Test
    public void testSettingsLaunchFromOverflowMenu_WorkDisabled() throws Exception {
        // Launch PhotoPickerActivity.
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        Assume.assumeTrue("Assume photo picker activity is visible", isPhotoPickerVisible());

        // Click on the Settings menu item in the overflow menu.
        final UiObject settingsMenuItem = PhotoPickerUiUtils.findSettingsOverflowMenuItem(sDevice);
        PhotoPickerUiUtils.clickAndWait(sDevice, settingsMenuItem);

        // Verify PhotoPickerSettingsActivity is launched and visible.
        verifySettingsActivityIsVisible();
        verifySettingsActionBarIsVisible();
        verifySettingsTitleIsVisible();
        verifySettingsDescriptionIsVisible();
        verifySettingsFragmentContainerExists();

        // Verify Tab container (to switch profiles) is not visible since Work profile is disabled.
        verifySettingsTabContainerIsNotVisible();
    }

    @Test
    @LargeTest
    @RequireRunOnWorkProfile
    public void testSettingsLaunchedInPersonalProfile_WorkEnabled() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES_SETTINGS);

        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        verifySettingsActivityIsVisible();

        verifySettingsTabContainerIsVisible();
        assertWithMessage("Personal tab is not selected")
                .that(isSelectedTabTitle(PERSONAL_TAB_TITLE_ENGLISH)).isTrue();
    }

    @Test
    @LargeTest
    @RequireRunOnWorkProfile
    public void testSettingsLaunchedInWorkProfile() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES_SETTINGS);
        intent.putExtra(EXTRA_TAB_USER_ID, UserHandle.myUserId());

        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        verifySettingsActivityIsVisible();

        verifySettingsTabContainerIsVisible();
        assertWithMessage("Work tab is not selected")
                .that(isSelectedTabTitle(WORK_TAB_TITLE_ENGLISH)).isTrue();
    }

    private static void verifySettingsTabContainerIsVisible() {
        assertWithMessage("Timed out waiting for settings profile select tab container to appear")
                .that(findObject(TAB_CONTAINER_RESOURCE_ID).waitForExists(SHORT_TIMEOUT))
                .isTrue();
    }

    private static void verifySettingsTabContainerIsNotVisible() {
        assertWithMessage("Found the settings profile select tab container")
                .that(findObject(TAB_CONTAINER_RESOURCE_ID).waitForExists(SHORT_TIMEOUT))
                .isFalse();
    }

    private static boolean isSelectedTabTitle(@NonNull String tabTitle)
            throws UiObjectNotFoundException {
        final UiObject tabLayout = findObject(TAB_LAYOUT_RESOURCE_ID);
        final UiObject tab = tabLayout.getChild(new UiSelector().textContains(tabTitle));
        return tab.isSelected();
    }

    private static UiObject findObject(@NonNull String resourceId) {
        return sDevice.findObject(new UiSelector().resourceIdMatches(resourceId));
    }
}
