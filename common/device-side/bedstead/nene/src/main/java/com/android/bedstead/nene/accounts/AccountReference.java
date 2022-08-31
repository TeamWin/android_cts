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

import android.accounts.Account;

import com.android.bedstead.nene.annotations.Experimental;

/**
 * An Account on the device.
 */
@Experimental
public final class AccountReference {

    private final Account mAndroidAccount;

    AccountReference(Account androidAccount) {
        mAndroidAccount = androidAccount;
    }

    @Override
    public int hashCode() {
        return mAndroidAccount.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AccountReference)) {
            return false;
        }
        return ((AccountReference) obj).mAndroidAccount.equals(mAndroidAccount);
    }
}
