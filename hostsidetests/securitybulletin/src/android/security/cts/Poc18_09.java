package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc18_09 extends SecurityTestCase {

  /**
   * CVE-2018-11261
   */
  @SecurityTest(minPatchLevel = "2018-09")
  public void testPocCVE_2018_11261() throws Exception {
    AdbUtils.runPocAssertNoCrashes("CVE-2018-11261", getDevice(), "mediaserver");
  }
}
