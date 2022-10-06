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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.PasswordComplexity;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PasswordComplexityTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // TODO(b/251079550): remove @ignore when bug is fixed.
    @Ignore
    @CannotSetPolicyTest(policy = PasswordComplexity.class)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_noPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().getPasswordComplexity());
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_noPassword_returnsPasswordComplexityNone() {
        assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                .isEqualTo(PASSWORD_COMPLEXITY_NONE);
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_patternLength4_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPattern("1234");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPattern();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_patternLength8_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPattern("13246587");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPattern();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_pinOrderedLength4_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPin("1234");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_pinOrderedLength8_returnsPasswordComplexityLow() {
        try {
            sDeviceState.dpc().user().setPin("12345678");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_pinNotOrderedLength4_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPin("1238");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_pinNotOrderedLength7_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPin("1238964");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_alphabeticLength4_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("c!qw");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_alphabeticLength5_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("bc!qw");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_alphanumericLength4_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("c!23");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_alphanumericLength5_returnsPasswordComplexityMedium() {
        try {
            sDeviceState.dpc().user().setPassword("bc!23");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_pinNotOrderedLength8_returnsPasswordComplexityHigh() {
        try {
            sDeviceState.dpc().user().setPin("12389647");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            sDeviceState.dpc().user().clearPin();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_alphabeticLength6_returnsPasswordComplexityHigh() {
        try {
            sDeviceState.dpc().user().setPassword("abc!qw");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }

    @CanSetPolicyTest(policy = PasswordComplexity.class, singleTestOnly = true)
    @Postsubmit(reason = "new test")
    public void getPasswordComplexity_alphanumericLength6_returnsPasswordComplexityHigh() {
        try {
            sDeviceState.dpc().user().setPassword("abc!23");
            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            sDeviceState.dpc().user().clearPassword();
        }
    }
}
