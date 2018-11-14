/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.app.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.DateUtils;
import android.util.Log;
import android.webkit.cts.CtsTestServer;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
public class DownloadManagerTest {
    private static final String TAG = "DownloadManagerTest";

    /**
     * According to the CDD Section 7.6.1, the DownloadManager implementation must be able to
     * download individual files of 100 MB.
     */
    private static final int MINIMUM_DOWNLOAD_BYTES = 100 * 1024 * 1024;

    private static final long SHORT_TIMEOUT = 5 * DateUtils.SECOND_IN_MILLIS;
    private static final long LONG_TIMEOUT = 3 * DateUtils.MINUTE_IN_MILLIS;

    private Context mContext;
    private DownloadManager mDownloadManager;

    private CtsTestServer mWebServer;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        mWebServer = new CtsTestServer(mContext);
        clearDownloads();
    }

    @After
    public void tearDown() throws Exception {
        mWebServer.shutdown();
    }

    @Test
    public void testDownloadManager() throws Exception {
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            long goodId = mDownloadManager.enqueue(new Request(getGoodUrl()));
            long badId = mDownloadManager.enqueue(new Request(getBadUrl()));

            int allDownloads = getTotalNumberDownloads();
            assertEquals(2, allDownloads);

            assertDownloadQueryableById(goodId);
            assertDownloadQueryableById(badId);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, goodId, badId);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);
            assertDownloadQueryableByStatus(DownloadManager.STATUS_FAILED);

            assertRemoveDownload(goodId, allDownloads - 1);
            assertRemoveDownload(badId, allDownloads - 2);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testDownloadManagerSupportsHttp() throws Exception {
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            long id = mDownloadManager.enqueue(new Request(getGoodUrl()));

            assertEquals(1, getTotalNumberDownloads());

            assertDownloadQueryableById(id);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, id);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testDownloadManagerSupportsHttpWithExternalWebServer() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testDownloadManagerSupportsHttpWithExternalWebServer() ignored on device without Internet");
            return;
        }

        // As a result of testDownloadManagerSupportsHttpsWithExternalWebServer relying on an
        // external resource https://www.example.com this test uses http://www.example.com to help
        // disambiguate errors from testDownloadManagerSupportsHttpsWithExternalWebServer.

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            long id = mDownloadManager.enqueue(new Request(Uri.parse("http://www.example.com")));

            assertEquals(1, getTotalNumberDownloads());

            assertDownloadQueryableById(id);

            receiver.waitForDownloadComplete(LONG_TIMEOUT, id);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testDownloadManagerSupportsHttpsWithExternalWebServer() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testDownloadManagerSupportsHttpsWithExternalWebServer() ignored on device without Internet");
            return;
        }

        // For HTTPS, DownloadManager trusts only SSL server certs issued by CAs trusted by the
        // system. Unfortunately, this means that it cannot trust the mock web server's SSL cert.
        // Until this is resolved (e.g., by making it possible to specify additional CA certs to
        // trust for a particular download), this test relies on https://www.example.com being
        // operational and reachable from the Android under test.

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            long id = mDownloadManager.enqueue(new Request(Uri.parse("https://www.example.com")));

            assertEquals(1, getTotalNumberDownloads());

            assertDownloadQueryableById(id);

            receiver.waitForDownloadComplete(LONG_TIMEOUT, id);

            assertDownloadQueryableByStatus(DownloadManager.STATUS_SUCCESSFUL);

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    @CddTest(requirement="7.6.1")
    @Test
    public void testMinimumDownload() throws Exception {
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            long id = mDownloadManager.enqueue(new Request(getMinimumDownloadUrl()));
            receiver.waitForDownloadComplete(LONG_TIMEOUT, id);

            ParcelFileDescriptor fileDescriptor = mDownloadManager.openDownloadedFile(id);
            assertEquals(MINIMUM_DOWNLOAD_BYTES, fileDescriptor.getStatSize());

            Cursor cursor = null;
            try {
                cursor = mDownloadManager.query(new Query().setFilterById(id));
                assertTrue(cursor.moveToNext());
                assertEquals(DownloadManager.STATUS_SUCCESSFUL, cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
                assertEquals(MINIMUM_DOWNLOAD_BYTES, cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
                assertFalse(cursor.moveToNext());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            assertRemoveDownload(id, 0);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Set download locations and verify that file is downloaded to correct location.
     *
     * Checks three different methods of setting location: directly via setDestinationUri, and
     * indirectly through setDestinationInExternalFilesDir and setDestinationinExternalPublicDir.
     */
    @Test
    public void testDownloadManagerDestination() throws Exception {
        File uriLocation = new File(mContext.getExternalFilesDir(null), "uriFile.bin");
        if (uriLocation.exists()) {
            assertTrue(uriLocation.delete());
        }

        File extFileLocation = new File(mContext.getExternalFilesDir(null), "extFile.bin");
        if (extFileLocation.exists()) {
            assertTrue(extFileLocation.delete());
        }

        File publicLocation = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "publicFile.bin");
        if (publicLocation.exists()) {
            assertTrue(publicLocation.delete());
        }

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            Request requestUri = new Request(getGoodUrl());
            requestUri.setDestinationUri(Uri.fromFile(uriLocation));
            long uriId = mDownloadManager.enqueue(requestUri);

            Request requestExtFile = new Request(getGoodUrl());
            requestExtFile.setDestinationInExternalFilesDir(mContext, null, "extFile.bin");
            long extFileId = mDownloadManager.enqueue(requestExtFile);

            Request requestPublic = new Request(getGoodUrl());
            requestPublic.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "publicFile.bin");
            long publicId = mDownloadManager.enqueue(requestPublic);

            int allDownloads = getTotalNumberDownloads();
            assertEquals(3, allDownloads);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, uriId, extFileId, publicId);

            assertSuccessfulDownload(uriId, uriLocation);
            assertSuccessfulDownload(extFileId, extFileLocation);
            assertSuccessfulDownload(publicId, publicLocation);

            assertRemoveDownload(uriId, allDownloads - 1);
            assertRemoveDownload(extFileId, allDownloads - 2);
            assertRemoveDownload(publicId, allDownloads - 3);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Set the download location and verify that the extension of the file name is left unchanged.
     */
    @Test
    public void testDownloadManagerDestinationExtension() throws Exception {
        String noExt = "noiseandchirps";
        File noExtLocation = new File(mContext.getExternalFilesDir(null), noExt);
        if (noExtLocation.exists()) {
            assertTrue(noExtLocation.delete());
        }

        String wrongExt = "noiseandchirps.wrong";
        File wrongExtLocation = new File(mContext.getExternalFilesDir(null), wrongExt);
        if (wrongExtLocation.exists()) {
            assertTrue(wrongExtLocation.delete());
        }

        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            Request requestNoExt = new Request(getAssetUrl(noExt));
            requestNoExt.setDestinationUri(Uri.fromFile(noExtLocation));
            long noExtId = mDownloadManager.enqueue(requestNoExt);

            Request requestWrongExt = new Request(getAssetUrl(wrongExt));
            requestWrongExt.setDestinationUri(Uri.fromFile(wrongExtLocation));
            long wrongExtId = mDownloadManager.enqueue(requestWrongExt);

            int allDownloads = getTotalNumberDownloads();
            assertEquals(2, allDownloads);

            receiver.waitForDownloadComplete(SHORT_TIMEOUT, noExtId, wrongExtId);

            assertSuccessfulDownload(noExtId, noExtLocation);
            assertSuccessfulDownload(wrongExtId, wrongExtLocation);

            assertRemoveDownload(noExtId, allDownloads - 1);
            assertRemoveDownload(wrongExtId, allDownloads - 2);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private String cannonicalizeProcessName(ApplicationInfo ai) {
        return cannonicalizeProcessName(ai.processName, ai);
    }

    private String cannonicalizeProcessName(String process, ApplicationInfo ai) {
        if (process == null) {
            return null;
        }
        // Handle private scoped process names.
        if (process.startsWith(":")) {
            return ai.packageName + process;
        }
        return process;
    }

    @Test
    public void testProviderAcceptsCleartext() throws Exception {
        // Check that all the applications that share an android:process with the DownloadProvider
        // accept cleartext traffic. Otherwise process loading races can lead to inconsistent flags.
        final PackageManager pm = mContext.getPackageManager();
        ProviderInfo downloadInfo = pm.resolveContentProvider("downloads", 0);
        assertNotNull(downloadInfo);
        String downloadProcess
                = cannonicalizeProcessName(downloadInfo.processName, downloadInfo.applicationInfo);

        for (PackageInfo pi : mContext.getPackageManager().getInstalledPackages(0)) {
            if (downloadProcess.equals(cannonicalizeProcessName(pi.applicationInfo))) {
                assertTrue("package: " + pi.applicationInfo.packageName
                        + " must set android:usesCleartextTraffic=true"
                        ,(pi.applicationInfo.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
                        != 0);
            }
        }
    }

    @Test
    public void testAddCompletedDownload() throws Exception {
        final String fileContents = "RED;GREEN;BLUE";
        final File file = createFile(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "colors.txt");
        writeToFile(file, fileContents);

        final long id = mDownloadManager.addCompletedDownload("Test title", "Test desc", true,
                "text/plain", file.getPath(), fileContents.getBytes().length, true);
        final String actualContents = readFromFile(mDownloadManager.openDownloadedFile(id));
        assertEquals(fileContents, actualContents);

        final String rawFilePath = getRawFilePath(mDownloadManager.getUriForDownloadedFile(id));
        final String rawFileContents = readFromRawFile(rawFilePath);
        assertEquals(fileContents, rawFileContents);
    }

    private static String getRawFilePath(Uri uri) throws Exception {
        final String res = runShellCommand("content query --uri " + uri + " --projection _data");
        final int i = res.indexOf("_data=");
        if (i >= 0) {
            return res.substring(i + 6);
        } else {
            throw new FileNotFoundException("Failed to find _data for " + uri + "; found " + res);
        }
    }

    private static String readFromRawFile(String filePath) throws Exception {
        Log.d(TAG, "Reading form file: " + filePath);
        return runShellCommand("cat " + filePath);
    }

    private static String readFromFile(ParcelFileDescriptor pfd) throws Exception {
        BufferedReader br = null;
        try (final InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    private static File createFile(File baseDir, String fileName) {
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, fileName);
    }

    private static void writeToFile(File file, String contents) throws Exception {
        try (final PrintWriter out = new PrintWriter(file)) {
            out.print(contents);
        }
    }

    private class DownloadCompleteReceiver extends BroadcastReceiver {
        private HashSet<Long> mCompleteIds = new HashSet<>();

        public DownloadCompleteReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mCompleteIds) {
                mCompleteIds.add(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                mCompleteIds.notifyAll();
            }
        }

        private boolean isCompleteLocked(long... ids) {
            for (long id : ids) {
                if (!mCompleteIds.contains(id)) {
                    return false;
                }
            }
            return true;
        }

        public void waitForDownloadComplete(long timeoutMillis, long... waitForIds)
                throws InterruptedException {
            if (waitForIds.length == 0) {
                throw new IllegalArgumentException("Missing IDs to wait for");
            }

            final long startTime = SystemClock.elapsedRealtime();
            do {
                synchronized (mCompleteIds) {
                    mCompleteIds.wait(timeoutMillis);
                    if (isCompleteLocked(waitForIds)) return;
                }
            } while ((SystemClock.elapsedRealtime() - startTime) < timeoutMillis);

            throw new InterruptedException("Timeout waiting for IDs " + Arrays.toString(waitForIds)
                    + "; received " + mCompleteIds.toString()
                    + ".  Make sure you have WiFi or some other connectivity for this test.");
        }
    }

    private void clearDownloads() {
        if (getTotalNumberDownloads() > 0) {
            Cursor cursor = null;
            try {
                Query query = new Query();
                cursor = mDownloadManager.query(query);
                int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                long[] removeIds = new long[cursor.getCount()];
                for (int i = 0; cursor.moveToNext(); i++) {
                    removeIds[i] = cursor.getLong(columnIndex);
                }
                assertEquals(removeIds.length, mDownloadManager.remove(removeIds));
                assertEquals(0, getTotalNumberDownloads());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    private Uri getGoodUrl() {
        return Uri.parse(mWebServer.getTestDownloadUrl("cts-good-download", 0));
    }

    private Uri getBadUrl() {
        return Uri.parse(mWebServer.getBaseUri() + "/nosuchurl");
    }

    private Uri getMinimumDownloadUrl() {
        return Uri.parse(mWebServer.getTestDownloadUrl("cts-minimum-download",
                MINIMUM_DOWNLOAD_BYTES));
    }

    private Uri getAssetUrl(String asset) {
        return Uri.parse(mWebServer.getAssetUrl(asset));
    }

    private int getTotalNumberDownloads() {
        Cursor cursor = null;
        try {
            Query query = new Query();
            cursor = mDownloadManager.query(query);
            return cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void assertDownloadQueryableById(long downloadId) {
        Cursor cursor = null;
        try {
            Query query = new Query().setFilterById(downloadId);
            cursor = mDownloadManager.query(query);
            assertEquals(1, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void assertDownloadQueryableByStatus(final int status) {
        new PollingCheck() {
            @Override
            protected boolean check() {
                Cursor cursor= null;
                try {
                    Query query = new Query().setFilterByStatus(status);
                    cursor = mDownloadManager.query(query);
                    return 1 == cursor.getCount();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }.run();
    }

    private void assertSuccessfulDownload(long id, File location) {
        Cursor cursor = null;
        try {
            cursor = mDownloadManager.query(new Query().setFilterById(id));
            assertTrue(cursor.moveToNext());
            assertEquals(DownloadManager.STATUS_SUCCESSFUL, cursor.getInt(
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
            assertEquals(Uri.fromFile(location).toString(),
                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
            assertTrue(location.exists());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void assertRemoveDownload(long removeId, int expectedNumDownloads) {
        Cursor cursor = null;
        try {
            assertEquals(1, mDownloadManager.remove(removeId));
            Query query = new Query();
            cursor = mDownloadManager.query(query);
            assertEquals(expectedNumDownloads, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean hasInternetConnection() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
                || pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET);
    }
}
