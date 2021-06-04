package android.security.cts;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import android.platform.test.annotations.SecurityTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_11282 extends SecurityTestCase {

    /**
     * CVE-2020-11282
     */
    @Test
    @SecurityTest(minPatchLevel = "2021-02")
    public void testPocCVE_2020_11282() throws Exception {
        assumeTrue(containsDriver(getDevice(), "/dev/kgsl-3d0"));
        AdbUtils.runPocAssertExitStatusNotVulnerable("CVE-2020-11282", getDevice(), 60);
    }
}
