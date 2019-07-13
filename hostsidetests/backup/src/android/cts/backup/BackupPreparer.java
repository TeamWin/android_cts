/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.cts.backup;

import com.android.compatibility.common.util.BackupHostSideUtils;
import com.android.compatibility.common.util.BackupUtils;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.IOException;

/**
 * Tradedfed target preparer for the backup tests.
 * Enables backup before all the tests and selects local transport.
 * Reverts to the original state after all the tests are executed.
 */
@OptionClass(alias = "backup-preparer")
public class BackupPreparer implements ITargetCleaner {
    @Option(name="enable-backup-if-needed", description=
            "Enable backup before all the tests and return to the original state after.")
    private boolean mEnableBackup = true;

    @Option(name="select-local-transport", description=
            "Select local transport before all the tests and return to the original transport "
                    + "after.")
    private boolean mSelectLocalTransport = true;

    /** Value of PackageManager.FEATURE_BACKUP */
    private static final String FEATURE_BACKUP = "android.software.backup";

    private static final String LOCAL_TRANSPORT =
            "com.android.localtransport/.LocalTransport";

    private static final int USER_SYSTEM = 0;

    private boolean mIsBackupSupported;
    private boolean mWasBackupEnabled;
    private String mOldTransport;
    private ITestDevice mDevice;
    private BackupUtils mBackupUtils;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        mDevice = device;

        mIsBackupSupported = mDevice.hasFeature("feature:" + FEATURE_BACKUP);
        if (mIsBackupSupported) {
            mBackupUtils = BackupHostSideUtils.createBackupUtils(mDevice);
            try {
                mBackupUtils.waitUntilBackupServiceIsRunning(USER_SYSTEM);
                checkHasLocalTransport();
                if (mEnableBackup) {
                    mWasBackupEnabled = enableBackup(true);
                    if (mSelectLocalTransport) {
                        mOldTransport = setBackupTransport(LOCAL_TRANSPORT);
                    }
                    mBackupUtils.waitForBackupInitialization();
                }
            } catch (Exception e) {
                throw new TargetSetupError("Exception in setup", e);
            }
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        mDevice = device;

        if (mIsBackupSupported) {
            try {
                if (mEnableBackup) {
                    CLog.i("Returning backup to it's previous state on %s",
                            mDevice.getSerialNumber());
                    enableBackup(mWasBackupEnabled);
                    if (mSelectLocalTransport) {
                        setBackupTransport(mOldTransport);
                    }
                }
            } catch (Exception ex) {
                throw new DeviceNotAvailableException("Exception in tearDown", ex);
            }
        }
    }

    private void checkHasLocalTransport() throws Exception {
        if (!mBackupUtils.userHasBackupTransport(LOCAL_TRANSPORT, USER_SYSTEM)) {
            throw new TargetSetupError(
                    "Device should have LocalTransport available", mDevice.getDeviceDescriptor());
        }
    }

    private boolean enableBackup(boolean enable) throws Exception {
        CLog.i("Setting backup enabled on %s to %s", mDevice.getSerialNumber(), enable);
        boolean previouslyEnabled = mBackupUtils.enableBackup(enable);
        CLog.d("Backup was enabled? : %s", previouslyEnabled);
        return previouslyEnabled;
    }

    private String setBackupTransport(String transport) throws IOException {
        CLog.i("Selecting %s on %s", transport, mDevice.getSerialNumber());
        String oldTransport = mBackupUtils.setBackupTransportForUser(transport, USER_SYSTEM);
        CLog.d("Old transport : %s", mOldTransport);
        return oldTransport;
    }
}
