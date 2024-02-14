/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarWifiHostTest extends CarHostJUnit4TestCase {
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15);

    private static final String GET_TETHERING_PERSISTING =
            "settings get global android.car.ENABLE_TETHERING_PERSISTING";
    private static final String ENABLE_TETHERING_PERSISTING =
            "settings put global android.car.ENABLE_TETHERING_PERSISTING true";
    private static final String DISABLE_TETHERING_PERSISTING =
            "settings put global android.car.ENABLE_TETHERING_PERSISTING false";
    private static final String CMD_DUMPSYS_WIFI =
            "dumpsys car_service --services CarWifiService";
    private static final String GET_TETHERING_CAPABILITY =
            "cmd car_service get-tethering-capability";
    private static final String WIFI_HOTSPOT_ON = "cmd wifi start-softap CarWifiService open";
    private static final String WIFI_HOTSPOT_OFF = "cmd wifi stop-softap";
    private static boolean sTetheringStatusBefore;
    private static boolean sTetheringPersistingBefore;

    /**
     * Prepares the device to restore back to original state post-test.
     *
     * @param testInfo Test Information
     * @throws Exception if connection with device is lost and cannot be recovered.
     */
    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        sTetheringStatusBefore = testInfo.getDevice().executeShellCommand(CMD_DUMPSYS_WIFI)
                .contains("Tethering enabled: true");
        sTetheringPersistingBefore = testInfo.getDevice().executeShellCommand(
                GET_TETHERING_PERSISTING).contains("true");
    }

    /**
     * Restores original state to conditions before testing.
     *
     * @param testInfo Test Information
     * @throws Exception if connection with device is lost and cannot be recovered.
     */
    @AfterClassWithInfo
    public static void afterClassWithInfo(TestInformation testInfo) throws Exception {
        String hotspotCommand = (sTetheringStatusBefore ? WIFI_HOTSPOT_ON : WIFI_HOTSPOT_OFF);
        testInfo.getDevice().executeShellCommand(hotspotCommand);

        String persistTetheringCommand =
                (sTetheringPersistingBefore ? ENABLE_TETHERING_PERSISTING
                        : DISABLE_TETHERING_PERSISTING);
        testInfo.getDevice().executeShellCommand(persistTetheringCommand);
    }

    @Test
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_TETHERING_PERSISTING"})
    public void testPersistTetheringCarSetting_enablingWithCapability_autoShutdownDisabled()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());
        executeCommand(ENABLE_TETHERING_PERSISTING);
        assertThat(isAutoShutdownDisabled()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_TETHERING_PERSISTING"})
    public void testPersistTetheringCarSetting_disablingWithCapability_autoShutdownEnabled()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());
        executeCommand(DISABLE_TETHERING_PERSISTING);
        assertThat(isAutoShutdownDisabled()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_TETHERING_PERSISTING"})
    public void testPersistTetheringCarSetting_enablingNoCapability_autoShutdownUnchanged()
            throws Exception {
        assumeFalse("Skipping test: tethering capability enabled",
                isPersistTetheringCapabilityEnabled());
        boolean autoShutdownEnabledBefore = isAutoShutdownDisabled();
        executeCommand(ENABLE_TETHERING_PERSISTING);
        assertThat(isAutoShutdownDisabled()).isEqualTo(autoShutdownEnabledBefore);
    }

    @Test
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_TETHERING_PERSISTING"})
    public void testPersistTetheringCarSetting_disablingNoCapability_autoShutdownUnchanged()
            throws Exception {
        assumeFalse("Skipping test: tethering capability enabled",
                isPersistTetheringCapabilityEnabled());
        boolean autoShutdownEnabledBefore = isAutoShutdownDisabled();
        executeCommand(DISABLE_TETHERING_PERSISTING);
        assertThat(isAutoShutdownDisabled()).isEqualTo(autoShutdownEnabledBefore);
    }

    @Test
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings",
            "android.car.settings.CarSettings#ENABLE_TETHERING_PERSISTING"})
    public void testPersistTetheringCarSetting_withCapabilityTetheringEnabled_tetheringOnReboot()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());

        enablePersistTetheringAndReboot(/* enableTethering= */ true);

        PollingCheck.check("Tethering NOT enabled", TIMEOUT_MS, this::isTetheringEnabled);
        assertThat(isAutoShutdownDisabled()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings",
            "android.car.settings.CarSettings#ENABLE_TETHERING_PERSISTING"})
    public void testPersistTetheringCarSetting_withCapabilityTetheringDisabled_noTetheringOnReboot()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());

        enablePersistTetheringAndReboot(/* enableTethering= */ false);

        assertThrows(AssertionError.class,
                () -> PollingCheck.check("Tethering NOT enabled", TIMEOUT_MS,
                        this::isTetheringEnabled));
    }

    @Test
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings",
            "android.car.settings.CarSettings#ENABLE_TETHERING_PERSISTING"})
    public void testPersistTetheringCarSetting_noCapabilityTetheringEnabled_noTetheringOnReboot()
            throws Exception {
        assumeFalse("Skipping test: tethering capability enabled",
                isPersistTetheringCapabilityEnabled());

        enablePersistTetheringAndReboot(/* enableTethering= */ true);

        assertThrows(AssertionError.class,
                () -> PollingCheck.check("Tethering NOT enabled", TIMEOUT_MS,
                        this::isTetheringEnabled));
    }

    private boolean isTetheringEnabled() throws Exception {
        String output = executeCommand(CMD_DUMPSYS_WIFI);
        return output.contains("Tethering enabled: true");
    }

    private boolean isAutoShutdownDisabled() throws Exception {
        String output = executeCommand(CMD_DUMPSYS_WIFI);
        return output.contains("Auto shutdown enabled: false");
    }

    private boolean isPersistTetheringCapabilityEnabled() throws Exception {
        String output = executeCommand(GET_TETHERING_CAPABILITY);
        return output.contains("true");
    }

    private void enablePersistTetheringAndReboot(boolean enableTethering) throws Exception {
        String hotspotCommand = (enableTethering ? WIFI_HOTSPOT_ON : WIFI_HOTSPOT_OFF);
        executeCommand(hotspotCommand);

        executeCommand(ENABLE_TETHERING_PERSISTING);

        reboot();
        waitForCarServiceReady();
        waitForUserInitialized(/* userId= */ 0);
    }
}
