package android.security.cts;

import android.platform.test.annotations.SecurityTest;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
@SecurityTest
public class CVE_2020_11173 extends SecurityTestCase {

    /**
     * CVE-2020-11173
     */
    @SecurityTest(minPatchLevel = "2020-10")
    @Test
    public void testPocCVE_2020_11173() throws Exception {
        if (containsDriver(getDevice(), "/dev/adsprpc-smd")
                && containsDriver(getDevice(), "/dev/ion")) {
            AdbUtils.runPocNoOutput("CVE-2020-11173", getDevice(), 300);
        }
    }
}
