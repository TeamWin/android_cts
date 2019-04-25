/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.webkit.cts.CtsTestServer;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;

public class DownloadManagerTestBase {
    protected static final String TAG = "DownloadManagerTest";

    /**
     * According to the CDD Section 7.6.1, the DownloadManager implementation must be able to
     * download individual files of 100 MB.
     */
    protected static final int MINIMUM_DOWNLOAD_BYTES = 100 * 1024 * 1024;

    protected static final long SHORT_TIMEOUT = 5 * DateUtils.SECOND_IN_MILLIS;
    protected static final long LONG_TIMEOUT = 3 * DateUtils.MINUTE_IN_MILLIS;

    protected Context mContext;
    protected DownloadManager mDownloadManager;

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
        clearDownloads();
    }

    protected void updateUri(Uri uri, String column, String value) throws Exception {
        final String cmd = String.format("content update --uri %s --bind %s:s:%s",
                uri, column, value);
        final String res = runShellCommand(cmd).trim();
        assertTrue(res, TextUtils.isEmpty(res));
    }

    protected static byte[] hash(InputStream in) throws Exception {
        try (DigestInputStream digestIn = new DigestInputStream(in,
                MessageDigest.getInstance("SHA-1"));
             OutputStream out = new FileOutputStream(new File("/dev/null"))) {
            FileUtils.copy(digestIn, out);
            return digestIn.getMessageDigest().digest();
        } finally {
            FileUtils.closeQuietly(in);
        }
    }

    protected Uri getMediaStoreUri(Uri downloadUri) throws Exception {
        final String cmd = String.format("content query --uri %s --projection %s",
                downloadUri, DownloadManager.COLUMN_MEDIASTORE_URI);
        final String res = runShellCommand(cmd).trim();
        final String str = DownloadManager.COLUMN_MEDIASTORE_URI + "=";
        final int i = res.indexOf(str);
        if (i >= 0) {
            return Uri.parse(res.substring(i + str.length()));
        } else {
            throw new FileNotFoundException("Failed to find "
                    + DownloadManager.COLUMN_MEDIASTORE_URI + " for "
                    + downloadUri + "; found " + res);
        }
    }

    protected static String getRawFilePath(Uri uri) throws Exception {
        final String res = runShellCommand("content query --uri " + uri + " --projection _data");
        final int i = res.indexOf("_data=");
        if (i >= 0) {
            return res.substring(i + 6);
        } else {
            throw new FileNotFoundException("Failed to find _data for " + uri + "; found " + res);
        }
    }

    protected static String readFromRawFile(String filePath) throws Exception {
        Log.d(TAG, "Reading form file: " + filePath);
        return runShellCommand("cat " + filePath);
    }

    protected static String readFromFile(ParcelFileDescriptor pfd) throws Exception {
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

    protected static File createFile(File baseDir, String fileName) {
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, fileName);
    }

    protected static void writeToFile(File file, String contents) throws Exception {
        try (final PrintWriter out = new PrintWriter(file)) {
            out.print(contents);
        }
    }

    protected static void writeToFileFromShell(File file, String contents) throws Exception {
        final String cmd = "echo \"" + contents + "\" > " + file;
        final String res = runShellCommand(cmd);
        Log.d(TAG, "Output of '" + cmd + "': '" + res + "'");
    }

    protected void clearDownloads() {
        if (getTotalNumberDownloads() > 0) {
            Cursor cursor = null;
            try {
                DownloadManager.Query query = new DownloadManager.Query();
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

    protected Uri getGoodUrl() {
        return Uri.parse(mWebServer.getTestDownloadUrl("cts-good-download", 0));
    }

    protected Uri getBadUrl() {
        return Uri.parse(mWebServer.getBaseUri() + "/nosuchurl");
    }

    protected Uri getMinimumDownloadUrl() {
        return Uri.parse(mWebServer.getTestDownloadUrl("cts-minimum-download",
                MINIMUM_DOWNLOAD_BYTES));
    }

    protected Uri getAssetUrl(String asset) {
        return Uri.parse(mWebServer.getAssetUrl(asset));
    }

    protected int getTotalNumberDownloads() {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            cursor = mDownloadManager.query(query);
            return cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void assertDownloadQueryableById(long downloadId) {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            cursor = mDownloadManager.query(query);
            assertEquals(1, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void assertDownloadQueryableByStatus(final int status) {
        new PollingCheck() {
            @Override
            protected boolean check() {
                Cursor cursor= null;
                try {
                    DownloadManager.Query query = new DownloadManager.Query().setFilterByStatus(status);
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

    protected void assertSuccessfulDownload(long id, File location) throws Exception {
        Cursor cursor = null;
        try {
            final File expectedLocation = location.getCanonicalFile();
            cursor = mDownloadManager.query(new DownloadManager.Query().setFilterById(id));
            assertTrue(cursor.moveToNext());
            assertEquals(DownloadManager.STATUS_SUCCESSFUL, cursor.getInt(
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
            assertEquals(Uri.fromFile(expectedLocation).toString(),
                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
            assertTrue(expectedLocation.exists());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected void assertRemoveDownload(long removeId, int expectedNumDownloads) {
        Cursor cursor = null;
        try {
            assertEquals(1, mDownloadManager.remove(removeId));
            DownloadManager.Query query = new DownloadManager.Query();
            cursor = mDownloadManager.query(query);
            assertEquals(expectedNumDownloads, cursor.getCount());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    protected boolean hasInternetConnection() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
                || pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET);
    }

    public static class DownloadCompleteReceiver extends BroadcastReceiver {
        private HashSet<Long> mCompleteIds = new HashSet<>();

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
}
