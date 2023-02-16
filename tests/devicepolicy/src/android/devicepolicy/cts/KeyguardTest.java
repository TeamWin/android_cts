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

import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FACE;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_IRIS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL;

import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.os.PersistableBundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.KeyguardDisableFace;
import com.android.bedstead.harrier.policies.KeyguardDisableFingerprint;
import com.android.bedstead.harrier.policies.KeyguardDisableIris;
import com.android.bedstead.harrier.policies.KeyguardDisableSecureCamera;
import com.android.bedstead.harrier.policies.KeyguardDisableSecureNotifications;
import com.android.bedstead.harrier.policies.KeyguardDisableTrustAgents;
import com.android.bedstead.harrier.policies.KeyguardDisableUnredactedNotifications;
import com.android.bedstead.harrier.policies.KeyguardDisableWidgetsAll;
import com.android.bedstead.harrier.policies.TrustAgentConfiguration;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import com.google.common.truth.Truth;

import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
@Ignore // TODO: Figure out expectations about applying to parent
public final class KeyguardTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final ComponentName TRUST_AGENT = new ComponentName("test.trust.agent", "t");

    private static final PersistableBundle CONFIGURATION =
            PersistableBundle.forPair("key", "test.trust.agent");

    @CanSetPolicyTest(policy = KeyguardDisableWidgetsAll.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_WIDGETS_ALL_doesNotThrowException() {
        sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_WIDGETS_ALL);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableWidgetsAll.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_WIDGETS_ALL"
    })
    public void setKeyguardDisabledFeatures_disableWidgetsAll_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_WIDGETS_ALL));
    }

    @PolicyAppliesTest(policy = KeyguardDisableWidgetsAll.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_WIDGETS_ALL"
    })
    public void setKeyguardDisabledFeatures_disableWidgetsAll_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_WIDGETS_ALL);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_WIDGETS_ALL);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableWidgetsAll.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_WIDGETS_ALL"
    })
    public void setKeyguardDisabledFeatures_disableWidgetsAll_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_WIDGETS_ALL);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_WIDGETS_ALL);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = KeyguardDisableSecureCamera.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_SECURE_CAMERA_doesNotThrowException() {
        sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_CAMERA);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableSecureCamera.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_CAMERA"
    })
    public void setKeyguardDisabledFeatures_disableSecureCamera_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_CAMERA));
    }

    @PolicyAppliesTest(policy = KeyguardDisableSecureCamera.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_CAMERA"
    })
    public void setKeyguardDisabledFeatures_disableSecureCamera_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_CAMERA);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_SECURE_CAMERA);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableSecureCamera.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_CAMERA"
    })
    public void setKeyguardDisabledFeatures_disableSecureCamera_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_CAMERA);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_SECURE_CAMERA);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = KeyguardDisableSecureNotifications.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_SECURE_NOTIFICATIONS_doesNotThrowException() {
        sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableSecureNotifications.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableSecureNotifications_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_NOTIFICATIONS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableSecureNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableSecureNotifications_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableSecureNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_SECURE_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableSecureNotifications_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = KeyguardDisableTrustAgents.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_TRUST_AGENTS_doesNotThrowException() {
        sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableTrustAgents.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_TRUST_AGENTS"
    })
    public void setKeyguardDisabledFeatures_disableTrustAgents_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableTrustAgents.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_TRUST_AGENTS"
    })
    public void setKeyguardDisabledFeatures_disableTrustAgents_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_TRUST_AGENTS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableTrustAgents.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_TRUST_AGENTS"
    })
    public void setKeyguardDisabledFeatures_disableTrustAgents_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_TRUST_AGENTS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_TRUST_AGENTS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = KeyguardDisableUnredactedNotifications.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_UNREDACTED_NOTIFICATIONS_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableUnredactedNotifications.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableUnredactedNotifications_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableUnredactedNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableUnredactedNotifications_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableUnredactedNotifications.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS"
    })
    public void setKeyguardDisabledFeatures_disableUnredactedNotifications_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = KeyguardDisableFingerprint.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_FINGERPRINT_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FINGERPRINT);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableFingerprint.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FINGERPRINT"
    })
    public void setKeyguardDisabledFeatures_disableFingerprint_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FINGERPRINT));
    }

    @PolicyAppliesTest(policy = KeyguardDisableFingerprint.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FINGERPRINT"
    })
    public void setKeyguardDisabledFeatures_disableFingerprint_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FINGERPRINT);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_FINGERPRINT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableFingerprint.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FINGERPRINT"
    })
    public void setKeyguardDisabledFeatures_disableFingerprint_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FINGERPRINT);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_FINGERPRINT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = KeyguardDisableFace.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_FACE_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FACE);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableFace.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FACE"
    })
    public void setKeyguardDisabledFeatures_disableFace_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FACE));
    }

    @PolicyAppliesTest(policy = KeyguardDisableFace.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FACE"
    })
    public void setKeyguardDisabledFeatures_disableFace_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FACE);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_FACE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableFace.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_FACE"
    })
    public void setKeyguardDisabledFeatures_disableFace_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_FACE);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_FACE);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = KeyguardDisableIris.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setKeyguardDisabledFeatures_DISABLE_IRIS_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_IRIS);
    }

    @CannotSetPolicyTest(policy = KeyguardDisableIris.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_IRIS"
    })
    public void setKeyguardDisabledFeatures_disableIris_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setKeyguardDisabledFeatures(
                        sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_IRIS));
    }

    @PolicyAppliesTest(policy = KeyguardDisableIris.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_IRIS"
    })
    public void setKeyguardDisabledFeatures_disableIris_featureIsSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_IRIS);

            assertThat(sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName())).isEqualTo(KEYGUARD_DISABLE_IRIS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isEqualTo(
                    KEYGUARD_DISABLE_IRIS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @PolicyDoesNotApplyTest(policy = KeyguardDisableIris.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#setKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#getKeyguardDisabledFeatures",
            "android.app.admin.DevicePolicyManager#KEYGUARD_DISABLE_IRIS"
    })
    public void setKeyguardDisabledFeatures_disableIris_doesNotApply_featureIsNotSet() {
        if (TestApis.users().instrumented().isProfile()) {
            throw new AssumptionViolatedException("Not relevant for non-work profiles");
        }
        int originalFeatures = sDeviceState.dpc().devicePolicyManager().getKeyguardDisabledFeatures(
                sDeviceState.dpc().componentName());

        try {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), KEYGUARD_DISABLE_IRIS);

            assertThat(TestApis.devicePolicy().getKeyguardDisabledFeatures()).isNotEqualTo(
                    KEYGUARD_DISABLE_IRIS);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setKeyguardDisabledFeatures(
                    sDeviceState.dpc().componentName(), originalFeatures);
        }
    }

    @CanSetPolicyTest(policy = TrustAgentConfiguration.class) // TODO: Remove
    @Postsubmit(reason = "New test")
    public void setTrustAgentConfiguration_doesNotThrowException() {
        sDeviceState.dpc().devicePolicyManager()
                .setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT, CONFIGURATION);
    }

    @CannotSetPolicyTest(policy = TrustAgentConfiguration.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setTrustAgentConfiguration")
    public void setTrustAgentConfiguration_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setTrustAgentConfiguration(
                        sDeviceState.dpc().componentName(), TRUST_AGENT, CONFIGURATION));
    }

    @PolicyAppliesTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @Ignore // Needs trust agent testapp
    public void setTrustAgentConfiguration_trustAgentConfigurationIsSet() {
        List<PersistableBundle> originalConfigurations = sDeviceState.dpc().devicePolicyManager()
                .getTrustAgentConfiguration(sDeviceState.dpc().componentName(), TRUST_AGENT);
        PersistableBundle originalConfiguration = originalConfigurations == null
                || originalConfigurations.isEmpty() ? null
                : originalConfigurations.stream().findAny().get();

        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, CONFIGURATION);

            assertContainsTestConfiguration(TestApis.devicePolicy().getTrustAgentConfiguration(
                    TRUST_AGENT));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, originalConfiguration);
        }
    }

    @PolicyDoesNotApplyTest(policy = TrustAgentConfiguration.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setTrustAgentConfiguration",
            "android.app.admin.DevicePolicyManager#getTrustAgentConfiguration"})
    @Ignore // Needs trust agent testapp
    public void setTrustAgentConfiguration_doesNotApply_trustAgentConfigurationIsNotSet() {
        List<PersistableBundle> originalConfigurations = sDeviceState.dpc().devicePolicyManager()
                .getTrustAgentConfiguration(sDeviceState.dpc().componentName(), TRUST_AGENT);
        PersistableBundle originalConfiguration = originalConfigurations == null
                || originalConfigurations.isEmpty() ? null
                : originalConfigurations.stream().findAny().get();

        try {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, CONFIGURATION);

            assertThat(TestApis.devicePolicy().getTrustAgentConfiguration(
                    TRUST_AGENT)).isEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setTrustAgentConfiguration(
                    sDeviceState.dpc().componentName(), TRUST_AGENT, originalConfiguration);
        }
    }

    private void assertContainsTestConfiguration(Set<PersistableBundle> bundle) {
        assertThat(bundle).hasSize(1);
        assertThat(bundle.iterator().next().getString("key"))
                .isEqualTo(CONFIGURATION.getString("key"));
    }

}
