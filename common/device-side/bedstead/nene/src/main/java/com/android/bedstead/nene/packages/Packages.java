/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.packages;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.nene.users.User.UserState.RUNNING_UNLOCKED;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.User;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test APIs relating to packages.
 */
public final class Packages {

    private Map<String, Package> mCachedPackages = null;
    private Set<String> mFeatures = null;
    private final AdbPackageParser mParser;
    final TestApis mTestApis;

    private final IntentFilter mPackageAddedIntentFilter =
            new IntentFilter(Intent.ACTION_PACKAGE_ADDED);


    public Packages(TestApis testApis) {
        if (testApis == null) {
            throw new NullPointerException();
        }
        mPackageAddedIntentFilter.addDataScheme("package");
        mTestApis = testApis;
        mParser = AdbPackageParser.get(mTestApis, SDK_INT);
    }


    /** Get the features available on the device. */
    public Set<String> features() {
        if (mFeatures == null) {
            fillCache();
        }

        return mFeatures;
    }

    /** Resolve all packages on the device. */
    public Collection<PackageReference> all() {
        return new HashSet<>(allResolved());
    }

    /** Resolve all packages installed for a given {@link UserReference}. */
    public Collection<PackageReference> installedForUser(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }
        Set<PackageReference> installedForUser = new HashSet<>();

        for (Package pkg : allResolved()) {
            if (pkg.installedOnUsers().contains(user)) {
                installedForUser.add(pkg);
            }
        }

        return installedForUser;
    }

    private Collection<Package> allResolved() {
        fillCache();

        return mCachedPackages.values();
    }

    /**
     * Install an APK file to a given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     */
    public PackageReference install(UserReference user, File apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }

        if (Versions.isRunningOn(Versions.S, "S")) {
            return install(user, loadBytes(apkFile));
        }

        BlockingBroadcastReceiver broadcastReceiver = BlockingBroadcastReceiver.create(
                mTestApis.context().instrumentedContext(), mPackageAddedIntentFilter);
        broadcastReceiver.register();

        try {
            // Expected output "Success"
            ShellCommand.builderForUser(user, "pm install")
                    .addOperand("-r") // Reinstall automatically
                    .addOperand(apkFile.getAbsolutePath())
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();

            return waitForPackageAddedBroadcast(broadcastReceiver);
        } catch (AdbException e) {
            User resolvedUser = user.resolve();

            if (resolvedUser == null || resolvedUser.state() != RUNNING_UNLOCKED) {
                throw new NeneException("Packages can not be installed in non-started users "
                        + "(Trying to install into user " + resolvedUser + ")");
            }
            throw new NeneException("Could not install " + apkFile + " for user " + user, e);
        } finally {
            broadcastReceiver.unregisterQuietly();
        }
    }

    private PackageReference waitForPackageAddedBroadcast(
            BlockingBroadcastReceiver broadcastReceiver) {
        Intent intent = broadcastReceiver.awaitForBroadcast();
        if (intent == null) {
            throw new NeneException(
                    "Did not receive ACTION_PACKAGE_ADDED broadcast after installing package.");
        }
        // TODO(scottjonathan): Could this be flaky? what if something is added elsewhere at
        //  the same time...
        String installedPackageName = intent.getDataString().split(":", 2)[1];
        return mTestApis.packages().find(installedPackageName);
    }

    // TODO: Move this somewhere reusable (in utils)
    private static byte[] loadBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return FileUtils.readInputStreamFully(fis);
        } catch (IOException e) {
            throw new NeneException("Could not read file bytes for file " + file);
        }
    }

    /**
     * Install an APK from the given byte array to a given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     */
    public PackageReference install(UserReference user, byte[] apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }

        //        if (!Versions.isRunningOn(Versions.S, "S")) {
        return installPreS(user, apkFile);
//        }

        // TODO(scottjonathan): Re-enable this after we have a TestAPI which allows us to install
        //   testOnly apks
