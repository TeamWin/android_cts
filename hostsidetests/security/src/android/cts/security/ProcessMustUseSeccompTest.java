/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

public class ProcessMustUseSeccompTest extends DeviceTestCase {
   /**
    * a reference to the device under test.
    */
    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }

    /*
     * get the PID of process "Name" using "ps" command. If prefix == True
     * only check that the name starts with "Name". This is useful for HALs
     * which are versioned e.g. android.hardware.configstore@1.1-service.
     * If prefix == False then name must be an exact match.
     */
    private String getPidByName(String Name, boolean prefix) throws DeviceNotAvailableException {
        String ret = "";
        CollectingOutputReceiver Out = new CollectingOutputReceiver();
        mDevice.executeShellCommand("toybox ps -A -o name,pid", Out);
        String[] ps = Out.getOutput().split(System.getProperty("line.separator"));

        for (String process: ps) {
            String[] namePid = process.trim().split("\\s+");
            if (!prefix && !namePid[0].equals(Name)) {
                continue;
            }
            if (prefix && !namePid[0].startsWith(Name)) {
                continue;
            }
            ret = namePid[1];
            break;
        }

        if (!java.util.regex.Pattern.matches("\\d+", ret)) {
            ret = "";
        }

        return ret;
    }

    /*
     * Return true if the "Seccomp" field of /proc/<pid>/status is "2" which
     * indicates that seccomp is running in filter mode
     */
    private boolean pidHasSeccompBpf(String Pid) throws DeviceNotAvailableException {
        CollectingOutputReceiver Out = new CollectingOutputReceiver();
        mDevice.executeShellCommand("toybox cat /proc/" + Pid + "/status", Out);
        String[] lines = Out.getOutput().split(System.getProperty("line.separator"));
        for (String line: lines) {
            String[] split = line.trim().split("\\s+");
            if (!split[0].equals("Seccomp:")) {
                continue;
            }
            if (split[1].equals("2")) {
                return true;
            }
            break;
        }
        return false;
    }

    public void assertSeccompFilter(String Name, boolean prefix) throws DeviceNotAvailableException {
        String Pid = getPidByName(Name, prefix);
        assertFalse(Name + " process not found.", Pid.equals(""));
        assertTrue(Name + " must have a seccomp filter enabled.\n"
                   + "The \"Seccomp\" field of " + Name + "'s "
                   + "/proc/<pid>/status file should be set to \"2\"",
                   pidHasSeccompBpf(Pid));
    }

    public void testConfigStoreHasSeccompFilter() throws DeviceNotAvailableException {
        assertSeccompFilter("android.hardware.configstore", true);
    }

    public void testMediaextractorHasSeccompFilter() throws DeviceNotAvailableException {
        assertSeccompFilter("media.extractor", false);
    }
}
