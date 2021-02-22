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

import androidx.annotation.Nullable;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import java.io.File;

/**
 * A representation of a package on device which may or may not exist.
 *
 * <p>To resolve the package into a {@link Package}, see {@link #resolve()}.
 */
public abstract class PackageReference {

    private final Packages mPackages;
    private final String mPackageName;

    PackageReference(Packages packages, String packageName) {
        mPackages = packages;
        mPackageName = packageName;
    }

    /** Return the package's name. */
    public String packageName() {
        return mPackageName;
    }

    /**
     * Get the current state of the {@link Package} from the device, or {@code null} if the package
     * does not exist.
     */
    @Nullable
    public Package resolve() {
        return mPackages.fetchPackage(mPackageName);
    }

    /**
     * Install the package on the given user.
     *
     * <p>If you wish to install a package which is not already installed on another user, see
     * {@link Packages#install(UserReference, File)}.
     */
    public PackageReference install(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }
        try {
            // Expected output "Package X installed for user: Y"
            ShellCommand.builderForUser(user, "pm install-existing")
                    .addOperand(mPackageName)
                    .validate(
                            (output) -> output.contains("installed for user"))
                    .execute();
            return this;
        } catch (AdbException e) {
            throw new NeneException("Could not install-existing package " + this, e);
        }
    }

    /**
     * Uninstall the package for the given user.
     *
     * <p>If this is the last user which has this package installed, then the package will no
     * longer {@link #resolve()}.
     */
    public PackageReference uninstall(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }
        try {
            // Expected output "Success"
            ShellCommand.builderForUser(user, "pm uninstall")
                    .addOperand(mPackageName)
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();
            return this;
        } catch (AdbException e) {
            throw new NeneException("Could not uninstall package " + this, e);
        }
    }

    @Override
    public int hashCode() {
        return mPackageName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PackageReference)) {
            return false;
        }

        PackageReference other = (PackageReference) obj;
        return other.mPackageName.equals(mPackageName);
    }
}
