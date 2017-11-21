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

@SecurityTest
public class Poc17_01 extends SecurityTestCase {

    /**
     *  b/31799863
     */
    @SecurityTest
    public void testPocCVE_2016_8482() throws Exception {
        if(containsDriver(getDevice(), "/dev/nvmap")) {
            AdbUtils.runPoc("CVE-2016-8482", getDevice(), 60);
        }
    }

   /**
     *  b/32636619
     */
    @SecurityTest
    public void testPocCVE_2017_0429() throws Exception {
        if(containsDriver(getDevice(), "/dev/nvhost-as-gpu")) {
            enableAdbRoot(getDevice());
            AdbUtils.runPoc("CVE-2017-0429", getDevice(), 60);
        }
    }

   /**
     *  b/32219121
     */
    @SecurityTest
    public void testPocCVE_2016_8455() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2016-8455", getDevice(), 60);
    }

   /**
     *  b/32219255
     */
    @SecurityTest
    public void testPocCVE_2016_8456() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2016-8456", getDevice(), 60);
        // CTS begins the next test before device finishes rebooting,
        // sleep to allow time for device to reboot.
        Thread.sleep(60000);
    }

   /**
     *  b/32219453
     */
    @SecurityTest
    public void testPocCVE_2016_8457() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("CVE-2016-8457", getDevice(), 60);
        // Device takes up to 60 seconds to crash after PoC run.
        Thread.sleep(60000);
    }
 }
