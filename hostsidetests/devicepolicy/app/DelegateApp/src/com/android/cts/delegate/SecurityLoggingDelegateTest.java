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
package com.android.cts.delegate;

import static com.android.cts.delegate.DelegateTestUtils.assertExpectException;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.app.admin.SecurityLog.SecurityEvent;
import android.content.Context;
import android.support.test.uiautomator.UiDevice;
import android.test.InstrumentationTestCase;

import androidx.test.InstrumentationRegistry;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Tests that a delegate app with DELEGATION_SECURITY_LOGGING is able to control and access
 * security logging.
 */
public class SecurityLoggingDelegateTest extends InstrumentationTestCase {

    private static final String TAG = "SecurityLoggingDelegateTest";

    private Context mContext;
    private DevicePolicyManager mDpm;
    private UiDevice mDevice;

    private static final String GENERATED_KEY_ALIAS = "generated_key_alias";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = getInstrumentation().getContext();
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
        DelegateTestUtils.DelegatedLogsReceiver.sBatchCountDown = new CountDownLatch(1);
    }

    public void testCannotAccessApis()throws Exception {
        assertExpectException(SecurityException.class, null,
                () -> mDpm.isSecurityLoggingEnabled(null));

        assertExpectException(SecurityException.class, null,
                () -> mDpm.setSecurityLoggingEnabled(null, true));

        assertExpectException(SecurityException.class, null,
                () -> mDpm.retrieveSecurityLogs(null));
    }

    /**
     * Test: Test enabling security logging.
     * This test has a side effect: security logging is enabled after its execution.
     */
    public void testEnablingSecurityLogging() {
        mDpm.setSecurityLoggingEnabled(null, true);

        assertThat(mDpm.isSecurityLoggingEnabled(null)).isTrue();
    }

    /**
     * Generates security events related to Keystore
     */
    public void testGenerateLogs() throws Exception {
        try {
            DelegateTestUtils.testGenerateKey(GENERATED_KEY_ALIAS);
        } finally {
            DelegateTestUtils.deleteKey(GENERATED_KEY_ALIAS);
        }
    }

    /**
     * Test: retrieves security logs and verifies that all events generated as a result of host
     * side actions and by {@link #testGenerateLogs()} are there.
     */
    public void testVerifyGeneratedLogs() throws Exception {
        mDevice.executeShellCommand("dpm force-security-logs");
        DelegateTestUtils.DelegatedLogsReceiver.waitForBroadcast();

        final List<SecurityEvent> events =
                DelegateTestUtils.DelegatedLogsReceiver.getSecurityEvents();
        DelegateTestUtils.verifyKeystoreEventsPresent(GENERATED_KEY_ALIAS, events);
    }

    /**
     * Test: retrieving security logs should be rate limited - subsequent attempts should return
     * null.
     */
    public void testSecurityLoggingRetrievalRateLimited() {
        final List<SecurityEvent> logs = mDpm.retrieveSecurityLogs(null);
        // if logs is null it means that that attempt was rate limited => test PASS
        if (logs != null) {
            assertThat(mDpm.retrieveSecurityLogs(null)).isNull();
            assertThat(mDpm.retrieveSecurityLogs(null)).isNull();
        }
    }

    /**
     * Test: Test disaling security logging.
     * This test has a side effect: security logging is disabled after its execution.
     */
    public void testDisablingSecurityLogging() {
        mDpm.setSecurityLoggingEnabled(null, false);

        assertThat(mDpm.isSecurityLoggingEnabled(null)).isFalse();
    }
}
