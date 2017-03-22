package android.cts.security;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FileSystemPermissionTest extends DeviceTestCase {

   /**
    * A reference to the device under test.
    */
    private ITestDevice mDevice;

    /**
     * Used to build the find command for finding insecure file system components
     */
    private static final String INSECURE_DEVICE_ADB_COMMAND = "find %s -type %s -perm /o=rwx 2>/dev/null";

    /**
     * Whitelist exceptions of allowed world accessbale char files under /dev
     */
    private static final Set<String> CHAR_DEV_EXCEPTIONS = new HashSet<String>(
        Arrays.asList(
            // All exceptions should be alphabetical and associated with a bug number.
            "/dev/adsprpc-smd", // b/11710243
            "/dev/alarm",      // b/9035217
            "/dev/ashmem",
            "/dev/binder",
            "/dev/card0",       // b/13159510
            "/dev/renderD128",
            "/dev/renderD129",  // b/23798677
            "/dev/dri/card0",   // b/13159510
            "/dev/dri/renderD128",
            "/dev/dri/renderD129", // b/23798677
            "/dev/felica",     // b/11142586
            "/dev/felica_ant", // b/11142586
            "/dev/felica_cen", // b/11142586
            "/dev/felica_pon", // b/11142586
            "/dev/felica_rfs", // b/11142586
            "/dev/felica_rws", // b/11142586
            "/dev/felica_uicc", // b/11142586
            "/dev/full",
            "/dev/galcore",
            "/dev/genlock",    // b/9035217
            "/dev/graphics/galcore",
            "/dev/hwbinder",   // b/30886151
            "/dev/ion",
            "/dev/kgsl-2d0",   // b/11271533
            "/dev/kgsl-2d1",   // b/11271533
            "/dev/kgsl-3d0",   // b/9035217
            "/dev/log/events", // b/9035217
            "/dev/log/main",   // b/9035217
            "/dev/log/radio",  // b/9035217
            "/dev/log/system", // b/9035217
            "/dev/mali0",       // b/9106968
            "/dev/mali",        // b/11142586
            "/dev/mm_interlock", // b/12955573
            "/dev/mm_isp",      // b/12955573
            "/dev/mm_v3d",      // b/12955573
            "/dev/msm_rotator", // b/9035217
            "/dev/null",
            "/dev/nvhost-as-gpu",
            "/dev/nvhost-ctrl", // b/9088251
            "/dev/nvhost-ctrl-gpu",
            "/dev/nvhost-dbg-gpu",
            "/dev/nvhost-gpu",
            "/dev/nvhost-gr2d", // b/9088251
            "/dev/nvhost-gr3d", // b/9088251
            "/dev/nvhost-tsec",
            "/dev/nvhost-prof-gpu",
            "/dev/nvhost-vic",
            "/dev/nvmap",       // b/9088251
            "/dev/pmsg0",       // b/31857082
            "/dev/ptmx",        // b/9088251
            "/dev/pvrsrvkm",    // b/9108170
            "/dev/pvr_sync",
            "/dev/quadd",
            "/dev/random",
            "/dev/snfc_cen",    // b/11142586
            "/dev/snfc_hsel",   // b/11142586
            "/dev/snfc_intu_poll", // b/11142586
            "/dev/snfc_rfs",    // b/11142586
            "/dev/tegra-throughput",
            "/dev/tiler",       // b/9108170
            "/dev/tty",
            "/dev/urandom",
            "/dev/ump",         // b/11142586
            "/dev/xt_qtaguid",  // b/9088251
            "/dev/zero",
            "/dev/fimg2d",      // b/10428016
            "/dev/mobicore-user" // b/10428016
    ));

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }

    public void testAllCharacterDevicesAreSecure() throws DeviceNotAvailableException {
        Set <String> insecure = getAllInsecureDevicesInDirAndSubdir("/dev", "c");
        Set <String> insecurePts = getAllInsecureDevicesInDirAndSubdir("/dev/pts", "c");
        insecure.removeAll(CHAR_DEV_EXCEPTIONS);
        insecure.removeAll(insecurePts);
        assertTrue("Found insecure character devices: " + insecure.toString(),
                insecure.isEmpty());
    }

    public void testAllBlockDevicesAreSecure() throws Exception {
        Set<String> insecure = getAllInsecureDevicesInDirAndSubdir("/dev", "b");
        assertTrue("Found insecure block devices: " + insecure.toString(),
                insecure.isEmpty());
    }

    /**
     * Searches for all world accessable files, note this may need sepolicy to search the desired
     * location and stat files.
     * @path The path to search, must be a directory.
     * @type The type of file to search for, must be a valid find command argument to the type
     *       option.
     * @returns The set of insecure fs objects found.
     */
    private Set<String> getAllInsecureDevicesInDirAndSubdir(String path, String type) throws DeviceNotAvailableException {

        String cmd = getInsecureDeviceAdbCommand(path, type);
        String output = mDevice.executeShellCommand(cmd);
        // Splitting an empty string results in an array of an empty string.
        String [] found = output.length() > 0 ? output.split("\\s") : new String[0];
        return new HashSet<String>(Arrays.asList(found));
    }

    private static String getInsecureDeviceAdbCommand(String path, String type) {
        return String.format(INSECURE_DEVICE_ADB_COMMAND, path, type);
    }
}
