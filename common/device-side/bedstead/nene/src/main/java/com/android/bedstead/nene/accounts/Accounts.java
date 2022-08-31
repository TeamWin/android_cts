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

package com.android.bedstead.nene.accounts;

import static com.android.bedstead.nene.permissions.CommonPermissions.GET_ACCOUNTS;
import static com.android.bedstead.nene.permissions.CommonPermissions.READ_CONTACTS;

import android.accounts.AccountManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.permissions.PermissionContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point to Nene Accounts.
 */
@Experimental
public final class Accounts {

    public static final Accounts sInstance = new Accounts();

    private static final AccountManager sAccountManager =
            TestApis.context().instrumentedContext().getSystemService(AccountManager.class);

    private Accounts() {

    }

    /**
     * Get all accounts of the given type.
     */
    // TODO: Wrap account with our own type
    public Set<AccountReference> getByType(String type) {
        // READ_CONTACTS allows access to accounts which manage contacts
        try (PermissionContext p = TestApis.permissions().withPermission(
                GET_ACCOUNTS, READ_CONTACTS)) {
            return Arrays.stream(sAccountManager.getAccountsByType(type))
                    .map(AccountReference::new).collect(Collectors.toSet());
        }
    }
}
