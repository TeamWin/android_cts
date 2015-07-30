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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.DynamicConfig;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Pushes dynamic config files from config repository
 */
@OptionClass(alias="dynamic-config-pusher")
public class DynamicConfigPusher implements ITargetCleaner {

    public enum TestTarget {
        DEVICE,
        HOST
    }

    private static final String LOG_TAG = DynamicConfigPusher.class.getSimpleName();

    @Option(name = "cleanup", description = "Whether to clean up config files after test is done.")
    private boolean mCleanup = true;

    @Option(name="module-name", description = "Specify the module name")
    private String mModuleName;

    @Option(name = "target", description = "Specify the target, 'device' or 'host'")
    private TestTarget mTarget;

    private String mFilePushed;

    void setModuleName(String moduleName) {
        mModuleName = moduleName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        File src = null;
        CompatibilityBuildHelper buildHelper =
                new CompatibilityBuildHelper((IFolderBuildInfo) buildInfo);
        try {
            src = DynamicConfig.getConfigFile(buildHelper.getTestsDir(), mModuleName);
        } catch (FileNotFoundException e) {
            throw new TargetSetupError(String.format(
                    "Cannot find config file for module '%s' in repository", mModuleName));
        }

        if (mTarget == TestTarget.DEVICE) {
            String dest = DynamicConfig.CONFIG_FOLDER_ON_DEVICE + src.getName();
            if (!device.pushFile(src, dest)) {
                throw new TargetSetupError(String.format("Failed to push local '%s' to remote '%s'",
                        src.getName(), dest));
            } else {
                mFilePushed = dest;
                buildHelper.addDynamicConfig(mModuleName, DynamicConfig.calculateSHA1(src));
            }

        } else if (mTarget == TestTarget.HOST) {
            File storageDir = new File(DynamicConfig.CONFIG_FOLDER_ON_HOST);
            if (!storageDir.exists()) storageDir.mkdir();
            File dest = new File(DynamicConfig.CONFIG_FOLDER_ON_HOST + src.getName());
            try {
                FileUtil.copyFile(src, dest);
            } catch (IOException e) {
                throw new TargetSetupError(String.format("Failed to copy file from %s to %s",
                        src.getAbsolutePath(), dest.getAbsolutePath()), e);
            }
            mFilePushed = dest.getAbsolutePath();
            buildHelper.addDynamicConfig(mModuleName, DynamicConfig.calculateSHA1(src));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mTarget == TestTarget.DEVICE) {
            if (!(e instanceof DeviceNotAvailableException) && mCleanup && mFilePushed != null) {
                device.executeShellCommand("rm -r " + mFilePushed);
            }
        } else if (mTarget == TestTarget.HOST) {
            new File(mFilePushed).delete();
        }
    }
}