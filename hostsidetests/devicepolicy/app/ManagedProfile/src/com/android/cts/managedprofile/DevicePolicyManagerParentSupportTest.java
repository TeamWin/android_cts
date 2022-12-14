package com.android.cts.managedprofile;

import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.os.PersistableBundle;

/**
 * Tests that the {@link DevicePolicyManager} APIs that should work for {@link
 * DevicePolicyManager#getParentProfileInstance(ComponentName)} are supported.
 *
 * <p>Minimum restriction APIs are already tested by {@link PasswordMinimumRestrictionsTest}.
 */
public class DevicePolicyManagerParentSupportTest extends BaseManagedProfileTest {
    private static final ComponentName FAKE_COMPONENT = new ComponentName(
            FakeComponent.class.getPackage().getName(), FakeComponent.class.getName());

    public void testSetAndGetRequiredPasswordComplexity_onParent() {
       if (!mHasSecureLockScreen) {
            return;
        }

        mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_HIGH);
        try {
            final int actualPasswordComplexity =
                    mParentDevicePolicyManager.getRequiredPasswordComplexity();

            assertThat(actualPasswordComplexity).isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            mParentDevicePolicyManager.setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
        }
    }

    public void testSetAndGetPasswordHistoryLength_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }
        final int passwordHistoryLength = 5;

        mParentDevicePolicyManager.setPasswordHistoryLength(
                ADMIN_RECEIVER_COMPONENT, passwordHistoryLength);
        final int actualPasswordHistoryLength =
                mParentDevicePolicyManager.getPasswordHistoryLength(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualPasswordHistoryLength).isEqualTo(passwordHistoryLength);
    }

    public void testGetPasswordComplexity_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }

        final int actualPasswordComplexity =
                mParentDevicePolicyManager.getPasswordComplexity();
        assertThat(actualPasswordComplexity).isEqualTo(
                PASSWORD_COMPLEXITY_NONE);
    }

    public void testSetAndGetPasswordExpirationTimeout_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }
        final int passwordExpirationTimeout = 432000000;

        mParentDevicePolicyManager.setPasswordExpirationTimeout(
                ADMIN_RECEIVER_COMPONENT, passwordExpirationTimeout);
        final long actualPasswordExpirationTimeout =
                mParentDevicePolicyManager.getPasswordExpirationTimeout(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualPasswordExpirationTimeout).isEqualTo(passwordExpirationTimeout);
    }

    public void testGetPasswordExpiration_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }
        final long passwordExpirationTimeout = 432000000;
        final long currentTime = System.currentTimeMillis();

        mParentDevicePolicyManager.setPasswordExpirationTimeout(
                ADMIN_RECEIVER_COMPONENT, passwordExpirationTimeout);
        final long actualPasswordExpiration =
                mParentDevicePolicyManager.getPasswordExpiration(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualPasswordExpiration).isAtLeast(passwordExpirationTimeout + currentTime);
    }

    public void testGetMaximumPasswordLength_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }
        final int actualMaximumPasswordLength =
                mParentDevicePolicyManager.getPasswordMaximumLength(
                        PASSWORD_QUALITY_NUMERIC_COMPLEX);
        assertThat(actualMaximumPasswordLength).isGreaterThan(0);
    }

    public void testIsActivePasswordSufficient_onParent_setOnParent_isSupported() {
        try {
            mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_HIGH);
            assertThat(mParentDevicePolicyManager.isActivePasswordSufficient()).isFalse();
        } finally {
            mParentDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
        }
    }

    public void testIsActivePasswordSufficient_onParent_setOnProfile_isSupported() {
        try {
            mDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_HIGH);
            assertThat(mParentDevicePolicyManager.isActivePasswordSufficient()).isFalse();
        } finally {
            mDevicePolicyManager.setRequiredPasswordComplexity(PASSWORD_COMPLEXITY_NONE);
        }
    }

    public void testSetPasswordQuality_onParent_isNotSupported() {
        assertThrows(SecurityException.class,
                () -> setPasswordQuality(PASSWORD_QUALITY_NUMERIC_COMPLEX));
    }

    private void setPasswordQuality(int quality) {
        mParentDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT, quality);
    }

    public void testIsActivePasswordSufficient_onParent_respectsProfileQualityWhenUnified() {
        mDevicePolicyManager.setPasswordQuality(
                ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_UNSPECIFIED);
        assertThat(mParentDevicePolicyManager.isActivePasswordSufficient()).isTrue();
        mDevicePolicyManager.setPasswordQuality(
                ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_NUMERIC_COMPLEX);
        try {
            assertThat(mParentDevicePolicyManager.isActivePasswordSufficient()).isFalse();
        } finally {
            mDevicePolicyManager.setPasswordQuality(
                    ADMIN_RECEIVER_COMPONENT, PASSWORD_QUALITY_UNSPECIFIED);
        }
    }

    public void testGetCurrentFailedPasswordAttempts_onParent_isSupported() {
        assertThat(mParentDevicePolicyManager.getCurrentFailedPasswordAttempts()).isEqualTo(0);
    }

    public void testSetAndGetMaximumFailedPasswordsForWipe_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }
        final int maximumFailedPasswordsForWipe = 15;

        mParentDevicePolicyManager.setMaximumFailedPasswordsForWipe(
                ADMIN_RECEIVER_COMPONENT, maximumFailedPasswordsForWipe);
        final int actualMaximumFailedPasswordsForWipe =
                mParentDevicePolicyManager.getMaximumFailedPasswordsForWipe(
                        ADMIN_RECEIVER_COMPONENT);

        assertThat(actualMaximumFailedPasswordsForWipe).isEqualTo(maximumFailedPasswordsForWipe);
    }

    public void testSetAndGetMaximumTimeToLock_onParent() {
        final int maximumTimeToLock = 6000;

        mParentDevicePolicyManager.setMaximumTimeToLock(
                ADMIN_RECEIVER_COMPONENT, maximumTimeToLock);
        final long actualMaximumTimeToLock =
                mParentDevicePolicyManager.getMaximumTimeToLock(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualMaximumTimeToLock).isEqualTo(maximumTimeToLock);
    }

    public void testSetAndGetKeyguardDisabledFeatures_onParent() {
        mParentDevicePolicyManager.setKeyguardDisabledFeatures(
                ADMIN_RECEIVER_COMPONENT, KEYGUARD_DISABLE_TRUST_AGENTS);
        long actualKeyguardDisabledFeatures =
                mParentDevicePolicyManager.getKeyguardDisabledFeatures(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualKeyguardDisabledFeatures).isEqualTo(KEYGUARD_DISABLE_TRUST_AGENTS);
    }

    public void testSetAndGetTrustAgentConfiguration_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }
        final PersistableBundle configuration = new PersistableBundle();
        final String key = "key";
        final String value = "value";
        configuration.putString(key, value);

        mParentDevicePolicyManager.setTrustAgentConfiguration(
                ADMIN_RECEIVER_COMPONENT, FAKE_COMPONENT, configuration);
        final PersistableBundle actualConfiguration =
                mParentDevicePolicyManager.getTrustAgentConfiguration(
                        ADMIN_RECEIVER_COMPONENT, FAKE_COMPONENT).get(0);

        assertThat(actualConfiguration.get(key)).isEqualTo(value);
    }

    public void testSetAndGetRequiredStrongAuthTimeout_onParent() {
        if (!mHasSecureLockScreen) {
            return;
        }
        final int requiredStrongAuthTimeout = 4600000;

        mParentDevicePolicyManager.setRequiredStrongAuthTimeout(
                ADMIN_RECEIVER_COMPONENT, requiredStrongAuthTimeout);
        final long actualRequiredStrongAuthTimeout =
                mParentDevicePolicyManager.getRequiredStrongAuthTimeout(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualRequiredStrongAuthTimeout).isEqualTo(requiredStrongAuthTimeout);
    }

    public abstract class FakeComponent extends BroadcastReceiver {}
}
