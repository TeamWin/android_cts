/*
 * Copyright 2020 The Android Open Source Project
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

package android.hdmicec.cts.tv;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC test to test audio return channel control (Section 11.2.17) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecAudioReturnChannelControlTest extends BaseHdmiCecCtsTest {

    private static final LogicalAddress TV_DEVICE = LogicalAddress.TV;

    public HdmiCecAudioReturnChannelControlTest() {
        super(TV_DEVICE, "-t", "a");
    }

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(CecRules.requiresDeviceType(this, TV_DEVICE))
                    .around(hdmiCecClient);

    /**
     * Test 11.1.17-2
     *
     * <p>Tests that the DUT responds with a directly addressed {@code <Report ARC initiated>}
     * message to the Audio System when ARC is initiated.
     */
    @Ignore("b/174813656")
    @Test
    public void cect_11_1_17_2_ReportArcInitiated() throws Exception {
        String params =
                String.format(
                        "%04d%02d",
                        hdmiCecClient.getPhysicalAddress(),
                        HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
        hdmiCecClient.sendCecMessage(
                LogicalAddress.AUDIO_SYSTEM,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                CecMessage.formatParams(params));
        hdmiCecClient.sendCecMessage(
                LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV, CecOperand.INITIATE_ARC);
        hdmiCecClient.checkExpectedOutput(LogicalAddress.AUDIO_SYSTEM, CecOperand.ARC_INITIATED);
    }

    /**
     * Test 11.1.17-2,4
     *
     * <p>Tests that the DUT responds with a directly addressed {@code <Report ARC terminated>}
     * message to the Audio System when ARC is terminated.
     */
    @Ignore("b/174813656")
    @Test
    public void cect_11_1_17_2_4_ReportArcInitiatedTerminated() throws Exception {
        /* We need to initiate ARC, so call the Initiate ARC test first */
        cect_11_1_17_2_ReportArcInitiated();
        hdmiCecClient.sendCecMessage(
                LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV, CecOperand.TERMINATE_ARC);
        hdmiCecClient.checkExpectedOutput(LogicalAddress.AUDIO_SYSTEM, CecOperand.ARC_TERMINATED);
    }

    /**
     * Test 11.1.17-5
     *
     * <p>Tests that the DUT does not respond with any directly addressed {@code <Report ARC
     * initiated>} message to a non-adjacent device
     */
    @Test
    public void cect_11_1_17_5_NonAdjacentDeviceArcInitiation() throws Exception {
        int originalPhyAdd = hdmiCecClient.getPhysicalAddress();
        try {
            int nonAdjacentPhyAdd = 0x1100;
            String params =
                    CecMessage.formatParams(nonAdjacentPhyAdd)
                            + CecMessage.formatParams(
                                    HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
            /* Take physical address 1.1.0.0 */
            hdmiCecClient.setPhysicalAddress(nonAdjacentPhyAdd);
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.AUDIO_SYSTEM,
                    LogicalAddress.BROADCAST,
                    CecOperand.REPORT_PHYSICAL_ADDRESS,
                    params);
            hdmiCecClient.sendCecMessage(
                    LogicalAddress.AUDIO_SYSTEM, LogicalAddress.TV, CecOperand.INITIATE_ARC);
            hdmiCecClient.checkOutputDoesNotContainMessage(
                    LogicalAddress.AUDIO_SYSTEM, CecOperand.ARC_INITIATED);
        } finally {
            /* Restore physical address */
            hdmiCecClient.setPhysicalAddress(originalPhyAdd);
        }
    }
}
