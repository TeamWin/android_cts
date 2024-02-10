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

package android.provider.cts.contactkeys;

import static android.provider.ContactKeysManager.getMaxKeySizeBytes;


import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactKeysManager;
import android.provider.ContactKeysManager.ContactKey;
import android.provider.ContactKeysManager.SelfKey;
import android.provider.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_USER_KEYS)
@RunWith(AndroidJUnit4.class)
public class ContactKeysManagerTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String LOOKUP_KEY = "0r1-423A2E4644502A2E50";
    private static final String DEVICE_ID = "device_id_value";
    private static final String DEVICE_ID_2 = "device_id_value_2";
    private static final String ACCOUNT_ID = "+1 (555) 555-1234";
    private static final byte[] KEY_VALUE = new byte[]{(byte) 0xba, (byte) 0x8a};
    private static final byte[] KEY_VALUE_2 = new byte[]{(byte) 0x5c, (byte) 0xab};
    private static final long STRIPPED_TIME_UPDATED = -1;
    private static final String HELPER_APP_PACKAGE = "android.provider.cts.visibleapp";
    private static final String HELPER_APP_CLASS =
            "android.provider.cts.visibleapp.VisibleService";
    private static final String HELPER_APP_LOOKUP_KEY = "0r1-423A2E4644502A2E50";
    private static final String HELPER_APP_DEVICE_ID = "someDeviceId";
    private static final String HELPER_APP_ACCOUNT_ID = "someAccountId";
    private static final String OWNER_PACKAGE_NAME = "android.provider.cts.contactkeys";

    private ContactKeysManager mContactKeysManager;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        mContactKeysManager = (ContactKeysManager)
                mContext.getSystemService(Context.CONTACT_KEYS_SERVICE);
    }

    @After
    public void tearDown() {
        mContactKeysManager.removeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        mContactKeysManager.removeContactKey(LOOKUP_KEY, DEVICE_ID_2, ACCOUNT_ID);
        mContactKeysManager.removeSelfKey(DEVICE_ID, ACCOUNT_ID);
        mContactKeysManager.removeSelfKey(DEVICE_ID_2, ACCOUNT_ID);
    }

    @Test
    public void testUpdateOrInsertContactKey_insertsNewEntry() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        ContactKey contactKey =
                mContactKeysManager.getContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID);

        assertThat(contactKey.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(contactKey.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(contactKey.getKeyValue()).isEqualTo(KEY_VALUE);
        assertThat(contactKey.getLocalVerificationState())
                .isEqualTo(ContactKeysManager.UNVERIFIED);
        assertThat(contactKey.getRemoteVerificationState())
                .isEqualTo(ContactKeysManager.UNVERIFIED);
        assertThat(contactKey.getOwnerPackageName()).isEqualTo(OWNER_PACKAGE_NAME);
        assertThat(contactKey.getDisplayName()).isEqualTo(null);
        assertThat(contactKey.getEmailAddress()).isEqualTo(null);
        assertThat(contactKey.getPhoneNumber()).isEqualTo(null);
    }

    @Test
    public void testUpdateOrInsertContactKey_updatesExistingEntry() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);
        int localVerificationState = ContactKeysManager.VERIFIED;
        int remoteVerificationState = ContactKeysManager.VERIFIED;
        mContactKeysManager.updateContactKeyLocalVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, localVerificationState);
        mContactKeysManager.updateContactKeyRemoteVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, remoteVerificationState);

        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE_2);

        ContactKey updatedContactKey =
                mContactKeysManager.getContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID);
        assertNotNull(updatedContactKey);
        assertThat(updatedContactKey.getKeyValue()).isEqualTo(KEY_VALUE_2);
        assertThat(updatedContactKey.getLocalVerificationState())
                .isEqualTo(localVerificationState);
        assertThat(updatedContactKey.getRemoteVerificationState())
                .isEqualTo(remoteVerificationState);
    }

    @Test
    public void testUpdateOrInsertContactKey_keyTooLargeThrows() {
        byte[] largeKey = new byte[getMaxKeySizeBytes() + 1];
        Arrays.fill(largeKey, (byte) 42);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID, largeKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + largeKey.length + "."
                + " Should be more than 0 and less than " + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateOrInsertContactKey_emptyKeyThrows() {
        byte[] emptyKey = new byte[0];
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID, emptyKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + emptyKey.length + "."
                + " Should be more than 0 and less than " + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateOrInsertContactKey_nullKeyThrows() {
        assertThrows(NullPointerException.class,
                () -> mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID, null));
    }

    @Test
    public void testGetContactKey_returnsNullForNonexistentEntry() {
        ContactKey contactKey =
                mContactKeysManager.getContactKey(LOOKUP_KEY, DEVICE_ID,
                        ACCOUNT_ID);

        assertNull(contactKey);
    }

    @Test
    public void testGetOwnerContactKeys_returnsEntriesForCaller() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID_2, ACCOUNT_ID,
                KEY_VALUE_2);

        List<ContactKey> contactKeys = mContactKeysManager.getOwnerContactKeys(LOOKUP_KEY);

        assertThat(contactKeys.size()).isEqualTo(2);
    }

    @Test
    public void testGetAllContactKeys_callerIsSameAsOwner() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        List<ContactKey> contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);

        assertThat(contactKeys.size()).isEqualTo(1);
        ContactKey actualKey = contactKeys.get(0);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        assertThat(actualKey.getDeviceId()).isNull();
        assertThat(actualKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(actualKey.getKeyValue()).isNull();
        assertThat(actualKey.getLocalVerificationState()).isEqualTo(
                ContactKeysManager.UNVERIFIED);
        assertThat(actualKey.getRemoteVerificationState()).isEqualTo(
                ContactKeysManager.UNVERIFIED);
    }

    @Test
    public void testGetAllContactKeys_callerIsDifferentFromOwner() {
        // Creates a contact key by another owner
        startHelperApp();

        List<ContactKey> contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);

        // This also verifies that the keys created by an app (CtsContactKeysProviderInvisibleApp)
        // that is not queryable by CTS test are not visible
        assertThat(contactKeys.size()).isEqualTo(1);
        ContactKey contactKey = contactKeys.get(0);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        assertThat(contactKey.getDeviceId()).isNull();
        assertThat(contactKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(contactKey.getKeyValue()).isNull();
        assertThat(contactKey.getLocalVerificationState()).isEqualTo(
                ContactKeysManager.UNVERIFIED);
        assertThat(contactKey.getRemoteVerificationState()).isEqualTo(
                ContactKeysManager.UNVERIFIED);
        stopHelperApp();
    }

    @Test
    public void testGetAllContactKeys_returnsEmptyListForNonexistentEntries() {
        List<ContactKey> contactKeys =
                mContactKeysManager.getAllContactKeys(LOOKUP_KEY);

        assertThat(contactKeys.size()).isEqualTo(0);
    }

    @Test
    public void testUpdateContactKeyLocalVerificationState_updatesState() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        mContactKeysManager.updateContactKeyLocalVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, ContactKeysManager.VERIFIED);

        ContactKey updatedContactKey =
                mContactKeysManager.getContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        assertThat(updatedContactKey.getLocalVerificationState())
                .isEqualTo(ContactKeysManager.VERIFIED);
    }


    @Test
    public void testUpdateContactKeyLocalVerificationState_illegalState() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        int illegalVerificationState = 4;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateContactKeyLocalVerificationState(LOOKUP_KEY,
                        DEVICE_ID, ACCOUNT_ID, illegalVerificationState));
        assertThat(e).hasMessageThat().contains("Verification state value "
                + illegalVerificationState + " is not supported");
    }

    @Test
    public void testUpdateContactKeyLocalVerificationState_securityExceptionThrows() {
        startHelperApp();
        List<ContactKey> contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);

        SecurityException e = assertThrows(SecurityException.class,
                () ->mContactKeysManager.updateContactKeyLocalVerificationState(
                        HELPER_APP_LOOKUP_KEY, HELPER_APP_DEVICE_ID,
                        HELPER_APP_ACCOUNT_ID, HELPER_APP_PACKAGE,
                        ContactKeysManager.VERIFIED));

        assertThat(e).hasMessageThat().contains("The caller must have the "
                + "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS permission");
        stopHelperApp();
    }

    @Test
    public void testUpdateContactKeyRemoteVerificationState_securityExceptionThrows() {
        startHelperApp();
        List<ContactKey> contactKeys = mContactKeysManager.getAllContactKeys(LOOKUP_KEY);
        assertThat(contactKeys.size()).isEqualTo(1);

        SecurityException e = assertThrows(SecurityException.class,
                () ->mContactKeysManager.updateContactKeyRemoteVerificationState(
                        HELPER_APP_LOOKUP_KEY, HELPER_APP_DEVICE_ID,
                        HELPER_APP_ACCOUNT_ID, HELPER_APP_PACKAGE,
                        ContactKeysManager.VERIFIED));

        assertThat(e).hasMessageThat().contains("The caller must have the "
                + "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS permission");
        stopHelperApp();
    }

    @Test
    public void testUpdateContactKeyRemoteVerificationState_updatesState() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        mContactKeysManager.updateContactKeyRemoteVerificationState(LOOKUP_KEY, DEVICE_ID,
                ACCOUNT_ID, ContactKeysManager.VERIFIED);

        ContactKey updatedContactKey =
                mContactKeysManager.getContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        assertThat(updatedContactKey.getRemoteVerificationState())
                .isEqualTo(ContactKeysManager.VERIFIED);
    }

    @Test
    public void testUpdateContactKeyRemoteVerificationState_illegalState() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        int illegalVerificationState = 4;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateContactKeyRemoteVerificationState(LOOKUP_KEY,
                        DEVICE_ID, ACCOUNT_ID, illegalVerificationState));
        assertThat(e).hasMessageThat().contains("Verification state value "
                + illegalVerificationState + " is not supported");
    }

    @Test
    public void testRemoveContactKey_deletesEntry() {
        mContactKeysManager.updateOrInsertContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID,
                KEY_VALUE);

        mContactKeysManager.removeContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);

        ContactKey contactKey =
                mContactKeysManager.getContactKey(LOOKUP_KEY, DEVICE_ID, ACCOUNT_ID);
        assertNull(contactKey);
    }

    @Test
    public void testUpdateOrInsertSelfKey_insertsNewEntry() {
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        SelfKey newSelfKey = mContactKeysManager.getSelfKey(DEVICE_ID,
                ACCOUNT_ID);
        assertNotNull(newSelfKey);
        assertThat(newSelfKey.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(newSelfKey.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(newSelfKey.getKeyValue()).isEqualTo(KEY_VALUE);
    }

    @Test
    public void testUpdateOrInsertSelfKey_updatesExistingEntry() {
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE_2);

        SelfKey updatedSelfKey = mContactKeysManager.getSelfKey(DEVICE_ID,
                ACCOUNT_ID);
        assertNotNull(updatedSelfKey);
        assertThat(updatedSelfKey.getKeyValue()).isEqualTo(KEY_VALUE_2);
    }

    @Test
    public void testUpdateOrInsertSelfKey_keyTooLargeThrows() {
        byte[] largeKey = new byte[getMaxKeySizeBytes() + 1];
        Arrays.fill(largeKey, (byte) 42);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID,
                        ACCOUNT_ID, largeKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + largeKey.length + "."
                + " Should be more than 0 and less than "
                + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateOrInsertSelfKey_emptyKeyThrows() {
        byte[] emptyKey = new byte[0];
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID,
                        ACCOUNT_ID, emptyKey));
        assertThat(e).hasMessageThat().contains("Key value length is " + emptyKey.length + "."
                + " Should be more than 0 and less than "
                + getMaxKeySizeBytes());
    }

    @Test
    public void testUpdateSelfKeyRemoteVerificationState_updatesState() {
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        mContactKeysManager.updateSelfKeyRemoteVerificationState(DEVICE_ID,
                ACCOUNT_ID, ContactKeysManager.VERIFIED);

        SelfKey updatedSelfKey = mContactKeysManager.getSelfKey(DEVICE_ID,
                ACCOUNT_ID);
        assertThat(updatedSelfKey.getRemoteVerificationState())
                .isEqualTo(ContactKeysManager.VERIFIED);
    }

    @Test
    public void testUpdateSelfKeyRemoteVerificationState_securityExceptionThrows() {
        startHelperApp();
        List<SelfKey> selfKeys = mContactKeysManager.getAllSelfKeys();
        assertThat(selfKeys.size()).isEqualTo(1);

        SecurityException e = assertThrows(SecurityException.class,
                () ->mContactKeysManager.updateSelfKeyRemoteVerificationState(
                        HELPER_APP_DEVICE_ID, HELPER_APP_ACCOUNT_ID,
                        HELPER_APP_PACKAGE, ContactKeysManager.VERIFIED));
        assertThat(e).hasMessageThat().contains("The caller must have the "
                + "android.permission.WRITE_VERIFICATION_STATE_E2EE_CONTACT_KEYS permission");

        stopHelperApp();
    }

    @Test
    public void testUpdateSelfKeyRemoteVerificationState_illegalState() {
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        int illegalVerificationState = 4;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mContactKeysManager.updateSelfKeyRemoteVerificationState(DEVICE_ID,
                        ACCOUNT_ID, illegalVerificationState));
        assertThat(e).hasMessageThat().contains("Verification state value "
                + illegalVerificationState + " is not supported");
    }

    @Test
    public void testGetSelfKey_returnsExpectedSelfKey() {
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        SelfKey selfKey = mContactKeysManager.getSelfKey(DEVICE_ID,
                ACCOUNT_ID);

        assertNotNull(selfKey);
        assertThat(selfKey.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(selfKey.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(selfKey.getKeyValue()).isEqualTo(KEY_VALUE);
    }

    @Test
    public void testGetSelfKey_returnsNullForNonexistentEntry() {
        SelfKey selfKey = mContactKeysManager.getSelfKey(DEVICE_ID, ACCOUNT_ID);

        assertNull(selfKey);
    }

    @Test
    public void testGetOwnerSelfKeys_returnsEntriesForCaller() {
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID_2, ACCOUNT_ID, KEY_VALUE_2);

        List<SelfKey> selfKeys = mContactKeysManager.getOwnerSelfKeys();

        assertThat(selfKeys.size()).isEqualTo(2);
    }

    @Test
    public void testGetAllSelfKeys_callerIsSameAsOwner() {
        mContactKeysManager.updateOrInsertSelfKey(DEVICE_ID, ACCOUNT_ID, KEY_VALUE);

        List<SelfKey> selfKeys = mContactKeysManager.getAllSelfKeys();

        assertThat(selfKeys.size()).isEqualTo(1);
        SelfKey actualKey = selfKeys.get(0);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        assertThat(actualKey.getDeviceId()).isNull();
        assertThat(actualKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(actualKey.getKeyValue()).isNull();
        assertThat(actualKey.getRemoteVerificationState()).isEqualTo(
                ContactKeysManager.UNVERIFIED);
    }

    @Test
    public void testGetAllSelfKeys_callerIsDifferentFromOwner() {
        // Creates a self key by another owner
        startHelperApp();

        List<SelfKey> selfKeys = mContactKeysManager.getAllSelfKeys();

        // This also verifies that the keys created by an app (CtsContactKeysProviderInvisibleApp)
        // that is not queryable by CTS test are not visible
        assertThat(selfKeys.size()).isEqualTo(1);
        // Check that deviceId, timeUpdated and keyValue data is stripped
        SelfKey selfKey = selfKeys.get(0);
        assertThat(selfKey.getDeviceId()).isNull();
        assertThat(selfKey.getTimeUpdated()).isEqualTo(STRIPPED_TIME_UPDATED);
        assertThat(selfKey.getKeyValue()).isNull();
        assertThat(selfKey.getRemoteVerificationState()).isEqualTo(
                ContactKeysManager.UNVERIFIED);
        stopHelperApp();
    }

    @Test
    public void testGetAllSelfKeys_returnsEmptyListForNonexistentEntries() {
        List<SelfKey> selfKeys = mContactKeysManager.getAllSelfKeys();

        assertThat(selfKeys.size()).isEqualTo(0);
    }

    /**
     * Starting helper app triggers creating one contact and one self key for test purposes.
     */
    private void startHelperApp() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                HELPER_APP_PACKAGE,
                HELPER_APP_CLASS));
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().startForegroundService(intent);
        // Wait as service start (that includes creation of the keys) is not immediate
        try {
            Thread.sleep(500);
        } catch (Exception e) {
            // Do nothing
        }
    }

    /**
     * Stopping helper app triggers removing previously created contact and self key.
     */
    private void stopHelperApp() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                HELPER_APP_PACKAGE,
                HELPER_APP_CLASS));
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().stopService(intent);
        // Wait as service stop (that includes removal of the keys) is not immediate
        try {
            Thread.sleep(500);
        } catch (Exception e) {
            // Do nothing
        }
    }
}
