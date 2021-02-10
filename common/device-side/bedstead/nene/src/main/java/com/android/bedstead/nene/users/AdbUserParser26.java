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

package com.android.bedstead.nene.users;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.exceptions.AdbParseException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parser for "adb dumpsys user" on Android 26+
 *
 * <p>Example output:
 * {@code
 * Users:
 *   UserInfo{0:null:13} serialNo=0
 *     State: RUNNING_UNLOCKED
 *     Created: <unknown>
 *     Last logged in: +11m34s491ms ago
 *     Last logged in fingerprint: generic/gce_x86_phone/gce_x86:8.0.0/OPR1.170623
 *     .041/4833325:userdebug/test-keys
 *     Has profile owner: false
 *     Restrictions:
 *       none
 *     Device policy global restrictions:
 *       null
 *     Device policy local restrictions:
 *       null
 *     Effective restrictions:
 *       none
 *   UserInfo{10:managedprofileuser:20} serialNo=10
 *     State: -1
 *     Created: +1s901ms ago
 *     Last logged in: <unknown>
 *     Last logged in fingerprint: generic/gce_x86_phone/gce_x86:8.0.0/OPR1.170623
 *     .041/4833325:userdebug/test-keys
 *     Has profile owner: false
 *     Restrictions:
 *       none
 *     Device policy global restrictions:
 *       null
 *     Device policy local restrictions:
 *       null
 *     Effective restrictions:
 *       none
 *
 *   Device owner id:-10000
 *
 *   Guest restrictions:
 *     no_sms
 *     no_install_unknown_sources
 *     no_config_wifi
 *     no_outgoing_calls
 *
 *   Device managed: false
 *   Started users state: {0=3}
 *
 *   Max users: 4
 *   Supports switchable users: false
 *   All guests ephemeral: false
 * @}
 */
@RequiresApi(Build.VERSION_CODES.O)
public class AdbUserParser26 implements AdbUserParser {
    static final int USER_LIST_BASE_INDENTATION = 2;

    private final Users mUsers;

    AdbUserParser26(Users users) {
        if (users == null) {
            throw new NullPointerException();
        }
        mUsers = users;
    }

    @Override
    public ParseResult parse(String dumpsysUsersOutput) throws AdbParseException {
        ParseResult parseResult = new ParseResult();
        parseResult.mUsers = parseUsers(dumpsysUsersOutput);
        return parseResult;
    }

    Map<Integer, User> parseUsers(String dumpsysUsersOutput) throws AdbParseException {
        String usersList = extractUsersList(dumpsysUsersOutput);
        Set<String> userStrings = extractUserStrings(usersList);
        Map<Integer, User> users = new HashMap<>();
        for (String userString : userStrings) {
            User user = new User(mUsers, parseUser(userString));
            users.put(user.id(), user);
        }
        return users;
    }

    String extractUsersList(String dumpsysUsersOutput) throws AdbParseException {
        try {
            return dumpsysUsersOutput.split("Users:\n", 2)[1].split("\n\n", 2)[0];
        } catch (RuntimeException e) {
            throw new AdbParseException("Error extracting user list", dumpsysUsersOutput, e);
        }
    }

    Set<String> extractUserStrings(String usersList) throws AdbParseException {
        return extractIndentedSections(usersList, USER_LIST_BASE_INDENTATION);
    }

    Set<String> extractIndentedSections(String list, int baseIndentation) throws AdbParseException {
        try {
            Set<String> sections = new HashSet<>();
            String[] lines = list.split("\n");
            StringBuilder sectionBuilder = null;
            for (String line : lines) {
                int indentation = countIndentation(line);
                if (indentation == baseIndentation) {
                    // New item
                    if (sectionBuilder != null) {
                        sections.add(sectionBuilder.toString().trim());
                    }
                    sectionBuilder = new StringBuilder(line).append("\n");
                } else {
                    sectionBuilder.append(line).append("\n");
                }
            }
            sections.add(sectionBuilder.toString().trim());
            return sections;
        } catch (RuntimeException e) {
            throw new AdbParseException("Error extracting indented sections", list, e);
        }
    }

    int countIndentation(String s) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return s.length();
        }
        return s.indexOf(trimmed);
    }

    User.MutableUser parseUser(String userString) throws AdbParseException {
        try {
            String userInfo[] = userString.split("UserInfo\\{", 2)[1].split("\\}", 2)[0].split(":");
            User.MutableUser user = new User.MutableUser();
            user.mName = userInfo[1];
            user.mId = Integer.parseInt(userInfo[0]);
            user.mSerialNo = Integer.parseInt(
                    userString.split("serialNo=", 2)[1].split("[ \n]", 2)[0]);
            user.mHasProfileOwner =
                    Boolean.parseBoolean(
                            userString.split("Has profile owner: ", 2)[1].split("\n", 2)[0]);
            user.mState =
                    User.UserState.fromDumpSysValue(
                            userString.split("State: ", 2)[1].split("\n", 2)[0]);
            return user;
        } catch (RuntimeException e) {
            throw new AdbParseException("Error parsing user", userString, e);
        }
    }
}
