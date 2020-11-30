/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.scopedstorage.cts;

import static android.app.AppOpsManager.permissionToOp;
import static android.os.SystemProperties.getBoolean;
import static android.provider.MediaStore.MediaColumns;
import static android.scopedstorage.cts.lib.TestUtils.BYTES_DATA1;
import static android.scopedstorage.cts.lib.TestUtils.adoptShellPermissionIdentity;
import static android.scopedstorage.cts.lib.TestUtils.allowAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.assertCanRenameDirectory;
import static android.scopedstorage.cts.lib.TestUtils.assertCanRenameFile;
import static android.scopedstorage.cts.lib.TestUtils.assertCantRenameDirectory;
import static android.scopedstorage.cts.lib.TestUtils.assertDirectoryContains;
import static android.scopedstorage.cts.lib.TestUtils.assertFileContent;
import static android.scopedstorage.cts.lib.TestUtils.assertThrows;
import static android.scopedstorage.cts.lib.TestUtils.canOpen;
import static android.scopedstorage.cts.lib.TestUtils.canOpenFileAs;
import static android.scopedstorage.cts.lib.TestUtils.canReadAndWriteAs;
import static android.scopedstorage.cts.lib.TestUtils.createFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAs;
import static android.scopedstorage.cts.lib.TestUtils.deleteFileAsNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.deleteWithMediaProviderNoThrow;
import static android.scopedstorage.cts.lib.TestUtils.denyAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.dropShellPermissionIdentity;
import static android.scopedstorage.cts.lib.TestUtils.executeShellCommand;
import static android.scopedstorage.cts.lib.TestUtils.getAndroidDir;
import static android.scopedstorage.cts.lib.TestUtils.getAndroidMediaDir;
import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;
import static android.scopedstorage.cts.lib.TestUtils.getDcimDir;
import static android.scopedstorage.cts.lib.TestUtils.getDefaultTopLevelDirs;
import static android.scopedstorage.cts.lib.TestUtils.getDownloadDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalFilesDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalMediaDir;
import static android.scopedstorage.cts.lib.TestUtils.getExternalStorageDir;
import static android.scopedstorage.cts.lib.TestUtils.getFileOwnerPackageFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileRowIdFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getFileUri;
import static android.scopedstorage.cts.lib.TestUtils.getImageContentUri;
import static android.scopedstorage.cts.lib.TestUtils.getMoviesDir;
import static android.scopedstorage.cts.lib.TestUtils.getMusicDir;
import static android.scopedstorage.cts.lib.TestUtils.getPicturesDir;
import static android.scopedstorage.cts.lib.TestUtils.getPodcastsDir;
import static android.scopedstorage.cts.lib.TestUtils.installApp;
import static android.scopedstorage.cts.lib.TestUtils.installAppWithStoragePermissions;
import static android.scopedstorage.cts.lib.TestUtils.listAs;
import static android.scopedstorage.cts.lib.TestUtils.openWithMediaProvider;
import static android.scopedstorage.cts.lib.TestUtils.pollForExternalStorageState;
import static android.scopedstorage.cts.lib.TestUtils.pollForManageExternalStorageAllowed;
import static android.scopedstorage.cts.lib.TestUtils.pollForPermission;
import static android.scopedstorage.cts.lib.TestUtils.queryImageFile;
import static android.scopedstorage.cts.lib.TestUtils.queryVideoFile;
import static android.scopedstorage.cts.lib.TestUtils.setupDefaultDirectories;
import static android.scopedstorage.cts.lib.TestUtils.uninstallApp;
import static android.scopedstorage.cts.lib.TestUtils.uninstallAppNoThrow;
import static android.system.OsConstants.F_OK;
import static android.system.OsConstants.R_OK;
import static android.system.OsConstants.W_OK;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.TestApp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the scoped storage tests on primary external storage.
 *
 * <p>These tests are also run on a public volume by {@link PublicVolumeTest}.
 */
@RunWith(AndroidJUnit4.class)
public class ScopedStorageTest {
    static final String TAG = "ScopedStorageTest";
    static final String CONTENT_PROVIDER_URL = "content://android.tradefed.contentprovider";
    static final String THIS_PACKAGE_NAME = getContext().getPackageName();
    static final int USER_SYSTEM = 0;

    /**
     * To help avoid flaky tests, give ourselves a unique nonce to be used for
     * all filesystem paths, so that we don't risk conflicting with previous
     * test runs.
     */
    static final String NONCE = String.valueOf(System.nanoTime());

    static final String TEST_DIRECTORY_NAME = "ScopedStorageTestDirectory" + NONCE;

    static final String AUDIO_FILE_NAME = "ScopedStorageTest_file_" + NONCE + ".mp3";
    static final String PLAYLIST_FILE_NAME = "ScopedStorageTest_file_" + NONCE + ".m3u";
    static final String SUBTITLE_FILE_NAME = "ScopedStorageTest_file_" + NONCE + ".srt";
    static final String VIDEO_FILE_NAME = "ScopedStorageTest_file_" + NONCE + ".mp4";
    static final String IMAGE_FILE_NAME = "ScopedStorageTest_file_" + NONCE + ".jpg";
    static final String NONMEDIA_FILE_NAME = "ScopedStorageTest_file_" + NONCE + ".pdf";

    static final String FILE_CREATION_ERROR_MESSAGE = "No such file or directory";

    private static final TestApp TEST_APP_A = new TestApp("TestAppA",
            "android.scopedstorage.cts.testapp.A", 1, false, "CtsScopedStorageTestAppA.apk");
    private static final TestApp TEST_APP_B = new TestApp("TestAppB",
            "android.scopedstorage.cts.testapp.B", 1, false, "CtsScopedStorageTestAppB.apk");
    private static final TestApp TEST_APP_C = new TestApp("TestAppC",
            "android.scopedstorage.cts.testapp.C", 1, false, "CtsScopedStorageTestAppC.apk");
    private static final TestApp TEST_APP_C_LEGACY = new TestApp("TestAppCLegacy",
            "android.scopedstorage.cts.testapp.C", 1, false, "CtsScopedStorageTestAppCLegacy.apk");
    private static final String[] SYSTEM_GALERY_APPOPS = {
            AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES, AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO};
    private static final String OPSTR_MANAGE_EXTERNAL_STORAGE =
            permissionToOp(Manifest.permission.MANAGE_EXTERNAL_STORAGE);

    @Before
    public void setup() throws Exception {
        // skips all test cases if FUSE is not active.
        assumeTrue(getBoolean("persist.sys.fuse", false));

        if (!getContext().getPackageManager().isInstantApp()) {
            pollForExternalStorageState();
            getExternalFilesDir().mkdirs();
        }
    }

