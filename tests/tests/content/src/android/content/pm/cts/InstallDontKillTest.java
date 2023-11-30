/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.content.pm.cts;

import static android.provider.DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.Flags;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class InstallDontKillTest {
    private static final String TMP_PATH = "/data/local/tmp/cts/content/";
    private static final String SPLIT_APK_1 = TMP_PATH + "CtsPackageManagerTestCases_hdpi-v4.apk";
    private static final String SPLIT_APK_2 = TMP_PATH + "CtsPackageManagerTestCases_mdpi-v4.apk";
    private static final String PROPERTY_DEFERRED_NO_KILL_POST_DELETE_DELAY_MS_EXTENDED =
            "deferred_no_kill_post_delete_delay_ms_extended";
    private static final int WAIT_MILLIS = 5000;
    private static final int ADJUSTED_DELETE_DELAY_MILLIS = 9 * 1000;

    private Context mContext;
    private String mPackageName;
    private String mOldPath;
    private String mPathAfterFirstSplitInstall;
    private String mPathAfterSecondSplitInstall;
    private String mDeleteDelayInDeviceConfig;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mPackageName = mContext.getPackageName();
        mDeleteDelayInDeviceConfig = getDeleteDelayInDeviceConfig();

        mOldPath = mContext.getApplicationInfo().getCodePath();
    }

    @After
    public void destroy() {
        // reset delete delay to the previous value
        setDeleteDelayInDeviceConfig(mDeleteDelayInDeviceConfig);
        assertThat(getDeleteDelayInDeviceConfig()).isEqualTo(mDeleteDelayInDeviceConfig);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_IMPROVE_INSTALL_DONT_KILL)
    public void testDontKill_pathsDeletedAfterSmallDelay() throws Exception {
        installSplitsWithDontKill();
        // Wait a bit and check that the old paths are deleted
        Thread.sleep(WAIT_MILLIS);
        // Test that the old code paths are deleted
        assertThat(new File(mOldPath).exists()).isFalse();
        assertThat(new File(mPathAfterFirstSplitInstall).exists()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IMPROVE_INSTALL_DONT_KILL)
    public void testDontKill_pathsNotDeletedAfterAdjustedDelay() throws Exception {
        setDeleteDelayInDeviceConfig(String.valueOf(ADJUSTED_DELETE_DELAY_MILLIS));
        installSplitsWithDontKill();
        // Wait a bit and check that the old paths are still there after some time
        Thread.sleep(WAIT_MILLIS);
        // Test that the files in the old code paths are still accessible
        assertThat(new File(mOldPath, "base.apk").exists()).isTrue();
        assertThat(new File(mPathAfterFirstSplitInstall, "base.apk").exists()).isTrue();
        assertThat(new File(mPathAfterSecondSplitInstall,
                "split_config.hdpi.apk").exists()).isTrue();
        // Wait a bit longer and check that the old paths are deleted
        Thread.sleep(WAIT_MILLIS);
        assertThat(new File(mOldPath).exists()).isFalse();
        assertThat(new File(mPathAfterFirstSplitInstall).exists()).isFalse();
    }

    private void installSplitsWithDontKill() {
        // Install a split for this test itself and check that the code path has changed
        assertThat(SystemUtil.runShellCommand(String.format(
                "pm install -p %s --dont-kill %s", mPackageName, SPLIT_APK_1))
        ).isEqualTo("Success\n");
        mPathAfterFirstSplitInstall = mContext.getApplicationInfo().getCodePath();
        assertThat(mPathAfterFirstSplitInstall).isNotEqualTo(mOldPath);
        // Do it again with another split
        assertThat(SystemUtil.runShellCommand(String.format(
                "pm install -p %s --dont-kill %s", mPackageName, SPLIT_APK_2))
        ).isEqualTo("Success\n");
        mPathAfterSecondSplitInstall = mContext.getApplicationInfo().getCodePath();
        assertThat(mPathAfterSecondSplitInstall).isNotEqualTo(mPathAfterFirstSplitInstall);
        // Test that the files in the new code path are accessible
        assertThat(new File(mPathAfterSecondSplitInstall, "base.apk").exists()).isTrue();
        assertThat(new File(mPathAfterSecondSplitInstall,
                "split_config.hdpi.apk").exists()).isTrue();
        assertThat(new File(mPathAfterSecondSplitInstall,
                "split_config.mdpi.apk").exists()).isTrue();
    }

    private String getDeleteDelayInDeviceConfig() {
        return SystemUtil.runWithShellPermissionIdentity(
                () -> DeviceConfig.getProperty(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                        PROPERTY_DEFERRED_NO_KILL_POST_DELETE_DELAY_MS_EXTENDED));
    }
    private void setDeleteDelayInDeviceConfig(String delayMillisStr) {
        // reset default delay to the previous value
        SystemUtil.runWithShellPermissionIdentity(
                () -> DeviceConfig.setProperty(NAMESPACE_PACKAGE_MANAGER_SERVICE,
                        PROPERTY_DEFERRED_NO_KILL_POST_DELETE_DELAY_MS_EXTENDED,
                        delayMillisStr, /* makeDefault */ false));
    }
}
