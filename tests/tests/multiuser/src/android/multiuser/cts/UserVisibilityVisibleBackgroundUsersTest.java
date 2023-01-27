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

package android.multiuser.cts;

import static com.android.bedstead.nene.TestApis.users;
import static com.android.bedstead.nene.types.OptionalBoolean.FALSE;

import static org.junit.Assert.assertThrows;

import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.view.Display;

import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

/**
 * Tests for user-related APIs that are only available on devices that
 * {@link UserManager#isVisibleBackgroundUsersSupported() support visible background users } (such
 * as cars with passenger displays), but DON'T support them started in the default display.
 *
 */
@AppModeFull(reason = "it's testing user features, not related to apps")
@RequireVisibleBackgroundUsers(reason = "Because class is testing exactly that")
public final class UserVisibilityVisibleBackgroundUsersTest
        extends UserVisibilityVisibleBackgroundUsersTestCase {

    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    @RequireRunOnInitialUser
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    @Test
    public void testStartVisibleBgUser_onDefaultDisplay() {
        UserReference user = users()
                .findUserOfType(users().supportedType(UserType.SECONDARY_USER_TYPE_NAME));

        assertThrows(IllegalArgumentException.class,
                () -> startVisibleBackgroundUser(user, Display.DEFAULT_DISPLAY));
    }
}
