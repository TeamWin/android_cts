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

import static android.server.wm.CliIntentExtra.extraString;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.LocaleConfig;
import android.content.ComponentName;
import android.content.Context;
import android.os.LocaleList;
import android.server.wm.ActivityManagerTestBase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tests for {@link android.app.LocaleConfig} API(s).
 *
 * Build/Install/Run: atest LocaleConfigTest
 */

@RunWith(AndroidJUnit4.class)
public class LocaleConfigTest extends ActivityManagerTestBase {
    private static final String APK_PATH = "/data/local/tmp/cts/localeconfig/";
    private static final String APK_WITH_LOCALECONFIG = APK_PATH + "ApkWithLocaleConfig.apk";
    private static final String APK_WITHOUT_LOCALECONFIG = APK_PATH + "ApkWithoutLocaleConfig.apk";
    private static final String TEST_PACKAGE = "com.android.cts.localeconfiginorout";
    private static final String EXTRA_SET_LOCALECONFIG = "set_localeconfig";
    private static final List<String> RESOURCE_LOCALES = Arrays.asList(
            new String[]{"en-US", "zh-Hant-TW", "pt-PT", "fr-FR", "zh-Hans-SG"});
    public static final LocaleList OVERRIDE_LOCALES =
            LocaleList.forLanguageTags("en-US,fr-FR,zh-Hant-TW");
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);

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

        assertEquals(RESOURCE_LOCALES.stream().sorted().collect(Collectors.toList()),
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
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);

        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());
    }

    /**
     * Tests the scenario where the correct LocaleConfig is returned when an overridden
     * LocaleConfig has been set by the application.
     */
    @Test
    public void testGetOverrideLocaleConfig() throws Exception {
        install(APK_WITH_LOCALECONFIG);

        // Tell the app to override its app-specific LocaleConfig
        launchActivity(new ComponentName(TEST_PACKAGE, TEST_PACKAGE + ".MainActivity"),
                extraString(EXTRA_SET_LOCALECONFIG, OVERRIDE_LOCALES.toLanguageTags()));
        // Verify where an override LocaleConfig of the test app can be read successfully
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);

        PollingCheck.check("Make sure that the override LocaleConfig is read correctly", TIMEOUT,
                () -> {
                    LocaleConfig override = new LocaleConfig(appContext);
                    return override.describeContents() == 0 && OVERRIDE_LOCALES.equals(
                            override.getSupportedLocales());
                });
    }

    /**
     * Tests the scenario where the LocaleConfig from the application resources is returned when
     * an overridden LocaleConfig has been set by the application.
     */
    @Test
    public void testGetOriginalLocaleConfig() throws Exception {
        install(APK_WITH_LOCALECONFIG);

        // Tell the app to override its app-specific LocaleConfig
        launchActivity(new ComponentName(TEST_PACKAGE, TEST_PACKAGE + ".MainActivity"),
                extraString(EXTRA_SET_LOCALECONFIG, OVERRIDE_LOCALES.toLanguageTags()));
        // Verify where an original LocaleConfig of the test app can be read successfully
        Context appContext = mContext.createPackageContext(TEST_PACKAGE, 0);

        PollingCheck.check("Make sure that the original LocaleConfig is read correctly", TIMEOUT,
                () -> RESOURCE_LOCALES.stream().sorted().collect(Collectors.toList()).equals(
                        new ArrayList<String>(Arrays.asList(
                                LocaleConfig.fromResources(
                                        appContext).getSupportedLocales().toLanguageTags().split(
                                        ","))).stream().sorted().collect(Collectors.toList())));
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

