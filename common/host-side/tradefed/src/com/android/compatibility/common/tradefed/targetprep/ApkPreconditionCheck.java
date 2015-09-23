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
import com.android.compatibility.common.tradefed.util.NoOpTestInvocationListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.InstrumentationTest;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs a precondition test by instrumenting an APK
 */
@OptionClass(alias="apk-precondition-check")
public class ApkPreconditionCheck extends PreconditionCheck {

    @Option(name = "apk-name", description = "name of the precondition apk", mandatory=true)
    protected String mApkFileName = null;

    @Option(name = "package-name", description = "Name of the precondition package", mandatory=true)
    protected String mPackageName = null;

    @Option(name = "runner", description = "Name of the test's runner", mandatory=true)
    protected String mRunner = null;

    private ConcurrentHashMap<TestIdentifier, String> testFailures =
            new ConcurrentHashMap<TestIdentifier, String>();

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {

        CompatibilityBuildHelper buildHelper =
                new CompatibilityBuildHelper((IFolderBuildInfo) buildInfo);

        File testsDir = null;

        try {
            testsDir = buildHelper.getTestsDir();
        } catch (FileNotFoundException e) {
            throw new TargetSetupError(
                    String.format("Test directory for %s not found", mApkFileName), e);
        }

        File apkFile = new File(testsDir, mApkFileName);
        if (!apkFile.isFile()) {
            throw new TargetSetupError(String.format("%s not found", mApkFileName));
        }

        ITestInvocationListener listener = new PreconditionsTestInvocationListener();

        InstrumentationTest instrTest = new InstrumentationTest();
        instrTest.setDevice(device);
        instrTest.setInstallFile(apkFile);
        instrTest.setPackageName(mPackageName);
        instrTest.setRunnerName(mRunner);
        instrTest.run(listener);

        if (!testFailures.isEmpty()) {
            for (TestIdentifier test : testFailures.keySet()) {
                String trace = testFailures.get(test);
                CLog.e("Precondition test %s failed.\n%s",
                        test.getTestName(), trace);
            }
            throw new TargetSetupError("Precondition test(s) failed");
        }
    }

    public class PreconditionsTestInvocationListener extends NoOpTestInvocationListener {

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            testFailures.put(test, trace);
        }

    }

}
