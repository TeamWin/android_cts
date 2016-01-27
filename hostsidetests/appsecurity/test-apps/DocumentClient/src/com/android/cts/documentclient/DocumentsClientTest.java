/*
 * Copyright (C) 2014 The Android Open Source Project
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
import static android.os.Environment.getExternalStorageDirectory;
import static android.test.MoreAsserts.assertContainsRegex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Configurator;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.MoreAsserts;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.MotionEvent;

import com.android.cts.documentclient.MyActivity.Result;

/**
 * Tests for {@link DocumentsProvider} and interaction with platform intents
 * like {@link Intent#ACTION_OPEN_DOCUMENT}.
 */
public class DocumentsClientTest extends InstrumentationTestCase {
    private static final String TAG = "DocumentsClientTest";

    private UiDevice mDevice;
    private MyActivity mActivity;

    private static final long TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;

    private static final int REQUEST_CODE = 42;

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

        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_FINGER);

        mDevice = UiDevice.getInstance(getInstrumentation());
        mActivity = launchActivity(getInstrumentation().getTargetContext().getPackageName(),
                MyActivity.class, null);
        mDevice.waitForIdle();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mActivity.finish();
    }

    private UiObject findRoot(String label) throws UiObjectNotFoundException {
        final UiSelector rootsList = new UiSelector().resourceId(
                "com.android.documentsui:id/container_roots").childSelector(
                new UiSelector().resourceId("android:id/list"));

        // We might need to expand drawer if not visible
        if (!new UiObject(rootsList).waitForExists(TIMEOUT)) {
            Log.d(TAG, "Failed to find roots list; trying to expand");
            final UiSelector hamburger = new UiSelector().resourceId(
                    "com.android.documentsui:id/toolbar").childSelector(
                    new UiSelector().className("android.widget.ImageButton").clickable(true));
            new UiObject(hamburger).click();
        }

        // Wait for the first list item to appear
        assertTrue("First list item",
                new UiObject(rootsList.childSelector(new UiSelector())).waitForExists(TIMEOUT));

        // Now scroll around to find our item
        new UiScrollable(rootsList).scrollIntoView(new UiSelector().text(label));
        return new UiObject(rootsList.childSelector(new UiSelector().text(label)));
    }

    private UiObject findDocument(String label) throws UiObjectNotFoundException {
        final UiSelector docList = new UiSelector().resourceId(
                "com.android.documentsui:id/container_directory").childSelector(
                new UiSelector().resourceId("com.android.documentsui:id/list"));

        // Wait for the first list item to appear
        assertTrue("First list item",
                new UiObject(docList.childSelector(new UiSelector())).waitForExists(TIMEOUT));

        // Now scroll around to find our item
        new UiScrollable(docList).scrollIntoView(new UiSelector().text(label));
        return new UiObject(docList.childSelector(new UiSelector().text(label)));
    }

    private UiObject findSaveButton() throws UiObjectNotFoundException {
        return new UiObject(new UiSelector().resourceId("com.android.documentsui:id/container_save")
                .childSelector(new UiSelector().resourceId("android:id/button1")));
    }

    public void testOpenSimple() throws Exception {
        if (!supportedHardware()) return;

        try {
            // Opening without permission should fail
            readFully(Uri.parse("content://com.android.cts.documentprovider/document/doc:file1"));
            fail("Able to read data before opened!");
        } catch (SecurityException expected) {
        }

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Ensure that we see both of our roots
        mDevice.waitForIdle();
        assertTrue("CtsLocal root", findRoot("CtsLocal").exists());
        assertTrue("CtsCreate root", findRoot("CtsCreate").exists());
        assertFalse("CtsGetContent root", findRoot("CtsGetContent").exists());

        // Choose the local root.
        mDevice.waitForIdle();
        findRoot("CtsLocal").click();

        // Try picking a virtual file. Virtual files must not be returned for CATEGORY_OPENABLE
        // though, so the click should be ignored.
        mDevice.waitForIdle();
        findDocument("VIRTUAL_FILE").click();
        mDevice.waitForIdle();

        // Pick a regular file.
        mDevice.waitForIdle();
        findDocument("FILE1").click();

        // Confirm that the returned file is a regular file caused by the second click.
        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();
        assertEquals("doc:file1", DocumentsContract.getDocumentId(uri));

        // We should now have permission to read/write
        MoreAsserts.assertEquals("fileone".getBytes(), readFully(uri));

        writeFully(uri, "replaced!".getBytes());
        SystemClock.sleep(500);
        MoreAsserts.assertEquals("replaced!".getBytes(), readFully(uri));
    }

    public void testOpenVirtual() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Pick a virtual file from the local root.
        mDevice.waitForIdle();
        findRoot("CtsLocal").click();

        mDevice.waitForIdle();
        findDocument("VIRTUAL_FILE").click();

        // Confirm that the returned file is actually the selected one.
        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();
        assertEquals("virtual-file", DocumentsContract.getDocumentId(uri));
    }

    public void testCreateNew() throws Exception {
        if (!supportedHardware()) return;

        final String DISPLAY_NAME = "My New Awesome Document Title";
        final String MIME_TYPE = "image/png";

        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, DISPLAY_NAME);
        intent.setType(MIME_TYPE);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        mDevice.waitForIdle();
        findSaveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        writeFully(uri, "meow!".getBytes());

        assertEquals(DISPLAY_NAME, getColumn(uri, Document.COLUMN_DISPLAY_NAME));
        assertEquals(MIME_TYPE, getColumn(uri, Document.COLUMN_MIME_TYPE));
    }

    public void testCreateExisting() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "NEVERUSED");
        intent.setType("mime2/file2");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        // Pick file2, which should be selected since MIME matches, then try
        // picking a non-matching MIME, which should leave file2 selected.
        mDevice.waitForIdle();
        findDocument("FILE2").click();
        mDevice.waitForIdle();
        findDocument("FILE1").click();

        mDevice.waitForIdle();
        findSaveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        MoreAsserts.assertEquals("filetwo".getBytes(), readFully(uri));
    }

    public void testTree() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        mDevice.waitForIdle();
        findDocument("DIR2").click();
        mDevice.waitForIdle();
        findSaveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        // We should have selected DIR2
        Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));

        assertEquals("DIR2", getColumn(doc, Document.COLUMN_DISPLAY_NAME));

        // Look around and make sure we can see children
        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
        Cursor cursor = resolver.query(children, new String[] {
                Document.COLUMN_DISPLAY_NAME }, null, null, null);
        try {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("FILE4", cursor.getString(0));
        } finally {
            cursor.close();
        }

        // Create some documents
        Uri pic = DocumentsContract.createDocument(resolver, doc, "image/png", "pic.png");
        Uri dir = DocumentsContract.createDocument(resolver, doc, Document.MIME_TYPE_DIR, "my dir");
        Uri dirPic = DocumentsContract.createDocument(resolver, dir, "image/png", "pic2.png");

        writeFully(pic, "pic".getBytes());
        writeFully(dirPic, "dirPic".getBytes());

        // Read then delete existing doc
        final Uri file4 = DocumentsContract.buildDocumentUriUsingTree(uri, "doc:file4");
        MoreAsserts.assertEquals("filefour".getBytes(), readFully(file4));
        assertTrue("delete", DocumentsContract.deleteDocument(resolver, file4));
        try {
            MoreAsserts.assertEquals("filefour".getBytes(), readFully(file4));
            fail("Expected file to be gone");
        } catch (FileNotFoundException expected) {
        }

        // And rename something
        dirPic = DocumentsContract.renameDocument(resolver, dirPic, "wow");
        assertNotNull("rename", dirPic);

        // We should only see single child
        assertEquals("wow", getColumn(dirPic, Document.COLUMN_DISPLAY_NAME));
        MoreAsserts.assertEquals("dirPic".getBytes(), readFully(dirPic));

        try {
            // Make sure we can't see files outside selected dir
            getColumn(DocumentsContract.buildDocumentUriUsingTree(uri, "doc:file1"),
                    Document.COLUMN_DISPLAY_NAME);
            fail("Somehow read document outside tree!");
        } catch (SecurityException expected) {
        }
    }

    // Not called by hostside tests, but useful during development...
    public void testOpenExternalDirectory() throws Exception {
        testOpenExternalDirectory_invalidPath();
        testOpenExternalDirectory_userRejects();
        testOpenExternalDirectory_userAccepts();
    }

    public void testOpenExternalDirectory_invalidPath() throws Exception {
        if (!supportedHardware()) return;

        final String externalRoot = getExternalStorageDirectory().getPath();
        openExternalDirectoryInvalidPath("");
        openExternalDirectoryInvalidPath("/dev/null");
        openExternalDirectoryInvalidPath(externalRoot + "/../");
        openExternalDirectoryInvalidPath(externalRoot + "/HiddenStuff");
    }

    public void testOpenExternalDirectory_userRejects() throws Exception {
        if (!supportedHardware()) return;

        final String externalRoot = getExternalStorageDirectory().getPath();

        // Tests user clicking DENY button, for all valid directories.
        for (String directory : STANDARD_DIRECTORIES) {
            final Uri uri = Uri.fromFile(new File(externalRoot, directory));

            final UiAlertDialog dialog = openExternalDirectoryValidPath(uri);
            dialog.noButton.click();
            assertActivityFailed();
        }

        // Also test user clicking back button - one directory is enough.
        openExternalDirectoryValidPath(Uri.fromFile(new File(externalRoot, DIRECTORY_PICTURES)));
        mDevice.pressBack();
        assertActivityFailed();
    }

    public void testOpenExternalDirectory_userAccepts() throws Exception {
        if (!supportedHardware())
            return;

        // TODO: once there is an API to get all volumes, use a for loop to call method below
        // to all of them (rather than hard-coding).
        userAcceptsOpenExternalDirectoryTest(getExternalStorageDirectory().getPath(),
                "Internal storage");
    }

    private void userAcceptsOpenExternalDirectoryTest(String rootPath, String rootDescription)
            throws Exception {
        final Uri requestedUri = Uri.fromFile(new File(rootPath, DIRECTORY_PICTURES));

        // Asserts dialog contain the proper message.
        final UiAlertDialog dialog = openExternalDirectoryValidPath(requestedUri);
        final String message = dialog.messageText.getText();
        Log.v(TAG, "request permission message: " + message);
        final Context context = getInstrumentation().getTargetContext();
        final String appLabel = context.getPackageManager().getApplicationLabel(
                context.getApplicationInfo()).toString();
        assertContainsRegex("missing app label", appLabel, message);
        assertContainsRegex("missing folder", DIRECTORY_PICTURES, message);
        assertContainsRegex("missing root", rootDescription, message);

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
        assertNotNull("could not get tree UURI", doc);
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
    }

    public void testGetContent() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Look around, we should be able to see both DocumentsProviders and
        // other GET_CONTENT sources.
        mDevice.waitForIdle();
        assertTrue("CtsLocal root", findRoot("CtsLocal").exists());
        assertTrue("CtsCreate root", findRoot("CtsCreate").exists());
        assertTrue("CtsGetContent root", findRoot("CtsGetContent").exists());

        mDevice.waitForIdle();
        findRoot("CtsGetContent").click();

        final Result result = mActivity.getResult();
        assertEquals("ReSuLt", result.data.getAction());
    }

    private String getColumn(Uri uri, String column) {
        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
        final Cursor cursor = resolver.query(uri, new String[] { column }, null, null, null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        } finally {
            cursor.close();
        }
    }

    private byte[] readFully(Uri uri) throws IOException {
        InputStream in = getInstrumentation().getContext().getContentResolver()
                .openInputStream(uri);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            in.close();
        }
    }

    private void writeFully(Uri uri, byte[] data) throws IOException {
        OutputStream out = getInstrumentation().getContext().getContentResolver()
                .openOutputStream(uri);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    private boolean supportedHardware() {
        final PackageManager pm = getInstrumentation().getContext().getPackageManager();
        if (pm.hasSystemFeature("android.hardware.type.television")
                || pm.hasSystemFeature("android.hardware.type.watch")) {
            return false;
        }
        return true;
    }

    private void assertActivityFailed() {
        final Result result = mActivity.getResult();
        assertEquals(REQUEST_CODE, result.requestCode);
        assertEquals(Activity.RESULT_CANCELED, result.resultCode);
        assertNull(result.data);
    }

    private Intent assertActivitySucceeded() {
        final Result result = mActivity.getResult();
        assertEquals(REQUEST_CODE, result.requestCode);
        assertEquals(Activity.RESULT_OK, result.resultCode);
        assertNotNull(result.data);
        return result.data;
    }

    private void openExternalDirectoryInvalidPath(String path) {
        sendOpenExternalDirectoryIntent(Uri.fromFile(new File(path)));
        assertActivityFailed();
    }

    private UiAlertDialog openExternalDirectoryValidPath(Uri uri) throws UiObjectNotFoundException {
        sendOpenExternalDirectoryIntent(uri);
        return new UiAlertDialog();
    }

    private void sendOpenExternalDirectoryIntent(Uri uri) {
        final Intent intent = new Intent(Intent.ACTION_OPEN_EXTERNAL_DIRECTORY);
        intent.setData(uri);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        mDevice.waitForIdle();
    }

    private final class UiAlertDialog {
        final UiObject messageText;
        final UiObject yesButton;
        final UiObject noButton;

        UiAlertDialog() throws UiObjectNotFoundException {
            final String id = "android:id/parentPanel";
            boolean gotIt = mDevice.wait(Until.hasObject(By.res(id)), TIMEOUT);
            assertTrue("object with id '(" + id + "') not visible yet", gotIt);
            final UiObject dialog = mDevice.findObject(new UiSelector().resourceId(id));
            assertTrue("object with id '(" + id + "') doesn't exist", dialog.exists());
            messageText = dialog.getChild(new UiSelector().resourceId("android:id/message"));
            yesButton = dialog.getChild(new UiSelector().resourceId("android:id/button1"));
            noButton  = dialog.getChild(new UiSelector().resourceId("android:id/button2"));
        }
    }
}
