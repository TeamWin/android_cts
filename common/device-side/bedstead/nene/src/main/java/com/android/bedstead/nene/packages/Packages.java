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

import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.nene.users.User.UserState.RUNNING_UNLOCKED;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.User;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import java.io.File;
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
    private final AdbPackageParser mParser = AdbPackageParser.get(this, SDK_INT);
    final TestApis mTestApis;

    public Packages(TestApis testApis) {
        if (testApis == null) {
            throw new NullPointerException();
        }
        mTestApis = testApis;
    }


    /** Get the features available on the device. */
    public Set<String> features() {
        if (mFeatures == null) {
            fillCache();
        }

        return mFeatures;
    }

    /** Resolve all packages on the device. */
    public Collection<Package> all() {
        fillCache();

        return mCachedPackages.values();
    }

    /** Resolve all packages installed for a given {@link UserReference}. */
    public Collection<Package> installedForUser(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }
        Set<Package> installedForUser = new HashSet<>();

        for (Package pkg : all()) {
            if (pkg.installedOnUsers().contains(user)) {
                installedForUser.add(pkg);
            }
        }

        return installedForUser;
    }

    /**
     * Install an APK file to a given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     */
    public void install(UserReference user, File apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }
        checkUserStartedBeforeInstall(user);

        // By default when using ADB we don't know the package name of the file upon success.
        // we could make an additional call to get it (either parsing all installed and finding the
        // one matching the apk, or by trying to install again and parsing the error - this would
        // only work before P because after P there isn't an error) - but that
        // would mean we are making two adb calls rather than one - needs to be decided.

        try {
            // Expected output "Success"
            ShellCommand.builderForUser(user, "pm install")
                    .addOperand("-r") // Reinstall automatically
                    .addOperand(apkFile.getAbsolutePath())
                    .executeAndValidateOutput(ShellCommandUtils::startsWithSuccess);
        } catch (AdbException e) {
            throw new NeneException("Could not install " + apkFile + " for user " + user, e);
        }
    }

    /**
     * Install an APK from the given byte array to a given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     */
    public void install(UserReference user, byte[] apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }

        checkUserStartedBeforeInstall(user);

        try {
            // Expected output "Success"
            ShellCommand.builderForUser(user, "pm install")
                    .addOption("-S", apkFile.length)
                    .addOperand("-r")
                    .writeToStdIn(apkFile)
                    .executeAndValidateOutput(ShellCommandUtils::startsWithSuccess);
        } catch (AdbException e) {
            throw new NeneException("Could not install from bytes for user " + user, e);
        }
    }

    private void checkUserStartedBeforeInstall(UserReference user) {
        User resolvedUser = user.resolve();
        // TODO(scottjonathan): Consider if it's worth the additional call here - we could
        //  optionally instead timeout the shell command (it doesn't respond if the user isn't
        //  started)
        if (resolvedUser == null || resolvedUser.state() != RUNNING_UNLOCKED) {
            throw new NeneException("Packages can not be installed in non-started users "
                    + "(Trying to install into user " + resolvedUser + ")");
        }
    }

    @Nullable
    Package fetchPackage(String packageName) {
        // TODO(scottjonathan): fillCache probably does more than we need here -
        //  can we make it more efficient?
        fillCache();

        Package pkg = mCachedPackages.get(packageName);
        if (pkg == null || pkg.installedOnUsers().isEmpty()) {
            return null; // Treat it as uninstalled once all users are removed/removing
        }

        return pkg;
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
        return new UnresolvedPackage(this, packageName);
    }

    private void fillCache() {
        try {
            // TODO: Replace use of adb on supported versions of Android
            String packageDumpsysOutput = ShellCommandUtils.executeCommand("dumpsys package");
            AdbPackageParser.ParseResult result = mParser.parse(packageDumpsysOutput);

            mCachedPackages = result.mPackages;
            mFeatures = result.mFeatures;
        } catch (AdbException | AdbParseException e) {
            throw new RuntimeException("Error filling cache", e);
        }
    }
}
