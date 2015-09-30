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
@OptionClass(alias="settings-task")
public class SettingsTask extends PreconditionTask {

    public enum SettingType {
        SECURE,
        GLOBAL,
        SYSTEM;
    }

    @Option(name = "device-setting", description = "The setting on the device to be checked",
            mandatory = true)
    protected String mSettingName = null;

    @Option(name = "setting-type",
            description = "If the setting is 'secure', 'global' or 'system'", mandatory = true)
    protected SettingType mSettingType = null;

    @Option(name = "set-value",
            description = "The value to be set for the setting", mandatory = true)
    protected String mSetValue = null;

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        device.executeShellCommand(
            String.format("settings put %s %s %s", mSettingType, mSettingName, mSetValue));
    }

}
