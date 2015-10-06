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

/**
 * Checks that the device's locale is as expected
 */
@OptionClass(alias="locale-check")
public class LocaleCheck extends PreconditionPreparer {

    @Option(name = "expected-locale", description = "The device's expected locale",
            mandatory = true)
    protected String mExpectedLocale = "en-US";

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        String deviceLocale = device.executeShellCommand("getprop ro.product.locale").trim();
        if (!mExpectedLocale.equals(deviceLocale)) {
            throw new TargetSetupError(String.format("Expected locale \"%s\", found \"%s\"",
                    mExpectedLocale, deviceLocale));
        }
    }

}
