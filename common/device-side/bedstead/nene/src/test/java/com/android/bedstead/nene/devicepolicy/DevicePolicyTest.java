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

package com.android.bedstead.nene.devicepolicy;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.eventlib.premade.EventLibDeviceAdminReceiver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DevicePolicyTest {

    //  TODO(180478924): We shouldn't need to hardcode this
    private static final String DEVICE_ADMIN_TESTAPP_PACKAGE_NAME = "android.DeviceAdminTestApp";
    private static final ComponentName DPC_COMPONENT_NAME =
            new ComponentName(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME,
                    EventLibDeviceAdminReceiver.class.getName());
    private static final ComponentName NOT_DPC_COMPONENT_NAME =
            new ComponentName(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME,
                    "incorrect.class.name");

    private static final TestApis sTestApis = new TestApis();
    private static final UserReference sUser = sTestApis.users().instrumented();
    private static final UserReference NON_EXISTENT_USER = sTestApis.users().find(99999);

    private static TestApp sTestApp;

    @BeforeClass
    public static void setupClass() {
        sTestApp = new TestAppProvider().query()
                .withPackageName(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME)
                .get();

        sTestApp.install(sUser);
    }

    @AfterClass
    public static void teardownClass() {
        sTestApp.reference().uninstall(sUser);
    }

    @Test
    public void setProfileOwner_profileOwnerIsSet() {
        UserReference profile = sTestApis.users().createUser()
                .parent(sUser)
                .type(sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sTestApp.install(profile);

        ProfileOwner profileOwner =
                sTestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME);

        try {
            assertThat(sTestApis.devicePolicy().getProfileOwner(profile)).isEqualTo(profileOwner);
        } finally {
            profile.remove();
        }
    }

    @Test
    public void setProfileOwner_profileOwnerIsAlreadySet_throwsException() {
        UserReference profile = sTestApis.users().createUser()
                .parent(sUser)
                .type(sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sTestApp.install(profile);
        sTestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME);

        try {
            assertThrows(NeneException.class,
                    () -> sTestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME));
        } finally {
            profile.remove();
        }
    }

    @Test
    public void setProfileOwner_componentNameNotInstalled_throwsException() {
        UserReference profile = sTestApis.users().createUser()
                .parent(sUser)
                .type(sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            assertThrows(NeneException.class,
                    () -> sTestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME));
        } finally {
            profile.remove();
        }
    }

    @Test
    public void setProfileOwner_componentNameIsNotDPC_throwsException() {
        assertThrows(NeneException.class,
                () -> sTestApis.devicePolicy().setProfileOwner(sUser, NOT_DPC_COMPONENT_NAME));
    }

    @Test
    public void setProfileOwner_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> sTestApis.devicePolicy().setProfileOwner(
                        /* user= */ null, DPC_COMPONENT_NAME));
    }

    @Test
    public void setProfileOwner_nullComponentName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> sTestApis.devicePolicy().setProfileOwner(
                        sUser, /* profileOwnerComponent= */ null));
    }

    @Test
    public void setProfileOwner_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class,
                () -> sTestApis.devicePolicy().setProfileOwner(
                        NON_EXISTENT_USER, DPC_COMPONENT_NAME));
    }

    @Test
    public void getProfileOwner_returnsProfileOwner() {
        UserReference profile = sTestApis.users().createUser()
                .parent(sUser)
                .type(sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sTestApp.install(profile);
        ProfileOwner profileOwner =
                sTestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME);

        try {
            assertThat(sTestApis.devicePolicy().getProfileOwner(profile)).isEqualTo(profileOwner);
        } finally {
            profile.remove();
        }
    }

    @Test
    public void getProfileOwner_noProfileOwner_returnsNull() {
        UserReference profile = sTestApis.users().createUser()
                .parent(sUser)
                .type(sTestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();

        try {
            assertThat(sTestApis.devicePolicy().getProfileOwner(profile)).isNull();
        } finally {
            profile.remove();
        }

    }

    @Test
    public void getProfileOwner_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> sTestApis.devicePolicy().getProfileOwner(null));
    }

    @Test
    public void setDeviceOwner_deviceOwnerIsSet() {
        DeviceOwner deviceOwner =
                sTestApis.devicePolicy().setDeviceOwner(sUser, DPC_COMPONENT_NAME);

        try {
            assertThat(sTestApis.devicePolicy().getDeviceOwner()).isEqualTo(deviceOwner);
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    public void setDeviceOwner_deviceOwnerIsAlreadySet_throwsException() {
        DeviceOwner deviceOwner =
                sTestApis.devicePolicy().setDeviceOwner(sUser, DPC_COMPONENT_NAME);

        try {
            assertThrows(NeneException.class,
                    () -> sTestApis.devicePolicy().setDeviceOwner(sUser, DPC_COMPONENT_NAME));
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    public void setDeviceOwner_componentNameNotInstalled_throwsException() {
        sTestApp.reference().uninstall(sUser);
        try {
            assertThrows(NeneException.class,
                    () -> sTestApis.devicePolicy().setDeviceOwner(sUser, DPC_COMPONENT_NAME));
        } finally {
            sTestApp.install(sUser);
        }
    }

    @Test
    public void setDeviceOwner_componentNameIsNotDPC_throwsException() {
        assertThrows(NeneException.class,
                () -> sTestApis.devicePolicy().setDeviceOwner(sUser, NOT_DPC_COMPONENT_NAME));
    }

    @Test
    public void setDeviceOwner_userAlreadyOnDevice_throwsException() {
        UserReference user = sTestApis.users().createUser().create();

        try {
            assertThrows(NeneException.class,
                    () -> sTestApis.devicePolicy().setDeviceOwner(sUser, DPC_COMPONENT_NAME));
        } finally {
            user.remove();
        }
    }

    @Test
    @Ignore("TODO: Update once account support is added to Nene")
    public void setDeviceOwner_accountAlreadyOnDevice_throwsException() {
    }

    @Test
    public void setDeviceOwner_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> sTestApis.devicePolicy().setDeviceOwner(
                        /* user= */ null, DPC_COMPONENT_NAME));
    }

    @Test
    public void setDeviceOwner_nullComponentName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> sTestApis.devicePolicy().setDeviceOwner(
                        sUser, /* deviceOwnerComponent= */ null));
    }

    @Test
    public void setDeviceOwner_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class,
                () -> sTestApis.devicePolicy().setDeviceOwner(
                        NON_EXISTENT_USER, DPC_COMPONENT_NAME));
    }

    @Test
    public void getDeviceOwner_returnsDeviceOwner() {
        DeviceOwner deviceOwner =
                sTestApis.devicePolicy().setDeviceOwner(sUser, DPC_COMPONENT_NAME);

        try {
            assertThat(sTestApis.devicePolicy().getDeviceOwner()).isEqualTo(deviceOwner);
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    public void getDeviceOwner_noDeviceOwner_returnsNull() {
        // We must assume no device owner entering the test
        // TODO(scottjonathan): Encode this assumption in the annotations when Harrier supports
        assertThat(sTestApis.devicePolicy().getDeviceOwner()).isNull();
    }
}
