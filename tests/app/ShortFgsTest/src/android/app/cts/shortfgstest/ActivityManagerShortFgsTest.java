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
package android.app.cts.shortfgstest;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.FGS0;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;

import static com.google.common.truth.Truth.assertThat;

import android.app.cts.shortfgstesthelper.ShortFgsHelper;
import android.app.cts.shortfgstesthelper.ShortFgsMessage;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.SystemClock;
import android.provider.DeviceConfig;

import com.android.compatibility.common.util.BroadcastMessenger.Receiver;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for "SHORT SERVICE" foreground services.
 *
 * TODO(short-service): Add more test cases, see b/260748204
 */
public class ActivityManagerShortFgsTest {
    protected static final Context sContext = ShortFgsHelper.sContext;

    public static DeviceConfigStateHelper sDeviceConfig;

    /**
     * Timeout for "short" FGS used throughout this test. It's shorter than the default value
     * to speed up the test.
     */
    public static final long SHORTENED_TIMEOUT = 10_000;

    /**
     * After {@link #SHORTENED_TIMEOUT} + {@link #SHORTENED_PROCSTATE_GRACE_PERIOD} ms, the
     * procstate and oom-adjustment of the short-fgs will drop.
     */
    public static final long SHORTENED_PROCSTATE_GRACE_PERIOD = 5_000;

    /**
     * After {@link #SHORTENED_TIMEOUT} + {@link #SHORTENED_PROCSTATE_GRACE_PERIOD}
     * + {@link #SHORTENED_ANR_GRACE_PERIOD} ms, the app will be declared an ANR.
     */
    public static final long SHORTENED_ANR_GRACE_PERIOD = 5_000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sDeviceConfig = new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);

        // TODO It's not working
        sDeviceConfig.set("short_fgs_timeout_duration", "" + SHORTENED_TIMEOUT);
        sDeviceConfig.set("short_fgs_proc_state_extra_wait_duration",
                "" + SHORTENED_PROCSTATE_GRACE_PERIOD);
        sDeviceConfig.set("short_fgs_anr_extra_wait_duration",
                "" + (SHORTENED_PROCSTATE_GRACE_PERIOD + SHORTENED_ANR_GRACE_PERIOD));

        // TODO Wait until the values are propagated.
        TestUtils.waitUntil("Waiting for `dumpsys activity settings` to update", () -> {
            String dumpsys = ShellUtils.runShellCommand("dumpsys activity settings");
            return dumpsys.contains("short_fgs_timeout_duration=" + SHORTENED_TIMEOUT);
        });
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        sDeviceConfig.close();
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Basic test: Start SHORT_SERVICE FGS and stop it, and make sure the nothing throws, and
     * the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the SHORT_TYPE FGS
     * - Stop it.
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     *
     * @throws Exception
     */
    @Test
    public void testStartStop() throws Exception {
        try (Receiver<ShortFgsMessage> receiver = new Receiver<>(sContext, TAG)) {

            // Start FGS0 and stop it right away.
            ShortFgsMessage startMessage = new ShortFgsMessage();
            startMessage.setComponentName(FGS0);
            startMessage.setSetForeground(true);
            startMessage.setFgsType(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Actual intent to start the FGS.
            Intent i = new Intent().setComponent(FGS0);
            ShortFgsHelper.setMessage(i, startMessage);

            sContext.startForegroundService(i);

            // Wait for the method name message.
            {
                ShortFgsMessage methodName = receiver.waitForNextMessage();
                assertThat(methodName.getComponentName()).isEqualTo(FGS0);
                assertThat(methodName.getMethodName()).isEqualTo("onStartCommand");
            }

            // Stop the service.
            sContext.stopService(i);
            {
                ShortFgsMessage methodName = receiver.waitForNextMessage();
                assertThat(methodName.getComponentName()).isEqualTo(FGS0);
                assertThat(methodName.getMethodName()).isEqualTo("onDestroy");
            }

            // Wait for the timeout + extra duration
            Thread.sleep(SHORTENED_TIMEOUT + 5000);

            // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
            // which would break the below ensureNoMoreMessages().)

            receiver.ensureNoMoreMessages();
        }
    }

    /**
     * Start SHORT_SERVICE FGS, and make sure the timeout callback is called.
     */
    @Test
    public void testTimeout() throws Exception {
        try (Receiver<ShortFgsMessage> receiver = new Receiver<>(sContext, TAG)) {

            // Start FGS0 and stop it right away.
            ShortFgsMessage startMessage = new ShortFgsMessage();
            startMessage.setComponentName(FGS0);
            startMessage.setSetForeground(true);
            startMessage.setFgsType(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Actual intent to start the FGS.
            Intent i = new Intent().setComponent(FGS0);
            ShortFgsHelper.setMessage(i, startMessage);

            long serviceStartTime = SystemClock.uptimeMillis();

            sContext.startForegroundService(i);

            int serviceStartId = -1;

            // Wait for the method name message.
            {
                ShortFgsMessage methodName = receiver.waitForNextMessage();
                assertThat(methodName.getComponentName()).isEqualTo(FGS0);
                assertThat(methodName.getMethodName()).isEqualTo("onStartCommand");
                serviceStartId = methodName.getServiceStartId();
            }

            // Wait for  onTimeout()
            Thread.sleep(SHORTENED_TIMEOUT);

            {
                ShortFgsMessage methodName = receiver.waitForNextMessage();
                assertThat(methodName.getComponentName()).isEqualTo(FGS0);
                assertThat(methodName.getMethodName()).isEqualTo("onTimeout");

                assertThat(methodName.getServiceStartId()).isEqualTo(serviceStartId);

                // Timeout should happen after SHORTENED_TIMEOUT.
                assertThat(methodName.getTimestamp())
                        .isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
            }

            // Stop the service.
            sContext.stopService(i);
            {
                ShortFgsMessage methodName = receiver.waitForNextMessage();
                assertThat(methodName.getComponentName()).isEqualTo(FGS0);
                assertThat(methodName.getMethodName()).isEqualTo("onDestroy");
            }

            receiver.ensureNoMoreMessages();
        }
    }
}