    /**
     * This method needs to be called once before running the whole test.
     */
    @Test
    public void setupExternalStorage() {
        setupDefaultDirectories();
    }

    /**
     * Test that readdir lists unsupported file types in default directories.
     */
    @Test
    public void testListUnsupportedFileType() throws Exception {
        final File pdfFile = new File(getDcimDir(), NONMEDIA_FILE_NAME);
        final File videoFile = new File(getMusicDir(), VIDEO_FILE_NAME);
        try {
            // TEST_APP_A with storage permission should not see pdf file in DCIM
            createFileUsingTradefedContentProvider(pdfFile);
            assertThat(pdfFile.exists()).isTrue();
            assertThat(MediaStore.scanFile(getContentResolver(), pdfFile)).isNotNull();

            installAppWithStoragePermissions(TEST_APP_A);
            assertThat(listAs(TEST_APP_A, getDcimDir().getPath()))
                    .doesNotContain(NONMEDIA_FILE_NAME);

            createFileUsingTradefedContentProvider(videoFile);
            // We don't insert files to db for files created by shell.
            assertThat(MediaStore.scanFile(getContentResolver(), videoFile)).isNotNull();
            // TEST_APP_A with storage permission should see video file in Music directory.
            assertThat(listAs(TEST_APP_A, getMusicDir().getPath())).contains(VIDEO_FILE_NAME);
        } finally {
            deleteFileUsingTradefedContentProvider(pdfFile);
            deleteFileUsingTradefedContentProvider(videoFile);
            MediaStore.scanFile(getContentResolver(), pdfFile);
            MediaStore.scanFile(getContentResolver(), videoFile);
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

   /**
     * Test that we don't allow renaming to top level directory
     */
    @Test
    public void testCantRenameToTopLevelDirectory() throws Exception {
        final File topLevelDir1 = new File(getExternalStorageDir(), TEST_DIRECTORY_NAME + "_1");
        final File topLevelDir2 = new File(getExternalStorageDir(), TEST_DIRECTORY_NAME + "_2");
        final File nonTopLevelDir = new File(getDcimDir(), TEST_DIRECTORY_NAME);
        try {
            createDirUsingTradefedContentProvider(topLevelDir1);
            assertTrue(topLevelDir1.exists());

            // We can't rename a top level directory to a top level directory
            assertCantRenameDirectory(topLevelDir1, topLevelDir2, null);

            // However, we can rename a top level directory to non-top level directory.
            assertCanRenameDirectory(topLevelDir1, nonTopLevelDir, null, null);

            // We can't rename a non-top level directory to a top level directory.
            assertCantRenameDirectory(nonTopLevelDir, topLevelDir2, null);
        } finally {
            deleteDirUsingTradefedContentProvider(topLevelDir1);
            deleteDirUsingTradefedContentProvider(topLevelDir2);
            nonTopLevelDir.delete();
        }
    }

    @Test
    public void testManageExternalStorageCanCreateFilesAnywhere() throws Exception {
        pollForManageExternalStorageAllowed();

        final File topLevelPdf = new File(getExternalStorageDir(), NONMEDIA_FILE_NAME);
        final File musicFileInMovies = new File(getMoviesDir(), AUDIO_FILE_NAME);
        final File imageFileInDcim = new File(getDcimDir(), IMAGE_FILE_NAME);

        // Nothing special about this, anyone can create an image file in DCIM
        assertCanCreateFile(imageFileInDcim);
        // This is where we see the special powers of MANAGE_EXTERNAL_STORAGE, because it can
        // create a top level file
        assertCanCreateFile(topLevelPdf);
        // It can even create a music file in Pictures
        assertCanCreateFile(musicFileInMovies);
    }

    @Test
    public void testManageExternalStorageCantReadWriteOtherAppExternalDir() throws Exception {
        pollForManageExternalStorageAllowed();

        try {
            // Install TEST_APP_A with READ_EXTERNAL_STORAGE permission.
            installAppWithStoragePermissions(TEST_APP_A);

            // Let app A create a file in its data dir
            final File otherAppExternalDataDir = new File(getExternalFilesDir().getPath().replace(
                    THIS_PACKAGE_NAME, TEST_APP_A.getPackageName()));
            final File otherAppExternalDataFile = new File(otherAppExternalDataDir,
                    NONMEDIA_FILE_NAME);
            assertCreateFilesAs(TEST_APP_A, otherAppExternalDataFile);

            // File Manager app gets global access with MANAGE_EXTERNAL_STORAGE permission, however,
            // file manager app doesn't have access to other app's external files directory
            assertThat(canOpen(otherAppExternalDataFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(otherAppExternalDataFile, /* forWrite */ true)).isFalse();
            assertThat(otherAppExternalDataFile.delete()).isFalse();

            assertThat(deleteFileAs(TEST_APP_A, otherAppExternalDataFile.getPath())).isTrue();

            assertThrows(IOException.class,
                    () -> { otherAppExternalDataFile.createNewFile(); });

        } finally {
            uninstallApp(TEST_APP_A); // Uninstalling deletes external app dirs
        }
    }

    /**
     * b/168830497: Test that app can write to file in DCIM/Camera even with .nomedia presence
     */
    @Test
    public void testCanWriteToDCIMCameraWithNomedia() throws Exception {
        final File cameraDir = new File(getDcimDir(), "Camera");
        final File nomediaFile = new File(cameraDir, ".nomedia");
        Uri targetUri = null;

        try {
            // Recreate required file and directory
            if (cameraDir.exists()) {
                // This is a work around to address a known inode cache inconsistency issue
                // that occurs when test runs for the second time.
                deleteDirUsingTradefedContentProvider(cameraDir);
            }

            createDirUsingTradefedContentProvider(cameraDir);
            assertTrue(cameraDir.exists());

            createFileUsingTradefedContentProvider(nomediaFile);
            assertTrue(nomediaFile.exists());

            ContentValues values = new ContentValues();
            values.put(MediaColumns.RELATIVE_PATH, "DCIM/Camera");
            targetUri = getContentResolver().insert(getImageContentUri(), values, Bundle.EMPTY);
            assertNotNull(targetUri);

            try (ParcelFileDescriptor pfd =
                         getContentResolver().openFileDescriptor(targetUri, "w")) {
                assertThat(pfd).isNotNull();
                Os.write(pfd.getFileDescriptor(), ByteBuffer.wrap(BYTES_DATA1));
            }

            assertFileContent(new File(getFilePathFromUri(targetUri)), BYTES_DATA1);
        } finally {
            deleteWithMediaProviderNoThrow(targetUri);
            deleteFileUsingTradefedContentProvider(nomediaFile);
            deleteDirUsingTradefedContentProvider(cameraDir);
        }
    }

    @Test
    public void testManageExternalStorageCanDeleteOtherAppsContents() throws Exception {
        pollForManageExternalStorageAllowed();

        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File otherAppImage = new File(getDcimDir(), "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(getMusicDir(), "other" + AUDIO_FILE_NAME);
        try {
            installApp(TEST_APP_A);

            // Create all of the files as another app
            assertThat(createFileAs(TEST_APP_A, otherAppPdf.getPath())).isTrue();
            assertThat(createFileAs(TEST_APP_A, otherAppImage.getPath())).isTrue();
            assertThat(createFileAs(TEST_APP_A, otherAppMusic.getPath())).isTrue();

            assertThat(otherAppPdf.delete()).isTrue();
            assertThat(otherAppPdf.exists()).isFalse();

            assertThat(otherAppImage.delete()).isTrue();
            assertThat(otherAppImage.exists()).isFalse();

            assertThat(otherAppMusic.delete()).isTrue();
            assertThat(otherAppMusic.exists()).isFalse();
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, otherAppPdf.getAbsolutePath());
            deleteFileAsNoThrow(TEST_APP_A, otherAppImage.getAbsolutePath());
            deleteFileAsNoThrow(TEST_APP_A, otherAppMusic.getAbsolutePath());
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testAccess_file() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);

        final File downloadDir = getDownloadDir();
        final File otherAppPdf = new File(downloadDir, "other-" + NONMEDIA_FILE_NAME);
        final File shellPdfAtRoot = new File(getExternalStorageDir(),
                "shell-" + NONMEDIA_FILE_NAME);
        final File otherAppImage = new File(getDcimDir(), "other-" + IMAGE_FILE_NAME);
        final File myAppPdf = new File(downloadDir, "my-" + NONMEDIA_FILE_NAME);
        final File doesntExistPdf = new File(downloadDir, "nada-" + NONMEDIA_FILE_NAME);

        try {
            installApp(TEST_APP_A);

            assertThat(createFileAs(TEST_APP_A, otherAppPdf.getPath())).isTrue();
            assertThat(createFileAs(TEST_APP_A, otherAppImage.getPath())).isTrue();

            // We can read our image and pdf files.
            assertThat(myAppPdf.createNewFile()).isTrue();
            assertFileAccess_readWrite(myAppPdf);

            // We can read the other app's image file because we hold R_E_S, but we can
            // check only exists for the pdf files.
            assertFileAccess_readOnly(otherAppImage);
            assertFileAccess_existsOnly(otherAppPdf);
            assertAccess(doesntExistPdf, false, false, false);

            // We can check only exists for another app's files on root.
            // Use content provider to create root file because TEST_APP_A is in
            // scoped storage.
            createFileUsingTradefedContentProvider(shellPdfAtRoot);
            MediaStore.scanFile(getContentResolver(), shellPdfAtRoot);
            assertFileAccess_existsOnly(shellPdfAtRoot);
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, otherAppPdf.getAbsolutePath());
            deleteFileAsNoThrow(TEST_APP_A, otherAppImage.getAbsolutePath());
            deleteFileUsingTradefedContentProvider(shellPdfAtRoot);
            MediaStore.scanFile(getContentResolver(), shellPdfAtRoot);
            myAppPdf.delete();
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testAccess_directory() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);
        pollForPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, /*granted*/ true);
        File topLevelDir = new File(getExternalStorageDir(), "Test");
        try {
            installApp(TEST_APP_A);

            // Let app A create a file in its data dir
            final File otherAppExternalDataDir = new File(getExternalFilesDir().getPath().replace(
                    THIS_PACKAGE_NAME, TEST_APP_A.getPackageName()));
            final File otherAppExternalDataSubDir = new File(otherAppExternalDataDir, "subdir");
            final File otherAppExternalDataFile = new File(otherAppExternalDataSubDir, "abc.jpg");
            assertThat(createFileAs(TEST_APP_A, otherAppExternalDataFile.getAbsolutePath()))
                    .isTrue();

            // We cannot read or write the file, but app A can.
            assertThat(canReadAndWriteAs(TEST_APP_A,
                    otherAppExternalDataFile.getAbsolutePath())).isTrue();
            assertCannotReadOrWrite(otherAppExternalDataFile);

            // We cannot read or write the dir, but app A can.
            assertThat(canReadAndWriteAs(TEST_APP_A,
                    otherAppExternalDataDir.getAbsolutePath())).isTrue();
            assertCannotReadOrWrite(otherAppExternalDataDir);

            // We cannot read or write the sub dir, but app A can.
            assertThat(canReadAndWriteAs(TEST_APP_A,
                    otherAppExternalDataSubDir.getAbsolutePath())).isTrue();
            assertCannotReadOrWrite(otherAppExternalDataSubDir);

            // We can read and write our own app dir, but app A cannot.
            assertThat(canReadAndWriteAs(TEST_APP_A,
                    getExternalFilesDir().getAbsolutePath())).isFalse();
            assertCanAccessMyAppFile(getExternalFilesDir());

            assertDirectoryAccess(getDcimDir(), /* exists */ true, /* canWrite */ true);
            assertDirectoryAccess(getExternalStorageDir(),true, false);
            assertDirectoryAccess(new File(getExternalStorageDir(), "Android"), true, false);
            assertDirectoryAccess(new File(getExternalStorageDir(), "doesnt/exist"), false, false);

            createDirUsingTradefedContentProvider(topLevelDir);
            assertDirectoryAccess(topLevelDir, true, false);

            // We can see "/storage/emulated" exists, but not read/write to it, since it's
            // outside the scope of external storage.
            assertAccess(new File("/storage/emulated"), true, false, false);

            // Verify we can enter "/storage/emulated/<userId>" and read
            int userId = getContext().getUserId();
            assertAccess(new File("/storage/emulated/" + userId), true, true, false);

            // Verify we can't get another userId
            int otherUserId = userId + 1;
            assertAccess(new File("/storage/emulated/" + otherUserId), false, false, false);

            // Or an obviously invalid userId (b/172629984)
            assertAccess(new File("/storage/emulated/100000000000"), false, false, false);
        } finally {
            uninstallApp(TEST_APP_A); // Uninstalling deletes external app dirs
            deleteDirUsingTradefedContentProvider(topLevelDir);
        }
    }

    @Test
    public void testManageExternalStorageCanRenameOtherAppsContents() throws Exception {
        pollForManageExternalStorageAllowed();

        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File pdf = new File(getDownloadDir(), NONMEDIA_FILE_NAME);
        final File pdfInObviouslyWrongPlace = new File(getPicturesDir(), NONMEDIA_FILE_NAME);
        final File topLevelPdf = new File(getExternalStorageDir(), NONMEDIA_FILE_NAME);
        final File musicFile = new File(getMusicDir(), AUDIO_FILE_NAME);
        try {
            installApp(TEST_APP_A);

            // Have another app create a PDF
            assertThat(createFileAs(TEST_APP_A, otherAppPdf.getPath())).isTrue();
            assertThat(otherAppPdf.exists()).isTrue();


            // Write some data to the file
            try (final FileOutputStream fos = new FileOutputStream(otherAppPdf)) {
                fos.write(BYTES_DATA1);
            }
            assertFileContent(otherAppPdf, BYTES_DATA1);

            // Assert we can rename the file and ensure the file has the same content
            assertCanRenameFile(otherAppPdf, pdf, /* checkDatabase */ false);
            assertFileContent(pdf, BYTES_DATA1);
            // We can even move it to the top level directory
            assertCanRenameFile(pdf, topLevelPdf, /* checkDatabase */ false);
            assertFileContent(topLevelPdf, BYTES_DATA1);
            // And even rename to a place where PDFs don't belong, because we're an omnipotent
            // external storage manager
            assertCanRenameFile(topLevelPdf, pdfInObviouslyWrongPlace, /* checkDatabase */ false);
            assertFileContent(pdfInObviouslyWrongPlace, BYTES_DATA1);

            // And we can even convert it into a music file, because why not?
            assertCanRenameFile(pdfInObviouslyWrongPlace, musicFile, /* checkDatabase */ false);
            assertFileContent(musicFile, BYTES_DATA1);
        } finally {
            pdf.delete();
            pdfInObviouslyWrongPlace.delete();
            topLevelPdf.delete();
            musicFile.delete();
            deleteFileAsNoThrow(TEST_APP_A, otherAppPdf.getAbsolutePath());
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testCanCreateDefaultDirectory() throws Exception {
        final File podcastsDir = getPodcastsDir();
        try {
            if (podcastsDir.exists()) {
                deleteDirUsingTradefedContentProvider(podcastsDir);
            }
            assertThat(podcastsDir.mkdir()).isTrue();
        } finally {
            createDirUsingTradefedContentProvider(podcastsDir);
        }
    }

    @Test
    public void testManageExternalStorageReaddir() throws Exception {
        pollForManageExternalStorageAllowed();

        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(getDcimDir(), "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(getMusicDir(), "other" + AUDIO_FILE_NAME);
        final File otherTopLevelFile = new File(getExternalStorageDir(),
                "other" + NONMEDIA_FILE_NAME);
        try {
            installApp(TEST_APP_A);
            assertCreateFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf);
            createFileUsingTradefedContentProvider(otherTopLevelFile);
            MediaStore.scanFile(getContentResolver(), otherTopLevelFile);

            // We can list other apps' files
            assertDirectoryContains(otherAppPdf.getParentFile(), otherAppPdf);
            assertDirectoryContains(otherAppImg.getParentFile(), otherAppImg);
            assertDirectoryContains(otherAppMusic.getParentFile(), otherAppMusic);
            // We can list top level files
            assertDirectoryContains(getExternalStorageDir(), otherTopLevelFile);

            // We can also list all top level directories
            assertDirectoryContains(getExternalStorageDir(), getDefaultTopLevelDirs());
        } finally {
            deleteFileUsingTradefedContentProvider(otherTopLevelFile);
            MediaStore.scanFile(getContentResolver(), otherTopLevelFile);
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf);
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testManageExternalStorageQueryOtherAppsFile() throws Exception {
        pollForManageExternalStorageAllowed();

        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(getDcimDir(), "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(getMusicDir(), "other" + AUDIO_FILE_NAME);
        final File otherHiddenFile = new File(getPicturesDir(), ".otherHiddenFile.jpg");
        try {
            installApp(TEST_APP_A);
            // Apps can't query other app's pending file, hence create file and publish it.
            assertCreatePublishedFilesAs(
                    TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);

            assertCanQueryAndOpenFile(otherAppPdf, "rw");
            assertCanQueryAndOpenFile(otherAppImg, "rw");
            assertCanQueryAndOpenFile(otherAppMusic, "rw");
            assertCanQueryAndOpenFile(otherHiddenFile, "rw");
        } finally {
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testAndroidMedia() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);

        try {
            installApp(TEST_APP_A);

            final File myMediaDir = getExternalMediaDir();
            final File otherAppMediaDir = new File(myMediaDir.getAbsolutePath().
                    replace(THIS_PACKAGE_NAME, TEST_APP_A.getPackageName()));

            // Verify that accessing other app's /sdcard/Android/media behaves exactly like DCIM for
            // image files and exactly like Downloads for documents.
            assertSharedStorageAccess(otherAppMediaDir, otherAppMediaDir, TEST_APP_A);
            assertSharedStorageAccess(getDcimDir(), getDownloadDir(), TEST_APP_A);

        } finally {
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testWallpaperApisNoPermission() throws Exception {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
        assumeTrue("Test skipped as wallpaper is not supported.",
                wallpaperManager.isWallpaperSupported());
        assertThrows(SecurityException.class, () -> wallpaperManager.getFastDrawable());
        assertThrows(SecurityException.class, () -> wallpaperManager.peekFastDrawable());
        assertThrows(SecurityException.class,
                () -> wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM));
    }

    @Test
    public void testWallpaperApisReadExternalStorage() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
        wallpaperManager.getFastDrawable();
        wallpaperManager.peekFastDrawable();
        wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM);
    }

    @Test
    public void testWallpaperApisManageExternalStorageAppOp() throws Exception {
        pollForManageExternalStorageAllowed();

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
        wallpaperManager.getFastDrawable();
        wallpaperManager.peekFastDrawable();
        wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM);
    }

    @Test
    public void testWallpaperApisManageExternalStoragePrivileged() throws Exception {
        adoptShellPermissionIdentity(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
            wallpaperManager.getFastDrawable();
            wallpaperManager.peekFastDrawable();
            wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM);
        } finally {
            dropShellPermissionIdentity();
        }
    }

    /**
     * Verifies that files created by {@code otherApp} in shared locations {@code imageDir}
     * and {@code documentDir} follow the scoped storage rules. Requires the running app to hold
     * {@code READ_EXTERNAL_STORAGE}.
     */
    private void assertSharedStorageAccess(File imageDir, File documentDir, TestApp otherApp)
            throws Exception {
        final File otherAppImage = new File(imageDir, "abc.jpg");
        final File otherAppBinary = new File(documentDir, "abc.bin");
        try {
            assertCreateFilesAs(otherApp, otherAppImage, otherAppBinary);

            // We can read the other app's image
            assertFileAccess_readOnly(otherAppImage);
            assertFileContent(otherAppImage, new String().getBytes());

            // .. but not the binary file
            assertFileAccess_existsOnly(otherAppBinary);
            assertThrows(FileNotFoundException.class, () -> {
                assertFileContent(otherAppBinary, new String().getBytes()); });
        } finally {
            deleteFileAsNoThrow(otherApp, otherAppImage.getAbsolutePath());
            deleteFileAsNoThrow(otherApp, otherAppBinary.getAbsolutePath());
        }
    }


    @Test
    public void testOpenOtherPendingFilesFromFuse() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);
        final File otherPendingFile = new File(getDcimDir(), IMAGE_FILE_NAME);
        try {
            installApp(TEST_APP_A);
            assertCreateFilesAs(TEST_APP_A, otherPendingFile);

            // We can read other app's pending file from FUSE via filePath
            assertCanQueryAndOpenFile(otherPendingFile, "r");

            // We can also read other app's pending file via MediaStore API
            assertNotNull(openWithMediaProvider(otherPendingFile, "r"));
        } finally {
            deleteFileAsNoThrow(TEST_APP_A, otherPendingFile.getAbsolutePath());
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    @Test
    public void testNoIsolatedStorageCanCreateFilesAnywhere() throws Exception {
        final File topLevelPdf = new File(getExternalStorageDir(), NONMEDIA_FILE_NAME);
        final File musicFileInMovies = new File(getMoviesDir(), AUDIO_FILE_NAME);
        final File imageFileInDcim = new File(getDcimDir(), IMAGE_FILE_NAME);
        // Nothing special about this, anyone can create an image file in DCIM
        assertCanCreateFile(imageFileInDcim);
        // This is where we see the special powers of MANAGE_EXTERNAL_STORAGE, because it can
        // create a top level file
        assertCanCreateFile(topLevelPdf);
        // It can even create a music file in Pictures
        assertCanCreateFile(musicFileInMovies);
    }

    @Test
    public void testNoIsolatedStorageCantReadWriteOtherAppExternalDir() throws Exception {
        try {
            // Install TEST_APP_A with READ_EXTERNAL_STORAGE permission.
            installAppWithStoragePermissions(TEST_APP_A);

            // Let app A create a file in its data dir
            final File otherAppExternalDataDir = new File(getExternalFilesDir().getPath().replace(
                    THIS_PACKAGE_NAME, TEST_APP_A.getPackageName()));
            final File otherAppExternalDataFile = new File(otherAppExternalDataDir,
                    NONMEDIA_FILE_NAME);
            assertCreateFilesAs(TEST_APP_A, otherAppExternalDataFile);

            // File Manager app gets global access with MANAGE_EXTERNAL_STORAGE permission, however,
            // file manager app doesn't have access to other app's external files directory
            assertThat(canOpen(otherAppExternalDataFile, /* forWrite */ false)).isFalse();
            assertThat(canOpen(otherAppExternalDataFile, /* forWrite */ true)).isFalse();
            assertThat(otherAppExternalDataFile.delete()).isFalse();

            assertThat(deleteFileAs(TEST_APP_A, otherAppExternalDataFile.getPath())).isTrue();

            assertThrows(IOException.class,
                    () -> { otherAppExternalDataFile.createNewFile(); });

        } finally {
            uninstallApp(TEST_APP_A); // Uninstalling deletes external app dirs
        }
    }

    @Test
    public void testNoIsolatedStorageStorageReaddir() throws Exception {
        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(getDcimDir(), "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(getMusicDir(), "other" + AUDIO_FILE_NAME);
        final File otherTopLevelFile = new File(getExternalStorageDir(),
                "other" + NONMEDIA_FILE_NAME);
        try {
            installApp(TEST_APP_A);
            assertCreateFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf);
            createFileUsingTradefedContentProvider(otherTopLevelFile);

            // We can list other apps' files
            assertDirectoryContains(otherAppPdf.getParentFile(), otherAppPdf);
            assertDirectoryContains(otherAppImg.getParentFile(), otherAppImg);
            assertDirectoryContains(otherAppMusic.getParentFile(), otherAppMusic);
            // We can list top level files
            assertDirectoryContains(getExternalStorageDir(), otherTopLevelFile);

            // We can also list all top level directories
            assertDirectoryContains(getExternalStorageDir(), getDefaultTopLevelDirs());
        } finally {
            deleteFileUsingTradefedContentProvider(otherTopLevelFile);
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf);
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testNoIsolatedStorageQueryOtherAppsFile() throws Exception {
        final File otherAppPdf = new File(getDownloadDir(), "other" + NONMEDIA_FILE_NAME);
        final File otherAppImg = new File(getDcimDir(), "other" + IMAGE_FILE_NAME);
        final File otherAppMusic = new File(getMusicDir(), "other" + AUDIO_FILE_NAME);
        final File otherHiddenFile = new File(getPicturesDir(), ".otherHiddenFile.jpg");
        try {
            installApp(TEST_APP_A);
            // Apps can't query other app's pending file, hence create file and publish it.
            assertCreatePublishedFilesAs(
                    TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);

            assertCanQueryAndOpenFile(otherAppPdf, "rw");
            assertCanQueryAndOpenFile(otherAppImg, "rw");
            assertCanQueryAndOpenFile(otherAppMusic, "rw");
            assertCanQueryAndOpenFile(otherHiddenFile, "rw");
        } finally {
            deleteFilesAs(TEST_APP_A, otherAppImg, otherAppMusic, otherAppPdf, otherHiddenFile);
            uninstallApp(TEST_APP_A);
        }
    }

    @Test
    public void testRenameFromShell() throws Exception {
        // This test is for shell and shell always runs as USER_SYSTEM
        assumeTrue("Test is applicable only for System User.", getCurrentUser() == USER_SYSTEM);
        final File imageFile = new File(getPicturesDir(), IMAGE_FILE_NAME);
        final File dir = new File(getMoviesDir(), TEST_DIRECTORY_NAME);
        final File renamedDir = new File(getMusicDir(), TEST_DIRECTORY_NAME);
        final File renamedImageFile = new File(dir, IMAGE_FILE_NAME);
        final File imageFileInRenamedDir = new File(renamedDir, IMAGE_FILE_NAME);
        try {
            assertTrue(imageFile.createNewFile());
            assertThat(getFileRowIdFromDatabase(imageFile)).isNotEqualTo(-1);
            if (!dir.exists()) {
                assertThat(dir.mkdir()).isTrue();
            }

            final String renameFileCommand = String.format("mv %s %s",
                    imageFile.getAbsolutePath(), renamedImageFile.getAbsolutePath());
            executeShellCommand(renameFileCommand);
            assertFalse(imageFile.exists());
            assertThat(getFileRowIdFromDatabase(imageFile)).isEqualTo(-1);
            assertTrue(renamedImageFile.exists());
            assertThat(getFileRowIdFromDatabase(renamedImageFile)).isNotEqualTo(-1);

            final String renameDirectoryCommand = String.format("mv %s %s",
                    dir.getAbsolutePath(), renamedDir.getAbsolutePath());
            executeShellCommand(renameDirectoryCommand);
            assertFalse(dir.exists());
            assertFalse(renamedImageFile.exists());
            assertThat(getFileRowIdFromDatabase(renamedImageFile)).isEqualTo(-1);
            assertTrue(renamedDir.exists());
            assertTrue(imageFileInRenamedDir.exists());
            assertThat(getFileRowIdFromDatabase(imageFileInRenamedDir)).isNotEqualTo(-1);
        } finally {
            imageFile.delete();
            renamedImageFile.delete();
            imageFileInRenamedDir.delete();
            dir.delete();
            renamedDir.delete();
        }
    }

    @Test
    public void testClearPackageData() throws Exception {
        pollForPermission(Manifest.permission.READ_EXTERNAL_STORAGE, /*granted*/ true);

        File fileToRemain = new File(getPicturesDir(), IMAGE_FILE_NAME);
        String testAppPackageName = TEST_APP_A.getPackageName();
        File fileToBeDeleted =
                new File(
                        getAndroidMediaDir(),
                        String.format("%s/%s", testAppPackageName, IMAGE_FILE_NAME));
        File nestedFileToBeDeleted =
                new File(
                        getAndroidMediaDir(),
                        String.format("%s/nesteddir/%s", testAppPackageName, IMAGE_FILE_NAME));

        try {
            installApp(TEST_APP_A);

            createAndCheckFileAsApp(TEST_APP_A, fileToRemain);
            createAndCheckFileAsApp(TEST_APP_A, fileToBeDeleted);
            createAndCheckFileAsApp(TEST_APP_A, nestedFileToBeDeleted);

            executeShellCommand("pm clear " + testAppPackageName);

            // Wait a max of 5 seconds for the cleaning after "pm clear" command to complete.
            int i = 0;
            while(i < 10 && getFileRowIdFromDatabase(fileToBeDeleted) != -1
                && getFileRowIdFromDatabase(nestedFileToBeDeleted) != -1) {
                Thread.sleep(500);
                i++;
            }

            assertThat(getFileOwnerPackageFromDatabase(fileToRemain)).isNull();
            assertThat(getFileRowIdFromDatabase(fileToRemain)).isNotEqualTo(-1);

            assertThat(getFileOwnerPackageFromDatabase(fileToBeDeleted)).isNull();
            assertThat(getFileRowIdFromDatabase(fileToBeDeleted)).isEqualTo(-1);

            assertThat(getFileOwnerPackageFromDatabase(nestedFileToBeDeleted)).isNull();
            assertThat(getFileRowIdFromDatabase(nestedFileToBeDeleted)).isEqualTo(-1);
        } finally {
            deleteFilesAs(TEST_APP_A, fileToRemain);
            deleteFilesAs(TEST_APP_A, fileToBeDeleted);
            deleteFilesAs(TEST_APP_A, nestedFileToBeDeleted);
            uninstallAppNoThrow(TEST_APP_A);
        }
    }

    private void createAndCheckFileAsApp(TestApp testApp, File newFile) throws Exception {
        assertThat(createFileAs(testApp, newFile.getPath())).isTrue();
        assertThat(getFileOwnerPackageFromDatabase(newFile))
            .isEqualTo(testApp.getPackageName());
        assertThat(getFileRowIdFromDatabase(newFile)).isNotEqualTo(-1);
    }

    /**
     * Checks restrictions for opening pending and trashed files by different apps. Assumes that
     * given {@code testApp} is already installed and has READ_EXTERNAL_STORAGE permission. This
     * method doesn't uninstall given {@code testApp} at the end.
     */
    private void assertOpenPendingOrTrashed(Uri uri, TestApp testApp, boolean isImageOrVideo)
            throws Exception {
        final File pendingOrTrashedFile = new File(getFilePathFromUri(uri));

        // App can open its pending or trashed file for read or write
        assertTrue(canOpen(pendingOrTrashedFile, /*forWrite*/ false));
        assertTrue(canOpen(pendingOrTrashedFile, /*forWrite*/ true));

        // App with READ_EXTERNAL_STORAGE can't open other app's pending or trashed file for read or
        // write
        assertFalse(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ false));
        assertFalse(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ true));

        final int testAppUid =
                getContext().getPackageManager().getPackageUid(testApp.getPackageName(), 0);
        try {
            allowAppOpsToUid(testAppUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
            // File Manager can open any pending or trashed file for read or write
            assertTrue(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ false));
            assertTrue(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ true));
        } finally {
            denyAppOpsToUid(testAppUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
        }

        try {
            allowAppOpsToUid(testAppUid, SYSTEM_GALERY_APPOPS);
            if (isImageOrVideo) {
                // System Gallery can open any pending or trashed image/video file for read or write
                assertTrue(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertTrue(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ false));
                assertTrue(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ true));
            } else {
                // System Gallery can't open other app's pending or trashed non-media file for read
                // or write
                assertFalse(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertFalse(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ false));
                assertFalse(canOpenFileAs(testApp, pendingOrTrashedFile, /*forWrite*/ true));
            }
        } finally {
            denyAppOpsToUid(testAppUid, SYSTEM_GALERY_APPOPS);
        }
    }

