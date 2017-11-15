/**
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;
import java.util.concurrent.TimeUnit;

@SecurityTest
public class Poc17_02 extends SecurityTestCase {
    /**
     *  b/31796345
     */
    @SecurityTest
    public void testPocCVE_2017_0451() throws Exception {
	enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/voice_svc")) {
            AdbUtils.runPoc("CVE-2017-0451", getDevice(), 60);
        }
    }
    /**
     *  b/31906415
     */
    @SecurityTest
    public void testPocCVE_2016_8481() throws Exception {
        if(containsDriver(getDevice(), "/dev/usf1")) {
            enableAdbRoot(getDevice());
            AdbUtils.runPoc("CVE-2016-8481", getDevice(), 60);
          // CTS begins the next test before device finishes rebooting,
          // sleep to allow time for device to reboot
            TimeUnit.SECONDS.sleep(40);
        }
    }
    /**
     *  b/32624661
     */
    @SecurityTest
    public void testPocCVE_2017_0436() throws Exception {
        if(containsDriver(getDevice(), "/dev/usf1")) {
            enableAdbRoot(getDevice());
            AdbUtils.runPoc("CVE-2017-0436", getDevice(), 60);
        }
    }
    /**
     *  b/32769717
     */
    @SecurityTest
    public void testPocCVE_2017_0445() throws Exception {
        if(containsDriver(getDevice(), "/dev/touch_fwu")) {
            enableAdbRoot(getDevice());
            AdbUtils.runPoc("CVE-2017-0445", getDevice(), 60);
        }
    }
    /**
     *  b/32402310
     */
    @SecurityTest
    public void testPocCVE_2017_0437() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2017-0437", getDevice(), 60);
    }
   /**
     *  b/32402604
     */
    @SecurityTest
    public void testPocCVE_2017_0438() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2017-0438", getDevice(), 60);
    }
   /**
     *  b/32872662
     */
    @SecurityTest
    public void testPocCVE_2017_0441() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2017-0441", getDevice(), 60);
    }
   /**
     *  b/32879283
     */
    @SecurityTest
    public void testPocCVE_2016_8476() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2016-8476", getDevice(), 60);
    }
   /**
     *  b/32451171
     */
    @SecurityTest
    public void testPocCVE_2016_8420() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2016-8420", getDevice(), 60);
        //Device restarts up to 50s after POC finishes running
        TimeUnit.SECONDS.sleep(50);
    }
 }
