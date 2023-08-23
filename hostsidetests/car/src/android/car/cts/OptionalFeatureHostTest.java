/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.junit.Assume.assumeThat;

import com.android.car.CarFeatureControlDumpProto;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Check Optional Feature related car configs.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class OptionalFeatureHostTest extends CarHostJUnit4TestCase {

    private static final String[] MANDATORY_FEATURES = {
            "android.car.input",
            "app_focus",
            "audio",
            "cabin",
            "car_bluetooth",
            "car_bugreport",
            "car_device_policy_service",
            "car_media",
            "car_navigation_service",
            "car_occupant_zone_service",
            "car_user_service",
            "car_watchdog",
            "drivingstate",
            "hvac",
            "info",
            "package",
            "power",
            "projection",
            "property",
            "sensor",
            "uxrestriction",
            "vendor_extension"
    };

    /**
     * Partners can use the same system image for multiple product configs with variation in
     * optional feature support. But CTS should run in a config where VHAL
     * DISABLED_OPTIONAL_FEATURES property does not disable any optional features.
     */
    @Test
    public void testNoDisabledFeaturesFromVHAL() throws Exception {
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();

        int numDisabledFeaturesFromVhal = featureControlDump.getDisabledFeaturesFromVhalCount();

        assertWithMessage("Number of features disabled from VHAL").that(
                numDisabledFeaturesFromVhal).isEqualTo(0);
    }

    /**
     * Experimental features cannot be shipped. There should be no experimental features available
     * in the device.
     */
    @Test
    public void testNoExperimentalFeatures() throws Exception {
        // experimental feature disabled in user build
        assumeUserBuild();
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();

        int numExperimentalFeatures = featureControlDump.getAvailableExperimentalFeaturesCount();

        assertWithMessage("Number of experimental features available").that(
                numExperimentalFeatures).isEqualTo(0);
    }

    /**
     * Experimental car service package should not exist in the device.
     */
    @Test
    public void testNoExperimentalCarServicePackage() throws Exception {
        // experimental feature disabled in user build
        assumeUserBuild();
        // Only check for user 0 as experimental car service is launched as user 0.
        String output = getDevice().executeShellCommand(
                "pm list package com.android.experimentalcar").strip();
        assertWithMessage("Experimental car service package").that(output).isEmpty();
    }

    /**
     * All optional features declared from {@code mDefaultEnabledFeaturesFromConfig} should be
     * enabled for CTS test.
     */
    @Test
    public void testAllOptionalFeaturesEnabled() throws Exception {
        List<String> enabledFeatures = getEnabledFeatures();
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();
        List<String> optionalFeaturesFromConfig =
                featureControlDump.getDefaultEnabledFeaturesFromConfigList();
        List<String> missingFeatures = new ArrayList<>();
        for (String optional : optionalFeaturesFromConfig) {
            if (!enabledFeatures.contains(optional)) {
                missingFeatures.add(optional);
            }
        }
        assertWithMessage("Missing optional features from config").that(
                missingFeatures).isEmpty();
    }

    /**
     * Confirms that selected mandatory features are enabled.
     */
    @Test
    public void testAllMandatoryFeaturesEnabled() throws Exception {
        List<String> enabledFeatures = getEnabledFeatures();
        List<String> missingFeatures = new ArrayList<>();
        for (String optional : MANDATORY_FEATURES) {
            if (!enabledFeatures.contains(optional)) {
                missingFeatures.add(optional);
            }
        }
        assertWithMessage("Missing mandatory features").that(missingFeatures).isEmpty();
    }

    /**
     * Confirms that adb command cannot drop feature.
     */
    @Test
    public void testNoFeatureChangeAfterRebootForAdbCommand() throws Exception {
        List<String> enabledFeaturesOrig = getEnabledFeatures();
        for (String feature : enabledFeaturesOrig) {
            getDevice().executeShellCommand("cmd car_service disable-feature %s" + feature);
        }

        getDevice().reboot();
        List<String> enabledFeaturesAfterReboot = getEnabledFeatures();

        assertWithMessage(
                "Comparison between enabled features before and after device reboot").that(
                        enabledFeaturesOrig).isEqualTo(enabledFeaturesAfterReboot);
    }

    private CarFeatureControlDumpProto getFeatureControlDumpProto() throws Exception {
        return ProtoUtils.getProto(getDevice(), CarFeatureControlDumpProto.parser(),
                "dumpsys car_service --services CarFeatureController --proto");
    }

    private List<String> getEnabledFeatures() throws Exception {
        CarFeatureControlDumpProto featureControlDump = getFeatureControlDumpProto();
        return featureControlDump.getEnabledFeaturesList();
    }

    private void assumeUserBuild() throws Exception {
        assumeThat(getDevice().getBuildFlavor(), endsWith("-user"));
    }
}
