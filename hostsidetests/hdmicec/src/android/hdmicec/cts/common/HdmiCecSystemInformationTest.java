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

import static android.hdmicec.cts.HdmiCecConstants.TIMEOUT_SAFETY_MS;

import static com.google.common.truth.Truth.assertThat;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecClientMessage;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.CecVersionHelper;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC system information tests (Section 11.2.6) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecSystemInformationTest extends BaseHdmiCecCtsTest {

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
        String command = CecClientMessage.POLL + " " + mDutLogicalAddress;
        String expectedOutput = "POLL sent";
        hdmiCecClient.sendConsoleMessage(command);
        if (!hdmiCecClient.checkConsoleOutput(expectedOutput)) {
            throw new Exception("Could not find " + expectedOutput);
        }
    }

    /**
     * Test 11.2.6-2
     * Tests that the device sends a {@code <Report Physical Address>} in response to a
     * {@code <Give Physical Address>}
     */
    @Test
    public void cect_11_2_6_2_GivePhysicalAddress() throws Exception {
        hdmiCecClient.sendCecMessage(CecOperand.GIVE_PHYSICAL_ADDRESS);
        String message = hdmiCecClient.checkExpectedOutput(CecOperand.REPORT_PHYSICAL_ADDRESS);
        /* Check that the physical address taken is valid. */
        CecMessage.assertPhysicalAddressValid(message, getDumpsysPhysicalAddress());
        int receivedParams = CecMessage.getParams(message);
        assertThat(receivedParams & 0xFF).isEqualTo(mDutLogicalAddress.getDeviceType());
    }

    /**
     * Test 11.2.6-6
     * Tests that the device sends a {@code <CEC Version>} in response to a {@code <Get CEC
     * Version>}
     */
    @Test
    public void cect_11_2_6_6_GiveCecVersion() throws Exception {
        int cecVersion = HdmiCecConstants.CEC_VERSION_1_4;
        CecVersionHelper.setCecVersion(getDevice(), cecVersion);

        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
        String message =
                hdmiCecClient.checkExpectedOutput(
                        hdmiCecClient.getSelfDevice(), CecOperand.CEC_VERSION);
        assertThat(CecMessage.getParams(message)).isEqualTo(cecVersion);
    }

    /**
     * Test HF4-2-12
     * Tests that the device sends a {@code <CEC Version>} with correct version argument in
     * response to a {@code <Get CEC Version>} message.
     *
     * Also verifies that the CEC version reported in {@code <Report Features>} matches the CEC
     * version reported in {@code <CEC Version>}.
     */
    @Test
    public void cect_hf4_2_12_GiveCecVersion() throws Exception {
        int cecVersion = HdmiCecConstants.CEC_VERSION_2_0;
        CecVersionHelper.setCecVersion(getDevice(), cecVersion);

        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GET_CEC_VERSION);
        String reportCecVersion = hdmiCecClient.checkExpectedOutput(hdmiCecClient.getSelfDevice(),
                CecOperand.CEC_VERSION);
        assertThat(CecMessage.getParams(reportCecVersion)).isEqualTo(cecVersion);

        Thread.sleep(TIMEOUT_SAFETY_MS);

        hdmiCecClient.sendCecMessage(hdmiCecClient.getSelfDevice(), CecOperand.GIVE_FEATURES);
        String reportFeatures = hdmiCecClient.checkExpectedOutput(LogicalAddress.BROADCAST,
                CecOperand.REPORT_FEATURES);
        assertThat(CecMessage.getParams(reportFeatures, 2)).isEqualTo(cecVersion);
    }
}
