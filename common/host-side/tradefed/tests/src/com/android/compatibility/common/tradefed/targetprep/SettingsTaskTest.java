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

public class SettingsTaskTest extends TestCase {

    private SettingsTask mSettingsTask;
    private IBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mSettingsTask = new SettingsTask();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockBuildInfo = new BuildInfo("0", "", "");
    }

    public void testCommandRun() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(
                "settings put GLOBAL stay_on_while_plugged_in 7")).andReturn("\n");
        OptionSetter optionSetter = new OptionSetter(mSettingsTask);
        optionSetter.setOptionValue("device-setting", "stay_on_while_plugged_in");
        optionSetter.setOptionValue("setting-type", "global");
        optionSetter.setOptionValue("set-value", "7");
        EasyMock.replay(mMockDevice);
        mSettingsTask.run(mMockDevice, mMockBuildInfo);
    }

}
