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

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.rules.TestRule;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Base class for all HDMI CEC CTS tests. */
@OptionClass(alias="hdmi-cec-client-cts-test")
public class BaseHdmiCecCtsTest extends BaseHostJUnit4Test {

    public final HdmiCecClientWrapper hdmiCecClient;

    /**
     * Constructor for BaseHdmiCecCtsTest.
     *
     * @param dutLogicalAddress The logical address that the DUT will have.
     * @param clientParams Extra parameters to use when launching cec-client
     */
    public BaseHdmiCecCtsTest(LogicalAddress dutLogicalAddress, String ...clientParams) {
        this.hdmiCecClient = new HdmiCecClientWrapper(this, dutLogicalAddress, clientParams);
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

    @Option(name = HdmiCecConstants.PHYSICAL_ADDRESS_NAME,
        description = "HDMI CEC physical address of the DUT",
        mandatory = false)
    public static int dutPhysicalAddress = HdmiCecConstants.DEFAULT_PHYSICAL_ADDRESS;

    /** Gets the physical address of the DUT by parsing the dumpsys hdmi_control. */
    public int getDumpsysPhysicalAddress() throws Exception {
        String line;
        String pattern = "(.*?)" + "(physical_address: )" + "(?<address>0x\\p{XDigit}{4})" +
                "(.*?)";
        Pattern p = Pattern.compile(pattern);
        Matcher m;
        ITestDevice device = getDevice();
        String dumpsys = device.executeShellCommand("dumpsys hdmi_control");
        BufferedReader reader = new BufferedReader(new StringReader(dumpsys));
        while ((line = reader.readLine()) != null) {
            m = p.matcher(line);
            if (m.matches()) {
                int address = Integer.decode(m.group("address"));
                return address;
            }
        }
        throw new Exception("Could not parse physical address from dumpsys.");
    }
}
