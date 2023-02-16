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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CELLULAR_2G;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_CELL_BROADCASTS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_MOBILE_NETWORKS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_DATA_ROAMING;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_OUTGOING_CALLS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SMS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ULTRA_WIDEBAND_RADIO;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowCellular2g;
import com.android.bedstead.harrier.policies.DisallowConfigCellBroadcasts;
import com.android.bedstead.harrier.policies.DisallowConfigMobileNetworks;
import com.android.bedstead.harrier.policies.DisallowDataRoaming;
import com.android.bedstead.harrier.policies.DisallowOutgoingCalls;
import com.android.bedstead.harrier.policies.DisallowSms;
import com.android.bedstead.harrier.policies.DisallowUltraWidebandRadio;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class TelephonyTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowConfigCellBroadcasts.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void setUserRestriction_disallowConfigCellBroadcasts_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS));
    }

    @PolicyAppliesTest(policy = DisallowConfigCellBroadcasts.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void setUserRestriction_disallowConfigCellBroadcasts_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_CELL_BROADCASTS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigCellBroadcasts.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void setUserRestriction_disallowConfigCellBroadcasts_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_CELL_BROADCASTS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_CELL_BROADCASTS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void disallowConfigCellBroadcastsIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_CELL_BROADCASTS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void disallowConfigCellBroadcasatsIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowConfigMobileNetworks.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void setUserRestriction_disallowConfigMobileNetworks_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS));
    }

    @PolicyAppliesTest(policy = DisallowConfigMobileNetworks.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void setUserRestriction_disallowConfigMobileNetworks_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_MOBILE_NETWORKS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigMobileNetworks.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void setUserRestriction_disallowConfigMobileNetworks_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_MOBILE_NETWORKS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_MOBILE_NETWORKS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void disallowConfigMobileNetworksIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_MOBILE_NETWORKS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void disallowConfigMobileNetworksIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowOutgoingCalls.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void setUserRestriction_disallowOutgoingCalls_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS));
    }

    @PolicyAppliesTest(policy = DisallowOutgoingCalls.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void setUserRestriction_disallowOutgoingCalls_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_OUTGOING_CALLS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowOutgoingCalls.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void setUserRestriction_disallowOutgoingCalls_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_OUTGOING_CALLS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_OUTGOING_CALLS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void disallowOutgoingCallsIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_OUTGOING_CALLS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void disallowOutgoingCallsIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowSms.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void setUserRestriction_disallowSms_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_SMS));
    }

    @PolicyAppliesTest(policy = DisallowSms.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void setUserRestriction_disallowSms_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SMS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowSms.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void setUserRestriction_disallowSms_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SMS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_SMS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void disallowSmsIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_SMS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void disallowSmsIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowDataRoaming.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void setUserRestriction_disallowDataRoaming_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING));
    }

    @PolicyAppliesTest(policy = DisallowDataRoaming.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void setUserRestriction_disallowDataRoaming_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_DATA_ROAMING))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowDataRoaming.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void setUserRestriction_disallowDataRoaming_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_DATA_ROAMING))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_DATA_ROAMING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void disallowDataRoamingIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_DATA_ROAMING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void disallowDataRoamingIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowCellular2g.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void setUserRestriction_disallowCellular2g_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G));
    }

    @PolicyAppliesTest(policy = DisallowCellular2g.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void setUserRestriction_disallowCellular2g_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CELLULAR_2G))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowCellular2g.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void setUserRestriction_disallowCellular2g_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CELLULAR_2G))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CELLULAR_2G)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void disallowCellular2gIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CELLULAR_2G)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void disallowCellular2gIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowUltraWidebandRadio.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void setUserRestriction_disallowUltraWidebandRadio_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO));
    }

    @PolicyAppliesTest(policy = DisallowUltraWidebandRadio.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void setUserRestriction_disallowUltraWidebandRadio_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ULTRA_WIDEBAND_RADIO))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowUltraWidebandRadio.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void setUserRestriction_disallowUltraWidebandRadio_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ULTRA_WIDEBAND_RADIO))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_ULTRA_WIDEBAND_RADIO)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void disallowUltraWidebandRadioIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_ULTRA_WIDEBAND_RADIO)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void disallowUltraWidebandRadioIsSet_todo() throws Exception {
        // TODO: Test
    }

}
