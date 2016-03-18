/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.documentclient;

import static android.os.Environment.DIRECTORY_ALARMS;
import static android.os.Environment.DIRECTORY_DCIM;
import static android.os.Environment.DIRECTORY_DOCUMENTS;
import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static android.os.Environment.DIRECTORY_MOVIES;
import static android.os.Environment.DIRECTORY_MUSIC;
import static android.os.Environment.DIRECTORY_NOTIFICATIONS;
import static android.os.Environment.DIRECTORY_PICTURES;
import static android.os.Environment.DIRECTORY_PODCASTS;
import static android.os.Environment.DIRECTORY_RINGTONES;
import static android.test.MoreAsserts.assertContainsRegex;
import static android.test.MoreAsserts.assertNotEqual;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;

/**
 * Set of tests that verify behavior of the Scoped Directory Access API.
 */
public class ScopedDirectoryAccessClientTest extends DocumentsClientTestCase {
    private static final String TAG = "ScopedDirectoryAccessClientTest";

    private static final String[] STANDARD_DIRECTORIES = {
        DIRECTORY_MUSIC,
        DIRECTORY_PODCASTS,
        DIRECTORY_RINGTONES,
        DIRECTORY_ALARMS,
        DIRECTORY_NOTIFICATIONS,
        DIRECTORY_PICTURES,
        DIRECTORY_MOVIES,
        DIRECTORY_DOWNLOADS,
        DIRECTORY_DCIM,
        DIRECTORY_DOCUMENTS
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // DocumentsUI caches some info like whether a user rejects a request, so we need to clear
        // its data before each test.
        clearDocumentsUi();
    }

    public void testInvalidPath() throws Exception {
        if (!supportedHardware()) return;

        for (StorageVolume volume : getVolumes()) {
            openExternalDirectoryInvalidPath(volume, "");
            openExternalDirectoryInvalidPath(volume, "/dev/null");
            openExternalDirectoryInvalidPath(volume, "/../");
            openExternalDirectoryInvalidPath(volume, "/HiddenStuff");
        }
    }

    public void testUserRejects() throws Exception {
        if (!supportedHardware()) return;

        final StorageVolume primaryVolume = getPrimaryVolume();

        // Tests user clicking DENY button, for all valid directories.
        for (String directory : STANDARD_DIRECTORIES) {
            final UiAlertDialog dialog = openExternalDirectoryValidPath(primaryVolume, directory);
            dialog.noButton.click();
            assertActivityFailed();
        }

        // Also test user clicking back button - one directory is enough.
        openExternalDirectoryValidPath(primaryVolume, DIRECTORY_PICTURES);
        mDevice.pressBack();
        assertActivityFailed();
    }

    public void testUserAccepts() throws Exception {
        if (!supportedHardware()) return;

        for (StorageVolume volume : getVolumes()) {
            userAcceptsOpenExternalDirectoryTest(volume, DIRECTORY_PICTURES);
        }
    }

    public void testUserAcceptsNewDirectory() throws Exception {
        if (!supportedHardware()) return;

        // TODO: figure out a better way to remove the directory.
        final String command = "rm -rf /sdcard/" + DIRECTORY_PICTURES;
        final String output = executeShellCommand(command);
        if (!output.isEmpty()) {
            fail("Command '" + command + "' failed: '" + output + "'");
        }
        userAcceptsOpenExternalDirectoryTest(getPrimaryVolume(), DIRECTORY_PICTURES);
    }

    public void testNotAskedAgain() throws Exception {
        if (!supportedHardware()) return;

        final StorageVolume volume = getPrimaryVolume();
        final Uri grantedUri = userAcceptsOpenExternalDirectoryTest(volume, DIRECTORY_PICTURES);

        // Calls it again - since the permission has been granted, it should return right away,
        // without popping up the permissions dialog.
        sendOpenExternalDirectoryIntent(volume, DIRECTORY_PICTURES);
        final Intent newData = assertActivitySucceeded();
        assertEquals(grantedUri, newData.getData());

        // Make sure other directories still require user permission.
        final Uri grantedUri2 = userAcceptsOpenExternalDirectoryTest(volume, DIRECTORY_ALARMS);
        assertNotEqual(grantedUri, grantedUri2);
    }

    public void testDeniesOnceButAllowsAskingAgain() throws Exception {
        if (!supportedHardware())return;

        for (StorageVolume volume : getVolumes()) {
            // Rejects the first attempt...
            UiAlertDialog dialog = openExternalDirectoryValidPath(volume, DIRECTORY_DCIM);
            dialog.assertDoNotAskAgainVisibility(false);
            dialog.noButton.click();
            assertActivityFailed();

            // ...and the second.
            dialog = openExternalDirectoryValidPath(volume, DIRECTORY_DCIM);
            dialog.assertDoNotAskAgainVisibility(true);
            dialog.noButton.click();
            assertActivityFailed();

            // Third time is a charm...
            userAcceptsOpenExternalDirectoryTest(volume, DIRECTORY_DCIM);
        }
    }

