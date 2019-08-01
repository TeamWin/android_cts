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

package android.apex.cts;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandResult;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class ApexTest extends BaseHostJUnit4Test {

  /**
   * Ensures that the built-in APEXes are all with flattened APEXes
   * or non-flattend APEXes. Mixture of them is not supported and thus
   * not allowed.
   */
  @Test
  public void testApexType() throws Exception {
    String[] builtinDirs = {
      "/system/apex",
      "/product/apex",
      "/vendor/apex"
    };

    int numFlattenedApexes = 0;
    int numNonFlattenedApexes = 0;
    for (String dir : builtinDirs) {
      numFlattenedApexes += countFlattenedApexes(dir);
      numNonFlattenedApexes += countNonFlattenedApexes(dir);
    }

    Assert.assertTrue(
        "No APEX found",
        (numFlattenedApexes + numNonFlattenedApexes) != 0);

    if (Boolean.parseBoolean(getDevice().getProperty("ro.apex.updatable"))) {
      Assert.assertTrue(numFlattenedApexes +
          " flattened APEX(es) found on a device supporting updatable APEX",
          numFlattenedApexes == 0);
    } else {
      Assert.assertTrue(numNonFlattenedApexes +
          " non-flattened APEX(es) found on a device not supporting updatable APEX",
          numNonFlattenedApexes == 0);
    }
  }

  // CTS shim APEX can be non-flattened - even when ro.apex.updatable=false.
  // Don't count it.
  private final static String CTS_SHIM_APEX_NAME = "com.android.apex.cts.shim";

  private int countFlattenedApexes(String dir) throws Exception {
    CommandResult result = getDevice().executeShellV2Command(
        "find " + dir + " -type f -name \"apex_manifest.json\" ! -path \"*" +
        CTS_SHIM_APEX_NAME + "*\" | wc -l");
    return result.getExitCode() == 0 ? Integer.parseInt(result.getStdout().trim()) : 0;
  }

  private int countNonFlattenedApexes(String dir) throws Exception {
    CommandResult result = getDevice().executeShellV2Command(
        "find " + dir + " -type f -name \"*.apex\" ! -name \"" +
        CTS_SHIM_APEX_NAME + ".apex\" | wc -l");
    return result.getExitCode() == 0 ? Integer.parseInt(result.getStdout().trim()) : 0;
  }
}
