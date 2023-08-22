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

package android.view.inputmethod.cts.util;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.SystemUtil;

import java.util.ArrayList;

/**
 * Utility methods for {@link Settings.Secure}.
 */
public final class SecureSettingsUtils {

    private static final char INPUT_METHOD_SEPARATOR = ':';
    private static final char INPUT_METHOD_SUBTYPE_SEPARATOR = ';';

    /**
     * Not intended to be instantiated.
     */
    private SecureSettingsUtils() {
    }

    /**
     * Update {@link Settings.Secure#ENABLED_INPUT_METHODS} so that the given IME can be enabled
     * with the specified array of {@link InputMethodSubtype}.
     *
     * @param context {@link Context} to be used to access
     *                {@link Settings.Secure#ENABLED_INPUT_METHODS}.
     * @param imeId IME ID to be updated.
     * @param subtypes An array of {@link InputMethodSubtype}. Can be empty.
     */
    public static void updateEnabledInputMethods(@NonNull Context context, @NonNull String imeId,
            @NonNull InputMethodSubtype[] subtypes) {
        final ContentResolver resolver = context.getContentResolver();
        final String enabledInputMethods = SystemUtil.runWithShellPermissionIdentity(
                () -> Settings.Secure.getString(resolver,
                        Settings.Secure.ENABLED_INPUT_METHODS));
        final var imeSplitter = new TextUtils.SimpleStringSplitter(INPUT_METHOD_SEPARATOR);
        imeSplitter.setString(enabledInputMethods);
        final StringBuilder sb = new StringBuilder();

        while (imeSplitter.hasNext()) {
            final String element = imeSplitter.next();
            final var subtypeSplitter =
                    new TextUtils.SimpleStringSplitter(INPUT_METHOD_SUBTYPE_SEPARATOR);
            subtypeSplitter.setString(element);
            final var id = subtypeSplitter.next();
            final ArrayList<String> subtypeHashCodes = new ArrayList<>();
            while (subtypeSplitter.hasNext()) {
                subtypeHashCodes.add(subtypeSplitter.next());
            }
            if (TextUtils.equals(imeId, id)) {
                subtypeHashCodes.clear();
                for (var subtype : subtypes) {
                    subtypeHashCodes.add(Integer.toString(subtype.hashCode()));
                }
            }
            if (sb.length() > 0) {
                sb.append(INPUT_METHOD_SEPARATOR);
            }
            sb.append(imeId);
            for (var hashCode : subtypeHashCodes) {
                sb.append(INPUT_METHOD_SUBTYPE_SEPARATOR).append(hashCode);
            }
        }
        final String newSecureSettings = sb.toString();
        SystemUtil.runWithShellPermissionIdentity(() ->
                Settings.Secure.putString(resolver,
                        Settings.Secure.ENABLED_INPUT_METHODS, newSecureSettings));
    }
}
