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
package com.android.bedstead.dpmwrapper;

import android.app.ActivityManager;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.os.UserManager;

final class Utils {

    static final boolean VERBOSE = false;

    static final int MY_USER_ID = UserHandle.myUserId();

    static final String ACTION_WRAPPED_MANAGER_CALL =
            "com.android.bedstead.dpmwrapper.action.WRAPPED_MANAGER_CALL";
    static final String EXTRA_CLASS = "className";
    static final String EXTRA_METHOD = "methodName";
    static final String EXTRA_NUMBER_ARGS = "number_args";
    static final String EXTRA_ARG_PREFIX = "arg_";

    static boolean isHeadlessSystemUser() {
        return UserManager.isHeadlessSystemUserMode() && MY_USER_ID == UserHandle.USER_SYSTEM;
    }

    static boolean isCurrentUserOnHeadlessSystemUser() {
        return UserManager.isHeadlessSystemUserMode()
                && MY_USER_ID == ActivityManager.getCurrentUser();
    }

    static void assertCurrentUserOnHeadlessSystemMode() {
        if (isCurrentUserOnHeadlessSystemUser()) return;

        throw new IllegalStateException("Should only be called by current user ("
                + ActivityManager.getCurrentUser() + ") on headless system user device, but was "
                        + "called by process from user " + MY_USER_ID);
    }

    static String toString(IntentFilter filter) {
        StringBuilder builder = new StringBuilder("[");
        filter.actionsIterator().forEachRemaining((s) -> builder.append(s).append(","));
        builder.deleteCharAt(builder.length() - 1);
        return builder.append(']').toString();
    }

    private Utils() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
