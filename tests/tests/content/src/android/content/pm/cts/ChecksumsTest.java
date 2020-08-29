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

package android.content.pm.cts;

import static android.content.pm.PackageManager.EXTRA_CHECKSUMS;
import static android.content.pm.PackageManager.PARTIAL_MERKLE_ROOT_1M_SHA256;
import static android.content.pm.PackageManager.PARTIAL_MERKLE_ROOT_1M_SHA512;
import static android.content.pm.PackageManager.TRUST_NONE;
import static android.content.pm.PackageManager.WHOLE_MD5;
import static android.content.pm.PackageManager.WHOLE_MERKLE_ROOT_4K_SHA256;
import static android.content.pm.PackageManager.WHOLE_SHA1;
import static android.content.pm.PackageManager.WHOLE_SHA256;
import static android.content.pm.PackageManager.WHOLE_SHA512;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import android.app.UiAutomation;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.FileChecksum;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.HexDump;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class ChecksumsTest {
    private static final String V2V3_PACKAGE_NAME = "android.content.cts";
    private static final String V4_PACKAGE_NAME = "com.example.helloworld";
    private static final String FIXED_PACKAGE_NAME = "android.appsecurity.cts.tinyapp";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";

    private static final String TEST_V4_APK = "HelloWorld5.apk";
    private static final String TEST_V4_SPLIT0 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_V4_SPLIT1 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_V4_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";
    private static final String TEST_V4_SPLIT3 = "HelloWorld5_xxhdpi-v4.apk";
    private static final String TEST_V4_SPLIT4 = "HelloWorld5_xxxhdpi-v4.apk";

    private static final String TEST_FIXED_APK = "CtsPkgInstallTinyAppV2V3V4.apk";
    private static final String TEST_FIXED_APK_V1 = "CtsPkgInstallTinyAppV1.apk";
    private static final String TEST_FIXED_APK_SHA512 =
            "CtsPkgInstallTinyAppV2V3V4-Sha512withEC.apk";
    private static final String TEST_FIXED_APK_VERITY = "CtsPkgInstallTinyAppV2V3V4-Verity.apk";

    private static final int ALL_CHECKSUMS =
            WHOLE_MERKLE_ROOT_4K_SHA256 | WHOLE_MD5 | WHOLE_SHA1 | WHOLE_SHA256 | WHOLE_SHA512
                    | PARTIAL_MERKLE_ROOT_1M_SHA256 | PARTIAL_MERKLE_ROOT_1M_SHA512;

    private static PackageManager getPackageManager() {
        return InstrumentationRegistry.getContext().getPackageManager();
    }

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @Before
    public void onBefore() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);
        assertFalse(isAppInstalled(V4_PACKAGE_NAME));
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        assertFalse(isAppInstalled(FIXED_PACKAGE_NAME));
    }

    @After
    public void onAfter() throws Exception {
        uninstallPackageSilently(V4_PACKAGE_NAME);
        assertFalse(isAppInstalled(V4_PACKAGE_NAME));
        uninstallPackageSilently(FIXED_PACKAGE_NAME);
        assertFalse(isAppInstalled(FIXED_PACKAGE_NAME));
    }

    @Test
    public void testDefaultChecksums() throws Exception {
        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(V2V3_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1);
        assertEquals(checksums[0].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
    }

    @Test
    public void testSplitsDefaultChecksums() throws Exception {
        installSplits(new String[]{TEST_V4_APK, TEST_V4_SPLIT0, TEST_V4_SPLIT1, TEST_V4_SPLIT2,
                TEST_V4_SPLIT3, TEST_V4_SPLIT4});
        assertTrue(isAppInstalled(V4_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(V4_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 6);
        // v2/v3 signature use 1M merkle tree.
        assertEquals(checksums[0].getSplitName(), null);
        assertEquals(checksums[0].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[1].getSplitName(), "config.hdpi");
        assertEquals(checksums[1].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[2].getSplitName(), "config.mdpi");
        assertEquals(checksums[2].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[3].getSplitName(), "config.xhdpi");
        assertEquals(checksums[3].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[4].getSplitName(), "config.xxhdpi");
        assertEquals(checksums[4].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[5].getSplitName(), "config.xxxhdpi");
        assertEquals(checksums[5].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
    }

    @Test
    public void testFixedDefaultChecksums() throws Exception {
        installPackage(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1);
        // v2/v3 signature use 1M merkle tree.
        assertEquals(checksums[0].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "1eec9e86e322b8d7e48e255fc3f2df2dbc91036e63982ff9850597c6a37bbeb3");
        assertNull(checksums[0].getSourceCertificate());
    }

    @Test
    public void testFixedV1DefaultChecksums() throws Exception {
        installPackage(TEST_FIXED_APK_V1);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 0);
    }

    @Test
    public void testFixedSha512DefaultChecksums() throws Exception {
        installPackage(TEST_FIXED_APK_SHA512);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1);
        // v2/v3 signature use 1M merkle tree.
        assertEquals(checksums[0].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA512);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "6b866e8a54a3e358dfc20007960fb96123845f6c6d6c45f5fddf88150d71677f"
                        + "4c3081a58921c88651f7376118aca312cf764b391cdfb8a18c6710f9f27916a0");
        assertNull(checksums[0].getSourceCertificate());
    }

    @Test
    public void testFixedVerityDefaultChecksums() throws Exception {
        installPackage(TEST_FIXED_APK_VERITY);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        // No usable hashes as verity-in-v2-signature does not cover the whole file.
        assertEquals(checksums.length, 0);
    }

    @Test
    public void testAllChecksums() throws Exception {
        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(V2V3_PACKAGE_NAME, true, ALL_CHECKSUMS, TRUST_NONE,
                receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 7);
        assertEquals(checksums[0].getKind(), WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(checksums[1].getKind(), WHOLE_MD5);
        assertEquals(checksums[2].getKind(), WHOLE_SHA1);
        assertEquals(checksums[3].getKind(), WHOLE_SHA256);
        assertEquals(checksums[4].getKind(), WHOLE_SHA512);
        assertEquals(checksums[5].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(checksums[6].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA512);
    }

    @Test
    public void testFixedAllChecksums() throws Exception {
        installPackage(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, ALL_CHECKSUMS, TRUST_NONE,
                receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 7);
        assertEquals(checksums[0].getKind(), WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "90553b8d221ab1b900b242a93e4cc659ace3a2ff1d5c62e502488b385854e66a");
        assertEquals(checksums[1].getKind(), WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[1].getValue()), "c19868da017dc01467169f8ea7c5bc57");
        assertEquals(checksums[2].getKind(), WHOLE_SHA1);
        assertEquals(bytesToHexString(checksums[2].getValue()),
                "331eef6bc57671de28cbd7e32089d047285ade6a");
        assertEquals(checksums[3].getKind(), WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[3].getValue()),
                "91aa30c1ce8d0474052f71cb8210691d41f534989c5521e27e794ec4f754c5ef");
        assertEquals(checksums[4].getKind(), WHOLE_SHA512);
        assertEquals(bytesToHexString(checksums[4].getValue()),
                "b59467fe578ebc81974ab3aaa1e0d2a76fef3e4ea7212a6f2885cec1af5253571"
                        + "1e2e94496224cae3eba8dc992144ade321540ebd458ec5b9e6a4cc51170e018");
        assertEquals(checksums[5].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[5].getValue()),
                "1eec9e86e322b8d7e48e255fc3f2df2dbc91036e63982ff9850597c6a37bbeb3");
        assertEquals(checksums[6].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA512);
        assertEquals(bytesToHexString(checksums[6].getValue()),
                "ef80a8630283f60108e8557c924307d0ccdfb6bbbf2c0176bd49af342f43bc84"
                        + "5f2888afcb71524196dda0d6dd16a6a3292bb75b431b8ff74fb60d796e882f80");
    }

    @Test
    public void testFixedV1AllChecksums() throws Exception {
        installPackage(TEST_FIXED_APK_V1);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, ALL_CHECKSUMS, TRUST_NONE,
                receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 5);
        assertEquals(checksums[0].getKind(), WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "1e8f831ef35257ca30d11668520aaafc6da243e853531caabc3b7867986f8886");
        assertEquals(checksums[1].getKind(), WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[1].getValue()), "78e51e8c51e4adc6870cd71389e0f3db");
        assertEquals(checksums[2].getKind(), WHOLE_SHA1);
        assertEquals(bytesToHexString(checksums[2].getValue()),
                "f6654505f2274fd9bfc098b660cdfdc2e4da6d53");
        assertEquals(checksums[3].getKind(), WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[3].getValue()),
                "43755d36ec944494f6275ee92662aca95079b3aa6639f2d35208c5af15adff78");
        assertEquals(checksums[4].getKind(), WHOLE_SHA512);
        assertEquals(bytesToHexString(checksums[4].getValue()),
                "030fc815a4957c163af2bc6f30dd5b48ac09c94c25a824a514609e1476f91421"
                        + "e2c8b6baa16ef54014ad6c5b90c37b26b0f5c8aeb01b63a1db2eca133091c8d1");
    }

    @Test
    public void testDefaultIncrementalChecksums() throws Exception {
        if (!checkIncrementalDeliveryFeature()) {
            return;
        }
        installPackageIncrementally(TEST_V4_APK);
        assertTrue(isAppInstalled(V4_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(V4_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1);
        assertEquals(checksums[0].getKind(), WHOLE_MERKLE_ROOT_4K_SHA256);
    }

    @Test
    public void testFixedDefaultIncrementalChecksums() throws Exception {
        if (!checkIncrementalDeliveryFeature()) {
            return;
        }
        installPackageIncrementally(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, 0, TRUST_NONE, receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 1);
        assertEquals(checksums[0].getKind(), WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "90553b8d221ab1b900b242a93e4cc659ace3a2ff1d5c62e502488b385854e66a");
    }

    @Test
    public void testFixedAllIncrementalChecksums() throws Exception {
        if (!checkIncrementalDeliveryFeature()) {
            return;
        }
        installPackageIncrementally(TEST_FIXED_APK);
        assertTrue(isAppInstalled(FIXED_PACKAGE_NAME));

        LocalIntentReceiver receiver = new LocalIntentReceiver();
        PackageManager pm = getPackageManager();
        pm.getChecksums(FIXED_PACKAGE_NAME, true, ALL_CHECKSUMS, TRUST_NONE,
                receiver.getIntentSender());
        FileChecksum[] checksums = receiver.getResult();
        assertNotNull(checksums);
        assertEquals(checksums.length, 7);
        assertEquals(checksums[0].getKind(), WHOLE_MERKLE_ROOT_4K_SHA256);
        assertEquals(bytesToHexString(checksums[0].getValue()),
                "90553b8d221ab1b900b242a93e4cc659ace3a2ff1d5c62e502488b385854e66a");
        assertEquals(checksums[1].getKind(), WHOLE_MD5);
        assertEquals(bytesToHexString(checksums[1].getValue()), "c19868da017dc01467169f8ea7c5bc57");
        assertEquals(checksums[2].getKind(), WHOLE_SHA1);
        assertEquals(bytesToHexString(checksums[2].getValue()),
                "331eef6bc57671de28cbd7e32089d047285ade6a");
        assertEquals(checksums[3].getKind(), WHOLE_SHA256);
        assertEquals(bytesToHexString(checksums[3].getValue()),
                "91aa30c1ce8d0474052f71cb8210691d41f534989c5521e27e794ec4f754c5ef");
        assertEquals(checksums[4].getKind(), WHOLE_SHA512);
        assertEquals(bytesToHexString(checksums[4].getValue()),
                "b59467fe578ebc81974ab3aaa1e0d2a76fef3e4ea7212a6f2885cec1af5253571"
                        + "1e2e94496224cae3eba8dc992144ade321540ebd458ec5b9e6a4cc51170e018");
        assertEquals(checksums[5].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA256);
        assertEquals(bytesToHexString(checksums[5].getValue()),
                "1eec9e86e322b8d7e48e255fc3f2df2dbc91036e63982ff9850597c6a37bbeb3");
        assertEquals(checksums[6].getKind(), PARTIAL_MERKLE_ROOT_1M_SHA512);
        assertEquals(bytesToHexString(checksums[6].getValue()),
                "ef80a8630283f60108e8557c924307d0ccdfb6bbbf2c0176bd49af342f43bc84"
                        + "5f2888afcb71524196dda0d6dd16a6a3292bb75b431b8ff74fb60d796e882f80");
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, -1);
        return result.toString("UTF-8");
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            Assert.assertEquals(expected, total);
        }
    }

    private static String executeShellCommand(String command) throws IOException {
        final ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(command);
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    private void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        Assert.assertEquals("Success\n", executeShellCommand(
                "pm install -t -g " + file.getPath()));
    }

    private void installPackageIncrementally(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        Assert.assertEquals("Success\n", executeShellCommand(
                "pm install-incremental -t -g " + file.getPath()));
    }

    private void installSplits(String[] baseNames) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        Assert.assertEquals("Success\n",
                executeShellCommand("pm install -t -g " + String.join(" ", splits)));
    }

    private void installSplitsIncrementally(String[] baseNames) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        Assert.assertEquals("Success\n",
                executeShellCommand("pm install-incremental -t -g " + String.join(" ", splits)));
    }

    private String uninstallPackageSilently(String packageName) throws IOException {
        return executeShellCommand("pm uninstall " + packageName);
    }

    private boolean isAppInstalled(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm list packages");
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.substring(prefixLength).equals(packageName));
    }

    @Nonnull
    private static String bytesToHexString(byte[] bytes) {
        return HexDump.toHexString(bytes, 0, bytes.length, /*upperCase=*/ false);
    }


    private boolean checkIncrementalDeliveryFeature() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_INCREMENTAL_DELIVERY);
    }

    private static class LocalIntentReceiver {
        private final LinkedBlockingQueue<FileChecksum[]> mResult = new LinkedBlockingQueue<>();

        private IIntentSender.Stub mLocalSender = new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType, IBinder allowlistToken,
                    IIntentReceiver finishedReceiver, String requiredPermission,
                    Bundle options) {
                Parcelable[] parcelables = intent.getParcelableArrayExtra(EXTRA_CHECKSUMS);
                assertNotNull(parcelables);
                FileChecksum[] checksums = Arrays.copyOf(parcelables, parcelables.length,
                        FileChecksum[].class);
                Arrays.sort(checksums, (FileChecksum lhs, FileChecksum rhs) ->  {
                    final String lhsSplit = lhs.getSplitName();
                    final String rhsSplit = rhs.getSplitName();
                    if (Objects.equals(lhsSplit, rhsSplit)) {
                        return Integer.signum(lhs.getKind() - rhs.getKind());
                    }
                    if (lhsSplit == null) {
                        return -1;
                    }
                    if (rhsSplit == null) {
                        return +1;
                    }
                    return lhsSplit.compareTo(rhsSplit);
                });
                mResult.offer(checksums);
            }
        };

        public IntentSender getIntentSender() {
            return new IntentSender((IIntentSender) mLocalSender);
        }

        public FileChecksum[] getResult() {
            try {
                return mResult.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
