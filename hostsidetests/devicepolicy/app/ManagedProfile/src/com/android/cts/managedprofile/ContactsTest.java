/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.managedprofile;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.test.AndroidTestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ContactsTest extends AndroidTestCase {

    private static final String TEST_ACCOUNT_NAME = "CTS";
    private static final String TEST_ACCOUNT_TYPE = "com.android.cts.test";
    // details of a sample primary contact
    private static final String PRIMARY_CONTACT_DISPLAY_NAME = "Primary";
    private static final String PRIMARY_CONTACT_PHONE = "00000001";
    private static final String PRIMARY_CONTACT_EMAIL = "one@primary.com";
    private static final String PRIMARY_CONTACT_SIP = "foo@sip";

    // details of a sample managed contact
    private static final String MANAGED_CONTACT_DISPLAY_NAME = "Managed";
    private static final String MANAGED_CONTACT_PHONE = "6891999";
    private static final String MANAGED_CONTACT_EMAIL = "one@managed.com";
    private static final String MANAGED_CONTACT_SIP = "bar@sip";

    // details of a sample primary and a sample managed contact, with the same phone & email
    private static final String PRIMARY_CONTACT_DISPLAY_NAME_2 = "PrimaryShared";
    private static final String MANAGED_CONTACT_DISPLAY_NAME_2 = "ManagedShared";
    private static final String SHARED_CONTACT_PHONE = "00000002";
    private static final String SHARED_CONTACT_EMAIL = "shared@shared.com";
    private static final String SHARED_CONTACT_SIP = "baz@sip";

    // Directory display name
    private static final String PRIMARY_DIRECTORY_NAME = "PrimaryDirectory";
    private static final String MANAGED_DIRECTORY_NAME = "ManagedDirectory";
    private static final String PRIMARY_DIRECTORY_CONTACT_NAME = "PrimaryDirectoryContact";
    private static final String MANAGED_DIRECTORY_CONTACT_NAME = "ManagedDirectoryContact";

    private DevicePolicyManager mDevicePolicyManager;
    private ContentResolver mResolver;

    private class ContactInfo { // Not static to access outer world.

        String contactId;
        String displayName;
        String photoUri;
        String photoThumbnailUri;
        String photoId;

        public ContactInfo(String contactId, String displayName, String photoUri,
                String photoThumbnailUri, String photoId) {
            this.contactId = contactId;
            this.displayName = displayName;
            this.photoUri = photoUri;
            this.photoThumbnailUri = photoThumbnailUri;
            this.photoId = photoId;
        }

        private void assertNoPhotoUri() {
            assertNull(photoUri);
            assertNull(photoThumbnailUri);
        }

        private void assertPhotoUrisReadable() throws IOException {
            assertPhotoUriReadable(photoUri);
            assertPhotoUriReadable(photoThumbnailUri);
        }

        private boolean hasPhotoId() {
            return photoId != null && Long.parseLong(photoId) > 0;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getContext().getContentResolver();
        mDevicePolicyManager = (DevicePolicyManager) mContext
                .getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public void testPrimaryProfilePhoneAndEmailLookup_insertedAndfound() throws RemoteException,
            OperationApplicationException, NotFoundException, IOException {
        assertFalse(isManagedProfile());
        // Do not insert to primary contact
        insertContact(PRIMARY_CONTACT_DISPLAY_NAME, PRIMARY_CONTACT_PHONE,
                PRIMARY_CONTACT_EMAIL, PRIMARY_CONTACT_SIP, 0);

        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(false /*isEnterprise*/,
                PRIMARY_CONTACT_PHONE);
        assertNotNull(contactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));

        contactInfo = getContactInfoFromEmailLookupUri(false /*isEnterprise*/,
                PRIMARY_CONTACT_EMAIL);
        assertNotNull(contactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));

    }

    public void testManagedProfilePhoneAndEmailLookup_insertedAndfound() throws RemoteException,
            OperationApplicationException, NotFoundException, IOException {
        assertTrue(isManagedProfile());
        // Insert ic_contact_picture as photo in managed contact
        insertContact(MANAGED_CONTACT_DISPLAY_NAME,
                MANAGED_CONTACT_PHONE,
                MANAGED_CONTACT_EMAIL,
                MANAGED_CONTACT_SIP,
                com.android.cts.managedprofile.R.raw.ic_contact_picture);

        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(false /*isEnterprise*/,
                MANAGED_CONTACT_PHONE);
        assertNotNull(contactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));

        contactInfo = getContactInfoFromEmailLookupUri(false /*isEnterprise*/,
                MANAGED_CONTACT_EMAIL);
        assertNotNull(contactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfileDuplicatedPhoneEmailContact_insertedAndfound() throws
            RemoteException, OperationApplicationException, NotFoundException, IOException {
        assertFalse(isManagedProfile());
        insertContact(PRIMARY_CONTACT_DISPLAY_NAME_2,
                SHARED_CONTACT_PHONE,
                SHARED_CONTACT_EMAIL,
                SHARED_CONTACT_SIP,
                com.android.cts.managedprofile.R.raw.ic_contact_picture);

        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(false /*isEnterprise*/,
                SHARED_CONTACT_PHONE);
        assertNotNull(contactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));

        contactInfo = getContactInfoFromEmailLookupUri(false /*isEnterprise*/, SHARED_CONTACT_EMAIL);
        assertNotNull(contactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testManagedProfileDuplicatedPhoneEmailContact_insertedAndfound() throws
            RemoteException, OperationApplicationException, NotFoundException, IOException {
        assertTrue(isManagedProfile());
        insertContact(MANAGED_CONTACT_DISPLAY_NAME_2, SHARED_CONTACT_PHONE,
                SHARED_CONTACT_EMAIL, SHARED_CONTACT_SIP , 0);

        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(false /*isEnterprise*/,
                SHARED_CONTACT_PHONE);
        assertNotNull(contactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));

        contactInfo = getContactInfoFromEmailLookupUri(false /*isEnterprise*/, SHARED_CONTACT_EMAIL);
        assertNotNull(contactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfileEnterprisePhoneLookup_canAccessEnterpriseContact()
            throws IOException {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(true /*isEnterprise*/,
                MANAGED_CONTACT_PHONE);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        // Cannot get photo id in ENTERPRISE_CONTENT_FILTER_URI
        assertFalse(contactInfo.hasPhotoId());
        assertTrue(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfileEnterpriseSipLookup_canAccessEnterpriseContact()
            throws IOException {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEnterprisePhoneLookupUriWithSipAddress(
                true /*isEnterprise*/, MANAGED_CONTACT_SIP);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertFalse(contactInfo.hasPhotoId());

        // Quirk: the _id column from the SIP lookup is actually of the data id, not the contact id.
        // assertTrue(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfileEnterpriseEmailLookup_canAccessEnterpriseContact()
            throws IOException {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(true /*isEnterprise*/,
                MANAGED_CONTACT_EMAIL);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        // Cannot get photo id in ENTERPRISE_CONTENT_FILTER_URI
        assertFalse(contactInfo.hasPhotoId());
        assertTrue(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfileEnterprisePhoneLookupDuplicated_canAccessPrimaryContact()
            throws IOException {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(true /*isEnterprise*/,
                SHARED_CONTACT_PHONE);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfileEnterpriseEmailLookupDuplicated_canAccessPrimaryContact()
            throws IOException {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(true /*isEnterprise*/,
                SHARED_CONTACT_EMAIL);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testManagedProfileEnterprisePhoneLookupDuplicated_canAccessEnterpriseContact() {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(true /*isEnterprise*/,
                SHARED_CONTACT_PHONE);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testManagedProfileEnterpriseEmailLookupDuplicated_canAccessEnterpriseContact() {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(true /*isEnterprise*/,
                SHARED_CONTACT_EMAIL);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME_2, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfilePhoneLookup_canNotAccessEnterpriseContact() {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(false /*isEnterprise*/,
                MANAGED_CONTACT_PHONE);
        assertNull(contactInfo);
    }

    public void testPrimaryProfileEmailLookup_canNotAccessEnterpriseContact() {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(false /*isEnterprise*/,
                MANAGED_CONTACT_EMAIL);
        assertNull(contactInfo);
    }

    public void testPrimaryProfileEnterprisePhoneLookup_canAccessPrimaryContact() {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(true /*isEnterprise*/,
                PRIMARY_CONTACT_PHONE);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testPrimaryProfileEnterpriseEmailLookup_canAccessPrimaryContact() {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(true /*isEnterprise*/,
                PRIMARY_CONTACT_EMAIL);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertNoPhotoUri();
        assertFalse(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testManagedProfileEnterprisePhoneLookup_canAccessEnterpriseContact()
            throws IOException {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(true /*isEnterprise*/,
                MANAGED_CONTACT_PHONE);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testManagedProfileEnterpriseEmailLookup_canAccessEnterpriseContact()
            throws IOException {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(true /*isEnterprise*/,
                MANAGED_CONTACT_EMAIL);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, contactInfo.displayName);
        contactInfo.assertPhotoUrisReadable();
        assertTrue(contactInfo.hasPhotoId());
        assertFalse(isEnterpriseContactId(contactInfo.contactId));
    }

    public void testManagedProfileEnterprisePhoneLookup_canNotAccessPrimaryContact() {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(true /*isEnterprise*/,
                PRIMARY_CONTACT_PHONE);
        assertNull(contactInfo);
    }

    public void testManagedProfileEnterpriseEmailLookup_canNotAccessPrimaryContact() {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(true /*isEnterprise*/,
                PRIMARY_CONTACT_EMAIL);
        assertNull(contactInfo);
    }

    public void testManagedProfilePhoneLookup_canNotAccessPrimaryContact() {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(false /*isEnterprise*/,
                PRIMARY_CONTACT_PHONE);
        assertNull(contactInfo);
    }

    public void testManagedProfileEmailLookup_canNotAccessPrimaryContact() {
        assertTrue(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(false /*isEnterprise */,
                PRIMARY_CONTACT_EMAIL);
        assertNull(contactInfo);
    }

    public void testPrimaryProfileEnterpriseEmailLookup_canNotAccessEnterpriseContact() {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromEmailLookupUri(true /*isEnterprise*/,
                MANAGED_CONTACT_EMAIL);
        assertNull(contactInfo);
    }

    public void testPrimaryProfileEnterprisePhoneLookup_canNotAccessEnterpriseContact() {
        assertFalse(isManagedProfile());
        ContactInfo contactInfo = getContactInfoFromPhoneLookupUri(true /*isEnterprise*/,
                MANAGED_CONTACT_PHONE);
        assertNull(contactInfo);
    }

    public void testSetCrossProfileCallerIdDisabled_true() {
        assertTrue(isManagedProfile());
        mDevicePolicyManager.setCrossProfileCallerIdDisabled(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT, true);
    }

    public void testSetCrossProfileCallerIdDisabled_false() {
        assertTrue(isManagedProfile());
        mDevicePolicyManager.setCrossProfileCallerIdDisabled(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT, false);
    }

    public void testCurrentProfileContacts_removeContacts() {
        removeAllTestContactsInProfile();
    }

    public void testSetBluetoothContactSharingDisabled_setterAndGetter() {
        mDevicePolicyManager.setBluetoothContactSharingDisabled(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT, false);
        assertFalse(mDevicePolicyManager.getBluetoothContactSharingDisabled(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT));
        mDevicePolicyManager.setBluetoothContactSharingDisabled(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT, true);
        assertTrue(mDevicePolicyManager.getBluetoothContactSharingDisabled(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT));
    }

    public void testGetDirectoryListInPrimaryProfile() {
        assertFalse(isManagedProfile());
        final Cursor cursor = mResolver.query(Directory.ENTERPRISE_CONTENT_URI,
                new String[]{
                        Directory._ID,
                        Directory.DISPLAY_NAME
                }, null, null, null);

        boolean hasPrimaryDefault = false;
        boolean hasPrimaryInvisible = false;
        boolean hasManagedDefault = false;
        boolean hasManagedInvisible = false;
        boolean hasPrimaryDirectory = false;
        boolean hasManagedDirectory = false;

        while(cursor.moveToNext()) {
            final long directoryId = cursor.getLong(0);
            if (directoryId == Directory.DEFAULT) {
                hasPrimaryDefault = true;
            } else if (directoryId == Directory.LOCAL_INVISIBLE) {
                hasPrimaryInvisible = true;
            } else if (directoryId == Directory.ENTERPRISE_DEFAULT) {
                hasManagedDefault = true;
            } else if (directoryId == Directory.ENTERPRISE_LOCAL_INVISIBLE) {
                hasManagedInvisible = true;
            } else {
                final String displayName = cursor.getString(1);
                if (Directory.isEnterpriseDirectoryId(directoryId)
                        && displayName.equals(MANAGED_DIRECTORY_NAME)) {
                    hasManagedDirectory = true;
                }
                if (!Directory.isEnterpriseDirectoryId(directoryId)
                        && displayName.equals(PRIMARY_DIRECTORY_NAME)) {
                    hasPrimaryDirectory = true;
                }
            }
        }
        cursor.close();
        assertTrue(hasPrimaryDefault);
        assertTrue(hasPrimaryInvisible);
        assertTrue(hasManagedDefault);
        assertTrue(hasManagedInvisible);
        assertTrue(hasPrimaryDirectory);
        assertTrue(hasManagedDirectory);
    }

    public void testPrimaryProfileEnterpriseEmailLookup_canAccessPrimaryDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseEmailLookupUriInDirectory(PRIMARY_CONTACT_EMAIL,
                Directory.DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getPrimaryRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseEmailLookupUriInDirectory(PRIMARY_CONTACT_EMAIL,
                directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(PRIMARY_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterpriseEmailLookup_canAccessManagedDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseEmailLookupUriInDirectory(MANAGED_CONTACT_EMAIL,
                Directory.ENTERPRISE_DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getEnterpriseRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseEmailLookupUriInDirectory(MANAGED_CONTACT_EMAIL,
                directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(MANAGED_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterprisePhoneLookup_canAccessPrimaryDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterprisePhoneLookupUriInDirectory(PRIMARY_CONTACT_PHONE,
                Directory.DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getPrimaryRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterprisePhoneLookupUriInDirectory(PRIMARY_CONTACT_PHONE,
                directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(PRIMARY_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterprisePhoneLookup_canAccessManagedDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterprisePhoneLookupUriInDirectory(MANAGED_CONTACT_PHONE,
                Directory.ENTERPRISE_DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getEnterpriseRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterprisePhoneLookupUriInDirectory(MANAGED_CONTACT_PHONE,
                directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(MANAGED_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterpriseCallableFilter_canAccessPrimaryDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseCallableFilterUriInDirectory(
                PRIMARY_CONTACT_PHONE, Directory.DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getPrimaryRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseCallableFilterUriInDirectory(
                PRIMARY_CONTACT_PHONE, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(PRIMARY_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterpriseCallableFilter_canAccessManagedDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseCallableFilterUriInDirectory(
                MANAGED_CONTACT_PHONE, Directory.ENTERPRISE_DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getEnterpriseRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseCallableFilterUriInDirectory(
                MANAGED_CONTACT_PHONE, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(MANAGED_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterpriseEmailFilter_canAccessPrimaryDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseEmailFilterUriInDirectory(
                PRIMARY_CONTACT_EMAIL, Directory.DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getPrimaryRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseEmailFilterUriInDirectory(
                PRIMARY_CONTACT_EMAIL, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(PRIMARY_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterpriseEmailFilter_canAccessManagedDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseEmailFilterUriInDirectory(
                MANAGED_CONTACT_EMAIL, Directory.ENTERPRISE_DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getEnterpriseRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseEmailFilterUriInDirectory(
                MANAGED_CONTACT_EMAIL, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(MANAGED_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterpriseContactFilter_canAccessPrimaryDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseContactFilterUriInDirectory(
                PRIMARY_CONTACT_DISPLAY_NAME, Directory.DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getPrimaryRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseEmailFilterUriInDirectory(
                PRIMARY_CONTACT_DISPLAY_NAME, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(PRIMARY_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterpriseContactFilter_canAccessManagedDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterpriseContactFilterUriInDirectory(
                MANAGED_CONTACT_DISPLAY_NAME, Directory.ENTERPRISE_DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getEnterpriseRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterpriseEmailFilterUriInDirectory(
                MANAGED_CONTACT_DISPLAY_NAME, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(MANAGED_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterprisePhoneFilter_canAccessPrimaryDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterprisePhoneFilterUriInDirectory(
                PRIMARY_CONTACT_PHONE, Directory.DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(PRIMARY_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getPrimaryRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterprisePhoneFilterUriInDirectory(
                PRIMARY_CONTACT_PHONE, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(PRIMARY_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testPrimaryProfileEnterprisePhoneFilter_canAccessManagedDirectories() {
        assertFalse(isManagedProfile());

        // local directory
        final ContactInfo defaultContactInfo
                = getContactInfoFromEnterprisePhoneFilterUriInDirectory(
                MANAGED_CONTACT_PHONE, Directory.ENTERPRISE_DEFAULT);
        assertNotNull(defaultContactInfo);
        assertEquals(MANAGED_CONTACT_DISPLAY_NAME, defaultContactInfo.displayName);

        // remote directory
        final long directoryId = getEnterpriseRemoteDirectoryId();
        final ContactInfo directoryContactInfo
                = getContactInfoFromEnterprisePhoneFilterUriInDirectory(
                MANAGED_CONTACT_PHONE, directoryId);
        assertNotNull(directoryContactInfo);
        assertEquals(MANAGED_DIRECTORY_CONTACT_NAME, directoryContactInfo.displayName);
    }

    public void testFilterUriWhenDirectoryParamMissing() {
        assertFailWhenDirectoryParamMissing(Phone.ENTERPRISE_CONTENT_FILTER_URI);
        assertFailWhenDirectoryParamMissing(Email.ENTERPRISE_CONTENT_FILTER_URI);
        assertFailWhenDirectoryParamMissing(Contacts.ENTERPRISE_CONTENT_FILTER_URI);
        assertFailWhenDirectoryParamMissing(Callable.ENTERPRISE_CONTENT_FILTER_URI);
    }

    private long getPrimaryRemoteDirectoryId() {
        assertFalse(isManagedProfile());
        final Cursor cursor = mResolver.query(Directory.ENTERPRISE_CONTENT_URI,
                new String[]{
                        Directory._ID
                }, null, null, null);
        try {
            while (cursor.moveToNext()) {
                final long directoryId = cursor.getLong(0);
                if (!Directory.isEnterpriseDirectoryId(directoryId)
                        && Directory.isRemoteDirectory(directoryId)) {
                    return directoryId;
                }
            }
        } finally {
            cursor.close();
        }
        fail("Cannot find primary directory id");
        return 0;
    }

    private long getEnterpriseRemoteDirectoryId() {
        assertFalse(isManagedProfile());
        final Cursor cursor = mResolver.query(Directory.ENTERPRISE_CONTENT_URI,
                new String[] {
                    Directory._ID
                }, null, null, null);
        try {
            while (cursor.moveToNext()) {
                final long directoryId = cursor.getLong(0);
                if (Directory.isEnterpriseDirectoryId(directoryId)
                        && Directory.isRemoteDirectory(directoryId)) {
                    return directoryId;
                }
            }
        } finally {
            cursor.close();
        }
        fail("Cannot find enterprise directory id");
        return 0;
    }

    private boolean isManagedProfile() {
        String adminPackage = BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT.getPackageName();
        return mDevicePolicyManager.isProfileOwnerApp(adminPackage);
    }

    private void insertContact(String displayName, String phoneNumber, String email,
            String sipAddress, int photoResId)
            throws RemoteException, OperationApplicationException, NotFoundException, IOException {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME)
                .build());
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        displayName)
                .build());
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,
                        phoneNumber)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                        Phone.TYPE_MOBILE)
                .build());
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS,
                        email)
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE,
                        Email.TYPE_WORK)
                .build());
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS,
                        sipAddress)
                .withValue(ContactsContract.CommonDataKinds.SipAddress.TYPE,
                        ContactsContract.CommonDataKinds.SipAddress.TYPE_WORK)
                .build());

        if (photoResId != 0) {
            InputStream phoneInputStream = mContext.getResources().openRawResource(photoResId);
            try {
                byte[] rawPhoto = getByteFromStream(phoneInputStream);
                ops.add(ContentProviderOperation
                        .newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(Photo.PHOTO, rawPhoto)
                        .build());
            } finally {
                phoneInputStream.close();
            }
        }

        mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
    }

    private ContactInfo getContactInfoFromPhoneLookupUri(boolean isEnterprise, String phoneNumber) {
        Uri baseUri = (isEnterprise) ? PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI
                : PhoneLookup.CONTENT_FILTER_URI;
        Uri uri = baseUri.buildUpon().appendPath(phoneNumber).build();
        return getContactInfoFromUri(uri, PhoneLookup._ID, PhoneLookup.DISPLAY_NAME,
                PhoneLookup.PHOTO_URI, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEnterprisePhoneLookupUriWithSipAddress(
            boolean isEnterprise, String sipAddress) {
        Uri baseUri = (isEnterprise) ? PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI
                : PhoneLookup.CONTENT_FILTER_URI;
        Uri uri = baseUri.buildUpon().appendPath(sipAddress)
                .appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, "1").build();
        return getContactInfoFromUri(uri, PhoneLookup._ID, PhoneLookup.DISPLAY_NAME,
                PhoneLookup.PHOTO_URI, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEnterprisePhoneLookupUriInDirectory(String phoneNumber,
            long directoryId) {
        Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon().appendPath(phoneNumber)
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId)).build();
        return getContactInfoFromUri(uri, PhoneLookup._ID, PhoneLookup.DISPLAY_NAME,
                PhoneLookup.PHOTO_URI, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEmailLookupUri(boolean isEnterprise, String email) {
        Uri baseUri = (isEnterprise) ? Email.ENTERPRISE_CONTENT_LOOKUP_URI
                : Email.CONTENT_LOOKUP_URI;
        Uri uri = Uri.withAppendedPath(baseUri, email);
        return getContactInfoFromUri(uri, Email.CONTACT_ID, Email.DISPLAY_NAME_PRIMARY,
                Email.PHOTO_URI, Email.PHOTO_THUMBNAIL_URI, Email.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEnterpriseEmailLookupUriInDirectory(String email,
            long directoryId) {
        Uri uri = Email.ENTERPRISE_CONTENT_LOOKUP_URI.buildUpon().appendPath(email)
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId)).build();
        return getContactInfoFromUri(uri, Email.CONTACT_ID, Email.DISPLAY_NAME_PRIMARY,
                Email.PHOTO_URI, Email.PHOTO_THUMBNAIL_URI, Email.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEnterpriseCallableFilterUriInDirectory(String filter,
            long directoryId) {
        final Uri uri = Uri.withAppendedPath(Callable.ENTERPRISE_CONTENT_FILTER_URI, filter)
                .buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId)).build();
        return getContactInfoFromUri(uri, Callable.CONTACT_ID, Callable.DISPLAY_NAME_PRIMARY,
                Callable.PHOTO_URI, Callable.PHOTO_THUMBNAIL_URI, Callable.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEnterpriseEmailFilterUriInDirectory(String filter,
            long directoryId) {
        final Uri uri = Uri.withAppendedPath(Email.ENTERPRISE_CONTENT_FILTER_URI, filter)
                .buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId)).build();
        return getContactInfoFromUri(uri, Email.CONTACT_ID, Email.DISPLAY_NAME_PRIMARY,
                Email.PHOTO_URI, Email.PHOTO_THUMBNAIL_URI, Email.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEnterpriseContactFilterUriInDirectory(String filter,
            long directoryId) {
        final Uri uri = Uri.withAppendedPath(Contacts.ENTERPRISE_CONTENT_FILTER_URI, filter)
                .buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId)).build();
        return getContactInfoFromUri(uri, Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY,
                Contacts.PHOTO_URI, Contacts.PHOTO_THUMBNAIL_URI, Contacts.PHOTO_ID);
    }

    private ContactInfo getContactInfoFromEnterprisePhoneFilterUriInDirectory(String filter,
            long directoryId) {
        final Uri uri = Uri.withAppendedPath(Phone.ENTERPRISE_CONTENT_FILTER_URI, filter)
                .buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId)).build();
        return getContactInfoFromUri(uri, Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY,
                Phone.PHOTO_URI, Phone.PHOTO_THUMBNAIL_URI, Phone.PHOTO_ID);
    }


    private ContactInfo getContactInfoFromUri(Uri uri, String idColumn,
            String displayNameColumn, String photoUriColumn, String photoThumbnailColumn,
            String photoIdColumn) {
        Cursor cursor = mResolver.query(uri,
                new String[] {
                        idColumn,
                        displayNameColumn,
                        photoUriColumn,
                        photoIdColumn,
                        photoThumbnailColumn,
                }, null, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                return new ContactInfo(
                        cursor.getString(cursor.getColumnIndexOrThrow(idColumn)),
                        cursor.getString(cursor.getColumnIndexOrThrow(displayNameColumn)),
                        cursor.getString(cursor.getColumnIndexOrThrow(photoUriColumn)),
                        cursor.getString(cursor.getColumnIndexOrThrow(photoThumbnailColumn)),
                        cursor.getString(cursor.getColumnIndexOrThrow(photoIdColumn)));
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private void removeAllTestContactsInProfile() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ops.add(ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                .withSelection(RawContacts.ACCOUNT_TYPE + "=?", new String[] {TEST_ACCOUNT_TYPE})
                .build());
        try {
            mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            // Catch all exceptions to let tearDown() run smoothly
            e.printStackTrace();
        }
    }

    private static byte[] getByteFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buf = new byte[1024 * 10];
        int i = 0;
        while ((i = is.read(buf, 0, buf.length)) > 0) {
            outputStream.write(buf, 0, i);
        }
        return outputStream.toByteArray();
    }

    private boolean isEnterpriseContactId(String contactId) {
        return ContactsContract.Contacts.isEnterpriseContactId(Long.valueOf(contactId));
    }

    private void assertPhotoUriReadable(String uri) throws IOException {
        assertNotNull(uri);
        final InputStream is = mResolver.openInputStream(Uri.parse(uri));
        try {
            // Make sure it's readabe.  Don't have to read all content.
            is.read();
        } finally {
            try {
                is.close();
            } catch (IOException ignore) {
            }
        }
    }

    private void assertFailWhenDirectoryParamMissing(Uri uri) {
        try {
            mResolver.query(uri, null, null, null, null);
            fail("IllegalArgumentException is not thrown");
        } catch (IllegalArgumentException ex) {
        }
    }
}