    /**
     * Checks restrictions for listing pending and trashed files by different apps. Assumes that
     * given {@code testApp} is already installed and has READ_EXTERNAL_STORAGE permission. This
     * method doesn't uninstall given {@code testApp} at the end.
     */
    private void assertListPendingOrTrashed(Uri uri, File file, TestApp testApp,
            boolean isImageOrVideo) throws Exception {
        final String parentDirPath = file.getParent();
        assertTrue(new File(parentDirPath).isDirectory());

        final List<String> listedFileNames = Arrays.asList(new File(parentDirPath).list());
        assertThat(listedFileNames).doesNotContain(file);

        final File pendingOrTrashedFile = new File(getFilePathFromUri(uri));

        assertThat(listedFileNames).contains(pendingOrTrashedFile.getName());

        // App with READ_EXTERNAL_STORAGE can't see other app's pending or trashed file.
        assertThat(listAs(testApp, parentDirPath)).doesNotContain(pendingOrTrashedFile.getName());

        final int testAppUid =
                getContext().getPackageManager().getPackageUid(testApp.getPackageName(), 0);
        try {
            allowAppOpsToUid(testAppUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
            // File Manager can see any pending or trashed file.
            assertThat(listAs(testApp, parentDirPath)).contains(pendingOrTrashedFile.getName());
        } finally {
            denyAppOpsToUid(testAppUid, OPSTR_MANAGE_EXTERNAL_STORAGE);
        }

        try {
            allowAppOpsToUid(testAppUid, SYSTEM_GALERY_APPOPS);
            if (isImageOrVideo) {
                // System Gallery can see any pending or trashed image/video file.
                assertTrue(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertThat(listAs(testApp, parentDirPath)).contains(pendingOrTrashedFile.getName());
            } else {
                // System Gallery can't see other app's pending or trashed non media file.
                assertFalse(isMediaTypeImageOrVideo(pendingOrTrashedFile));
                assertThat(listAs(testApp, parentDirPath))
                        .doesNotContain(pendingOrTrashedFile.getName());
            }
        } finally {
            denyAppOpsToUid(testAppUid, SYSTEM_GALERY_APPOPS);
        }
    }

    private Uri createPendingFile(File pendingFile) throws Exception {
        assertTrue(pendingFile.createNewFile());

        final ContentResolver cr = getContentResolver();
        final Uri trashedFileUri = MediaStore.scanFile(cr, pendingFile);
        assertNotNull(trashedFileUri);

        final ContentValues values = new ContentValues();
        values.put(MediaColumns.IS_PENDING, 1);
        assertEquals(1, cr.update(trashedFileUri, values, Bundle.EMPTY));

        return trashedFileUri;
    }

    private Uri createTrashedFile(File trashedFile) throws Exception {
        assertTrue(trashedFile.createNewFile());

        final ContentResolver cr = getContentResolver();
        final Uri trashedFileUri = MediaStore.scanFile(cr, trashedFile);
        assertNotNull(trashedFileUri);

        trashFile(trashedFileUri);
        return trashedFileUri;
    }

    private void trashFile(Uri uri) throws Exception {
        final ContentValues values = new ContentValues();
        values.put(MediaColumns.IS_TRASHED, 1);
        assertEquals(1, getContentResolver().update(uri, values, Bundle.EMPTY));
    }

    /**
     * Gets file path corresponding to the db row pointed by {@code uri}. If {@code uri} points to
     * multiple db rows, file path is extracted from the first db row of the database query result.
     */
    private String getFilePathFromUri(Uri uri) {
        final String[] projection = new String[] {MediaColumns.DATA};
        try (Cursor c = getContentResolver().query(uri, projection, null, null)) {
            assertTrue(c.moveToFirst());
            return c.getString(0);
        }
    }

    private boolean isMediaTypeImageOrVideo(File file) {
        return queryImageFile(file).getCount() == 1 || queryVideoFile(file).getCount() == 1;
    }

    private static void assertIsMediaTypeImage(File file) {
        final Cursor c = queryImageFile(file);
        assertEquals(1, c.getCount());
    }

    private static void assertNotMediaTypeImage(File file) {
        final Cursor c = queryImageFile(file);
        assertEquals(0, c.getCount());
    }

    private static void assertCantQueryFile(File file) {
        assertThat(getFileUri(file)).isNull();
        // Confirm that file exists in the database.
        assertNotNull(MediaStore.scanFile(getContentResolver(), file));
    }

    private static void assertCreateFilesAs(TestApp testApp, File... files) throws Exception {
        for (File file : files) {
            assertFalse("File already exists: " + file, file.exists());
            assertTrue("Failed to create file " + file + " on behalf of "
                            + testApp.getPackageName(), createFileAs(testApp, file.getPath()));
        }
    }

    /**
     * Makes {@code testApp} create {@code files}. Publishes {@code files} by scanning the file.
     * Pending files from FUSE are not visible to other apps via MediaStore APIs. We have to publish
     * the file or make the file non-pending to make the file visible to other apps.
     * <p>
     * Note that this method can only be used for scannable files.
     */
    private static void assertCreatePublishedFilesAs(TestApp testApp, File... files)
            throws Exception {
        for (File file : files) {
            assertTrue("Failed to create published file " + file + " on behalf of "
                    + testApp.getPackageName(), createFileAs(testApp, file.getPath()));
            assertNotNull("Failed to scan " + file,
                    MediaStore.scanFile(getContentResolver(), file));
        }
    }


    private static void deleteFilesAs(TestApp testApp, File... files) throws Exception {
        for (File file : files) {
            deleteFileAs(testApp, file.getPath());
        }
    }
    private static void assertCanDeletePathsAs(TestApp testApp, String... filePaths)
            throws Exception {
        for (String path: filePaths) {
            assertTrue("Failed to delete file " + path + " on behalf of "
                    + testApp.getPackageName(), deleteFileAs(testApp, path));
        }
    }

    private static void assertCantDeletePathsAs(TestApp testApp, String... filePaths)
            throws Exception {
        for (String path: filePaths) {
            assertFalse("Deleting " + path + " on behalf of " + testApp.getPackageName()
                            + " was expected to fail", deleteFileAs(testApp, path));
        }
    }

    private void deleteFiles(File... files) {
        for (File file: files) {
            if (file == null) continue;
            file.delete();
        }
    }

    private void deletePaths(String... paths) {
        for (String path: paths) {
            if (path == null) continue;
            new File(path).delete();
        }
    }

    private static void assertCanDeletePaths(String... filePaths) {
        for (String filePath : filePaths) {
            assertTrue("Failed to delete " + filePath,
                    new File(filePath).delete());
        }
    }

    /**
     * For possible values of {@code mode}, look at {@link android.content.ContentProvider#openFile}
     */
    private static void assertCanQueryAndOpenFile(File file, String mode) throws IOException {
        // This call performs the query
        final Uri fileUri = getFileUri(file);
        // The query succeeds iff it didn't return null
        assertThat(fileUri).isNotNull();
        // Now we assert that we can open the file through ContentResolver
        try (final ParcelFileDescriptor pfd =
                        getContentResolver().openFileDescriptor(fileUri, mode)) {
            assertThat(pfd).isNotNull();
        }
    }

    /**
     * Assert that the last read in: read - write - read using {@code readFd} and {@code writeFd}
     * see the last write. {@code readFd} and {@code writeFd} are fds pointing to the same
     * underlying file on disk but may be derived from different mount points and in that case
     * have separate VFS caches.
     */
    private void assertRWR(ParcelFileDescriptor readPfd, ParcelFileDescriptor writePfd)
            throws Exception {
        FileDescriptor readFd = readPfd.getFileDescriptor();
        FileDescriptor writeFd = writePfd.getFileDescriptor();

        byte[] readBuffer = new byte[10];
        byte[] writeBuffer = new byte[10];
        Arrays.fill(writeBuffer, (byte) 1);

        // Write so readFd has content to read from next
        Os.pwrite(readFd, readBuffer, 0, 10, 0);
        // Read so readBuffer is in readFd's mount VFS cache
        Os.pread(readFd, readBuffer, 0, 10, 0);

        // Assert that readBuffer is zeroes
        assertThat(readBuffer).isEqualTo(new byte[10]);

        // Write so writeFd and readFd should now see writeBuffer
        Os.pwrite(writeFd, writeBuffer, 0, 10, 0);

        // Read so the last write can be verified on readFd
        Os.pread(readFd, readBuffer, 0, 10, 0);

        // Assert that the last write is indeed visible via readFd
        assertThat(readBuffer).isEqualTo(writeBuffer);
        assertThat(readPfd.getStatSize()).isEqualTo(writePfd.getStatSize());
    }

    private void assertLowerFsFd(ParcelFileDescriptor pfd) throws Exception {
        assertThat(Os.readlink("/proc/self/fd/" + pfd.getFd()).startsWith("/storage")).isTrue();
    }

    private void assertUpperFsFd(ParcelFileDescriptor pfd) throws Exception {
        assertThat(Os.readlink("/proc/self/fd/" + pfd.getFd()).startsWith("/mnt/user")).isTrue();
    }

    private static void assertCanCreateFile(File file) throws IOException {
        // If the file somehow managed to survive a previous run, then the test app was uninstalled
        // and MediaProvider will remove our its ownership of the file, so it's not guaranteed that
        // we can create nor delete it.
        if (!file.exists()) {
            assertThat(file.createNewFile()).isTrue();
            assertThat(file.delete()).isTrue();
        } else {
            Log.w(TAG,
                    "Couldn't assertCanCreateFile(" + file + ") because file existed prior to "
                            + "running the test!");
        }
    }

    private static void assertFileAccess_existsOnly(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true, false, false);
    }

    private static void assertFileAccess_readOnly(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true, true, false);
    }

