/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.res.Resources;
import android.os.UserManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

public final class InputMethodManagerServiceProxyTest {

    protected final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private UserManager mUserManager;
    private Resources mResources;

    @Before
    public void setUp() {
        mUserManager = mContext.getSystemService(UserManager.class);
        mResources = mContext.getResources();
    }

    @Test
    public void testIsVisibleBackgroundUsersSupportedIsInSyncWithIMMSProxy() {
        assumeTrue(mUserManager.isVisibleBackgroundUsersSupported());

        String immsProxyClass = mResources.getString(mResources.getIdentifier(
                "config_deviceSpecificInputMethodManagerService", "string", "android"));
        assertThat(immsProxyClass).isNotEmpty();
    }
}
