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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.service.dataloader.DataLoaderService;
import android.text.TextUtils;
import android.util.ArrayMap;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(AndroidJUnit4.class)
@AppModeFull
@LargeTest
public class PackageManagerShellCommandIncrementalTest {
    private static final String CTS_PACKAGE_NAME = "android.content.cts";
    private static final String TEST_APP_PACKAGE = "com.example.helloworld";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_APK = "HelloWorld5.apk";
    private static final String TEST_APK_IDSIG = "HelloWorld5.apk.idsig";
    private static final String TEST_APK_PROFILEABLE = "HelloWorld5Profileable.apk";
    private static final String TEST_APK_SHELL = "HelloWorldShell.apk";
    private static final String TEST_APK_SPLIT0 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_APK_SPLIT0_IDSIG = "HelloWorld5_mdpi-v4.apk.idsig";
    private static final String TEST_APK_SPLIT1 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_APK_SPLIT1_IDSIG = "HelloWorld5_hdpi-v4.apk.idsig";
    private static final String TEST_APK_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";
    private static final String TEST_APK_SPLIT2_IDSIG = "HelloWorld5_xhdpi-v4.apk.idsig";

    private static final long EXPECTED_READ_TIME = 1000L;

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    @Before
    public void onBefore() throws Exception {
        checkIncrementalDeliveryFeature();
        cleanup();
    }

    @After
    public void onAfter() throws Exception {
        cleanup();
    }

