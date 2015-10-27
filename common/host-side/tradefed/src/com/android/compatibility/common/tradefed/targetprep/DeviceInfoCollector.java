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
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

/**
 * An {@link ApkInstrumentationPreparer} that collects device info.
 */
public class DeviceInfoCollector extends ApkInstrumentationPreparer {

    private static String LOG_TAG = "DeviceInfoCollector";
    private static String DEVICE_INFO_CLASS = "com.android.compatibility.common.deviceinfo";
    private static String DEVICE_INFO_GENERIC = "DEVICE_INFO_GENERIC_";
    private static String DEVICE_INFO_ERROR = "DEVICE_INFO_ERROR_";

    @Option(name = CompatibilityTest.SKIP_DEVICE_INFO_OPTION, description =
            "Whether device info collection should be skipped")
    private boolean mSkipDeviceInfo = false;

    @Option(name= "src-dir", description = "The directory to copy to the results dir")
    private String mSrcDir;

    @Option(name = "dest-dir", description = "The directory under the result to store the files")
    private String mDestDir;

    public DeviceInfoCollector() {
        mWhen = When.BEFORE;
    }

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (mSkipDeviceInfo) {
            return;
        }

        run(device, buildInfo);

        // Check test metrics for errors and copy generic device info results to build attribute.
        for (Map.Entry<TestIdentifier, Map<String, String>> metricEntry : testMetrics.entrySet()) {
            if (!metricEntry.getKey().getClassName().startsWith(DEVICE_INFO_CLASS)) {
                continue;
            }
            for (Map.Entry<String, String> testEntry : metricEntry.getValue().entrySet()) {
                String key = testEntry.getKey();
                String value = testEntry.getValue();
                if (key.startsWith(DEVICE_INFO_ERROR)) {
                    throw new TargetSetupError(String.format("[%s] %s", key, value));
                }
                if (key.startsWith(DEVICE_INFO_GENERIC)) {
                    buildInfo.addBuildAttribute(key, value);
                }
            }
        }

        getDeviceInfoFiles(device, buildInfo);
    }

    private void getDeviceInfoFiles(ITestDevice device, IBuildInfo buildInfo) {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        try {
            File resultDir = buildHelper.getResultDir();
            if (mDestDir != null) {
                resultDir = new File(resultDir, mDestDir);
            }
            resultDir.mkdirs();
            if (!resultDir.isDirectory()) {
                CLog.e(LOG_TAG, String.format("% is not a directory", resultDir.getAbsolutePath()));
                return;
            }
            String resultPath = resultDir.getAbsolutePath();
            pull(device, mSrcDir, resultPath);
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    private void pull(ITestDevice device, String src, String dest) {
        String command = String.format("adb -s %s pull %s %s", device.getSerialNumber(), src, dest);
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"/bin/bash", "-c", command});
            if (p.waitFor() != 0) {
                CLog.e(LOG_TAG, String.format("Failed to run %s", command));
            }
        } catch (Exception e) {
            CLog.e(LOG_TAG, e);
        }
    }
}