//        BlockingBroadcastReceiver broadcastReceiver =
//                registerPackageInstalledBroadcastReceiver(user);
//
//        PackageManager packageManager =
//                mTestApis.context().androidContextAsUser(user).getPackageManager();
//        PackageInstaller packageInstaller = packageManager.getPackageInstaller();
//
//        try {
//            int sessionId;
//            try(PermissionContext p =
//                        mTestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
//                PackageInstaller.SessionParams sessionParams =
//                      new PackageInstaller.SessionParams(MODE_FULL_INSTALL);
//                // TODO(scottjonathan): Enable installing test apps once there is a test
//                //  API for this
////                    sessionParams.installFlags =
//                          sessionParams.installFlags | INSTALL_ALLOW_TEST;
//                sessionId = packageInstaller.createSession(sessionParams);
//            }
//
//            PackageInstaller.Session session = packageInstaller.openSession(sessionId);
//            try (OutputStream out =
//                         session.openWrite("NAME", 0, apkFile.length)) {
//                out.write(apkFile);
//                session.fsync(out);
//            }
//
//            try (BlockingIntentSender intentSender = BlockingIntentSender.create()) {
//                try (PermissionContext p =
//                             mTestApis.permissions().withPermission(INSTALL_PACKAGES)) {
//                    session.commit(intentSender.intentSender());
//                    session.close();
//                }
//
//                Intent intent = intentSender.await();
//                if (intent.getIntExtra(EXTRA_STATUS, /* defaultValue= */ STATUS_FAILURE)
//                        != STATUS_SUCCESS) {
//                    throw new NeneException("Not successful while installing package. "
//                            + "Got status: "
//                            + intent.getIntExtra(
//                            EXTRA_STATUS, /* defaultValue= */ STATUS_FAILURE)
//                            + " exta info: " + intent.getStringExtra(EXTRA_STATUS_MESSAGE));
//                }
//            }
//
//            return waitForPackageAddedBroadcast(broadcastReceiver);
//        } catch (IOException e) {
//            throw new NeneException("Could not install package", e);
//        } finally {
//            broadcastReceiver.unregisterQuietly();
//        }
    }


    private PackageReference installPreS(UserReference user, byte[] apkFile) {
        User resolvedUser = user.resolve();

        if (resolvedUser == null || resolvedUser.state() != RUNNING_UNLOCKED) {
            throw new NeneException("Packages can not be installed in non-started users "
                    + "(Trying to install into user " + resolvedUser + ")");
        }

        BlockingBroadcastReceiver broadcastReceiver =
                registerPackageInstalledBroadcastReceiver(user);
        try {
            // Expected output "Success"
            ShellCommand.builderForUser(user, "pm install")
                    .addOption("-S", apkFile.length)
                    .addOperand("-r")
                    .addOperand("-t")
                    .writeToStdIn(apkFile)
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();

            return waitForPackageAddedBroadcast(broadcastReceiver);
        } catch (AdbException e) {
            throw new NeneException("Could not install from bytes for user " + user, e);
        } finally {
            broadcastReceiver.unregisterQuietly();
        }
    }

    private BlockingBroadcastReceiver registerPackageInstalledBroadcastReceiver(
            UserReference user) {
        BlockingBroadcastReceiver broadcastReceiver = BlockingBroadcastReceiver.create(
                mTestApis.context().androidContextAsUser(user),
                mPackageAddedIntentFilter);

        try (PermissionContext p =
                    mTestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            broadcastReceiver.register();
        }

        return broadcastReceiver;
    }

    /**
     * Set packages which will not be cleaned up by the system even if they are not installed on
     * any user.
     *
     * <p>This will ensure they can still be resolved and re-installed without needing the APK
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @CheckResult
    public KeepUninstalledPackagesBuilder keepUninstalledPackages() {
        Versions.requireS();

        return new KeepUninstalledPackagesBuilder(mTestApis);
    }

    @Nullable
    Package fetchPackage(String packageName) {
        // TODO(scottjonathan): fillCache probably does more than we need here -
        //  can we make it more efficient?
        fillCache();

        return mCachedPackages.get(packageName);
    }

    /**
     * Get a reference to a package with the given {@code packageName}.
     *
     * <p>This does not guarantee that the package exists. Call {@link PackageReference#resolve()}
     * to find specific details about the package on the device.
     */
    public PackageReference find(String packageName) {
        if (packageName == null) {
            throw new NullPointerException();
        }
        return new UnresolvedPackage(mTestApis, packageName);
    }

    /**
     * Get a reference to a given {@code componentName}.
     *
     * <p>This does not guarantee that the component exists.
     */
    @Experimental
    public ComponentReference component(ComponentName componentName) {
        if (componentName == null) {
            throw new NullPointerException();
        }

        return new ComponentReference(mTestApis,
                find(componentName.getPackageName()), componentName.getClassName());
    }

    private void fillCache() {
        try {
            // TODO: Replace use of adb on supported versions of Android
            String packageDumpsysOutput = ShellCommand.builder("dumpsys package").execute();
            AdbPackageParser.ParseResult result = mParser.parse(packageDumpsysOutput);

            mCachedPackages = result.mPackages;
            mFeatures = result.mFeatures;
        } catch (AdbException | AdbParseException e) {
            throw new RuntimeException("Error filling cache", e);
        }
    }
}
