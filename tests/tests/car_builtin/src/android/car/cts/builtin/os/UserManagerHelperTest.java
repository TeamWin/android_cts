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

package android.car.cts.builtin.os;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.car.builtin.os.UserManagerHelper;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;

public final class UserManagerHelperTest {

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private Context mContext;
    private UserManager mUserManager;

    @Before
    public void setup() {
        mContext = mInstrumentation.getContext();
        mUserManager = mContext.getSystemService(UserManager.class);
    }

    @Test
    public void testPreCreateUser_fullUser() {
        preCreateUserTest(UserManager.USER_TYPE_FULL_SECONDARY);
    }

    @Test
    public void testPreCreateUser_guestUser() {
        preCreateUserTest(UserManager.USER_TYPE_FULL_GUEST);
    }

    private void preCreateUserTest(String type) {
        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    android.Manifest.permission.CREATE_USERS);

            UserHandle user = UserManagerHelper.preCreateUser(mUserManager, type);

            assertPrecreatedUserExists(user, type);
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void assertPrecreatedUserExists(UserHandle user, String type) {
        assertThat(user).isNotNull();
        try {
            String allUsers = SystemUtil.runShellCommand("cmd user list --all -v");
            String[] result = allUsers.split("\n");
            for (int i = 0; i < result.length; i++) {
                if (result[i].contains("id=" + user.getIdentifier())) {
                    assertThat(result[i]).contains("(pre-created)");
                    if (type == UserManager.USER_TYPE_FULL_SECONDARY) {
                        assertThat(result[i]).contains("type=full.SECONDARY");
                    }
                    if (type == UserManager.USER_TYPE_FULL_GUEST) {
                        assertThat(result[i]).contains("type=full.GUEST");
                    }
                    return;
                }
            }
            fail("User not found. All users: " + allUsers + ". Expected user: " + user);
        } finally {
            // Remove User
            mUserManager.removeUser(user);
        }
    }
}
