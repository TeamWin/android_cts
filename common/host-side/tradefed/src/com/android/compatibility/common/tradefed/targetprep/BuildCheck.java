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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * Checks that the device's build type is as expected
 */
@OptionClass(alias="build-check")
public class BuildCheck extends PreconditionPreparer {

    public enum BuildType {
        USER,
        USERDEBUG,
        ENG;
    }

    @Option(name = "expected-build-type", description =
            "The device's expected build type: 'user', 'userdebug' or 'eng'", mandatory = true)
    protected BuildType mExpectedBuildType = null;

    @Option(name = "throw-error",
            description = "Whether to throw an error for an unexpected build type")
    protected boolean mThrowError = false;

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        String deviceBuildType = device.getProperty("ro.build.type");
        if (!mExpectedBuildType.name().equalsIgnoreCase(deviceBuildType)) {
            String msg = String.format("Expected build type \"%s\" but found build type \"%s\"",
                    mExpectedBuildType.name().toLowerCase(), deviceBuildType);
            // Handle unexpected build type with either exception or warning
            if(mThrowError) {
                throw new TargetSetupError(msg);
            } else {
                CLog.e(msg);
            }
        }
    }

}
