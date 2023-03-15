/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

/**
 * Helper to set user preferences.
 */
public final class UserSettings {

    private static final String TAG = UserSettings.class.getSimpleName();

    private static final int CURRENT_USER = UserHandle.CURRENT.getIdentifier();

    // Constants below are needed for switch statements
    public static final String NAMESPACE_SECURE = "secure";
    public static final String NAMESPACE_GLOBAL = "global";

    private final Context mContext;
    private final Namespace mNamespace;
    private final int mUserId;

    /**
     * Default constructor, it uses:
     *
     * <ul>
     *   <li>target context of the instrumented app
     *   <li>secure namespace
     *   <li>{@link android.app.ActivityManager#getCurrentUser() current foreground user}
     * </ul>
     */
    public UserSettings() {
        this(CURRENT_USER);
    }

    /**
     * Constructor for the {@link android.app.ActivityManager#getCurrentUser() current foreground
     * user}
     */
    public UserSettings(Context context, Namespace namespace) {
        this(context, namespace, CURRENT_USER);
    }

    /**
     * Constructor for the given user using target context and the secure namespace.
     */
    public UserSettings(int userId) {
        this(InstrumentationRegistry.getTargetContext(), Namespace.SECURE, userId);
    }

    /**
     * Full constructor.
     */
    public UserSettings(Context context, Namespace namespace, int userId) {
        mContext = context;
        mNamespace = namespace;
        mUserId = userId;
    }

    /**
     * Sets the value of the given preference, using a Settings listener to block until it's set.
     */
    public void syncSet(String key, @Nullable String value) {
        logd("syncSet(%s, %s)", key, value);
        if (value == null) {
            syncDelete(key);
            return;
        }

        String currentValue = get(key);
        if (value.equals(currentValue)) {
            // Already set, ignore
            return;
        }

        OneTimeSettingsListener observer = new OneTimeSettingsListener(mContext, mNamespace.get(),
                key);
        set(key, value);
        observer.assertCalled();

        String newValue = get(key);
        if (TMP_HACK_REMOVE_EMPTY_PROPERTIES && TextUtils.isEmpty(value)) {
            assertWithMessage("value of '%s'", key).that(newValue).isNull();
        } else {
            assertWithMessage("value of '%s'", key).that(newValue).isEqualTo(value);
        }
    }

    /**
     * Sets the value of the given preference.
     */
    public void set(String key, @Nullable String value) {
        if (value == null) {
            delete(key);
            return;
        }
        if (TMP_HACK_REMOVE_EMPTY_PROPERTIES && TextUtils.isEmpty(value)) {
            Log.w(TAG, "Value of " + mNamespace.get() + ":" + key + " is empty; deleting it "
                    + "instead");
            delete(key);
            return;
        }
        runShellCommand("settings put --user %s %s %s %s default", getUser(), mNamespace.get(), key,
                value);
    }

    /**
     * Deletes the given preference using a Settings listener to block until it's deleted.
     */
    public void syncDelete(String key) {
        String currentValue = get(key);
        logd("syncDelete(%s), currentValue=%s", key, currentValue);
        if (currentValue == null) {
            // Already set, ignore
            return;
        }

        OneTimeSettingsListener observer = new OneTimeSettingsListener(mContext, mNamespace.get(),
                key);
        delete(key);
        observer.assertCalled();

        String newValue = get(key);
        assertWithMessage("value of '%s' after it was removed", key).that(newValue).isNull();
    }

    /**
     * Deletes the given preference.
     */
    public void delete(String key) {
        logd("delete(%s)", key);
        runShellCommand("settings delete --user %s %s %s", getUser(), mNamespace.get(), key);
    }

    /**
     * Gets the value of a preference.
     */
    public String get(String key) {
        String value = runShellCommand("settings get --user %s %s %s", getUser(), mNamespace.get(),
                key);
        String returnedValue = value == null || value.equals("null") ? null : value;
        logd("get(%s): settings returned '%s', returning '%s'", key, value, returnedValue);
        return returnedValue;
    }

    @Override
    public String toString() {
        return "UserSettings[" + toShortString() + "]";
    }

    private void logd(String template, Object...args) {
        Log.d(TAG, "[" + toShortString() + "]: " + String.format(template, args));
    }

    private String toShortString() {
        StringBuilder string = new StringBuilder("namespace=").append(mNamespace)
                .append(", user=");
        if (mUserId == CURRENT_USER) {
            string.append("CURRENT");
        } else {
            string.append(mUserId);
        }
        return string.toString();
    }

    /**
     * Abstracts the Settings namespace.
     */
    public enum Namespace {
        SECURE(NAMESPACE_SECURE),
        GLOBAL(NAMESPACE_GLOBAL);

        private final String mName;

        Namespace(String name) {
            mName = name;
        }

        /**
         * Gets the namespace as used by the {@code settings} shell command.
         */
        public String get() {
            return mName;
        }
    }

    // TODO(b/123885378): remove once it uses Settings API (instead of ShellCommand)
    private String getUser() {
        return mUserId == CURRENT_USER ? "cur" : String.valueOf(mUserId);
    }

    // TODO(b/123885378): we cannot pass an empty value when using 'cmd settings', so we need
    // to remove the property instead. Once we use the Settings API directly, we can remove this
    // constant and all if() statements that uses it
    static final boolean TMP_HACK_REMOVE_EMPTY_PROPERTIES = true;
}