    private void checkIncrementalDeliveryFeature() throws Exception {
        Assume.assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY));
    }

    private void checkIncrementalDeliveryV2Feature() throws Exception {
        checkIncrementalDeliveryFeature();
        Assume.assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY, 2));
    }

    @Test
    public void testAndroid12RequiresIncFsV2() throws Exception {
        final boolean v2Required = (SystemProperties.getInt("ro.product.first_api_level", 0) > 30);
        if (v2Required) {
            Assert.assertTrue(getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_INCREMENTAL_DELIVERY, 2));
        }
    }

    @Test
    public void testInstallWithIdSig() throws Exception {
        final Result stateListenerResult = startListeningForBroadcast();
        installPackage(TEST_APK);
        assertTrue(stateListenerResult.await());
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitInstallWithIdSig() throws Exception {
        // First fully install the apk.
        {
            final Result stateListenerResult = startListeningForBroadcast();
            installPackage(TEST_APK);
            assertTrue(stateListenerResult.await());
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        }

        installSplit(TEST_APK_SPLIT0);
        assertEquals("base, config.mdpi", getSplits(TEST_APP_PACKAGE));

        installSplit(TEST_APK_SPLIT1);
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSystemInstallWithIdSig() throws Exception {
        final String baseName = TEST_APK_SHELL;
        final File file = new File(createApkPath(baseName));
        assertEquals(
                "Failure [INSTALL_FAILED_SESSION_INVALID: Incremental installation of this "
                        + "package is not allowed.]\n",
                executeShellCommand("pm install-incremental -t -g " + file.getPath()));
    }

    @Test
    public void testInstallWithIdSigAndSplit() throws Exception {
        File apkfile = new File(createApkPath(TEST_APK));
        File splitfile = new File(createApkPath(TEST_APK_SPLIT0));
        File[] files = new File[]{apkfile, splitfile};
        String param = Arrays.stream(files).map(
                file -> file.getName() + ":" + file.length()).collect(Collectors.joining(" "));
        final Result stateListenerResult = startListeningForBroadcast();
        assertEquals("Success\n", executeShellCommand(
                String.format("pm install-incremental -t -g -S %s %s",
                        (apkfile.length() + splitfile.length()), param),
                files));
        assertTrue(stateListenerResult.await());
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.mdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testInstallWithIdSigInvalidLength() throws Exception {
        File file = new File(createApkPath(TEST_APK));
        final Result stateListenerResult = startListeningForBroadcast();
        assertTrue(
                executeShellCommand("pm install-incremental -t -g -S " + (file.length() - 1),
                        new File[]{file}).contains(
                        "Failure"));
        assertFalse(stateListenerResult.await());
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigStreamIncompleteData() throws Exception {
        File file = new File(createApkPath(TEST_APK));
        long length = file.length();
        // Streaming happens in blocks of 1024 bytes, new length will not stream the last block.
        long newLength = length - (length % 1024 == 0 ? 1024 : length % 1024);
        final Result stateListenerResult = startListeningForBroadcast();
        assertTrue(
                executeShellCommand(
                        "pm install-incremental -t -g -S " + length,
                        new File[] {file},
                        new long[] {newLength})
                        .contains("Failure"));
        assertFalse(stateListenerResult.await());
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigNoMissingPages() throws Exception {
        final int installIterations = 1;
        final int atraceDumpIterations = 3;
        final int atraceDumpDelayMs = 1000;
        final String missingPageReads = "|missing_page_reads: count=";

        final ArrayList<String> missingPages = new ArrayList<>();

        checkSysTrace(
                installIterations,
                atraceDumpIterations,
                atraceDumpDelayMs,
                () -> {
                    // Install multiple splits so that digesters won't kick in.
                    installPackage(TEST_APK);
                    installSplit(TEST_APK_SPLIT0);
                    installSplit(TEST_APK_SPLIT1);
                    installSplit(TEST_APK_SPLIT2);
                    // Now read it as fast as we can.
                    readSplitInChunks("base.apk");
                    readSplitInChunks("split_config.mdpi.apk");
                    readSplitInChunks("split_config.hdpi.apk");
                    readSplitInChunks("split_config.xhdpi.apk");
                    return null;
                },
                (stdout) -> {
                    try (Scanner scanner = new Scanner(stdout)) {
                        ReadLogEntry prevLogEntry = null;
                        while (scanner.hasNextLine()) {
                            final String line = scanner.nextLine();

                            final ReadLogEntry readLogEntry = ReadLogEntry.parse(line);
                            if (readLogEntry != null) {
                                prevLogEntry = readLogEntry;
                                continue;
                            }

                            int missingPageIdx = line.indexOf(missingPageReads);
                            if (missingPageIdx == -1) {
                                continue;
                            }
                            String missingBlocks = line.substring(
                                    missingPageIdx + missingPageReads.length());

                            int prvTimestamp = prevLogEntry != null ? extractTimestamp(
                                    prevLogEntry.line) : -1;
                            int curTimestamp = extractTimestamp(line);
                            if (prvTimestamp == -1 || curTimestamp == -1) {
                                missingPages.add("count=" + missingBlocks);
                                continue;
                            }

                            int delta = curTimestamp - prvTimestamp;
                            missingPages.add(
                                    "count=" + missingBlocks + ", timestamp delta=" + delta + "ms");
                        }
                        return false;
                    }
                });

        assertTrue("Missing page reads found in atrace dump: " + String.join("\n", missingPages),
                missingPages.isEmpty());
    }

    static class ReadLogEntry {
        public final String line;
        public final int blockIdx;
        public final int count;
        public final int fileIdx;
        public final int appId;
        public final int userId;

        private ReadLogEntry(String line, int blockIdx, int count, int fileIdx, int appId,
                int userId) {
            this.line = line;
            this.blockIdx = blockIdx;
            this.count = count;
            this.fileIdx = fileIdx;
            this.appId = appId;
            this.userId = userId;
        }

        public String toString() {
            return blockIdx + "/" + count + "/" + fileIdx + "/" + appId + "/" + userId;
        }

        static final String BLOCK_PREFIX = "|page_read: index=";
        static final String COUNT_PREFIX = " count=";
        static final String FILE_PREFIX = " file=";
        static final String APP_ID_PREFIX = " appid=";
        static final String USER_ID_PREFIX = " userid=";

        private static int parseInt(String line, int prefixIdx, int prefixLen, int endIdx) {
            if (prefixIdx == -1) {
                return -1;
            }
            final String intStr;
            if (endIdx != -1) {
                intStr = line.substring(prefixIdx + prefixLen, endIdx);
            } else {
                intStr = line.substring(prefixIdx + prefixLen);
            }

            return Integer.parseInt(intStr);
        }

        static ReadLogEntry parse(String line) {
            int blockIdx = line.indexOf(BLOCK_PREFIX);
            if (blockIdx == -1) {
                return null;
            }
            int countIdx = line.indexOf(COUNT_PREFIX, blockIdx + BLOCK_PREFIX.length());
            if (countIdx == -1) {
                return null;
            }
            int fileIdx = line.indexOf(FILE_PREFIX, countIdx + COUNT_PREFIX.length());
            if (fileIdx == -1) {
                return null;
            }
            int appIdIdx = line.indexOf(APP_ID_PREFIX, fileIdx + FILE_PREFIX.length());
            final int userIdIdx;
            if (appIdIdx != -1) {
                userIdIdx = line.indexOf(USER_ID_PREFIX, appIdIdx + APP_ID_PREFIX.length());
            } else {
                userIdIdx = -1;
            }

            return new ReadLogEntry(
                    line,
                    parseInt(line, blockIdx, BLOCK_PREFIX.length(), countIdx),
                    parseInt(line, countIdx, COUNT_PREFIX.length(), fileIdx),
                    parseInt(line, fileIdx, FILE_PREFIX.length(), appIdIdx),
                    parseInt(line, appIdIdx, APP_ID_PREFIX.length(), userIdIdx),
                    parseInt(line, userIdIdx, USER_ID_PREFIX.length(), -1));
        }
    }

    @Test
    public void testReadLogParser() throws Exception {
        assertEquals(null, ReadLogEntry.parse("# tracer: nop\n"));
        assertEquals(
                "178/290/0/10184/0",
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.227110: tracing_mark_write: "
                                + "B|1639|page_read: index=178 count=290 file=0 appid=10184 "
                                + "userid=0")
                        .toString());
        assertEquals(
                null,
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.227111: tracing_mark_write: E|1639"));
        assertEquals(
                "468/337/0/10184/2",
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.243227: tracing_mark_write: "
                                + "B|1639|page_read: index=468 count=337 file=0 appid=10184 "
                                + "userid=2")
                        .toString());
        assertEquals(
                null,
                ReadLogEntry.parse(
                        "<...>-2777  ( 1639) [006] ....  2764.243229: tracing_mark_write: E|1639"));
        assertEquals(
                "18/9/3/-1/-1",
                ReadLogEntry.parse(
                        "           <...>-2777  ( 1639) [006] ....  2764.227095: "
                                + "tracing_mark_write: B|1639|page_read: index=18 count=9 file=3")
                        .toString());
    }

    static int extractTimestamp(String line) {
        final String timestampEnd = ": tracing_mark_write:";
        int timestampEndIdx = line.indexOf(timestampEnd);
        if (timestampEndIdx == -1) {
            return -1;
        }

        int timestampBegIdx = timestampEndIdx - 1;
        for (; timestampBegIdx >= 0; --timestampBegIdx) {
            char ch = line.charAt(timestampBegIdx);
            if ('0' <= ch && ch <= '9' || ch == '.') {
                continue;
            }
            break;
        }
        double timestamp = Double.parseDouble(line.substring(timestampBegIdx, timestampEndIdx));
        return (int) (timestamp * 1000);
    }

    @Test
    public void testExtractTimestamp() throws Exception {
        assertEquals(-1, extractTimestamp("# tracer: nop\n"));
        assertEquals(14255168, extractTimestamp(
                "<...>-10355 ( 1636) [006] .... 14255.168694: tracing_mark_write: "
                        + "B|1636|page_read: index=1 count=16 file=0 appid=10184 userid=0"));
        assertEquals(2764243, extractTimestamp(
                "<...>-2777  ( 1639) [006] ....  2764.243225: tracing_mark_write: "
                        + "B|1639|missing_page_reads: count=132"));
    }

    static class AppReads {
        public final String packageName;
        public final int reads;

        AppReads(String packageName, int reads) {
            this.packageName = packageName;
            this.reads = reads;
        }
    }

    @LargeTest
    @Test
    @Ignore("Pending fix in GMSCore")
    public void testInstallWithIdSigNoDigesting() throws Exception {
        // Overall timeout of 3secs in 100ms intervals.
        final int installIterations = 1;
        final int atraceDumpIterations = 30;
        final int atraceDumpDelayMs = 100;
        final int blockSize = 4096;

        File apkfile = new File(createApkPath(TEST_APK));
        int blocks = (int) ((apkfile.length() + blockSize - 1) / blockSize);
        boolean[] touched = new boolean[blocks];

        final ArrayMap<Integer, Integer> uids = new ArrayMap<>();

        final AtomicInteger totalTouchedBlocks = new AtomicInteger(0);
        checkSysTrace(
                installIterations,
                atraceDumpIterations,
                atraceDumpDelayMs,
                () -> installPackage(TEST_APK),
                (stdout) -> {
                    try (Scanner scanner = new Scanner(stdout)) {
                        while (scanner.hasNextLine()) {
                            final ReadLogEntry readLogEntry = ReadLogEntry.parse(
                                    scanner.nextLine());
                            if (readLogEntry == null) {
                                continue;
                            }
                            for (int i = 0, count = readLogEntry.count; i < count; ++i) {
                                int blockIdx = readLogEntry.blockIdx + i;
                                if (touched[blockIdx]) {
                                    continue;
                                }

                                touched[blockIdx] = true;

                                int uid = UserHandle.getUid(readLogEntry.userId,
                                        readLogEntry.appId);
                                Integer touchedByUid = uids.get(uid);
                                uids.put(uid, touchedByUid == null ? 1 : touchedByUid + 1);

                                if (totalTouchedBlocks.incrementAndGet() >= blocks) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                });

        if (totalTouchedBlocks.get() < blocks) {
            return;
        }

        PackageManager pm = getPackageManager();

        AppReads[] appIdReads = new AppReads[uids.size()];
        for (int i = 0, size = uids.size(); i < size; ++i) {
            final int uid = uids.keyAt(i);
            final int appId = UserHandle.getAppId(uid);
            final int userId = UserHandle.getUserId(uid);

            final String packageName;
            if (appId < Process.FIRST_APPLICATION_UID) {
                packageName = "<system>";
            } else {
                String[] packages = pm.getPackagesForUid(uid);
                if (packages == null || packages.length == 0) {
                    packageName = "<unknown package, appId=" + appId + ", userId=" + userId + ">";
                } else {
                    packageName = "[" + String.join(",", packages) + "]";
                }
            }
            appIdReads[i] = new AppReads(packageName, uids.valueAt(i));
        }
        Arrays.sort(appIdReads, (lhs, rhs) -> Integer.compare(rhs.reads, lhs.reads));

        final String packages = String.join("\n", Arrays.stream(appIdReads).map(
                item -> item.packageName + " : " + item.reads + " blocks").toArray(String[]::new));
        assertTrue("Digesting detected, list of packages: " + packages,
                totalTouchedBlocks.get() < blocks);
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigPerUidTimeouts() throws Exception {
        executeShellCommand("atrace --async_start -b 1024 -c adb");
        try {
            setDeviceProperty("incfs_default_timeouts", "5000000:5000000:5000000");
            setDeviceProperty("known_digesters_list", CTS_PACKAGE_NAME);

            final Result stateListenerResult = startListeningForBroadcast();
            installPackage(TEST_APK);
            assertTrue(stateListenerResult.await());
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        } finally {
            executeShellCommand("atrace --async_stop");
        }
    }

    @LargeTest
    @Test
    @Ignore("flaky in the lab")
    public void testInstallWithIdSigStreamPerUidTimeoutsIncompleteData() throws Exception {
        checkIncrementalDeliveryV2Feature();
        executeShellCommand("atrace --async_start -b 1024 -c adb");
        try {
            setDeviceProperty("incfs_default_timeouts", "5000000:5000000:5000000");
            setDeviceProperty("known_digesters_list", CTS_PACKAGE_NAME);

            // First fully install the apk and a split0.
            {
                final Result stateListenerResult = startListeningForBroadcast();
                installPackage(TEST_APK);
                assertTrue(stateListenerResult.await());
                assertTrue(isAppInstalled(TEST_APP_PACKAGE));
                installSplit(TEST_APK_SPLIT0);
                assertEquals("base, config.mdpi", getSplits(TEST_APP_PACKAGE));
                installSplit(TEST_APK_SPLIT1);
                assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));
            }

            // Try to read a split and see if we are throttled.
            final File apkToRead = getSplit("split_config.mdpi.apk");
            final long readTime0 = readAndReportTime(apkToRead, 1000);

            // Install another split, interrupt in the middle, and measure read time.
            File splitfile = new File(createApkPath(TEST_APK_SPLIT2));
            long splitLength = splitfile.length();
            // Don't fully stream the split.
            long newSplitLength =
                    splitLength - (splitLength % 1024 == 0 ? 1024 : splitLength % 1024);
            final Result stateListenerResult = startListeningForBroadcast();

            try (InputStream inputStream = executeShellCommandRw(
                    "pm install-incremental -t -g -p " + TEST_APP_PACKAGE + " -S " + newSplitLength,
                    new File[]{splitfile}, new long[]{splitLength})) {

                // While 'installing', let's try and read the base apk.
                final long readTime1 = readAndReportTime(apkToRead, 1000);
                assertTrue("Must take longer than " + EXPECTED_READ_TIME + "ms: time0=" + readTime0
                                + "ms, time1=" + readTime1 + "ms",
                        readTime0 >= EXPECTED_READ_TIME || readTime1 >= EXPECTED_READ_TIME);

                // apk
                assertTrue(readFullStream(inputStream).contains("Failure"));
            }

            assertFalse(stateListenerResult.await());
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
            assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));
        } finally {
            executeShellCommand("atrace --async_stop");
        }
    }

    @LargeTest
    @Test
    public void testInstallWithIdSigPerUidTimeoutsIgnored() throws Exception {
        // Timeouts would be ignored as there are no readlogs collected.
        final int beforeReadDelayMs = 5000;
        setDeviceProperty("incfs_default_timeouts", "5000000:5000000:5000000");
        setDeviceProperty("known_digesters_list", CTS_PACKAGE_NAME);

        // First fully install the apk and a split0.
        {
            final Result stateListenerResult = startListeningForBroadcast();
            installPackage(TEST_APK);
            assertTrue(stateListenerResult.await());
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
            installSplit(TEST_APK_SPLIT0);
            assertEquals("base, config.mdpi", getSplits(TEST_APP_PACKAGE));
            installSplit(TEST_APK_SPLIT1);
            assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));
        }

        // Allow IncrementalService to update the timeouts after full download.
        Thread.currentThread().sleep(beforeReadDelayMs);

        // Try to read a split and see if we are throttled.
        final long readTime = readAndReportTime(getSplit("split_config.mdpi.apk"), 1000);
        assertTrue("Must take less than " + EXPECTED_READ_TIME + "ms vs " + readTime + "ms",
                readTime < EXPECTED_READ_TIME);
    }

    @Test
    public void testInstallWithIdSigStreamIncompleteDataForSplit() throws Exception {
        File apkfile = new File(createApkPath(TEST_APK));
        File splitfile = new File(createApkPath(TEST_APK_SPLIT0));
        long splitLength = splitfile.length();
        // Don't fully stream the split.
        long newSplitLength = splitLength - (splitLength % 1024 == 0 ? 1024 : splitLength % 1024);
        File[] files = new File[]{apkfile, splitfile};
        String param = Arrays.stream(files).map(
                file -> file.getName() + ":" + file.length()).collect(Collectors.joining(" "));
        final Result stateListenerResult = startListeningForBroadcast();
        assertTrue(executeShellCommand(
                String.format("pm install-incremental -t -g -S %s %s",
                        (apkfile.length() + splitfile.length()), param),
                files, new long[]{apkfile.length(), newSplitLength}).contains(
                "Failure"));
        assertFalse(stateListenerResult.await());
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    static class TestDataLoaderService extends DataLoaderService {
    }

    @Test
    public void testDataLoaderServiceDefaultImplementation() {
        DataLoaderService service = new TestDataLoaderService();
        assertEquals(null, service.onCreateDataLoader(null));
        IBinder binder = service.onBind(null);
        assertNotEquals(null, binder);
        assertEquals(binder, service.onBind(new Intent()));
    }

    @LargeTest
    @Test
    public void testInstallSysTraceDebuggable() throws Exception {
        doTestInstallSysTrace(TEST_APK);
    }

    @LargeTest
    @Test
    public void testInstallSysTraceProfileable() throws Exception {
        doTestInstallSysTrace(TEST_APK_PROFILEABLE);
    }

    private boolean checkSysTraceForSubstring(String testApk, final String expected,
            int atraceDumpIterations, int atraceDumpDelayMs) throws Exception {
        final int installIterations = 3;
        return checkSysTrace(
                installIterations,
                atraceDumpIterations,
                atraceDumpDelayMs,
                () -> installPackage(testApk),
                (stdout) -> stdout.contains(expected));
    }

    private boolean checkSysTrace(
            int installIterations,
            int atraceDumpIterations,
            int atraceDumpDelayMs,
            final Callable<Void> installer,
            final Function<String, Boolean> checker)
            throws Exception {
        final int beforeReadDelayMs = 1000;

        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        final Thread readFromProcess = new Thread(() -> {
            try {
                executeShellCommand("atrace --async_start -b 10240 -c adb");
                try {
                    for (int i = 0; i < atraceDumpIterations; ++i) {
                        final String stdout = executeShellCommand("atrace --async_dump");
                        try {
                            if (checker.apply(stdout)) {
                                result.complete(true);
                                break;
                            }
                            Thread.currentThread().sleep(atraceDumpDelayMs);
                        } catch (InterruptedException ignored) {
                        }
                    }
                } finally {
                    executeShellCommand("atrace --async_stop");
                }
            } catch (IOException ignored) {
            }
        });
        readFromProcess.start();

        for (int i = 0; i < installIterations; ++i) {
            installer.call();
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
            Thread.currentThread().sleep(beforeReadDelayMs);
            uninstallPackageSilently(TEST_APP_PACKAGE);
        }

        readFromProcess.join();
        return result.getNow(false);
    }

    private void doTestInstallSysTrace(String testApk) throws Exception {
        // Async atrace dump uses less resources but requires periodic pulls.
        // Overall timeout of 30secs in 100ms intervals should be enough.
        final int atraceDumpIterations = 300;
        final int atraceDumpDelayMs = 100;
        final String expected = "|page_read:";

        assertTrue(
                "No page reads (" + expected + ") found in atrace dump",
                checkSysTraceForSubstring(testApk, expected, atraceDumpIterations,
                        atraceDumpDelayMs));
    }

    private boolean isAppInstalled(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm list packages");
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.substring(prefixLength).equals(packageName));
    }

    private String getSplits(String packageName) throws IOException {
        final String result = parsePackageDump(packageName, "    splits=[");
        if (TextUtils.isEmpty(result)) {
            return null;
        }
        return result.substring(0, result.length() - 1);
    }

    private String getCodePath(String packageName) throws IOException {
        return parsePackageDump(packageName, "    codePath=");
    }

    private File getSplit(String splitName) throws Exception {
        return new File(getCodePath(TEST_APP_PACKAGE), splitName);
    }

    private String parsePackageDump(String packageName, String prefix) throws IOException {
        final String commandResult = executeShellCommand("pm dump " + packageName);
        final int prefixLength = prefix.length();
        Optional<String> maybeSplits = Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix)).findFirst();
        if (!maybeSplits.isPresent()) {
            return null;
        }
        String splits = maybeSplits.get();
        return splits.substring(prefixLength);
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    private Void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm install-incremental -t -g " + file.getPath()));
        return null;
    }

    private void installSplit(String splitName) throws Exception {
        final File splitfile = new File(createApkPath(splitName));
        final Result stateListenerResult = startListeningForBroadcast();

        try (InputStream inputStream = executeShellCommandStream(
                "pm install-incremental -t -g -p " + TEST_APP_PACKAGE + " "
                        + splitfile.getPath())) {
            assertEquals("Success\n", readFullStream(inputStream));
        }
        assertTrue(stateListenerResult.await());
    }

    private void readSplitInChunks(String splitName) throws Exception {
        final int chunks = 2;
        final int waitBetweenChunksMs = 100;
        final File file = getSplit(splitName);

        assertTrue(file.toString(), file.exists());
        final long totalSize = file.length();
        final long chunkSize = totalSize / chunks;
        try (InputStream baseApkStream = new FileInputStream(file)) {
            final byte[] buffer = new byte[4 * 1024];
            long readSoFar = 0;
            long maxToRead = 0;
            for (int i = 0; i < chunks; ++i) {
                maxToRead += chunkSize;
                int length;
                while ((length = baseApkStream.read(buffer)) != -1) {
                    readSoFar += length;
                    if (readSoFar >= maxToRead) {
                        break;
                    }
                }
                if (readSoFar < totalSize) {
                    Thread.currentThread().sleep(waitBetweenChunksMs);
                }
            }
        }
    }

    private long readAndReportTime(File file, long borderTime) throws Exception {
        assertTrue(file.toString(), file.exists());
        final long startTime = SystemClock.uptimeMillis();
        long readTime = 0;
        try (InputStream baseApkStream = new FileInputStream(file)) {
            final byte[] buffer = new byte[128 * 1024];
            while (baseApkStream.read(buffer) != -1) {
                readTime = SystemClock.uptimeMillis() - startTime;
                if (readTime >= borderTime) {
                    break;
                }
            }
        }
        return readTime;
    }

    private String uninstallPackageSilently(String packageName) throws IOException {
        return executeShellCommand("pm uninstall " + packageName);
    }

    interface Result {
        boolean await() throws Exception;
    }

    private Result startListeningForBroadcast() {
        final Intent intent = new Intent()
                .setComponent(new ComponentName("android.content.pm.cts.app", "android.content"
                        + ".pm.cts.app.MainActivity"))
                // data uri unique to each activity start to ensure actual launch and not just
                // redisplay
                .setData(Uri.parse("test://" + UUID.randomUUID().toString()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE);
        // Should receive at least one fully_loaded broadcast
        final CompletableFuture<Boolean> fullyLoaded = new CompletableFuture<>();
        final RemoteCallback callback = new RemoteCallback(
                bundle -> {
                    if (bundle == null) {
                        return;
                    }
                    if (bundle.getString("intent").equals(
                            Intent.ACTION_PACKAGE_FULLY_LOADED)) {
                        fullyLoaded.complete(true);
                    }
                });
        intent.putExtra("callback", callback);
        getContext().startActivity(intent);
        return () -> {
            try {
                return fullyLoaded.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                return false;
            }
        };
    }

    private static String executeShellCommand(String command) throws IOException {
        try (InputStream inputStream = executeShellCommandStream(command)) {
            return readFullStream(inputStream);
        }
    }

    private static InputStream executeShellCommandStream(String command) throws IOException {
        final ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(command);
        return new ParcelFileDescriptor.AutoCloseInputStream(stdout);
    }

    private static String executeShellCommand(String command, File[] inputs)
            throws IOException {
        return executeShellCommand(command, inputs, Stream.of(inputs).mapToLong(
                File::length).toArray());
    }

    private static String executeShellCommand(String command, File[] inputs, long[] expected)
            throws IOException {
        try (InputStream inputStream = executeShellCommandRw(command, inputs, expected)) {
            return readFullStream(inputStream);
        }
    }

    private static InputStream executeShellCommandRw(String command, File[] inputs, long[] expected)
            throws IOException {
        assertEquals(inputs.length, expected.length);
        final ParcelFileDescriptor[] pfds =
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommandRw(command);
        ParcelFileDescriptor stdout = pfds[0];
        ParcelFileDescriptor stdin = pfds[1];
        try (FileOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                stdin)) {
            for (int i = 0; i < inputs.length; i++) {
                try (FileInputStream inputStream = new FileInputStream(inputs[i])) {
                    writeFullStream(inputStream, outputStream, expected[i]);
                }
            }
        }
        return new ParcelFileDescriptor.AutoCloseInputStream(stdout);
    }

    private static String readFullStream(InputStream inputStream, long expected)
            throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, expected);
        return result.toString("UTF-8");
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        return readFullStream(inputStream, -1);
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        final byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1 && (expected < 0 || total < expected)) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            assertEquals(expected, total);
        }
    }

    private void cleanup() throws Exception {
        uninstallPackageSilently(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals(null, getSplits(TEST_APP_PACKAGE));
        setDeviceProperty("incfs_default_timeouts", null);
        setDeviceProperty("known_digesters_list", null);
    }

    private void setDeviceProperty(String name, String value) {
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name, value,
                    false);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

}

