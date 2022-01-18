/*
 * Copyright 2021 The Android Open Source Project
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

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.RemoteControlPassthrough;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/** HDMI CEC 2.0 general protocol tests */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecGeneralProtocolTest extends BaseHdmiCecCtsTest {

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(CecRules.requiresCec(this))
                    .around(CecRules.requiresLeanback(this))
                    .around(hdmiCecClient);

    /**
     * Test HF4-2-5, HF4-2-6 (CEC 2.0)
     *
     * <p>Tests that the device ignores any additional trailing parameters in an otherwise correct
     * CEC message.
     *
     * <p>e.g. If {@code 14:44:01:02 (<UCP>[KEYCODE_DPAD_UP])} is sent to the DUT, the DUT should
     * ignore the last byte of the parameter and treat it as {@code <UCP>[KEYCODE_DPAD_UP]}
     */
    @Test
    public void cect_hf_ignoreAdditionalParams() throws Exception {
        setCec20();
        RemoteControlPassthrough.checkUserControlPressAndReleaseWithAdditionalParams(
                hdmiCecClient, getDevice(), LogicalAddress.RECORDER_1, getTargetLogicalAddress());
    }
}
