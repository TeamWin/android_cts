/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.devicepolicy.cts;


import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_BACKUP;
import static com.android.bedstead.nene.permissions.CommonPermissions.BACKUP;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.app.backup.BackupManager;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.Backup;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_BACKUP)
public final class BackupTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    @Test
    @PolicyAppliesTest(policy = Backup.class)
    @EnsureHasPermission(BACKUP)
    @Postsubmit(reason = "new test")
    public void isBackupEnabled_default_returnsFalse() {
        BackupManager bm = new BackupManager(sContext);
        assertThat(bm.isBackupEnabled()).isFalse();
    }

    @Test
    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    public void isBackupServiceEnabled_default_returnsFalse() {
        assertThat(sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                sDeviceState.dpc().componentName())).isFalse();
    }

    @Test
    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    public void setBackupServiceEnabled_true_setsBackupServiceEnabled() {
        try {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                    sDeviceState.dpc().componentName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @Test
    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    public void setBackupServiceEnabled_false_setsBackupServiceNotEnabled() {
        try {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), false);

            assertThat(sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                    sDeviceState.dpc().componentName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }
}
