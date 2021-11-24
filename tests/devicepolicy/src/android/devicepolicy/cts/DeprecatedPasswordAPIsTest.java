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
package android.devicepolicy.cts;

import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.PositivePolicyTest;
import com.android.bedstead.harrier.policies.DeprecatedPasswordAPIs;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.remotedpc.RemotePolicyManager;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test cases for password management APIs that are deprecated and not supported in some platforms.
 */
@RunWith(BedsteadJUnit4.class)
public final class DeprecatedPasswordAPIsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private ComponentName mAdmin;
    private RemoteDevicePolicyManager mDpm;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDpm = dpc.devicePolicyManager();
    }

    @Test
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_AUTOMOTIVE)
    public void testPasswordQuality_featureUnsupported_returnsUnspecified() {
        mDpm.setPasswordQuality(mAdmin, DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);

        assertWithMessage("getPasswordQuality()").that(mDpm.getPasswordQuality(mAdmin))
                .isEqualTo(DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordQuality_featureSupported_returnsValueSet() {
        mDpm.setPasswordQuality(mAdmin, DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);

        assertWithMessage("getPasswordQuality()").that(mDpm.getPasswordQuality(mAdmin))
                .isEqualTo(DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);
    }

    @Test
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumLength_featureUnsupported_ignored() {
        int valueBefore = mDpm.getPasswordMinimumLength(mAdmin);

        mDpm.setPasswordMinimumLength(mAdmin, 42);

        assertWithMessage("getPasswordMinimumLength()")
                .that(mDpm.getPasswordMinimumLength(mAdmin))
                .isEqualTo(valueBefore);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumLength_featureSupported_returnsValueSet() {
        mDpm.setPasswordMinimumLength(mAdmin, 42);

        assertWithMessage("getPasswordMinimumLength()")
                .that(mDpm.getPasswordMinimumLength(mAdmin))
                .isEqualTo(42);
    }

    @Test
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumNumeric_ignored() {
        int valueBefore = mDpm.getPasswordMinimumNumeric(mAdmin);

        mDpm.setPasswordMinimumNumeric(mAdmin, 42);

        assertWithMessage("getPasswordMinimumNumeric()")
                .that(mDpm.getPasswordMinimumNumeric(mAdmin))
                .isEqualTo(valueBefore);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumNumeric_returnsValueSet() {
        mDpm.setPasswordMinimumNumeric(mAdmin, 42);

        assertWithMessage("getPasswordMinimumNumeric()")
                .that(mDpm.getPasswordMinimumNumeric(mAdmin))
                .isEqualTo(42);
    }

    @Test
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumLowerCase_ignored() {
        int valueBefore = mDpm.getPasswordMinimumLowerCase(mAdmin);

        mDpm.setPasswordMinimumLowerCase(mAdmin, 42);

        assertWithMessage("getPasswordMinimumLowerCase()")
                .that(mDpm.getPasswordMinimumLowerCase(mAdmin))
                .isEqualTo(valueBefore);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumLowerCase_returnsValueSet() {
        mDpm.setPasswordMinimumLowerCase(mAdmin, 42);

        assertWithMessage("getPasswordMinimumLowerCase()")
                .that(mDpm.getPasswordMinimumLowerCase(mAdmin))
                .isEqualTo(42);
    }

    @Test
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumUpperCase_ignored() {
        int valueBefore = mDpm.getPasswordMinimumUpperCase(mAdmin);

        mDpm.setPasswordMinimumUpperCase(mAdmin, 42);

        assertWithMessage("getPasswordMinimumUpperCase()")
                .that(mDpm.getPasswordMinimumUpperCase(mAdmin))
                .isEqualTo(valueBefore);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumUpperCase_returnsValueSet() {
        mDpm.setPasswordMinimumUpperCase(mAdmin, 42);

        assertWithMessage("getPasswordMinimumUpperCase()")
                .that(mDpm.getPasswordMinimumUpperCase(mAdmin))
                .isEqualTo(42);
    }

    @Test
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumLetters_ignored() {
        int valueBefore = mDpm.getPasswordMinimumLetters(mAdmin);

        mDpm.setPasswordMinimumLetters(mAdmin, 42);

        assertWithMessage("getPasswordMinimumLetters()")
                .that(mDpm.getPasswordMinimumLetters(mAdmin))
                .isEqualTo(valueBefore);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumLetters_returnsValueSet() {
        mDpm.setPasswordMinimumLetters(mAdmin, 42);

        assertWithMessage("getPasswordMinimumLetters()")
                .that(mDpm.getPasswordMinimumLetters(mAdmin))
                .isEqualTo(42);
    }

    @Test
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumSymbols_ignored() {
        int valueBefore = mDpm.getPasswordMinimumSymbols(mAdmin);

        mDpm.setPasswordMinimumSymbols(mAdmin, 42);

        assertWithMessage("getPasswordMinimumSymbols()")
                .that(mDpm.getPasswordMinimumSymbols(mAdmin))
                .isEqualTo(valueBefore);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumSymbols_returnsValueSet() {
        mDpm.setPasswordMinimumSymbols(mAdmin, 42);

        assertWithMessage("getPasswordMinimumSymbols()")
                .that(mDpm.getPasswordMinimumSymbols(mAdmin))
                .isEqualTo(42);
    }

    @Test
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumNonLetter_ignored() {
        int valueBefore = mDpm.getPasswordMinimumNonLetter(mAdmin);

        mDpm.setPasswordMinimumNonLetter(mAdmin, 42);

        assertWithMessage("getPasswordMinimumNonLetter()")
                .that(mDpm.getPasswordMinimumNonLetter(mAdmin))
                .isEqualTo(valueBefore);
    }

    @Test
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @PositivePolicyTest(policy = DeprecatedPasswordAPIs.class)
    @Postsubmit(reason = "new test")
    public void testPasswordMinimumNonLetter_returnsValueSet() {
        mDpm.setPasswordMinimumNonLetter(mAdmin, 42);

        assertWithMessage("getPasswordMinimumNonLetter()")
                .that(mDpm.getPasswordMinimumNonLetter(mAdmin))
                .isEqualTo(42);
    }
}
