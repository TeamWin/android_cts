/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.provider.cts.contactkeys.privilegedapp;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactKeysManager;
import android.provider.ContactKeysManager.ContactKey;
import android.provider.ContactKeysManager.SelfKey;
import android.provider.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_USER_KEYS)
@RunWith(AndroidJUnit4.class)
public class PrivilegedAppTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private static final String UPDATE_VERIFICATION_STATE_PERMISSION =
            "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS";

    private static final String HELPER_APP_PACKAGE = "android.provider.cts.visibleapp";

    // Values used by the VisibleService of the Helper app package.
    private static final String LOOKUP_KEY = "0r1-423A2E4644502A2E50";
    public static final String DEVICE_ID = "someDeviceId";
    public static final String ACCOUNT_ID = "someAccountId";

    private ContactKeysManager mContactKeysManager;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        mContactKeysManager = (ContactKeysManager) mContext
                .getSystemService(Context.CONTACT_KEYS_SERVICE);
    }

    @Test
    public void testUpdateContactKeyRemoteVerificationState_updatesState() {
        List<ContactKey> contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mContactKeysManager.updateContactKeyRemoteVerificationState(
                    LOOKUP_KEY, DEVICE_ID,
                    ACCOUNT_ID, HELPER_APP_PACKAGE,
                    ContactKeysManager.VERIFICATION_STATE_VERIFIED);
        }, UPDATE_VERIFICATION_STATE_PERMISSION);

        contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);
        assertThat(contactKeys.get(0).getRemoteVerificationState())
                .isEqualTo(ContactKeysManager.VERIFICATION_STATE_VERIFIED);
    }

    @Test
    public void testUpdateContactKeyLocalVerificationState_updatesState() {
        List<ContactKey> contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mContactKeysManager.updateContactKeyLocalVerificationState(
                    LOOKUP_KEY, DEVICE_ID,
                    ACCOUNT_ID, HELPER_APP_PACKAGE,
                    ContactKeysManager.VERIFICATION_STATE_VERIFIED);
        }, UPDATE_VERIFICATION_STATE_PERMISSION);

        contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);
        assertThat(contactKeys.get(0).getLocalVerificationState())
                .isEqualTo(ContactKeysManager.VERIFICATION_STATE_VERIFIED);
    }

    @Test
    public void testUpdateSelfKeyRemoteVerificationState_updatesState() {
        List<SelfKey> selfKeys = mContactKeysManager.getAllSelfKeys();
        assertThat(selfKeys.size()).isEqualTo(1);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mContactKeysManager.updateSelfKeyRemoteVerificationState(
                    DEVICE_ID, ACCOUNT_ID,
                    HELPER_APP_PACKAGE, ContactKeysManager.VERIFICATION_STATE_VERIFIED);
        }, UPDATE_VERIFICATION_STATE_PERMISSION);

        selfKeys = mContactKeysManager.getAllSelfKeys();
        assertThat(selfKeys.size()).isEqualTo(1);
        assertThat(selfKeys.get(0).getRemoteVerificationState())
                .isEqualTo(ContactKeysManager.VERIFICATION_STATE_VERIFIED);
    }
}
