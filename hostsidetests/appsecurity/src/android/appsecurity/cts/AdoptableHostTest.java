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

package android.appsecurity.cts;

import static android.appsecurity.cts.SplitTests.ABI_TO_APK;
import static android.appsecurity.cts.SplitTests.APK;
import static android.appsecurity.cts.SplitTests.APK_mdpi;
import static android.appsecurity.cts.SplitTests.APK_xxhdpi;
import static android.appsecurity.cts.SplitTests.CLASS;
import static android.appsecurity.cts.SplitTests.PKG;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Set of tests that verify behavior of adopted storage media, if supported.
 */
public class AdoptableHostTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    private int[] mUsers;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUsers = Utils.prepareMultipleUsers(getDevice(), Integer.MAX_VALUE);
        assertNotNull(mAbi);
        assertNotNull(mCtsBuild);

        getDevice().uninstallPackage(PKG);
        getDevice().executeShellCommand("sm set-virtual-disk true");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(PKG);
        getDevice().executeShellCommand("sm set-virtual-disk false");
    }

    public void testApps() throws Exception {
        final String diskId = getAdoptionDisk();
        try {
            final String abi = mAbi.getName();
            final String apk = ABI_TO_APK.get(abi);
            assertNotNull("Failed to find APK for ABI " + abi, apk);

            // Install simple app on internal
            new InstallMultiple().useNaturalAbi().addApk(APK).addApk(apk).run();
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataInternal", user);
                runDeviceTests(PKG, CLASS, "testDataWrite", user);
                runDeviceTests(PKG, CLASS, "testDataRead", user);
                runDeviceTests(PKG, CLASS, "testNative", user);
            }

            // Adopt that disk!
            assertEmpty(getDevice().executeShellCommand("sm partition " + diskId + " private"));
            final LocalVolumeInfo vol = getAdoptionVolume();

            // Move app and verify
            assertSuccess(getDevice().executeShellCommand(
                    "pm move-package " + PKG + " " + vol.uuid));
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataNotInternal", user);
                runDeviceTests(PKG, CLASS, "testDataRead", user);
                runDeviceTests(PKG, CLASS, "testNative", user);
            }

            // Unmount, remount and verify
            unmount(vol);
            mount(vol);
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataNotInternal", user);
                runDeviceTests(PKG, CLASS, "testDataRead", user);
                runDeviceTests(PKG, CLASS, "testNative", user);
            }

            // Move app back and verify
            assertSuccess(getDevice().executeShellCommand("pm move-package " + PKG + " internal"));
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataInternal", user);
                runDeviceTests(PKG, CLASS, "testDataRead", user);
                runDeviceTests(PKG, CLASS, "testNative", user);
            }

            // Un-adopt volume and app should still be fine
            getDevice().executeShellCommand("sm partition " + diskId + " public");
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataInternal", user);
                runDeviceTests(PKG, CLASS, "testDataRead", user);
                runDeviceTests(PKG, CLASS, "testNative", user);
            }

        } finally {
            cleanUp(diskId);
        }
    }

    public void testPrimaryStorage() throws Exception {
        final String diskId = getAdoptionDisk();
        try {
            final String originalVol = getDevice()
                    .executeShellCommand("sm get-primary-storage-uuid").trim();

            if ("null".equals(originalVol)) {
                verifyPrimaryInternal(diskId);
            } else if ("primary_physical".equals(originalVol)) {
                verifyPrimaryPhysical(diskId);
            }
        } finally {
            cleanUp(diskId);
        }
    }

    private void verifyPrimaryInternal(String diskId) throws Exception {
        // Write some data to shared storage
        new InstallMultiple().addApk(APK).run();
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryOnSameVolume", user);
            runDeviceTests(PKG, CLASS, "testPrimaryInternal", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataWrite", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        // Adopt that disk!
        assertEmpty(getDevice().executeShellCommand("sm partition " + diskId + " private"));
        final LocalVolumeInfo vol = getAdoptionVolume();

        // Move storage there and verify that data went along for ride
        CollectingOutputReceiver out = new CollectingOutputReceiver();
        getDevice().executeShellCommand("pm move-primary-storage " + vol.uuid, out, 2,
                TimeUnit.HOURS, 1);
        assertSuccess(out.getOutput());
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryAdopted", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        // Unmount and verify
        unmount(vol);
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryUnmounted", user);
        }
        mount(vol);
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryAdopted", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        // Move app and verify backing storage volume is same
        assertSuccess(getDevice().executeShellCommand("pm move-package " + PKG + " " + vol.uuid));
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryOnSameVolume", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        // And move back to internal
        out = new CollectingOutputReceiver();
        getDevice().executeShellCommand("pm move-primary-storage internal", out, 2,
                TimeUnit.HOURS, 1);
        assertSuccess(out.getOutput());

        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryInternal", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        assertSuccess(getDevice().executeShellCommand("pm move-package " + PKG + " internal"));
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryOnSameVolume", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }
    }

    private void verifyPrimaryPhysical(String diskId) throws Exception {
        // Write some data to shared storage
        new InstallMultiple().addApk(APK).run();
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryPhysical", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataWrite", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        // Adopt that disk!
        assertEmpty(getDevice().executeShellCommand("sm partition " + diskId + " private"));
        final LocalVolumeInfo vol = getAdoptionVolume();

        // Move primary storage there, but since we just nuked primary physical
        // the storage device will be empty
        assertSuccess(getDevice().executeShellCommand("pm move-primary-storage " + vol.uuid));
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryAdopted", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataWrite", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        // Unmount and verify
        unmount(vol);
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryUnmounted", user);
        }
        mount(vol);
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryAdopted", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }

        // And move to internal
        assertSuccess(getDevice().executeShellCommand("pm move-primary-storage internal"));
        for (int user : mUsers) {
            runDeviceTests(PKG, CLASS, "testPrimaryOnSameVolume", user);
            runDeviceTests(PKG, CLASS, "testPrimaryInternal", user);
            runDeviceTests(PKG, CLASS, "testPrimaryDataRead", user);
        }
    }

    /**
     * Verify that we can install both new and inherited packages directly on
     * adopted volumes.
     */
    public void testPackageInstaller() throws Exception {
        final String diskId = getAdoptionDisk();
        try {
            assertEmpty(getDevice().executeShellCommand("sm partition " + diskId + " private"));
            final LocalVolumeInfo vol = getAdoptionVolume();

            // Install directly onto adopted volume
            new InstallMultiple().locationAuto().forceUuid(vol.uuid)
                    .addApk(APK).addApk(APK_mdpi).run();
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataNotInternal", user);
                runDeviceTests(PKG, CLASS, "testDensityBest1", user);
            }

            // Now splice in an additional split which offers better resources
            new InstallMultiple().locationAuto().inheritFrom(PKG)
                    .addApk(APK_xxhdpi).run();
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataNotInternal", user);
                runDeviceTests(PKG, CLASS, "testDensityBest2", user);
            }

        } finally {
            cleanUp(diskId);
        }
    }

    /**
     * Verify behavior when changes occur while adopted device is ejected and
     * returned at a later time.
     */
    public void testEjected() throws Exception {
        final String diskId = getAdoptionDisk();
        try {
            assertEmpty(getDevice().executeShellCommand("sm partition " + diskId + " private"));
            final LocalVolumeInfo vol = getAdoptionVolume();

            // Install directly onto adopted volume, and write data there
            new InstallMultiple().locationAuto().forceUuid(vol.uuid).addApk(APK).run();
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataNotInternal", user);
                runDeviceTests(PKG, CLASS, "testDataWrite", user);
                runDeviceTests(PKG, CLASS, "testDataRead", user);
            }

            // Now unmount and uninstall; leaving stale package on adopted volume
            unmount(vol);
            getDevice().uninstallPackage(PKG);

            // Install second copy on internal, but don't write anything
            new InstallMultiple().locationInternalOnly().addApk(APK).run();
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataInternal", user);
            }

            // Kick through a remount cycle, which should purge the adopted app
            mount(vol);
            for (int user : mUsers) {
                runDeviceTests(PKG, CLASS, "testDataInternal", user);
            }
            for (int user : mUsers) {
                boolean threw = false;
                try {
                    runDeviceTests(PKG, CLASS, "testDataRead", user);
                } catch (AssertionError expected) {
                    threw = true;
                }
                if (!threw) {
                    fail("Unexpected data from adopted volume picked up from user " + user);
                }
            }
            unmount(vol);

            // Uninstall the internal copy and remount; we should have no record of app
            getDevice().uninstallPackage(PKG);
            mount(vol);

            assertEmpty(getDevice().executeShellCommand("pm list packages " + PKG));
        } finally {
            cleanUp(diskId);
        }
    }

    private String getAdoptionDisk() throws Exception {
        // In the case where we run multiple test we cleanup the state of the device. This
        // results in the execution of sm forget all which causes the MountService to "reset"
        // all its knowledge about available drives. This can cause the adoptable drive to
        // become temporarily unavailable.
        int attempt = 0;
        String disks = getDevice().executeShellCommand("sm list-disks adoptable");
        while ((disks == null || disks.isEmpty()) && attempt++ < 15) {
            Thread.sleep(1000);
            disks = getDevice().executeShellCommand("sm list-disks adoptable");
        }

        if (disks == null || disks.isEmpty()) {
            throw new AssertionError("Devices that claim to support adoptable storage must have "
                    + "adoptable media inserted during CTS to verify correct behavior");
        }
        return disks.split("\n")[0].trim();
    }

    private LocalVolumeInfo getAdoptionVolume() throws Exception {
        String[] lines = null;
        int attempt = 0;
        while (attempt++ < 15) {
            lines = getDevice().executeShellCommand("sm list-volumes private").split("\n");
            for (String line : lines) {
                final LocalVolumeInfo info = new LocalVolumeInfo(line.trim());
                if (!"private".equals(info.volId) && "mounted".equals(info.state)) {
                    return info;
                }
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Expected private volume; found " + Arrays.toString(lines));
    }

    private void unmount(LocalVolumeInfo vol) throws Exception {
        getDevice().executeShellCommand("sm unmount " + vol.volId);
        for (int i = 0; i < 15; i++) {
            final String raw = getDevice().executeShellCommand("dumpsys package volumes");
            if (raw.contains("Loaded volumes:") && !raw.contains(vol.volId)) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Private volume " + vol.volId + " failed to be unloaded");
    }

    private void mount(LocalVolumeInfo vol) throws Exception {
        getDevice().executeShellCommand("sm mount " + vol.volId);
        for (int i = 0; i < 15; i++) {
            final String raw = getDevice().executeShellCommand("dumpsys package volumes");
            if (raw.contains("Loaded volumes:") && raw.contains(vol.volId)) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Private volume " + vol.volId + " failed to be loaded");
    }

    private void cleanUp(String diskId) throws Exception {
        getDevice().executeShellCommand("sm partition " + diskId + " public");
        getDevice().executeShellCommand("sm forget all");
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName,
            int userId) throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName, userId);
    }

    private static void assertSuccess(String str) {
        if (str == null || !str.startsWith("Success")) {
            throw new AssertionError("Expected success string but found " + str);
        }
    }

    private static void assertEmpty(String str) {
        if (str != null && str.trim().length() > 0) {
            throw new AssertionError("Expected empty string but found " + str);
        }
    }

    private static class LocalVolumeInfo {
        public String volId;
        public String state;
        public String uuid;

        public LocalVolumeInfo(String line) {
            final String[] split = line.split(" ");
            volId = split[0];
            state = split[1];
            uuid = split[2];
        }
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        public InstallMultiple() {
            super(getDevice(), mCtsBuild, mAbi);
        }
    }
}