    private static void assertFileAccess_readWrite(File file) throws Exception {
        assertThat(file.isFile()).isTrue();
        assertAccess(file, true, true, true);
    }

    private static void assertDirectoryAccess(File dir, boolean exists, boolean canWrite)
            throws Exception {
        // This util does not handle app data directories.
        assumeFalse(dir.getAbsolutePath().startsWith(getAndroidDir().getAbsolutePath())
                && !dir.equals(getAndroidDir()));
        assertThat(dir.isDirectory()).isEqualTo(exists);
        // For non-app data directories, exists => canRead().
        assertAccess(dir, exists, exists, exists && canWrite);
    }

    private static void assertAccess(File file, boolean exists, boolean canRead, boolean canWrite)
            throws Exception {
        assertAccess(file, exists, canRead, canWrite, true /* checkExists */);
    }

    private static void assertCannotReadOrWrite(File file)
            throws Exception {
        // App data directories have different 'x' bits on upgrading vs new devices. Let's not
        // check 'exists', by passing checkExists=false. But assert this app cannot read or write
        // the other app's file.
        assertAccess(file, false /* value is moot */, false /* canRead */,
                false /* canWrite */, false /* checkExists */);
    }

    private static void assertCanAccessMyAppFile(File file)
            throws Exception {
        assertAccess(file, true, true /* canRead */,
                true /*canWrite */, true /* checkExists */);
    }

