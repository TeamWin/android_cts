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

package android.hdmicec.cts.common;

import static com.google.common.truth.Truth.assertThat;


import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/** HDMI CEC system information tests (Section 11.2.6) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemInformationTest extends BaseHdmiCecCtsTest {

    /** The version number 0x05 refers to CEC v1.4 */
    private static final int CEC_VERSION_NUMBER = 0x05;

    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(hdmiCecClient);

    /**
     * Test 11.2.6-1
     * Tests for Ack {@code <Polling Message>} message.
     */
    @Test
    public void cect_11_2_6_1_Ack() throws Exception {
        String expectedOutput = "POLL sent";
        hdmiCecClient.sendPoll();
        if (!hdmiCecClient.checkConsoleOutput(expectedOutput)) {
            throw new Exception("Could not find " + expectedOutput);
        }
    }

    /**
     * Tests 11.2.6-2, 10.1.1.1-1
     *
     * <p>Tests that the device sends a {@code <Report Physical Address>} in response to a {@code
     * <Give Physical Address>}
     */
    @Test
    public void cect_11_2_6_2_GivePhysicalAddress() throws Exception {
        List<LogicalAddress> testDevices =
                Arrays.asList(
                        LogicalAddress.TV,
                        LogicalAddress.RECORDER_1,
                        LogicalAddress.TUNER_1,
                        LogicalAddress.PLAYBACK_1,
                        LogicalAddress.AUDIO_SYSTEM,
                        LogicalAddress.BROADCAST);
        for (LogicalAddress testDevice : testDevices) {
            if (hasLogicalAddress(testDevice)) {
                /* Skip the DUT logical address */
                continue;
            }
            hdmiCecClient.sendCecMessage(testDevice, CecOperand.GIVE_PHYSICAL_ADDRESS);
            String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_PHYSICAL_ADDRESS);
            /* Check that the physical address taken is valid. */
            CecMessage.assertPhysicalAddressValid(message, getDumpsysPhysicalAddress());
            int receivedParams = CecMessage.getParams(message);
            assertThat(hasDeviceType(receivedParams & 0xFF)).isTrue();
        }
    }

    /**
     * Test 11.2.6-6
     * Tests that the device sends a {@code <CEC Version>} in response to a {@code <Get CEC
     * Version>}
     */
    @Test
    public void cect_11_2_6_6_GiveCecVersion() throws Exception {
        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
        String message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.CEC_VERSION);
        assertThat(CecMessage.getParams(message)).isEqualTo(CEC_VERSION_NUMBER);
    }
}
