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

package com.android.bedstead.nene.settings;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class SecureSettingsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final String KEY = "key";
    private static final String INVALID_KEY = "noKey";
    private static final int INT_VALUE = 123;

    @After
    public void resetSecureSettings() {
        sTestApis.settings().secure().reset();
    }

    @Test
    public void putInt_putsIntIntoSecureSettingsOnInstrumentedUser() throws Exception {
        sTestApis.settings().secure().putInt(KEY, INT_VALUE);

        assertThat(android.provider.Settings.Secure.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putIntWithContentResolver_putsIntIntoSecureSettings() throws Exception {
        sTestApis.settings().secure().putInt(sContext.getContentResolver(), KEY, INT_VALUE);

        assertThat(android.provider.Settings.Secure.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putIntWithContentResolver_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                sTestApis.settings().secure().putInt(
                        sContext.getContentResolver(), KEY, INT_VALUE));
    }

    @Test
    public void putIntWithUser_instrumentedUser_putsIntIntoSecureSettings() throws Exception {
        sTestApis.settings().secure().putInt(sTestApis.users().instrumented(), KEY, INT_VALUE);

        assertThat(android.provider.Settings.Secure.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putIntWithUser_differentUser_putsIntIntoSecureSettings() throws Exception {
        sTestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        try (PermissionContext p =
                     sTestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            assertThat(android.provider.Settings.Secure.getInt(
                    sTestApis.context().androidContextAsUser(sDeviceState.secondaryUser())
                            .getContentResolver(), KEY)).isEqualTo(INT_VALUE);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putIntWithUser_differentUser_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                sTestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE));
    }

    @Test
    public void getInt_getsIntFromSecureSettingsOnInstrumentedUser() {
        sTestApis.settings().secure().putInt(KEY, INT_VALUE);

        assertThat(sTestApis.settings().secure().getInt(KEY)).isEqualTo(INT_VALUE);
    }

    @Test
    public void getInt_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> sTestApis.settings().secure().getInt(INVALID_KEY));
    }

    @Test
    public void getInt_invalidKey_withDefault_returnsDefault() {
        assertThat(sTestApis.settings().secure().getInt(INVALID_KEY, INT_VALUE)).isEqualTo(
                INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_getsIntFromSecureSettings() {
        sTestApis.settings().secure().putInt(
                sTestApis.context().instrumentedContext().getContentResolver(), KEY, INT_VALUE);

        assertThat(sTestApis.settings().secure().getInt(
                sTestApis.context().instrumentedContext().getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_preS_throwsException() {
        assertThrows(NeneException.class, () -> sTestApis.settings().secure().getInt(
                sTestApis.context().instrumentedContext().getContentResolver(), KEY));
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> sTestApis.settings().secure().getInt(
                        sTestApis.context().instrumentedContext().getContentResolver(),
                        INVALID_KEY));
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_invalidKey_withDefault_returnsDefault() {
        assertThat(sTestApis.settings().secure().getInt(
                sTestApis.context().instrumentedContext().getContentResolver(),
                INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE);
    }

    @Test
    public void getIntWithUser_instrumentedUser_getsIntFromSecureSettings() {
        sTestApis.settings().secure().putInt(KEY, INT_VALUE);

        assertThat(sTestApis.settings().secure().getInt(sTestApis.users().instrumented(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    public void getIntWithUser_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> sTestApis.settings().secure().getInt(
                        sTestApis.users().instrumented(), INVALID_KEY));
    }

    @Test
    public void getIntWithUser_invalidKey_withDefault_returnsDefault() {
        assertThat(sTestApis.settings().secure().getInt(
                sTestApis.users().instrumented(), INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithUser_differentUser_getsIntFromSecureSettings() {
        sTestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        assertThat(sTestApis.settings().secure().getInt(
                sDeviceState.secondaryUser(), KEY)).isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void getIntWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            sTestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        });
    }

    @Test
    public void reset_resetsSecureSettings() {
        sTestApis.settings().secure().putInt(KEY, INT_VALUE);

        sTestApis.settings().secure().reset();

        assertThrows(NeneException.class, () -> sTestApis.settings().secure().getInt(KEY));
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void resetWithContentResolver_resetsSecureSettings() {
        ContentResolver contentResolver =
                sTestApis.context().instrumentedContext().getContentResolver();
        sTestApis.settings().secure().putInt(contentResolver, KEY, INT_VALUE);

        sTestApis.settings().secure().reset(contentResolver);

        assertThrows(NeneException.class,
                () -> sTestApis.settings().secure().getInt(
                        contentResolver,
                        KEY));
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void resetWithContentResolver_preS_throwsException() {
        ContentResolver contentResolver =
                sTestApis.context().instrumentedContext().getContentResolver();

        assertThrows(UnsupportedOperationException.class,
                () -> sTestApis.settings().secure().reset(contentResolver));
    }

    @Test
    public void resetWithUser_instrumentedUser_resetsSecureSettings() {
        sTestApis.settings().secure().putInt(sTestApis.users().instrumented(), KEY, INT_VALUE);

        sTestApis.settings().secure().reset(sTestApis.users().instrumented());

        assertThrows(NeneException.class,
                () -> sTestApis.settings().secure().getInt(
                        sTestApis.users().instrumented(),
                        KEY));
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Ignore("b/194669450")
    public void resetWithUser_differentUser_resetsSecureSettings() {
        sTestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        sTestApis.settings().secure().reset(sDeviceState.secondaryUser());

        assertThrows(NeneException.class,
                () -> sTestApis.settings().secure().getInt(
                        sDeviceState.secondaryUser(),
                        KEY));
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void resetWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class,
                () -> sTestApis.settings().secure().reset(sDeviceState.secondaryUser()));
    }
}
