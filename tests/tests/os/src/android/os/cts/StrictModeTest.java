/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.os.cts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.StrictMode;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import android.util.Log;

import libcore.io.Streams;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Tests for {@link StrictMode}
 */
public class StrictModeTest extends InstrumentationTestCase {
    private static final String TAG = "StrictModeTest";

    private StrictMode.ThreadPolicy mThreadPolicy;
    private StrictMode.VmPolicy mVmPolicy;

    private Context getContext() {
        return getInstrumentation().getContext();
    }

    @Override
    protected void setUp() {
        mThreadPolicy = StrictMode.getThreadPolicy();
        mVmPolicy = StrictMode.getVmPolicy();
    }

    @Override
    protected void tearDown() {
        StrictMode.setThreadPolicy(mThreadPolicy);
        StrictMode.setVmPolicy(mVmPolicy);
    }

    /**
     * Insecure connection should be detected
     */
    public void testCleartextNetwork() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testCleartextNetwork() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectCleartextNetwork()
                .penaltyLog()
                .build());

        long mark = System.currentTimeMillis();
        ((HttpURLConnection) new URL("http://example.com/").openConnection()).getResponseCode();
        assertLogged("Detected cleartext network traffic from UID "
                + android.os.Process.myUid(), mark, 5000);
    }

    /**
     * Secure connection should be ignored
     */
    public void testEncryptedNetwork() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testEncryptedNetwork() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectCleartextNetwork()
                .penaltyLog()
                .build());

        long mark = System.currentTimeMillis();
        ((HttpURLConnection) new URL("https://example.com/").openConnection()).getResponseCode();
        assertNotLogged("Detected cleartext network traffic from UID "
                + android.os.Process.myUid(), mark, 5000);
    }

    public void testFileUriExposure() throws Exception {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectFileUriExposure()
                .penaltyLog()
                .build());

        long mark = System.currentTimeMillis();
        Uri uri = Uri.fromFile(new File("/sdcard/meow.jpg"));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/jpeg");
        getContext().startActivity(intent);
        assertLogged(uri + " exposed beyond app", mark);

        mark = System.currentTimeMillis();
        uri = Uri.parse("content://com.example/foobar");
        intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/jpeg");
        getContext().startActivity(intent);
        assertNotLogged(uri + " exposed beyond app", mark);
    }

    public void testContentUriWithoutPermission() throws Exception {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectContentUriWithoutPermission()
                .penaltyLog()
                .build());

        long mark = System.currentTimeMillis();
        final Uri uri = Uri.parse("content://com.example/foobar");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/jpeg");
        getContext().startActivity(intent);
        assertLogged(uri + " exposed beyond app", mark);

        mark = System.currentTimeMillis();
        intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/jpeg");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getContext().startActivity(intent);
        assertNotLogged(uri + " exposed beyond app", mark);
    }

    public void testUntaggedSocketsHttp() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testUntaggedSockets() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectUntaggedSockets()
                .penaltyLog()
                .build());

        long mark = System.currentTimeMillis();
        ((HttpURLConnection) new URL("http://example.com/").openConnection()).getResponseCode();
        assertLogged("Untagged socket detected", mark);

        mark = System.currentTimeMillis();
        TrafficStats.setThreadStatsTag(0xDECAFBAD);
        try {
            ((HttpURLConnection) new URL("http://example.com/").openConnection()).getResponseCode();
        } finally {
            TrafficStats.clearThreadStatsTag();
        }
        assertNotLogged("Untagged socket detected", mark);
    }

    public void testUntaggedSocketsRaw() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testUntaggedSockets() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectUntaggedSockets()
                .penaltyLog()
                .build());

        long mark = System.currentTimeMillis();
        TrafficStats.setThreadStatsTag(0xDECAFBAD);
        try (Socket socket = new Socket("example.com", 80)) {
            socket.getOutputStream().close();
        } finally {
            TrafficStats.clearThreadStatsTag();
        }
        assertNotLogged("Untagged socket detected", mark);

        mark = System.currentTimeMillis();
        try (Socket socket = new Socket("example.com", 80)) {
            socket.getOutputStream().close();
        }
        assertLogged("Untagged socket detected", mark);
    }

    public void testRead() throws Exception {
        final File test = File.createTempFile("foo", "bar");
        final File dir = test.getParentFile();

        FileInputStream is = null;
        FileDescriptor fd = null;

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .penaltyLog()
                .build());

        SystemClock.sleep(1500);

        try (AssertDiskReadLogged l = new AssertDiskReadLogged()) {
            test.exists();
        }
        try (AssertDiskReadLogged l = new AssertDiskReadLogged()) {
            test.length();
        }
        try (AssertDiskReadLogged l = new AssertDiskReadLogged()) {
            dir.list();
        }
        try (AssertDiskReadLogged l = new AssertDiskReadLogged()) {
            is = new FileInputStream(test);
        }
        try (AssertDiskReadLogged l = new AssertDiskReadLogged()) {
            is.read();
        }
        try (AssertDiskReadLogged l = new AssertDiskReadLogged()) {
            fd = Os.open(test.getAbsolutePath(), OsConstants.O_RDONLY, 0600);
        }
        try (AssertDiskReadLogged l = new AssertDiskReadLogged()) {
            Os.read(fd, new byte[10], 0, 1);
        }
    }

    public void testWrite() throws Exception {
        File file = null;

        FileOutputStream os = null;
        FileDescriptor fd = null;

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskWrites()
                .penaltyLog()
                .build());

        SystemClock.sleep(1500);

        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            file = File.createTempFile("foo", "bar");
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            file.delete();
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            file.createNewFile();
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            os = new FileOutputStream(file);
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            os.write(32);
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            fd = Os.open(file.getAbsolutePath(), OsConstants.O_RDWR, 0600);
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            Os.write(fd, new byte[10], 0, 1);
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            Os.fsync(fd);
        }
        try (AssertDiskWriteLogged l = new AssertDiskWriteLogged()) {
            file.renameTo(new File(file.getParent(), "foobar"));
        }
    }

    public void testNetwork() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testUntaggedSockets() ignored on device without Internet");
            return;
        }

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyLog()
                .build());

        long mark = System.currentTimeMillis();
        try (Socket socket = new Socket("example.com", 80)) {
            socket.getOutputStream().close();
        }
        assertLogged("StrictModeNetworkViolation", mark);

        mark = System.currentTimeMillis();
        ((HttpURLConnection) new URL("http://example.com/").openConnection()).getResponseCode();
        assertLogged("StrictModeNetworkViolation", mark);
    }

    private static class AssertLogged implements AutoCloseable {
        private final String mMessage;
        private final long mStart;

        public AssertLogged(String message) {
            mMessage = message;
            mStart = System.currentTimeMillis();
        }

        @Override
        public void close() throws Exception {
            assertLogged(mMessage, mStart);
        }
    }

    private static class AssertDiskReadLogged extends AssertLogged {
        public AssertDiskReadLogged() {
            super("StrictModeDiskReadViolation");
        }
    }

    private static class AssertDiskWriteLogged extends AssertLogged {
        public AssertDiskWriteLogged() {
            super("StrictModeDiskWriteViolation");
        }
    }

    private static void assertLogged(String msg, long since) throws Exception {
        assertLogged(msg, since, 1100);
    }

    private static void assertLogged(String msg, long since, long wait) throws Exception {
        SystemClock.sleep(wait);
        assertTrue("Expected message not found: " + msg, readLogSince(since).contains(msg));
    }

    private static void assertNotLogged(String msg, long since) throws Exception {
        assertNotLogged(msg, since, 1100);
    }

    private static void assertNotLogged(String msg, long since, long wait) throws Exception {
        SystemClock.sleep(wait);
        assertFalse("Unexpected message found: " + msg, readLogSince(since).contains(msg));
    }

    private static String readLogSince(long millis) throws Exception {
        final SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        final Process proc = new ProcessBuilder("logcat", "-t", format.format(new Date(millis)))
                .redirectErrorStream(true).start();

        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Streams.copy(proc.getInputStream(), buf);
        final int res = proc.waitFor();

        Log.d(TAG, "Log output was " + buf.size() + " bytes, exit code " + res);
        return new String(buf.toByteArray());
    }

    private boolean hasInternetConnection() {
        final PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
                || pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET);
    }
}
