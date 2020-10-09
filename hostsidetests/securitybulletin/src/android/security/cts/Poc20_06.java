package android.security.cts;

import android.platform.test.annotations.SecurityTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Poc20_06 extends SecurityTestCase {

    /**
     * CVE-2020-3676
     */
    @Test
    @SecurityTest(minPatchLevel = "2020-06")
    public void testPocCVE_2020_3676() throws Exception {
        String isApplicable = AdbUtils.runCommandLine("service list", getDevice());
        if (isApplicable.contains("com.qualcomm.qti.IPerfManager")) {
            AdbUtils.runCommandLine("logcat -c", getDevice());
            AdbUtils.runCommandLine(
                    "service call vendor.perfservice 4 i32 2442302356 i64 -2", getDevice());
            AdbUtils.assertNoCrashes(getDevice(), "perfservice");
        }
    }
}
