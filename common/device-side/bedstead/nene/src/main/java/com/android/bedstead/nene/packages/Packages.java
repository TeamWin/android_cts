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

import static android.Manifest.permission.INSTALL_PACKAGES;
import static android.Manifest.permission.INSTALL_TEST_ONLY_PACKAGE;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.pm.PackageInstaller.EXTRA_STATUS;
import static android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE;
import static android.content.pm.PackageInstaller.STATUS_FAILURE;
import static android.content.pm.PackageInstaller.STATUS_SUCCESS;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.nene.users.User.UserState.RUNNING_UNLOCKED;
import static com.android.compatibility.common.util.FileUtils.readInputStreamFully;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.CheckResult;
import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.User;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.BlockingIntentSender;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Test APIs relating to packages.
 */
public final class Packages {

    /** Reference to a Java resource. */
    public static final class JavaResource {
        private final String mName;

        private JavaResource(String name) {
            mName = name;
        }

        /** Reference a Java resource by name. */
        public static JavaResource javaResource(String name) {
            if (name == null) {
                throw new NullPointerException();
            }
            return new JavaResource(name);
        }

        @Override
        public String toString() {
            return "JavaResource{name=" + mName + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JavaResource)) return false;
            JavaResource that = (JavaResource) o;
            return mName.equals(that.mName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName);
        }
    }

    /** Reference to an Android resource. */
    public static final class AndroidResource {
        private final String mName;

        private AndroidResource(String name) {
            if (name == null) {
                throw new NullPointerException();
            }
            mName = name;
        }

        /** Reference an Android resource by name. */
        public static AndroidResource androidResource(String name) {
            return new AndroidResource(name);
        }

        @Override
        public String toString() {
            return "AndroidResource{name=" + mName + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AndroidResource)) return false;
            AndroidResource that = (AndroidResource) o;
            return mName.equals(that.mName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName);
        }
    }

    public static final Packages sInstance = new Packages();

    private Set<String> mFeatures = null;
    private final Context mInstrumentedContext;

    private final IntentFilter mPackageAddedIntentFilter =
            new IntentFilter(Intent.ACTION_PACKAGE_ADDED);

    private static final PackageManager sPackageManager =
            TestApis.context().instrumentedContext().getPackageManager();

    static final AdbPackageParser sParser = AdbPackageParser.get(SDK_INT);


    public Packages() {
        mPackageAddedIntentFilter.addDataScheme("package");
        mInstrumentedContext = TestApis.context().instrumentedContext();
    }

    /** Get the features available on the device. */
    public Set<String> features() {
        if (mFeatures == null) {
            mFeatures = new HashSet<>();
            PackageManager pm = TestApis.context().instrumentedContext().getPackageManager();
            FeatureInfo[] features = pm.getSystemAvailableFeatures();
            if (features != null) {
                Arrays.stream(features).map(f -> f.name).forEach(mFeatures::add);
            }
        }
        return mFeatures;
    }

    /** Get packages installed for the instrumented user. */
    public Collection<Package> installedForUser() {
        return installedForUser(TestApis.users().instrumented());
    }

    /** Resolve all packages installed for a given {@link UserReference}. */
    public Collection<Package> installedForUser(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        return TestApis.context().androidContextAsUser(user).getPackageManager()
                .getInstalledPackages(/* flags= */ 0)
                .stream()
                .map(i -> new Package(i.packageName))
                .collect(Collectors.toSet());
    }

    /** Install the {@link File} to the instrumented user. */
    public Package install(File apkFile) {
        return install(TestApis.users().instrumented(), apkFile);
    }

    /** Install a file as a byte array to the instrumented user. */
    public Package install(byte[] apkFile) {
        return install(TestApis.users().instrumented(), apkFile);
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
    public Package install(UserReference user, File apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }

        if (Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            return install(user, loadBytes(apkFile));
        }

        User resolvedUser = user.resolve();

        if (resolvedUser == null || resolvedUser.state() != RUNNING_UNLOCKED) {
            throw new NeneException("Packages can not be installed in non-started users "
                    + "(Trying to install into user " + resolvedUser + ")");
        }

        // This is not in the try because if the install fails we don't want to await the broadcast
        BlockingBroadcastReceiver broadcastReceiver =
                registerPackageInstalledBroadcastReceiver(user);

        try {
            // Expected output "Success"
            ShellCommand.builderForUser(user, "pm install")
                    .addOperand("-r") // Reinstall automatically
                    .addOperand("-t") // Allow test-only install
                    .addOperand(apkFile.getAbsolutePath())
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .asRoot()
                    .execute();

            return waitForPackageAddedBroadcast(broadcastReceiver);
        } catch (AdbException e) {
            throw new NeneException("Could not install " + apkFile + " for user " + user, e);
        } finally {
            broadcastReceiver.unregisterQuietly();
        }
    }

    private Package waitForPackageAddedBroadcast(
            BlockingBroadcastReceiver broadcastReceiver) {
        Intent intent = broadcastReceiver.awaitForBroadcast();
        if (intent == null) {
            throw new NeneException(
                    "Did not receive ACTION_PACKAGE_ADDED broadcast after installing package.");
        }
        // TODO(scottjonathan): Could this be flaky? what if something is added elsewhere at
        //  the same time...
        String installedPackageName = intent.getDataString().split(":", 2)[1];

        return TestApis.packages().find(installedPackageName);
    }

    // TODO: Move this somewhere reusable (in utils)
    private static byte[] loadBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readInputStreamFully(fis);
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
    public Package install(UserReference user, byte[] apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            return installPreS(user, apkFile);
        }

        User resolvedUser = user.resolve();

        if (resolvedUser == null || resolvedUser.state() != RUNNING_UNLOCKED) {
            throw new NeneException("Packages can not be installed in non-started users "
                    + "(Trying to install into user " + resolvedUser + ")");
        }

        // This is not inside the try because if the install is unsuccessful we don't want to await
        // the broadcast
        BlockingBroadcastReceiver broadcastReceiver =
                registerPackageInstalledBroadcastReceiver(user);

        try  {
            PackageManager packageManager =
                    TestApis.context().androidContextAsUser(user).getPackageManager();
            PackageInstaller packageInstaller = packageManager.getPackageInstaller();

            int sessionId;
            try (PermissionContext p = TestApis.permissions().withPermission(
                    INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, INSTALL_TEST_ONLY_PACKAGE)) {
                PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(
                        MODE_FULL_INSTALL);
                sessionParams.setInstallFlagAllowTest();
                sessionId = packageInstaller.createSession(sessionParams);
            }

            PackageInstaller.Session session = packageInstaller.openSession(sessionId);
            try (OutputStream out =
                         session.openWrite("NAME", 0, apkFile.length)) {
                out.write(apkFile);
                session.fsync(out);
            }

            try (BlockingIntentSender intentSender = BlockingIntentSender.create()) {
                try (PermissionContext p =
                             TestApis.permissions().withPermission(INSTALL_PACKAGES)) {
                    session.commit(intentSender.intentSender());
                    session.close();

                    Intent intent = intentSender.await();

                    if (intent.getIntExtra(EXTRA_STATUS, /* defaultValue= */ STATUS_FAILURE)
                            != STATUS_SUCCESS) {
                        throw new NeneException("Not successful while installing package. "
                                + "Got status: "
                                + intent.getIntExtra(
                                EXTRA_STATUS, /* defaultValue= */ STATUS_FAILURE)
                                + " extra info: " + intent.getStringExtra(EXTRA_STATUS_MESSAGE));
                    }
                }
            }

            return waitForPackageAddedBroadcast(broadcastReceiver);
        } catch (IOException e) {
            throw new NeneException("Could not install package", e);
        } finally {
            broadcastReceiver.unregisterQuietly();
        }
    }

    private Package installPreS(UserReference user, byte[] apkFile) {
        // Prior to S we cannot pass bytes to stdin so we write it to a temp file first
        File outputDir = TestApis.context().instrumentedContext().getFilesDir();
        File outputFile = null;
        try {
            // TODO(b/202705721): Replace this with fixed name
            outputFile = new File(outputDir, UUID.randomUUID() + ".apk");
            outputFile.getParentFile().mkdirs();
            try (FileOutputStream output = new FileOutputStream(outputFile)) {
                output.write(apkFile);
            }
            // Shell can't read the file in files dir, so we can move it to /data/local/tmp
            File localTmpFile = new File("/data/local/tmp", outputFile.getName());
            ShellCommand.builder("mv")
                    .addOperand(outputFile.getAbsolutePath())
                    .addOperand(localTmpFile.getAbsolutePath())
                    .asRoot()
                    .validate(String::isEmpty)
                    .allowEmptyOutput(true)
                    .execute();
            return install(user, localTmpFile);
        } catch (IOException e) {
            throw new NeneException("Error when writing bytes to temp file", e);
        } catch (AdbException e) {
            throw new NeneException("Error when moving file to /data/local/tmp", e);
        } finally {
            if (outputFile != null) {
                outputFile.delete();
            }
        }
    }

    /**
     * Install an APK stored in Android resources to the given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     */
    @Experimental
    public Package install(UserReference user, AndroidResource resource) {
        int indexId = mInstrumentedContext.getResources().getIdentifier(
                resource.mName, /* defType= */ null, /* defPackage= */ null);

        try (InputStream inputStream =
                     mInstrumentedContext.getResources().openRawResource(indexId)) {
            return install(user, readInputStreamFully(inputStream));
        } catch (IOException e) {
            throw new NeneException("Error reading resource " + resource, e);
        }
    }

    /**
     * Install an APK stored in Java resources to the given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     */
    @Experimental
    public Package install(UserReference user, JavaResource resource) {
        try (InputStream inputStream =
                     Packages.class.getClassLoader().getResourceAsStream(resource.mName)) {
            return install(user, readInputStreamFully(inputStream));
        } catch (IOException e) {
            throw new NeneException("Error reading java resource " + resource, e);
        }
    }

    private BlockingBroadcastReceiver registerPackageInstalledBroadcastReceiver(
            UserReference user) {
        BlockingBroadcastReceiver broadcastReceiver = BlockingBroadcastReceiver.create(
                TestApis.context().androidContextAsUser(user),
                mPackageAddedIntentFilter);

        if (user.equals(TestApis.users().instrumented())) {
            broadcastReceiver.register();
        } else {
            // TODO(scottjonathan): If this is cross-user then it needs _FULL, but older versions
            //  cannot get full - so we'll need to poll
            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                broadcastReceiver.register();
            }
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
        Versions.requireMinimumVersion(Build.VERSION_CODES.S);

        return new KeepUninstalledPackagesBuilder();
    }

    /**
     * Get a reference to a package with the given {@code packageName}.
     *
     * <p>This does not guarantee that the package exists. Call {@link Package#exists()}
     * to find if the package exists on the device, or {@link Package#installedOnUsers()}
     * to find the users it is installed for.
     */
    public Package find(String packageName) {
        if (packageName == null) {
            throw new NullPointerException();
        }
        return new Package(packageName);
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

        return new ComponentReference(
                find(componentName.getPackageName()), componentName.getClassName());
    }

    /** Get a reference to the package being instrumented. */
    @Experimental
    public Package instrumented() {
        return find(TestApis.context().instrumentedContext().getPackageName());
    }
}
