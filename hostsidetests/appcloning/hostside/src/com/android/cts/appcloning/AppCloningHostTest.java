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

package com.android.cts.appcloning;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.contentprovider.ContentProviderHandler;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs the AppCloning tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class AppCloningHostTest extends AppCloningBaseHostTest {

    private static final int CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS = 20000;
    private static final int CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS = 30000;

    private static final String IMAGE_NAME_TO_BE_CREATED_KEY = "imageNameToBeCreated";
    private static final String IMAGE_NAME_TO_BE_DISPLAYED_KEY = "imageNameToBeDisplayed";
    private static final String IMAGE_NAME_TO_BE_VERIFIED_IN_OWNER_PROFILE_KEY =
            "imageNameToBeVerifiedInOwnerProfile";
    private static final String IMAGE_NAME_TO_BE_VERIFIED_IN_CLONE_PROFILE_KEY =
            "imageNameToBeVerifiedInCloneProfile";
    private static final String CLONE_USER_ID = "cloneUserId";
    private static final String MEDIA_PROVIDER_IMAGES_PATH = "/external/images/media/";
    private ContentProviderHandler mContentProviderHandler;

    @Before
    public void setup() throws Exception {
        super.baseHostSetup();

        mContentProviderHandler = new ContentProviderHandler(getDevice());
        mContentProviderHandler.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.baseHostTeardown();

        if (mContentProviderHandler != null) {
            mContentProviderHandler.tearDown();
        }
    }

    @Test
    public void testCreateCloneUserFile() throws Exception {
        CommandResult out;

        // Check that the clone user directories exist
        eventually(() -> {
            // Wait for finish.
            assertThat(isSuccessful(
                    runContentProviderCommand("query", mCloneUserId,
                            CONTENT_PROVIDER_URL, "/sdcard", ""))).isTrue();
        }, CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS);

        // Create a file on the clone user storage
        out = executeShellV2Command("touch /sdcard/testFile.txt");
        assertThat(isSuccessful(out)).isTrue();
        eventually(() -> {
            // Wait for finish.
            assertThat(isSuccessful(
                    runContentProviderCommand("write", mCloneUserId,
                            CONTENT_PROVIDER_URL, "/sdcard/testFile.txt",
                            "< /sdcard/testFile.txt"))).isTrue();
        }, CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS);

        // Check that the above created file exists on the clone user storage
        out = runContentProviderCommand("query", mCloneUserId,
                CONTENT_PROVIDER_URL, "/sdcard/testFile.txt", "");
        assertThat(isSuccessful(out)).isTrue();

        // Cleanup the created file
        out = runContentProviderCommand("delete", mCloneUserId,
                CONTENT_PROVIDER_URL, "/sdcard/testFile.txt", "");
        assertThat(isSuccessful(out)).isTrue();
    }

    /**
     * Once the clone profile is removed, the storage directory is deleted and the media provider
     * should be cleaned of any media files associated with clone profile.
     * This test ensures that with removal of clone profile there are no stale reference in
     * media provider for media files related to clone profile.
     * @throws Exception
     */
    @Test
    public void testRemoveClonedProfileMediaProviderCleanup() throws Exception {
        CommandResult out;
        String cloneProfileImage = "cloneProfileImage.png";

        // Inserting blank image in clone profile
        eventually(() -> {
            assertThat(isSuccessful(
                    runContentProviderCommand("insert", mCloneUserId,
                            MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                            String.format("--bind _data:s:/storage/emulated/%s/Pictures/%s",
                                    mCloneUserId, cloneProfileImage),
                            String.format("--bind _user_id:s:%s", mCloneUserId)))).isTrue();
        }, CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS);

        //Ensuring that image is added to media provider
        out = runContentProviderCommand("query", mCloneUserId,
                MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                "--projection _id",
                String.format("--where \"_display_name=\\'%s\\'\"", cloneProfileImage));
        assertThat(isSuccessful(out)).isTrue();
        assertThat(out.getStdout()).doesNotContain("No result found.");

        //Removing the clone profile
        eventually(() -> {
            assertThat(isSuccessful(executeShellV2Command("pm remove-user %s", mCloneUserId)))
                    .isTrue();
        }, CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS);

        //Checking that added image should not be available in share media provider
        try {
            eventually(() -> {
                CommandResult queryResult = runContentProviderCommand("query",
                        String.valueOf(getCurrentUserId()),
                        MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                        "--projection _id",
                        String.format("--where \"_display_name=\\'%s\\'\"", cloneProfileImage));
                assertThat(isSuccessful(queryResult)).isTrue();
                assertThat(queryResult.getStdout()).contains("No result found.");
            }, CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS);
        } catch (Exception exception) {
            //If the image is available i.e. test have failed, delete the added user
            runContentProviderCommand("delete", String.valueOf(getCurrentUserId()),
                    MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                    String.format("--where \"_display_name=\\'%s\\'\"", cloneProfileImage));
            throw exception;
        }
    }

    @Test
    public void testPrivateAppDataDirectoryForCloneUser() throws Exception {
        eventually(() -> {
            // Wait for finish.
            assertThat(isPackageInstalled(APP_A_PACKAGE, mCloneUserId)).isTrue();
        }, CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS);
    }

    @Test
    public void testCrossUserMediaAccess() throws Exception {
        // Run save image test in owner user space
        Map<String, String> ownerArgs = new HashMap<>();
        ownerArgs.put(IMAGE_NAME_TO_BE_DISPLAYED_KEY, "WeirdOwnerProfileImage");
        ownerArgs.put(IMAGE_NAME_TO_BE_CREATED_KEY, "owner_profile_image");

        runDeviceTestAsUserInPkgA("testMediaStoreManager_writeImageToSharedStorage",
                getCurrentUserId(), ownerArgs);

        // Run save image test in clone user space
        Map<String, String> cloneArgs = new HashMap<>();
        cloneArgs.put(IMAGE_NAME_TO_BE_DISPLAYED_KEY, "WeirdCloneProfileImage");
        cloneArgs.put(IMAGE_NAME_TO_BE_CREATED_KEY, "clone_profile_image");

        runDeviceTestAsUserInPkgA("testMediaStoreManager_writeImageToSharedStorage",
                Integer.valueOf(mCloneUserId), cloneArgs);

        // Run cross user access test
        Map<String, String> args = new HashMap<>();
        args.put(IMAGE_NAME_TO_BE_VERIFIED_IN_OWNER_PROFILE_KEY, "WeirdOwnerProfileImage");
        args.put(IMAGE_NAME_TO_BE_VERIFIED_IN_CLONE_PROFILE_KEY, "WeirdCloneProfileImage");
        args.put(CLONE_USER_ID, mCloneUserId);

        // From owner user space
        runDeviceTestAsUserInPkgA(
                "testMediaStoreManager_verifyCrossUserImagesInSharedStorage",
                getCurrentUserId(), args);

        // From clone user space
        runDeviceTestAsUserInPkgA(
                "testMediaStoreManager_verifyCrossUserImagesInSharedStorage",
                Integer.valueOf(mCloneUserId), args);
    }

    @Test
    public void testGetStorageVolumesIncludingSharedProfiles() throws Exception {
        assumeTrue(isAtLeastT());
        Map<String, String> args = new HashMap<>();
        args.put(CLONE_USER_ID, mCloneUserId);
        runDeviceTestAsUserInPkgA("testStorageManager_verifyInclusionOfSharedProfileVolumes",
                getCurrentUserId(), args);
    }
}