    private static void assertAccess(File file, boolean exists, boolean canRead, boolean canWrite,
            boolean checkExists) throws Exception {
        if (checkExists) {
            assertThat(file.exists()).isEqualTo(exists);
        }
        assertThat(file.canRead()).isEqualTo(canRead);
        assertThat(file.canWrite()).isEqualTo(canWrite);
        if (file.isDirectory()) {
            if (checkExists) {
                assertThat(file.canExecute()).isEqualTo(exists);
            }
        } else {
            assertThat(file.canExecute()).isFalse(); // Filesytem is mounted with MS_NOEXEC
        }

        // Test some combinations of mask.
        assertAccess(file, R_OK, canRead);
        assertAccess(file, W_OK, canWrite);
        assertAccess(file, R_OK | W_OK, canRead && canWrite);
        assertAccess(file, W_OK | F_OK, canWrite);

        if (checkExists) {
            assertAccess(file, F_OK, exists);
        }
    }

    private static void assertAccess(File file, int mask, boolean expected) throws Exception {
        if (expected) {
            assertThat(Os.access(file.getAbsolutePath(), mask)).isTrue();
        } else {
            assertThrows(ErrnoException.class, () -> { Os.access(file.getAbsolutePath(), mask); });
        }
    }

    private void createFileUsingTradefedContentProvider(File file) throws Exception {
        // Files/Dirs are created using content provider. Owner of the Filse/Dirs is
        // android.tradefed.contentprovider.
        Log.d(TAG, "Creating file " + file);
        getContentResolver().openFile(Uri.parse(CONTENT_PROVIDER_URL + file.getPath()), "w", null);
    }

