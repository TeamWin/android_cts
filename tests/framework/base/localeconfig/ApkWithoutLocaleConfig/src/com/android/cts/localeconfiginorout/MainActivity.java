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

package com.android.cts.localeconfiginorout;

import static android.localeconfig.cts.util.LocaleConstants.APP_CREATION_INFO_PROVIDER_ACTION;
import static android.localeconfig.cts.util.LocaleConstants.EXTRA_QUERY_LOCALES;
import static android.localeconfig.cts.util.LocaleConstants.EXTRA_SET_LOCALES;
import static android.localeconfig.cts.util.LocaleUtils.constructResultIntent;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.LocaleList;

import androidx.annotation.Nullable;

/**
 * An activity used by {@link LocaleConfigTest} and {@link LocaleConfigAppUpdateTest}
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocaleManager localeManager = getSystemService(LocaleManager.class);
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_SET_LOCALES)) {
            localeManager.setApplicationLocales(LocaleList.forLanguageTags(
                    intent.getStringExtra(EXTRA_SET_LOCALES)));
        } else if (intent != null && intent.hasExtra(EXTRA_QUERY_LOCALES)) {
            String packageName = intent.getStringExtra(EXTRA_QUERY_LOCALES);
            try {
                LocaleList locales = localeManager.getApplicationLocales(packageName);
                sendBroadcast(constructResultIntent(APP_CREATION_INFO_PROVIDER_ACTION,
                        packageName, locales));
            } catch (SecurityException e) {
            }
        }
        finish();
    }
}
