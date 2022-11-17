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

package android.os.lib.app;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.lib.app.StaticSharedLibsTests.InstallUninstallBroadcastReceiver;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * On-device tests driven by StaticSharedLibsHostTests. Specifically tests multi user scenarios.
 */
@RunWith(BedsteadJUnit4.class)
public class StaticSharedLibsMultiUserTests {

    private static final String STATIC_LIB_PROVIDER_RECURSIVE_APK =
            "CtsStaticSharedLibProviderRecursive.apk";

    private static final String APK_BASE_PATH = "/data/local/tmp/cts/hostside/os/";
    private static final String STATIC_LIB_PROVIDER1_APK = APK_BASE_PATH
            + "CtsStaticSharedLibProviderApp1.apk";
    private static final String STATIC_LIB_PROVIDER1_PKG = "android.os.lib.provider";

    private UserReference mPrimaryUser;
    private UserReference mSecondaryUser;

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();
    private final Context mContext = InstrumentationRegistry.getTargetContext();

    @Before
    public void cacheUsers() {
        mPrimaryUser = sDeviceState.primaryUser();
        mSecondaryUser = sDeviceState.secondaryUser();
    }

    @Before
    public void installLibraryDependency() {
        installPackageAsUser(STATIC_LIB_PROVIDER_RECURSIVE_APK, null /* installerName */,
                mSecondaryUser);
    }

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private boolean installPackageAsUser(String apkPath, String installerName, UserReference user) {
        StringBuilder builder = new StringBuilder("pm install");
        if (installerName != null) {
            builder.append(" -i ").append(installerName);
        }
        if (user != null) {
            builder.append(" --user ").append(user.id());
        }
        builder.append(" ").append(apkPath);
        return runShellCommand(builder.toString()).equals("Success\n");
    }

    private boolean uninstallPackage(String packageName) {
        return runShellCommand("pm uninstall " + packageName).equals("Success\n");
    }

    private Context createContextAsUser(UserReference user) {
        return mContext.createContextAsUser(user.userHandle(), 0);
    }

    @Test
    @RequireRunOnSecondaryUser
    public void testStaticSharedLibInstallOnSecondaryUser_broadcastReceivedByAllUsers() {
        Context contextPrimary = createContextAsUser(mPrimaryUser);
        Context contextSecondary = createContextAsUser(mSecondaryUser);


        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        InstallUninstallBroadcastReceiver receiverPrimary = new InstallUninstallBroadcastReceiver();
        InstallUninstallBroadcastReceiver receiverSecondary =
                new InstallUninstallBroadcastReceiver();

        contextPrimary.registerReceiver(receiverPrimary, filter);
        contextSecondary.registerReceiver(receiverSecondary, filter);
        try {
            assertThat(installPackageAsUser(STATIC_LIB_PROVIDER1_APK,
                    contextSecondary.getPackageName(), mSecondaryUser), is(true));

            Intent intent = receiverPrimary.getResult();
            assertThat("Primary user should get the broadcast.", intent, is(notNullValue()));
            assertThat("Incorrect broadcast action in primary user", intent.getAction(),
                    is(Intent.ACTION_PACKAGE_ADDED));

            intent = receiverSecondary.getResult();
            assertThat("Secondary user should get the broadcast.", intent, is(notNullValue()));
            assertThat("Incorrect broadcast action in secondary user", intent.getAction(),
                    is(Intent.ACTION_PACKAGE_ADDED));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        } finally {
            contextPrimary.unregisterReceiver(receiverPrimary);
            contextSecondary.unregisterReceiver(receiverSecondary);
            uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        }
    }

    @Test
    @RequireRunOnSecondaryUser
    public void testStaticSharedLibUninstallOnAllUsers_broadcastReceivedByAllUsers() {
        Context contextPrimary = createContextAsUser(mPrimaryUser);
        Context contextSecondary = createContextAsUser(mSecondaryUser);


        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        InstallUninstallBroadcastReceiver receiverPrimary = new InstallUninstallBroadcastReceiver();
        InstallUninstallBroadcastReceiver receiverSecondary =
                new InstallUninstallBroadcastReceiver();

        contextPrimary.registerReceiver(receiverPrimary, filter);
        contextSecondary.registerReceiver(receiverSecondary, filter);
        try {
            assertThat(installPackageAsUser(STATIC_LIB_PROVIDER1_APK,
                    contextSecondary.getPackageName(), mSecondaryUser), is(true));
            assertThat(uninstallPackage(STATIC_LIB_PROVIDER1_PKG), is(true));

            Intent intent = receiverPrimary.getResult();
            assertThat("Primary user should get the broadcast", intent, is(notNullValue()));
            assertThat("Incorrect broadcast action in primary user", intent.getAction(),
                    is(Intent.ACTION_PACKAGE_REMOVED));

            intent = receiverSecondary.getResult();
            assertThat("Secondary user should get the broadcast", intent, is(notNullValue()));
            assertThat("Incorrect broadcast action in secondary user", intent.getAction(),
                    is(Intent.ACTION_PACKAGE_REMOVED));
        } catch (InterruptedException e) {
            fail(e.getMessage());
        } finally {
            contextPrimary.unregisterReceiver(receiverPrimary);
            contextSecondary.unregisterReceiver(receiverSecondary);
        }
    }
}
