/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecOperand;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HDMI CEC tests verifying CEC messages sent after startup (CEC 2.0 CTS Section 7.5)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecStartupTest extends BaseHdmiCecCtsTest {

    private static final ImmutableList<CecOperand> necessaryMessages =
            new ImmutableList.Builder<CecOperand>()
                    .add(CecOperand.REPORT_PHYSICAL_ADDRESS)
                    .add(CecOperand.REPORT_FEATURES)
                    .build();
    @Rule
    public RuleChain ruleChain =
            RuleChain
                    .outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(hdmiCecClient);

    /**
     * CEC 2.0 CTS 7.5.
     *
     * Verifies that {@code <Report Features>} and {@code <Report Physical Address>} are sent at
     * device startup.
     * Verifies that both messages are sent in the given order.
     */
    @Test
    public void cectVerifyStartupMessages() throws Exception {
        ITestDevice device = getDevice();
        setCec20();

        device.executeShellCommand("reboot");
        device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
        /* Monitor CEC messages for 20s after reboot */
        final List<CecOperand> messagesReceived =
                hdmiCecClient.getAllMessages(mDutLogicalAddress, 20);

        List<CecOperand> requiredMessages = messagesReceived.stream()
                .filter(necessaryMessages::contains)
                .collect(Collectors.toList());

        assertWithMessage("Some necessary messages are missing").that(requiredMessages).hasSize(
                necessaryMessages.size());
        assertWithMessage("Expected <Report Features> first").that(
                requiredMessages.get(0)).isEqualTo(CecOperand.REPORT_FEATURES);
        assertWithMessage("Expected <Report Physical Address> last").that(
                requiredMessages.get(1)).isEqualTo(CecOperand.REPORT_PHYSICAL_ADDRESS);
    }
}