    private void createDirUsingTradefedContentProvider(File file) throws Exception {
        // Files/Dirs are created using content provider. Owner of the Files/Dirs is
        // android.tradefed.contentprovider.
        Log.d(TAG, "Creating Dir " + file);
        // Create a tmp file in the target directory, this would also create the required
        // directory, then delete the tmp file. It would leave only new directory.
        getContentResolver()
            .openFile(Uri.parse(CONTENT_PROVIDER_URL + file.getPath() + "/tmp.txt"), "w", null);
        getContentResolver()
            .delete(Uri.parse(CONTENT_PROVIDER_URL + file.getPath() + "/tmp.txt"), null, null);
    }

    private void deleteFileUsingTradefedContentProvider(File file) throws Exception {
        Log.d(TAG, "Deleting file " + file);
        getContentResolver().delete(Uri.parse(CONTENT_PROVIDER_URL + file.getPath()), null, null);
    }

    private void deleteDirUsingTradefedContentProvider(File file) throws Exception {
        Log.d(TAG, "Deleting Dir " + file);
        getContentResolver().delete(Uri.parse(CONTENT_PROVIDER_URL + file.getPath()), null, null);
    }

    private int getCurrentUser() throws Exception {
        String userId = executeShellCommand("am get-current-user");
        return Integer.parseInt(userId.trim());
    }
}
