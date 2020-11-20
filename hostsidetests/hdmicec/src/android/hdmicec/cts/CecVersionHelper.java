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

package android.hdmicec.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/** Helper class to set the CEC version. */
public final class CecVersionHelper {

    public static void setCecVersion(ITestDevice device, int cecVersion) throws Exception {
        device.executeShellCommand("settings put global hdmi_cec_version " + cecVersion);

        TimeUnit.SECONDS.sleep(HdmiCecConstants.TIMEOUT_CEC_REINIT_SECONDS);
    }

    public static void setCec20(ITestDevice device) throws Exception {
        setCecVersion(device, HdmiCecConstants.CEC_VERSION_2_0);
    }

    public static void setCec14(ITestDevice device) throws Exception {
        setCecVersion(device, HdmiCecConstants.CEC_VERSION_1_4);
    }
}
