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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.UI_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.provider.Settings.Secure.AUTO_FILL_SERVICE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * Base class for all other tests.
 */
@RunWith(AndroidJUnit4.class)
abstract class AutoFillServiceTestCase {

    private static final String SERVICE_NAME =
            InstrumentedAutoFillService.class.getPackage().getName()
            + "/." + InstrumentedAutoFillService.class.getSimpleName();

    protected static UiBot sUiBot;

    @BeforeClass
    public static void setUiBot() throws Exception {
        sUiBot = new UiBot(InstrumentationRegistry.getInstrumentation(), UI_TIMEOUT_MS);
    }

    @AfterClass
    public static void disableService() {
        runShellCommand("settings delete secure %s", AUTO_FILL_SERVICE);
        assertServiceDisabled();
    }

    @Before
    public void resetServiceState() {
        InstrumentedAutoFillService.resetNumberFillRequests();
    }

    /**
     * Enables the {@link InstrumentedAutoFillService} for auto-fill for the default user.
     */
    protected void enableService() {
        runShellCommand(
                "settings put secure %s %s default", AUTO_FILL_SERVICE, SERVICE_NAME);
        assertServiceEnabled();
    }

    /**
     * Asserts that the {@link InstrumentedAutoFillService} is enabled for the default user.
     */
    protected static void assertServiceEnabled() {
        assertServiceStatus(true);
    }

    /**
     * Asserts that the {@link InstrumentedAutoFillService} is disabled for the default user.
     */
    protected static void assertServiceDisabled() {
        assertServiceStatus(false);
    }

    private static void assertServiceStatus(boolean enabled) {
        final String actual = runShellCommand("settings get secure %s", AUTO_FILL_SERVICE);
        final String expected = enabled ? SERVICE_NAME : "null";
        assertWithMessage("Invalid value for secure setting %s", AUTO_FILL_SERVICE)
                .that(actual).isEqualTo(expected);
    }
}
