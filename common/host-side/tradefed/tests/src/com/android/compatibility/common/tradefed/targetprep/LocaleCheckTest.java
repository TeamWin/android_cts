/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.targetprep;

import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import org.easymock.EasyMock;

public class LocaleCheckTest extends TestCase {

    private LocaleCheck mLocaleCheck;
    private IBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mLocaleCheck = new LocaleCheck();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockBuildInfo = new BuildInfo("0", "", "");
        mOptionSetter = new OptionSetter(mLocaleCheck);
    }

    public void testDefaultMatch() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(
                "getprop ro.product.locale")).andReturn("\nen-US\n").once();
        EasyMock.replay(mMockDevice);
        mLocaleCheck.run(mMockDevice, mMockBuildInfo); // no errors, expecting 'en-US' by default
    }

    public void testDefaultMismatch() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(
                "getprop ro.product.locale")).andReturn("\nfr-FR\n").once();
        EasyMock.replay(mMockDevice);
        try {
            mLocaleCheck.run(mMockDevice, mMockBuildInfo); // expecting TargetSetupError
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            //Expected
        }
    }

    public void testOptionMatch() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(
                "getprop ro.product.locale")).andReturn("\nen-GB\n").once();
        mOptionSetter.setOptionValue("expected-locale", "en-GB");
        EasyMock.replay(mMockDevice);
        mLocaleCheck.run(mMockDevice, mMockBuildInfo); // no errors
    }

    public void testOptionMismatch() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(
                "getprop ro.product.locale")).andReturn("\nfr-FR\n").once();
        mOptionSetter.setOptionValue("expected-locale", "en-GB");
        EasyMock.replay(mMockDevice);
        try {
            mLocaleCheck.run(mMockDevice, mMockBuildInfo); // expecting TargetSetupError
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

}
