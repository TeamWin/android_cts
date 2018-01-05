/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyManager;
import android.util.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class PasswordBlacklistTest extends BaseDeviceAdminTest {
    private static final String TAG = "PasswordBlacklistTest";
    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();

    private boolean mShouldRun = true;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Set up a token to reset the password. This is used to check the blacklist is being
        // enforced.
        try {
            // On devices with password token disabled, calling this method will throw
            // a security exception. If that's anticipated, then return early without failing.
            assertTrue(mDevicePolicyManager.setResetPasswordToken(ADMIN_RECEIVER_COMPONENT,
                    TOKEN));
        } catch (SecurityException e) {
            if (e.getMessage().equals("Escrow token is disabled on the current user")) {
                Log.i(TAG, "Skip some password blacklist test because escrow token is disabled");
                mShouldRun = false;
            } else {
                throw e;
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (!mShouldRun) {
            return;
        }
        // Remove the blacklist, password and password reset token
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(ADMIN_RECEIVER_COMPONENT, null, null));
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(
                ADMIN_RECEIVER_COMPONENT, null, TOKEN, 0));
        assertTrue(mDevicePolicyManager.clearResetPasswordToken(ADMIN_RECEIVER_COMPONENT));
    }

    public void testSettingEmptyBlacklist() {
        if (!mShouldRun) {
            return;
        }
        final String notInBlacklist = "4ur3>#C$a#rC3W9Rhs";

        testPasswordBlacklist(null, notInBlacklist);
    }

    public void testClearingBlacklist() {
        if (!mShouldRun) {
            return;
        }
        final String notInBlacklist = "4ur3>#C$a#rC3W9Rhs";

        testPasswordBlacklist(Arrays.asList(notInBlacklist), null);
        testPasswordBlacklist(null, notInBlacklist);
    }

    public void testSettingBlacklist() {
        if (!mShouldRun) {
            return;
        }
        final List<String> blacklist = Arrays.asList("password", "letmein", "football");
        final String notInBlacklist = "falseponycellfastener";

        testPasswordBlacklist(blacklist, notInBlacklist);
    }

    public void testChangingBlacklist() {
        if (!mShouldRun) {
            return;
        }
        final List<String> blacklist = Arrays.asList("password", "letmein", "football");
        final String notInBlacklist = "falseponycellfastener";

        testPasswordBlacklist(Arrays.asList(notInBlacklist), null);
        testPasswordBlacklist(blacklist, notInBlacklist);
    }

    public void testBlacklistNotTreatedAsRegex() {
        if (!mShouldRun) {
            return;
        }
        final List<String> blacklist = Arrays.asList("hi\\d*", ".*", "[^adb]{2}.\\d.\\S");
        final String notInBlacklist = "hi123";

        testPasswordBlacklist(blacklist, notInBlacklist);
    }

    public void testBlacklistCaseInsensitive() {
        if (!mShouldRun) {
            return;
        }
        final List<String> blacklist = Arrays.asList("baseball", "MONKEY", "ShAdOw");
        final String notInBlacklist = "falsecellfastenerpony";

        testPasswordBlacklist(blacklist, notInBlacklist);

        // These are also blocked by the blacklist as they only differ in case
        final List<String> inBlacklist = Arrays.asList(
                "baseball", "BASEBALL", "BASEball",
                "monkey", "MONKEY", "moNKEy",
                "shadow", "SHADOW", "ShAdOw");
        for (final String password : inBlacklist) {
            assertFalse(mDevicePolicyManager.resetPasswordWithToken(
                    ADMIN_RECEIVER_COMPONENT, password, TOKEN, 0));
        }
    }

    public void testMaxBlacklistSize() {
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, "max size", generateMaxBlacklist()));
    }

    public void testBlacklistTooBig() {
        try {
            mDevicePolicyManager.setPasswordBlacklist(
                    ADMIN_RECEIVER_COMPONENT, "too big", generateJustTooBigBlacklist());
            fail("Did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            return;
        }
    }

    public void testNullNameWhenSettingBlacklist() {
        if (!mShouldRun) {
            return;
        }
        final String password = "bad one";
        try {
            mDevicePolicyManager.setPasswordBlacklist(
                    ADMIN_RECEIVER_COMPONENT, null, Arrays.asList(password));
            fail("Did not throw NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(mDevicePolicyManager.resetPasswordWithToken(
                    ADMIN_RECEIVER_COMPONENT, password, TOKEN, 0));
            return;
        }
    }

    public void testNullAdminWhenSettingBlacklist() {
        if (!mShouldRun) {
            return;
        }
        final String password = "example";
        try {
            mDevicePolicyManager.setPasswordBlacklist(null, "no admin", Arrays.asList(password));
            fail("Did not throw NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(mDevicePolicyManager.resetPasswordWithToken(
                    ADMIN_RECEIVER_COMPONENT, password, TOKEN, 0));
            return;
        }
    }

    public void testPasswordBlacklistName() {
        if (!mShouldRun) {
            return;
        }
        final String name = "Version 1.0";
        final List<String> blacklist = Arrays.asList("one", "1", "i");
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, name, blacklist));
        assertEquals(
                mDevicePolicyManager.getPasswordBlacklistName(ADMIN_RECEIVER_COMPONENT), name);
        for (final String password : blacklist) {
            assertFalse(mDevicePolicyManager.resetPasswordWithToken(
                    ADMIN_RECEIVER_COMPONENT, password, TOKEN, 0));
        }
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(
                ADMIN_RECEIVER_COMPONENT, "notintheblacklist", TOKEN, 0));
    }

    public void testPasswordBlacklistWithEmptyName() {
        final String emptyName = "";
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, emptyName, Arrays.asList("test", "empty", "name")));
        assertEquals(
                mDevicePolicyManager.getPasswordBlacklistName(ADMIN_RECEIVER_COMPONENT), emptyName);
    }

    public void testBlacklistNameCanBeChanged() {
        final String firstName = "original";
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, firstName, Arrays.asList("a")));
        assertEquals(
                mDevicePolicyManager.getPasswordBlacklistName(ADMIN_RECEIVER_COMPONENT), firstName);

        final String newName = "different";
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, newName, Arrays.asList("a")));
        assertEquals(
                mDevicePolicyManager.getPasswordBlacklistName(ADMIN_RECEIVER_COMPONENT), newName);
    }

    public void testCannotNameClearedBlacklist() {
        final String name = "empty!";
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, name, null));
        assertTrue(mDevicePolicyManager.getPasswordBlacklistName(ADMIN_RECEIVER_COMPONENT) == null);
    }

    public void testClearingBlacklistClearsName() {
        final String firstName = "gotone";
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, firstName, Arrays.asList("something")));
        assertEquals(
                mDevicePolicyManager.getPasswordBlacklistName(ADMIN_RECEIVER_COMPONENT), firstName);

        final String newName = "empty!";
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, newName, null));
        assertTrue(mDevicePolicyManager.getPasswordBlacklistName(ADMIN_RECEIVER_COMPONENT) == null);
    }

    public void testNullAdminWhenGettingBlacklistName() {
        try {
            mDevicePolicyManager.getPasswordBlacklistName(null);
            fail("Did not throw NullPointerException");
        } catch (NullPointerException e) {
            return;
        }
    }

    public void testBlacklistNotConsideredByIsActivePasswordSufficient() {
        if (!mShouldRun) {
            return;
        }
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX);
        final String complexPassword = ".password123";
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(
                ADMIN_RECEIVER_COMPONENT, complexPassword, TOKEN, 0));
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, "Sufficient", Arrays.asList(complexPassword)));
        assertPasswordSufficiency(true);
    }

    private static final int MAX_BLACKLIST_ITEM_SIZE = 8;

    /* Generate a list based on the 128 thousand character limit */
    private List<String> generateMaxBlacklist() {
        final int numItems = (128 * 1000) / MAX_BLACKLIST_ITEM_SIZE;
        assertTrue(numItems == 16 * 1000);
        final List<String> blacklist = new ArrayList(numItems);
        final String item = new String(new char[MAX_BLACKLIST_ITEM_SIZE]).replace('\0', 'a');
        for (int i = 0; i < numItems; ++i) {
            blacklist.add(item);
        }
        return blacklist;
    }

    private List<String> generateJustTooBigBlacklist() {
        List<String> list = generateMaxBlacklist();
        list.set(0, new String(new char[MAX_BLACKLIST_ITEM_SIZE + 1]).replace('\0', 'a'));
        return list;
    }

    /**
     * Install a blacklist, ensure items match and don't match it correctly.
     */
    private void testPasswordBlacklist(List<String> blacklist, String notInBlacklist) {
        assertTrue(mDevicePolicyManager.setPasswordBlacklist(
                ADMIN_RECEIVER_COMPONENT, "Test Blacklist", blacklist));

        if (blacklist != null) {
            // These are blacklisted so can't be set
            for (final String password : blacklist) {
                assertFalse(mDevicePolicyManager.resetPasswordWithToken(
                        ADMIN_RECEIVER_COMPONENT, password, TOKEN, 0));
            }
        }

        if (notInBlacklist != null) {
            // This isn't blacklisted so can be set
            assertTrue(mDevicePolicyManager.resetPasswordWithToken(
                    ADMIN_RECEIVER_COMPONENT, notInBlacklist, TOKEN, 0));
        }
    }
}
