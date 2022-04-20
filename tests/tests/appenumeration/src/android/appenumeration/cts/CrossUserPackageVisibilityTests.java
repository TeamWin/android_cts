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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.TARGET_STUB;
import static android.appenumeration.cts.Constants.TARGET_STUB_APK;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Verify that app without holding the {@link android.Manifest.permission.INTERACT_ACROSS_USERS}
 * can't detect the existence of another app in the different users on the device via the
 * side channel attacks.
 */
@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4.class)
public class CrossUserPackageVisibilityTests {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Context mContext;
    private PackageManager mPackageManager;
    private UserReference mOtherUser;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();

        // Get another user
        final UserReference primaryUser = sDeviceState.primaryUser();
        if (primaryUser.id() == UserHandle.myUserId()) {
            mOtherUser = sDeviceState.secondaryUser();
        } else {
            mOtherUser = primaryUser;
        }

        uninstallPackage(TARGET_STUB);
    }

    @After
    public void tearDown() {
        uninstallPackage(TARGET_STUB);
    }

    @Test
    public void testIsPackageSuspended_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.isPackageSuspended(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.isPackageSuspended(TARGET_STUB));
    }

    @Test
    public void testGetTargetSdkVersion_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getTargetSdkVersion(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getTargetSdkVersion(TARGET_STUB));
    }

    @Test
    public void testCheckSignatures_cannotDetectStubPkg() {
        final String selfPackageName = mContext.getPackageName();
        assertThat(mPackageManager.checkSignatures(selfPackageName, TARGET_STUB))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
        assertThat(mPackageManager.checkSignatures(TARGET_STUB, selfPackageName))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.checkSignatures(selfPackageName, TARGET_STUB))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
        assertThat(mPackageManager.checkSignatures(TARGET_STUB, selfPackageName))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
    }

    @Test
    public void testGetAllIntentFilters_cannotDetectStubPkg() {
        assertThat(mPackageManager.getAllIntentFilters(TARGET_STUB)).isEmpty();

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.getAllIntentFilters(TARGET_STUB)).isEmpty();
    }

    @Test
    public void testGetInstallerPackageName_cannotDetectStubPkg() {
        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getInstallerPackageName(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getInstallerPackageName(TARGET_STUB));
    }

    @Test
    public void testGetInstallSourceInfo_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstallSourceInfo(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstallSourceInfo(TARGET_STUB));
    }

    private static void installPackageForUser(String apkPath, UserReference user) {
        installPackageForUser(apkPath, user,
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName());
    }

    private static void installPackageForUser(String apkPath, UserReference user,
            String installerPackageName) {
        assertThat(new File(apkPath).exists()).isTrue();
        final StringBuilder cmd = new StringBuilder("pm install --user ");
        cmd.append(user.id()).append(" ");
        cmd.append("-i ").append(installerPackageName).append(" ");
        cmd.append(apkPath);
        final String result = runShellCommand(cmd.toString());
        assertThat(result.trim()).contains("Success");
    }

    private static void uninstallPackage(String packageName) {
        runShellCommand("pm uninstall " + packageName);
    }
}
