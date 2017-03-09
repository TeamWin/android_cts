/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.backup.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.tradefed.testtype.CompatibilityHostTestBase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

@RunWith(DeviceJUnit4ClassRunner.class)
public class WallpaperRestoreHostSideTest extends CompatibilityHostTestBase {

    // Value of PackageManager.FEATURE_BACKUP
    private static final String FEATURE_BACKUP = "android.software.backup";

    private static final String DISMISS_KEYGUARD_COMMAND = "wm dismiss-keyguard";

    @Before
    public void skipTestUnlessBackupSupported() throws Exception {
        assumeTrue(supportsBackup());
        getDevice().executeShellCommand(DISMISS_KEYGUARD_COMMAND);
    }

    @Test
    public void testRestoreSameImageToBoth() throws Exception {
        restoreBackup("wallpaper_green.ab");
        checkDeviceTest("assertBothWallpapersAreGreen");
    }

    @Test
    public void testRestoreDifferentImageToEach() throws Exception {
        restoreBackup("wallpaper_red_green.ab");
        checkDeviceTest("assertSystemIsRedAndLockIsGreen");
    }

    @Test
    public void testRestoreLiveWallpaper() throws Exception {
        restoreBackup("wallpaper_live.ab");
        checkDeviceTest("assertBothWallpapersAreLive");
    }

    @Test
    public void testRestoreLiveWallpaperAndImageLock() throws Exception {
        restoreBackup("wallpaper_live_green.ab");
        checkDeviceTest("assertSystemIsLiveAndLockIsGreen");
    }

    private boolean supportsBackup() throws Exception {
        return getDevice().hasFeature("feature:" + FEATURE_BACKUP);
    }

    private void restoreBackup(final String filename) throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);
        Thread restore =
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            device.executeAdbCommand("restore", createTestFile(filename));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
        restore.start();
        checkDeviceTest("clickBackupConfirmButton");
        restore.join();
    }

    private void checkDeviceTest(String testName) throws DeviceNotAvailableException {
        boolean result =
                runDeviceTests(
                        "android.backup.cts.app2",
                        "android.backup.cts.app2.WallpaperRestoreTest",
                        testName);
        assertTrue("Device test failed: " + testName, result);
    }

    private String createTestFile(String filename) throws IOException {
        File tempFile = File.createTempFile(WallpaperRestoreHostSideTest.class.getName(), "tmp");
        tempFile.deleteOnExit();
        try (InputStream input = openResourceAsStream(filename);
                OutputStream output = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
        return tempFile.getAbsolutePath();
    }

    private InputStream openResourceAsStream(String filename) {
        InputStream input = getClass().getResourceAsStream(File.separator + filename);
        assertNotNull(input);
        return input;
    }
}