    public void testDeniesOnceForAll() throws Exception {
        if (!supportedHardware()) return;

        for (StorageVolume volume : getVolumes()) {
            // Rejects the first attempt...
            UiAlertDialog dialog = openExternalDirectoryValidPath(volume, DIRECTORY_RINGTONES);
            dialog.assertDoNotAskAgainVisibility(false);
            dialog.noButton.click();
            assertActivityFailed();

            // ...and the second, checking the box
            dialog = openExternalDirectoryValidPath(volume, DIRECTORY_RINGTONES);
            UiObject checkbox = dialog.assertDoNotAskAgainVisibility(true);
            assertTrue("checkbox should not be checkable", checkbox.isCheckable());
            assertFalse("checkbox should not be checked", checkbox.isChecked());
            checkbox.click();
            assertTrue("checkbox should be checked", checkbox.isChecked()); // Sanity check
            assertFalse("allow button should be disabled", dialog.yesButton.isEnabled());

            dialog.noButton.click();
            assertActivityFailed();

            // Third strike out...
            sendOpenExternalDirectoryIntent(volume, DIRECTORY_RINGTONES);
            assertActivityFailed();
        }
    }

    private Uri userAcceptsOpenExternalDirectoryTest(StorageVolume volume, String directoryName)
            throws Exception {
        // Asserts dialog contain the proper message.
        final UiAlertDialog dialog = openExternalDirectoryValidPath(volume, directoryName);
        final String message = dialog.messageText.getText();
        Log.v(TAG, "request permission message: " + message);
        final Context context = getInstrumentation().getTargetContext();
        final String appLabel = context.getPackageManager().getApplicationLabel(
                context.getApplicationInfo()).toString();
        assertContainsRegex("missing app label", appLabel, message);
        assertContainsRegex("missing folder", directoryName, message);
        assertContainsRegex("missing root", volume.getDescription(context), message);

        // Call API...
        dialog.yesButton.click();

        // ...and get its response.
        final Intent data = assertActivitySucceeded();
        final Uri grantedUri = data.getData();

        // Test granted permission directly by persisting it...
        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
        final int modeFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        resolver.takePersistableUriPermission(grantedUri, modeFlags);

        // ...and indirectly by creating some documents
        final Uri doc = DocumentsContract.buildDocumentUriUsingTree(grantedUri,
                DocumentsContract.getTreeDocumentId(grantedUri));
        assertNotNull("could not get tree URI", doc);
        final Uri pic = DocumentsContract.createDocument(resolver, doc, "image/png", "pic.png");
        assertNotNull("could not create file (pic.png) on tree root", pic);
        final Uri dir = DocumentsContract.createDocument(resolver, doc, Document.MIME_TYPE_DIR,
                "my dir");
        assertNotNull("could not create child dir (my dir)", pic);
        final Uri dirPic = DocumentsContract.createDocument(resolver, dir, "image/png", "pic2.png");
        assertNotNull("could not create file (pic.png) on child dir (my dir)", dirPic);

        writeFully(pic, "pic".getBytes());
        writeFully(dirPic, "dirPic".getBytes());

        // Clean up created documents.
        assertTrue("delete", DocumentsContract.deleteDocument(resolver, pic));
        assertTrue("delete", DocumentsContract.deleteDocument(resolver, dirPic));
        assertTrue("delete", DocumentsContract.deleteDocument(resolver, dir));

        return grantedUri;
    }

    private void openExternalDirectoryInvalidPath(StorageVolume volume, String path) {
        sendOpenExternalDirectoryIntent(volume, path);
        assertActivityFailed();
    }

    private UiAlertDialog openExternalDirectoryValidPath(StorageVolume volume, String path)
            throws UiObjectNotFoundException {
        sendOpenExternalDirectoryIntent(volume, path);
        return new UiAlertDialog();
    }

    private void sendOpenExternalDirectoryIntent(StorageVolume volume, String directoryName) {
        final Intent intent = volume.createAccessIntent(directoryName);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        mDevice.waitForIdle();
    }

    private StorageVolume[] getVolumes() {
        final StorageManager sm = (StorageManager)
                getInstrumentation().getTargetContext().getSystemService(Context.STORAGE_SERVICE);
        final StorageVolume[] volumes = sm.getVolumeList();
        assertTrue("empty volumes", volumes.length > 0);
        return volumes;
    }

    private StorageVolume getPrimaryVolume() {
        final StorageManager sm = (StorageManager)
                getInstrumentation().getTargetContext().getSystemService(Context.STORAGE_SERVICE);
        return sm.getPrimaryVolume();
    }

    private final class UiAlertDialog {
        final UiObject dialog;
        final UiObject messageText;
        final UiObject yesButton;
        final UiObject noButton;

        UiAlertDialog() throws UiObjectNotFoundException {
            final String id = "android:id/parentPanel";
            boolean gotIt = mDevice.wait(Until.hasObject(By.res(id)), TIMEOUT);
            assertTrue("object with id '(" + id + "') not visible yet", gotIt);
            dialog = mDevice.findObject(new UiSelector().resourceId(id));
            assertTrue("object with id '(" + id + "') doesn't exist", dialog.exists());
            messageText = dialog.getChild(
                    new UiSelector().resourceId("com.android.documentsui:id/message"));
            yesButton = dialog.getChild(new UiSelector().resourceId("android:id/button1"));
            noButton  = dialog.getChild(new UiSelector().resourceId("android:id/button2"));
        }

        private UiObject getDoNotAskAgainCheckBox() throws UiObjectNotFoundException {
            return dialog.getChild(
                    new UiSelector().resourceId("com.android.documentsui:id/do_not_ask_checkbox"));
        }

        UiObject assertDoNotAskAgainVisibility(boolean expectVisible) {
            UiObject checkbox = null;
            try {
                checkbox = getDoNotAskAgainCheckBox();
                assertEquals("Wrong value for 'DoNotAskAgain.exists()",
                        expectVisible, checkbox.exists());
            } catch (UiObjectNotFoundException e) {
                if (expectVisible) {
                    fail("'Do Not Ask Again' not found");
                }
            }
            return checkbox;
        }
    }
}
