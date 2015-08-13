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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Installs specified APKs from Compatibility repository.
 */
@OptionClass(alias="apk-installer")
public class ApkInstaller extends TestAppInstallSetup {

    private CompatibilityBuildInfo mBuild = null;

    protected CompatibilityBuildInfo getBuild(IBuildInfo buildInfo) {
        if (mBuild == null) {
            mBuild = (CompatibilityBuildInfo) buildInfo;
        }
        return mBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected File getLocalPathForFilename(IBuildInfo buildInfo, String apkFileName)
            throws TargetSetupError {
        File apkFile = null;
        try {
            apkFile = new File(getBuild(buildInfo).getTestsDir(), apkFileName);
            if (!apkFile.isFile()) {
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException e) {
            throw new TargetSetupError(String.format("%s not found", apkFileName), e);
        }
        return apkFile;
    }
}
