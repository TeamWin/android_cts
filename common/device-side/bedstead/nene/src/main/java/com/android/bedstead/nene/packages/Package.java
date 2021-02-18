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

import com.android.bedstead.nene.users.UserReference;

import java.util.Set;

/**
 * Resolved information about a package on the device.
 */
public class Package extends PackageReference {

    static final class MutablePackage {
        String mPackageName;
        Set<UserReference> mInstalledOnUsers;
    }

    private final MutablePackage mMutablePackage;

    Package(Packages packages, MutablePackage mutablePackage) {
        super(packages, mutablePackage.mPackageName);
        mMutablePackage = mutablePackage;
    }

    /** Get {@link UserReference}s who have this {@link Package} installed. */
    public Set<UserReference> installedOnUsers() {
        return mMutablePackage.mInstalledOnUsers;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("Package{");
        stringBuilder.append("packageName=" + mMutablePackage.mPackageName);
        stringBuilder.append("installedOnUsers=" + mMutablePackage.mInstalledOnUsers);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }
}
