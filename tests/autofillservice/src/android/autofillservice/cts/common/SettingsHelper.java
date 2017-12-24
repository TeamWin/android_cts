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

package android.autofillservice.cts.common;

import static android.autofillservice.cts.common.ShellHelper.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Provides utilities to interact with the device's {@link Settings}.
 */
// TODO: make it more generic (it's hardcoded to 'secure' provider on current user
public final class SettingsHelper {

    /**
     * Uses a Shell command to set the given preference.
     */
    public static void set(@NonNull String key, @Nullable String value) {
        if (value == null) {
            delete(key);
            return;
        }
        runShellCommand("settings put secure %s %s default", key, value);
    }

    /**
     * Uses a Shell command to set the given preference, and verifies it was correctly set.
     */
    public static void syncSet(@NonNull Context context, @NonNull String key,
            @Nullable String value) {
        if (value == null) {
            syncDelete(context, key);
            return;
        }

        final OneTimeSettingsListener observer = new OneTimeSettingsListener(context, key);
        set(key, value);
        observer.assertCalled();

        final String newValue = get(key);
        assertWithMessage("invalid value for '%s' settings", key).that(newValue).isEqualTo(value);
    }

    /**
     * Uses a Shell command to delete the given preference.
     */
    public static void delete(@NonNull String key) {
        runShellCommand("settings delete secure %s", key);
    }

    /**
     * Uses a Shell command to delete the given preference, and verifies it was correctly deleted.
     */
    public static void syncDelete(@NonNull Context context, @NonNull String key) {

        final OneTimeSettingsListener observer = new OneTimeSettingsListener(context, key);
        delete(key);
        observer.assertCalled();

        final String newValue = get(key);
        assertWithMessage("invalid value for '%s' settings", key).that(newValue).isEqualTo("null");
    }

    /**
     * Gets the value of a given preference using Shell command.
     */
    @NonNull
    public static String get(@NonNull String key) {
        return runShellCommand("settings get secure %s", key);
    }

    private SettingsHelper() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
