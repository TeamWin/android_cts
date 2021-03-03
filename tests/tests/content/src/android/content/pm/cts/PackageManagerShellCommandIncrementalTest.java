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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.service.dataloader.DataLoaderService;
import android.text.TextUtils;

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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    private static final String TEST_APK_PROFILEABLE = "HelloWorld5Profileable.apk";
    private static final String TEST_APK_SHELL = "HelloWorldShell.apk";
    private static final String TEST_APK_SPLIT0 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_APK_SPLIT1 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_APK_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";

    private static final long EXPECTED_READ_TIME = 1000L;

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
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
                executeShellCommand("pm install-incremental -t -g -S " + length,
                        new File[]{file}, new long[]{newLength}).contains(
                        "Failure"));
        assertFalse(stateListenerResult.await());
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
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
            final File apkToRead = new File(getCodePath(TEST_APP_PACKAGE), "split_config.mdpi.apk");
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
        final File apkToRead = new File(getCodePath(TEST_APP_PACKAGE), "split_config.mdpi.apk");
        final long readTime = readAndReportTime(apkToRead, 1000);
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

    private void doTestInstallSysTrace(String testApk) throws Exception {
        // Async atrace dump uses less resources but requires periodic pulls.
        // Overall timeout of 30secs in 100ms intervals should be enough.
        final int atraceDumpIterations = 300;
        final int atraceDumpDelayMs = 100;

        final String expected = "|page_read:";
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final Thread readFromProcess = new Thread(() -> {
            try {
                executeShellCommand("atrace --async_start -b 1024 -c adb");
                try {
                    for (int i = 0; i < atraceDumpIterations; ++i) {
                        final ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(
                                "atrace --async_dump");
                        try (InputStream inputStream =
                                     new ParcelFileDescriptor.AutoCloseInputStream(
                                             stdout)) {
                            final String found = waitForSubstring(inputStream, expected);
                            if (!TextUtils.isEmpty(found)) {
                                result.write(found.getBytes());
                                return;
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

        for (int i = 0; i < 3; ++i) {
            installPackage(testApk);
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));
            uninstallPackageSilently(TEST_APP_PACKAGE);
        }

        readFromProcess.join();
        assertNotEquals(0, result.size());
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

    private void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm install-incremental -t -g " + file.getPath()));
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

    private long readAndReportTime(File file, long borderTime) throws Exception {
        assertTrue(file.toString(), file.exists());

        final long startTime = SystemClock.uptimeMillis();
        long readTime = 0;
        try (InputStream baseApkStream = new FileInputStream(file)) {
            final byte[] buffer = new byte[4096];
            int length;
            while ((length = baseApkStream.read(buffer)) != -1) {
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
        HandlerThread responseThread = new HandlerThread("response");
        responseThread.start();
        // Should receive at least one fully_loaded broadcast
        final Semaphore fullyLoadedSemaphore = new Semaphore(0);
        final RemoteCallback callback = new RemoteCallback(
                bundle -> {
                    if (bundle == null) {
                        return;
                    }
                    if (bundle.getString("intent").equals(Intent.ACTION_PACKAGE_FULLY_LOADED)) {
                        fullyLoadedSemaphore.release();
                    }
                },
                new Handler(responseThread.getLooper())
        );
        intent.putExtra("callback", callback);
        InstrumentationRegistry.getInstrumentation().getContext().startActivity(intent);
        return () -> fullyLoadedSemaphore.tryAcquire(10, TimeUnit.SECONDS);
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

    private static String waitForSubstring(InputStream inputStream, String expected)
            throws IOException {
        try (Reader reader = new InputStreamReader(inputStream);
             BufferedReader lines = new BufferedReader(reader)) {
            return lines.lines().filter(line -> line.contains(expected)).findFirst().orElse("");
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

