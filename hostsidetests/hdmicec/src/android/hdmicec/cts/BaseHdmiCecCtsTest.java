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

package android.hdmicec.cts;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.rules.TestRule;

/** Base class for all HDMI CEC CTS tests. */
public class BaseHdmiCecCtsTest extends BaseHostJUnit4Test {

    public final HdmiCecClientWrapper hdmiCecClient;

    /**
     * Constructor for BaseHdmiCecCtsTest.
     *
     * @param dutLogicalAddress The logical address that the DUT will have.
     * @param clientParams Extra parameters to use when launching cec-client
     */
    public BaseHdmiCecCtsTest(LogicalAddress dutLogicalAddress, String ...clientParams) {
        this.hdmiCecClient = new HdmiCecClientWrapper(dutLogicalAddress, clientParams);
    }

    /** Class with predefined rules which can be used by HDMI CEC CTS tests. */
    public static class CecRules {

        public static TestRule requiresCec(BaseHostJUnit4Test testPointer) {
            return new RequiredFeatureRule(testPointer, HdmiCecConstants.HDMI_CEC_FEATURE);
        }

        public static TestRule requiresLeanback(BaseHostJUnit4Test testPointer) {
            return new RequiredFeatureRule(testPointer, HdmiCecConstants.LEANBACK_FEATURE);
        }

        public static TestRule requiresDeviceType(BaseHostJUnit4Test testPointer,
                                                  LogicalAddress dutLogicalAddress) {
            return RequiredPropertyRule.asCsvContainsValue(
                        testPointer,
                        HdmiCecConstants.HDMI_DEVICE_TYPE_PROPERTY,
                        dutLogicalAddress.getDeviceType());
        }
    }
}
