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

package android.compilation.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;

import com.google.common.io.ByteStreams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various integration tests for dex to oat compilation, with or without profiles.
 * When changing this test, make sure it still passes in each of the following
 * configurations:
 * <ul>
 *     <li>On a 'user' build</li>
 *     <li>On a 'userdebug' build with system property 'dalvik.vm.usejitprofiles' set to false</li>
 *     <li>On a 'userdebug' build with system property 'dalvik.vm.usejitprofiles' set to true</li>
 * </ul>
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AdbRootDependentCompilationTest extends BaseHostJUnit4Test {
    private static final int ADB_ROOT_RETRY_ATTEMPTS = 3;
    private static final String TEMP_DIR = "/data/local/tmp/AdbRootDependentCompilationTest";
    private static final String APPLICATION_PACKAGE = "android.compilation.cts";

    enum ProfileLocation {
        CUR("/data/misc/profiles/cur/0/"),
        REF("/data/misc/profiles/ref/");

        private String directory;

        ProfileLocation(String directory) {
            this.directory = directory;
        }

        public String getDirectory(String packageName) {
            return directory + packageName;
        }

        public String getPath(String packageName) {
            return directory + packageName + "/primary.prof";
        }
    }

    private ITestDevice mDevice;
    private File mCtsCompilationAppApkFile;
    private boolean mWasAdbRoot = false;
    private boolean mAdbRootEnabled = false;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();

        mWasAdbRoot = mDevice.isAdbRoot();
        mAdbRootEnabled = mWasAdbRoot || enableAdbRoot();

        assumeTrue("The device does not allow root access", mAdbRootEnabled);

        mCtsCompilationAppApkFile = copyResourceToFile(
                "/CtsCompilationApp.apk", File.createTempFile("CtsCompilationApp", ".apk"));
        mDevice.uninstallPackage(APPLICATION_PACKAGE); // in case it's still installed
        String error = mDevice.installPackage(mCtsCompilationAppApkFile, false);
        assertNull("Got install error: " + error, error);

        mDevice.executeShellV2Command("rm -rf " + TEMP_DIR);  // Make sure we have a clean state.
        assertCommandSucceeds("mkdir", "-p", TEMP_DIR);
    }

    @After
    public void tearDown() throws Exception {
        mDevice.executeShellV2Command("rm -rf " + TEMP_DIR);

        FileUtil.deleteFile(mCtsCompilationAppApkFile);
        mDevice.uninstallPackage(APPLICATION_PACKAGE);

        if (!mWasAdbRoot && mAdbRootEnabled) {
            mDevice.disableAdbRoot();
        }
    }

    /**
     * Tests compilation using {@code -r bg-dexopt -f}.
     */
    @Test
    public void testCompile_bgDexopt() throws Exception {
        resetProfileState(APPLICATION_PACKAGE);

        // Copy the profile to the reference location so that the bg-dexopt
        // can actually do work if it's configured to speed-profile.
        for (ProfileLocation profileLocation : EnumSet.of(ProfileLocation.REF)) {
            writeSystemManagedProfile("/primary.prof.txt", profileLocation, APPLICATION_PACKAGE);
        }

        // Usually "speed-profile"
        String expectedInstallFilter =
                Objects.requireNonNull(mDevice.getProperty("pm.dexopt.install"));
        if (expectedInstallFilter.equals("speed-profile")) {
            // If the filter is speed-profile but no profile is present, the compiler
            // will change it to verify.
            expectedInstallFilter = "verify";
        }
        // Usually "speed-profile"
        String expectedBgDexoptFilter =
                Objects.requireNonNull(mDevice.getProperty("pm.dexopt.bg-dexopt"));

        String odexPath = getOdexFilePath(APPLICATION_PACKAGE);
        assertEquals(expectedInstallFilter, getCompilerFilter(odexPath));

        // Without -f, the compiler would only run if it judged the bg-dexopt filter to
        // be "better" than the install filter. However manufacturers can change those
        // values so we don't want to depend here on the resulting filter being better.
        executeCompile(APPLICATION_PACKAGE, "-r", "bg-dexopt", "-f");

        assertEquals(expectedBgDexoptFilter, getCompilerFilter(odexPath));
    }

    /*
     The tests below test the remaining combinations of the "ref" (reference) and
     "cur" (current) profile being available. The "cur" profile gets moved/merged
     into the "ref" profile when it differs enough; as of 2016-05-10, "differs
     enough" is based on number of methods and classes in profile_assistant.cc.

     No nonempty profile exists right after an app is installed.
     Once the app runs, a profile will get collected in "cur" first but
     may make it to "ref" later. While the profile is being processed by
     profile_assistant, it may only be available in "ref".
     */

    @Test
    public void testCompile_noProfile() throws Exception {
        compileWithProfilesAndCheckFilter(false /* expectOdexChange */,
                EnumSet.noneOf(ProfileLocation.class));
    }

    @Test
    public void testCompile_curProfile() throws Exception {
        compileWithProfilesAndCheckFilter(true  /* expectOdexChange */,
                EnumSet.of(ProfileLocation.CUR));
        assertTrue("ref profile should have been created by the compiler",
                mDevice.doesFileExist(ProfileLocation.REF.getPath(APPLICATION_PACKAGE)));
    }

    @Test
    public void testCompile_refProfile() throws Exception {
        compileWithProfilesAndCheckFilter(true /* expectOdexChange */,
                 EnumSet.of(ProfileLocation.REF));
        // expect a change in odex because the of the change form
        // verify -> speed-profile
    }

    @Test
    public void testCompile_curAndRefProfile() throws Exception {
        compileWithProfilesAndCheckFilter(true /* expectOdexChange */,
                EnumSet.of(ProfileLocation.CUR, ProfileLocation.REF));
        // expect a change in odex because the of the change form
        // verify -> speed-profile
    }

    /**
     * Places the profile in the specified locations, recompiles (without -f)
     * and checks the compiler-filter in the odex file.
     */
    private void compileWithProfilesAndCheckFilter(boolean expectOdexChange,
            Set<ProfileLocation> profileLocations) throws Exception {
        if (!profileLocations.isEmpty()) {
            checkProfileSupport();
        }

        resetProfileState(APPLICATION_PACKAGE);

        executeCompile(APPLICATION_PACKAGE, "-m", "speed-profile", "-f");
        String odexFilePath = getOdexFilePath(APPLICATION_PACKAGE);
        String initialOdexFileContents = mDevice.pullFileContents(odexFilePath);
        // validity check
        assertWithMessage("empty odex file").that(initialOdexFileContents.length())
                .isGreaterThan(0);

        for (ProfileLocation profileLocation : profileLocations) {
            writeSystemManagedProfile("/primary.prof.txt", profileLocation, APPLICATION_PACKAGE);
        }
        executeCompile(APPLICATION_PACKAGE, "-m", "speed-profile");

        // Confirm the compiler-filter used in creating the odex file
        String compilerFilter = getCompilerFilter(odexFilePath);

        // Without profiles, the compiler filter should be verify.
        String expectedCompilerFilter = profileLocations.isEmpty() ? "verify" : "speed-profile";
        assertEquals("compiler-filter", expectedCompilerFilter, compilerFilter);

        String odexFileContents = mDevice.pullFileContents(odexFilePath);
        boolean odexChanged = !initialOdexFileContents.equals(odexFileContents);
        if (odexChanged && !expectOdexChange) {
            String msg = String.format(Locale.US, "Odex file without filters (%d bytes) "
                    + "unexpectedly different from odex file (%d bytes) compiled with filters: %s",
                    initialOdexFileContents.length(), odexFileContents.length(), profileLocations);
            fail(msg);
        } else if (!odexChanged && expectOdexChange) {
            fail("odex file should have changed when recompiling with " + profileLocations);
        }
    }

    private void resetProfileState(String packageName) throws Exception {
        mDevice.executeShellV2Command("rm -f " + ProfileLocation.REF.getPath(packageName));
        mDevice.executeShellV2Command("truncate -s 0 " + ProfileLocation.CUR.getPath(packageName));
    }

    /**
     * Invokes the dex2oat compiler on the client.
     *
     * @param compileOptions extra options to pass to the compiler on the command line
     */
    private void executeCompile(String packageName, String... compileOptions) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList("cmd", "package", "compile"));
        command.addAll(Arrays.asList(compileOptions));
        command.add(packageName);
        String[] commandArray = command.toArray(new String[0]);
        assertCommandSucceeds(commandArray);
    }

    /**
     * Writes the given profile in binary format in a system-managed directory on the device, and
     * sets appropriate owner.
     */
    private void writeSystemManagedProfile(String profileResourceName, ProfileLocation location,
            String packageName) throws Exception {
        String targetPath = location.getPath(packageName);
        // Get the owner of the parent directory so we can set it on the file
        String targetDir = location.getDirectory(packageName);
        assertTrue("Directory " + targetDir + " not found", mDevice.doesFileExist(targetDir));
        // In format group:user so we can directly pass it to chown.
        String owner = assertCommandOutputsLines(1, "stat", "-c", "%U:%g", targetDir)[0];

        String dexLocation = assertCommandOutputsLines(1, "pm", "path", packageName)[0];
        dexLocation = dexLocation.replace("package:", "");
        assertTrue("Failed to find APK " + dexLocation, mDevice.doesFileExist(dexLocation));

        writeProfile(profileResourceName, dexLocation, targetPath);

        // Verify that the file was written successfully.
        assertTrue("Failed to create profile file", mDevice.doesFileExist(targetPath));
        String result = assertCommandOutputsLines(1, "stat", "-c", "%s", targetPath)[0];
        assertWithMessage("profile " + targetPath + " is " + Integer.parseInt(result) + " bytes")
                .that(Integer.parseInt(result)).isGreaterThan(0);

        assertCommandSucceeds("chown", owner, targetPath);
    }

    /**
     * Writes the given profile in binary format on the device.
     */
    private void writeProfile(String profileResourceName, String dexLocation, String pathOnDevice)
            throws Exception {
        File textProfileFile = File.createTempFile("primary", ".prof.txt");
        String textProfileFileOnDevice = TEMP_DIR + "/primary.prof.txt";

        try {
            copyResourceToFile(profileResourceName, textProfileFile);
            assertTrue(mDevice.pushFile(textProfileFile, textProfileFileOnDevice));

            assertCommandSucceeds(
                    "profman",
                    "--create-profile-from=" + textProfileFileOnDevice,
                    "--apk=" + dexLocation,
                    "--dex-location=" + dexLocation,
                    "--reference-profile-file=" + pathOnDevice);
        } finally {
            mDevice.executeShellV2Command("rm " + textProfileFileOnDevice);
            FileUtil.deleteFile(textProfileFile);
        }
    }

    /**
     * Parses the value for the key "compiler-filter" out of the output from
     * {@code oatdump --header-only}.
     */
    private String getCompilerFilter(String odexFilePath) throws DeviceNotAvailableException {
        String[] response = assertCommandSucceeds(
                "oatdump", "--header-only", "--oat-file=" + odexFilePath).split("\n");
        String prefix = "compiler-filter =";
        for (String line : response) {
            line = line.trim();
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        fail("No occurence of \"" + prefix + "\" in: " + Arrays.toString(response));
        return null;
    }

    /**
     * Returns the path to the application's base.odex file that should have
     * been created by the compiler.
     */
    private String getOdexFilePath(String packageName) throws DeviceNotAvailableException {
        // Something like "package:/data/app/android.compilation.cts-1/base.apk"
        String pathSpec = assertCommandOutputsLines(1, "pm", "path", packageName)[0];
        Matcher matcher = Pattern.compile("^package:(.+/)base\\.apk$").matcher(pathSpec);
        boolean found = matcher.find();
        assertTrue("Malformed spec: " + pathSpec, found);
        String apkDir = matcher.group(1);
        // E.g. /data/app/android.compilation.cts-1/oat/arm64/base.odex
        String result = assertCommandOutputsLines(1, "find", apkDir, "-name", "base.odex")[0];
        assertTrue("odex file not found: " + result, mDevice.doesFileExist(result));
        return result;
    }

    /**
     * Skips the test if it does not use JIT profiles.
     */
    private void checkProfileSupport() throws Exception {
        assumeTrue("The device does not use JIT profiles", isUseJitProfiles());
    }

    private boolean isUseJitProfiles() throws Exception {
        return Boolean.parseBoolean(assertCommandSucceeds("getprop", "dalvik.vm.usejitprofiles"));
    }

    private String[] assertCommandOutputsLines(int numLinesOutputExpected, String... command)
            throws DeviceNotAvailableException {
        String output = assertCommandSucceeds(command);
        // "".split() returns { "" }, but we want an empty array
        String[] lines = output.equals("") ? new String[0] : output.split("\n");
        assertEquals(
                String.format(Locale.US, "Expected %d lines output, got %d running %s: %s",
                        numLinesOutputExpected, lines.length, Arrays.toString(command),
                        Arrays.toString(lines)),
                numLinesOutputExpected, lines.length);
        return lines;
    }

    private String assertCommandSucceeds(String... command) throws DeviceNotAvailableException {
        CommandResult result = mDevice.executeShellV2Command(String.join(" ", command));
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
        // Remove trailing \n's.
        return result.getStdout().trim();
    }

    private File copyResourceToFile(String resourceName, File file) throws Exception {
        try (OutputStream outputStream = new FileOutputStream(file);
                InputStream inputStream = getClass().getResourceAsStream(resourceName)) {
            assertThat(ByteStreams.copy(inputStream, outputStream)).isGreaterThan(0);
        }
        return file;
    }

    /**
     * Turns on adb root. Returns true if successful.
     *
     * This is a workaround to run the test as root in CTS on userdebug/eng builds. We have to keep
     * this test in CTS because it's the only integration test we have to verify platform's dexopt
     * behavior. We cannot use `mDevice.enableAdbRoot()` because it does not allow enabling root in
     * CTS, even on userdebug/eng builds.
     *
     * The implementation below is copied from {@link NativeDevice#enableAdbRoot()}.
     */
    private boolean enableAdbRoot() throws DeviceNotAvailableException {
        // adb root is a relatively intensive command, so do a brief check first to see
        // if its necessary or not
        if (mDevice.isAdbRoot()) {
            CLog.i("adb is already running as root for AdbRootDependentCompilationTest on %s",
                    mDevice.getSerialNumber());
            // Still check for online, in some case we could see the root, but device could be
            // very early in its cycle.
            mDevice.waitForDeviceOnline();
            return true;
        }
        CLog.i("adb root for AdbRootDependentCompilationTest on device %s",
                mDevice.getSerialNumber());
        int attempts = ADB_ROOT_RETRY_ATTEMPTS;
        for (int i = 1; i <= attempts; i++) {
            String output = mDevice.executeAdbCommand("root");
            // wait for device to disappear from adb
            boolean res = mDevice.waitForDeviceNotAvailable(2 * 1000);
            if (!res && TestDeviceState.ONLINE.equals(mDevice.getDeviceState())) {
                if (mDevice.isAdbRoot()) {
                    return true;
                }
            }

            if (mDevice instanceof NativeDevice) {
                ((NativeDevice) mDevice).postAdbRootAction();
            }

            // wait for device to be back online
            mDevice.waitForDeviceOnline();

            if (mDevice.isAdbRoot()) {
                return true;
            }
            CLog.w("'adb root' for AdbRootDependentCompilationTest on %s unsuccessful on attempt "
                            + "%d of %d. Output: '%s'",
                    mDevice.getSerialNumber(), i, attempts, output);
        }
        return false;
    }
}
