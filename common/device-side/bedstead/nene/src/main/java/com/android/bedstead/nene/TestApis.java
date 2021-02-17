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

package com.android.bedstead.nene;

import com.android.bedstead.nene.packages.Packages;
import com.android.bedstead.nene.users.Users;

/**
 * Entry point to Nene Test APIs.
 */
public final class TestApis {
    private final Users mUsers = new Users();
    private final Packages mPackages = new Packages(this);

    /** Access Test APIs related to Users. */
    public Users users() {
        return mUsers;
    }

    /** Access Test APIs related to Packages. */
    public Packages packages() {
        return mPackages;
    }
}
