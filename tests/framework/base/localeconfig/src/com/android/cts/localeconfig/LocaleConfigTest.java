/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.localeconfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.LocaleConfig;
import android.content.Context;
import android.os.LocaleList;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link android.app.LocaleConfig} API(s).
 *
 * Build/Install/Run: atest LocaleConfigTest
 */

@RunWith(AndroidJUnit4.class)
public class LocaleConfigTest {
    private static final String APK_PATH = "/data/local/tmp/cts/localeconfig/";
    private static final String APK_WITH_LOCALECONFIG = APK_PATH + "ApkWithLocaleConfig.apk";
    private static final String APK_WITHOUT_LOCALECONFIG = APK_PATH + "ApkWithoutLocaleConfig.apk";
    private static final String TEST_PACKAGE = "com.android.cts.localeconfiginorout";
    private static final List<String> EXPECT_LOCALES = Arrays.asList(
            new String[]{"en-US", "zh-TW", "pt", "fr", "zh-Hans-SG"});

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @After
    public void tearDown() throws Exception {
        uninstall(TEST_PACKAGE);
    }

    /**
     * Tests the scenario where a LocaleConfig of an application can be read successfully.
     */
    @Test
    public void testGetLocaleList() throws Exception {
        install(APK_WITH_LOCALECONFIG);
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);

        assertEquals(EXPECT_LOCALES.stream().sorted().collect(Collectors.toList()),
                new ArrayList<String>(Arrays.asList(
                        localeConfig.getSupportedLocales().toLanguageTags().split(
                                ","))).stream().sorted().collect(Collectors.toList()));

        assertEquals(LocaleConfig.STATUS_SUCCESS, localeConfig.getStatus());
    }

    /**
     * Tests the scenario where the correct status is returned when there is no LocaleConfig in the
     * application.
     */
    @Test
    public void testNoLocaleConfigTag() throws Exception {
        install(APK_WITHOUT_LOCALECONFIG);
        Context context = InstrumentationRegistry.getTargetContext();
        Context appContext = context.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);

        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());
    }

    private void install(String apk) throws InterruptedException {
        String installResult = ShellUtils.runShellCommand("pm install " + apk);
        Thread.sleep(3000);
        assertThat(installResult.trim()).isEqualTo("Success");
    }

    private void uninstall(String packageName) throws InterruptedException {
        String uninstallResult = ShellUtils.runShellCommand("pm uninstall " + packageName);
        Thread.sleep(3000);
        assertThat(uninstallResult.trim()).isEqualTo("Success");
    }
}

