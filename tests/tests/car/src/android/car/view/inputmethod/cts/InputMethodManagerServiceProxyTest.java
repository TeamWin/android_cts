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

package android.car.view.inputmethod.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.SparseArray;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO(b/267678351): Mark this test with RequireCheckerRule
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public final class InputMethodManagerServiceProxyTest {

    public static final String NULL_AUTOFILL_SUGGESTIONS_CONTROLLER =
            "com.android.server.inputmethod.NullAutofillSuggestionsController";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private UserManager mUserManager;
    private Resources mResources;

    @Before
    public void setUp() {
        mUserManager = mContext.getSystemService(UserManager.class);
        assumeTrue(mUserManager.isVisibleBackgroundUsersSupported());

        mResources = mContext.getResources();
    }

    @Test
    public void testIsVisibleBackgroundUsersSupportedIsInSyncWithIMMSProxy() {
        String immsProxyClass = mResources.getString(mResources.getIdentifier(
                "config_deviceSpecificInputMethodManagerService", "string", "android"));
        assertThat(immsProxyClass).isNotEmpty();
    }

    @Test
    public void testAutofillIsDisabledForSystemUser() {
        String dump = ShellUtils.runShellCommand("dumpsys input_method");
        SparseArray<String> configs = parseServiceConfigDump(dump);
        assertWithMessage("A dedicated IMMS must be initialized for USER_SYSTEM")
                .that(configs.contains(UserHandle.USER_SYSTEM)).isTrue();
        assertWithMessage("USER_SYSTEM's IMMS must be set with null autofill")
                .that(configs.get(UserHandle.USER_SYSTEM)).contains(
                NULL_AUTOFILL_SUGGESTIONS_CONTROLLER);
    }

    private SparseArray<String> parseServiceConfigDump(String dump) {
        Pattern dumpPattern = Pattern.compile("\\*\\*mServicesForUser\\*\\*(.+?)\\*\\*",
                Pattern.DOTALL);
        Matcher dumpMatcher = dumpPattern.matcher(dump);
        SparseArray<String> configs = new SparseArray<>();
        if (!dumpMatcher.find()) {
            return configs;
        }
        Pattern immsPattern = Pattern.compile("userId=(.*?) imms=(.*?) \\{autofill=(.*?)\\}");
        String immsMatch = dumpMatcher.group(1);
        Matcher immsMatcher = immsPattern.matcher(immsMatch);
        while (immsMatcher.find()) {
            int userId = Integer.parseInt(immsMatcher.group(1));
            String autofillClass = immsMatcher.group(3);
            configs.put(userId, autofillClass);
        }
        return configs;
    }
}
