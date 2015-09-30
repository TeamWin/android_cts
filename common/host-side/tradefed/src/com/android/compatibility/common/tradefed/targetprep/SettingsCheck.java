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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a given setting on the device is one of the given values
 */
@OptionClass(alias="settings-check")
public class SettingsCheck extends PreconditionCheck {

    public enum SettingType {
        SECURE,
        GLOBAL,
        SYSTEM;
    }

    @Option(name = "device-setting", description = "The setting on the device to be checked",
            mandatory = true)
    protected String mSettingName = null;

    @Option(name = "expected-values", description = "The set of expected values of the setting")
    protected List<String> mExpectedSettingValues = new ArrayList<String>();

    @Option(name = "setting-type",
            description = "If the setting is 'secure', 'global', or 'system'", mandatory = true)
    protected SettingType mSettingType = null;

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {

        String shellCmd = String.format("settings get %s %s", mSettingType, mSettingName);
        String actualSettingValue = device.executeShellCommand(shellCmd).trim();
        if (!mExpectedSettingValues.contains(actualSettingValue)) {
            throw new TargetSetupError(
                    String.format("Device setting \"%s\" returned \"%s\", not found in %s",
                    mSettingName, actualSettingValue, mExpectedSettingValues.toString()));
        }
    }

}
