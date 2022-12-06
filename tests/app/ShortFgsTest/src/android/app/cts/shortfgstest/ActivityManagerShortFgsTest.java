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
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.HELPER_PACKAGE;
import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;
import static android.app.nano.AppProtoEnums.PROCESS_STATE_IMPORTANT_FOREGROUND;
import static android.app.nano.AppProtoEnums.PROCESS_STATE_LAST_ACTIVITY;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static com.google.common.truth.Truth.assertThat;

import android.app.Service;
import android.app.cts.shortfgstest.DumpProtoUtils.ProcStateInfo;
import android.app.cts.shortfgstesthelper.ShortFgsHelper;
import android.app.cts.shortfgstesthelper.ShortFgsMessage;
import android.app.cts.shortfgstesthelper.ShortFgsMessageReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.DeviceConfig;

import com.android.compatibility.common.util.BroadcastMessenger.Receiver;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ShellUtils;
import com.android.server.am.nano.ServiceRecordProto;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
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
     * Timeout for "short" FGS used throughout this test. It's shorter than the default value to
     * speed up the test.
     */
    public static final long SHORTENED_TIMEOUT = 5_000;

    /**
     * After {@link #SHORTENED_TIMEOUT} + {@link #SHORTENED_PROCSTATE_GRACE_PERIOD} ms, the
     * procstate and oom-adjustment of the short-fgs will drop.
     */
    public static final long SHORTENED_PROCSTATE_GRACE_PERIOD = 5_000;

    /**
     * After {@link #SHORTENED_TIMEOUT} + {@link #SHORTENED_PROCSTATE_GRACE_PERIOD} + {@link
     * #EXTENDED_ANR_GRACE_PERIOD} ms, the app will be declared an ANR.
     * <p>
     * We don't want to trigger an ANR accidentally on a slow device, so we use a large value here.
     * <p>
     * (We set a smaller value when we test the ANR behavior)
     */
    public static final long EXTENDED_ANR_GRACE_PERIOD = 24 * 60 * 60 * 1000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sDeviceConfig = new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);

        // TODO It's not working
        sDeviceConfig.set("short_fgs_timeout_duration", "" + SHORTENED_TIMEOUT);
        sDeviceConfig.set("short_fgs_proc_state_extra_wait_duration",
                "" + SHORTENED_PROCSTATE_GRACE_PERIOD);
        sDeviceConfig.set("short_fgs_anr_extra_wait_duration",
                "" + (SHORTENED_PROCSTATE_GRACE_PERIOD + EXTENDED_ANR_GRACE_PERIOD));

        // TODO Wait until the values are propagated.
        waitUntil("Waiting for `dumpsys activity settings` to update", () -> {
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

    private static ShortFgsMessage newMessage() {
        return new ShortFgsMessage();
    }

    public static void waitForAckMessage(Receiver<ShortFgsMessage> receiver) {
        ShortFgsMessage ack = receiver.waitForNextMessage();
        if (ack.isAck()) {
            return;
        }
        Assert.fail("Expected an ACK message, but received: " + ack);
    }

    public static void startForegroundService(ComponentName cn, int fgsTypes) {
        startForegroundService(cn, fgsTypes, Service.START_NOT_STICKY);
    }

    public static void startForegroundService(ComponentName cn, int fgsTypes,
            int startCommandResult) {
        ShortFgsMessage startMessage = newMessage()
                .setComponentName(cn)
                .setSetForeground(true)
                .setFgsType(fgsTypes)
                .setStartCommandResult(startCommandResult);

        // Actual intent to start the FGS.
        Intent i = new Intent().setComponent(cn);
        ShortFgsHelper.setMessage(i, startMessage);

        sContext.startForegroundService(i);
    }

    public static ShortFgsMessage waitForMethodCall(Receiver<ShortFgsMessage> receiver,
            ComponentName cn, String methodName) {
        ShortFgsMessage m = receiver.waitForNextMessage();
        assertThat(m.getComponentName()).isEqualTo(FGS0);
        assertThat(m.getMethodName()).isEqualTo(methodName);
        return m;
    }

    public static void assertHelperPackageProcState(int procState, int oomAdjustment) {
        final ProcStateInfo expected = new ProcStateInfo();
        expected.mProcState = procState;
        expected.mOomAdjustment = oomAdjustment;

        final ProcStateInfo actual = DumpProtoUtils.getProcessProcState(HELPER_PACKAGE);

        assertThat(actual.toString()).isEqualTo(expected.toString());
    }

    public static void assertHelperPackageIsCached() {
        final ProcStateInfo actual = DumpProtoUtils.getProcessProcState(HELPER_PACKAGE);

        assertThat(actual.mProcState).isAtMost(PROCESS_STATE_LAST_ACTIVITY);
    }

    public static ServiceRecordProto assertServiceRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        assertThat(srp).isNotNull();
        return srp;
    }

    public static void assertServiceNotRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        assertThat(srp).isNull();
    }

    /**
     * Basic test: Start SHORT_SERVICE FGS and stop it, and make sure the nothing throws, and
     * the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the SHORT_TYPE FGS
     * - Stop it with Context.stopService().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    public void testStartStop_stopService() throws Exception {
        try (Receiver<ShortFgsMessage> receiver = new Receiver<>(sContext, TAG)) {

            // Start FGS0 and stop it right away.
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Wait for the method name message.
            waitForMethodCall(receiver, FGS0, "onStartCommand");

            assertServiceRunning(FGS0);

            // Short service's procstat is IMP-FG, and when it starts. It's not started from TOP,
            // so the oom-adj will be 225 + 1.
            // If it was started from TOP, it'd be 50 + 1.
            assertHelperPackageProcState(PROCESS_STATE_IMPORTANT_FOREGROUND, 225 + 1);

            // Stop the service.
            sContext.stopService(new Intent().setComponent(FGS0));

            waitForMethodCall(receiver, FGS0, "onDestroy");

            assertServiceNotRunning(FGS0);

            // Wait for the timeout + extra duration
            Thread.sleep(SHORTENED_TIMEOUT + 5000);

            // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
            // which would break the below ensureNoMoreMessages().)

            receiver.ensureNoMoreMessages();
        }
    }

    /**
     * Start SHORT_SERVICE FGS and stop it, and make sure the nothing throws, and
     * the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the SHORT_TYPE FGS
     * - Stop it with Service.stopSelf().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    public void testStartStop_stopSelf() throws Exception {
        try (Receiver<ShortFgsMessage> receiver = new Receiver<>(sContext, TAG)) {

            // Start FGS0 and stop it right away.
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Wait for the method name message.
            waitForMethodCall(receiver, FGS0, "onStartCommand");

            assertServiceRunning(FGS0);

            // Short service's procstat is IMP-FG, and when it starts. It's not started from TOP,
            // so the oom-adj will be 225 + 1.
            // If it was started from TOP, it'd be 50 + 1.
            assertHelperPackageProcState(PROCESS_STATE_IMPORTANT_FOREGROUND, 225 + 1);

            // Stop the service, using Service.stopSelf().
            ShortFgsMessageReceiver.sendMessage(
                    newMessage().setDoCallStopSelf(true).setComponentName(FGS0));

            // Wait for "ACK".
            waitForAckMessage(receiver);

            // Service should be destroyed.
            waitForMethodCall(receiver, FGS0, "onDestroy");

            assertServiceNotRunning(FGS0);

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

            final long serviceStartTime = SystemClock.uptimeMillis();

            // Start FGS0 and stop it right away.
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Wait for the method name message.
            final int startId =
                    waitForMethodCall(receiver, FGS0, "onStartCommand").getServiceStartId();

            assertServiceRunning(FGS0);

            // Short service's procstat is IMP-FG, and when it starts. It's not started from TOP,
            // so the oom-adj will be 225 + 1.
            // If it was started from TOP, it'd be 50 + 1.
            assertHelperPackageProcState(PROCESS_STATE_IMPORTANT_FOREGROUND, 225 + 1);

            // Wait for onTimeout()
            Thread.sleep(SHORTENED_TIMEOUT);

            {
                ShortFgsMessage m = waitForMethodCall(receiver, FGS0, "onTimeout");

                assertThat(m.getServiceStartId()).isEqualTo(startId);

                // Timeout should happen after SHORTENED_TIMEOUT.
                assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
            }

            // At this point, the procstate should still be the same.
            assertHelperPackageProcState(PROCESS_STATE_IMPORTANT_FOREGROUND, 225 + 1);

            assertServiceRunning(FGS0);

            // Stop the service.
            sContext.stopService(new Intent().setComponent(FGS0));

            waitForMethodCall(receiver, FGS0, "onDestroy");

            assertServiceNotRunning(FGS0);

            // Wait for the timeout + extra duration
            Thread.sleep(SHORTENED_TIMEOUT + 5000);

            // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
            // which would break the below ensureNoMoreMessages().)

            receiver.ensureNoMoreMessages();
        }
    }

    /**
     * Start SHORT_SERVICE FGS:
     * - Make sure the timeout callback is called.
     * - If the service still doesn't stop after the grace period, the process should now be cached.
     */
    @Test
    public void testTimeout_procStateDemotion() throws Exception {
        try (Receiver<ShortFgsMessage> receiver = new Receiver<>(sContext, TAG)) {

            final long serviceStartTime = SystemClock.uptimeMillis();

            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);

            // Wait for the method name message.
            final int startId =
                    waitForMethodCall(receiver, FGS0, "onStartCommand").getServiceStartId();

            assertServiceRunning(FGS0);

            // Wait until the procstate is demoted
            Thread.sleep(SHORTENED_TIMEOUT + SHORTENED_PROCSTATE_GRACE_PERIOD);

            // Timeout callback should still have been made.
            {
                ShortFgsMessage m = waitForMethodCall(receiver, FGS0, "onTimeout");
                assertThat(m.getServiceStartId()).isEqualTo(startId);
                assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
            }

            // The process should now be CACHED
            assertHelperPackageIsCached();

            // Service should still exist.
            assertServiceRunning(FGS0);

            // Stop the service.
            sContext.stopService(new Intent().setComponent(FGS0));

            waitForMethodCall(receiver, FGS0, "onDestroy");

            assertServiceNotRunning(FGS0);

            // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
            // which would break the below ensureNoMoreMessages().)

            receiver.ensureNoMoreMessages();
        }
    }

    @Test
    public void testTimeout_cannotBeSticky_startSticky() throws Exception {
        testServiceIsNotSticky(Service.START_STICKY);
    }

    @Test
    public void testTimeout_cannotBeSticky_startStickyCompatibility() throws Exception {
        testServiceIsNotSticky(Service.START_STICKY_COMPATIBILITY);
    }

    @Test
    public void testTimeout_cannotBeSticky_startNotSticky() throws Exception {
        testServiceIsNotSticky(Service.START_NOT_STICKY);
    }

    @Test
    public void testTimeout_cannotBeSticky_startRedeliveryIntent() throws Exception {
        testServiceIsNotSticky(Service.START_REDELIVER_INTENT);
    }

    /**
     * Make sure a SHORT FGS is not sticky. That is, if the process dies, the ServiceRecord
     * should be deleted.
     */
    private void testServiceIsNotSticky(int startCommand) throws Exception {
        try (Receiver<ShortFgsMessage> receiver = new Receiver<>(sContext, TAG)) {

            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, startCommand);

            // Wait for the method name message.
            waitForMethodCall(receiver, FGS0, "onStartCommand");

            // Tell the helper to kill itself.
            ShortFgsMessageReceiver.sendMessage(newMessage().setDoKillProcess(true));

            waitForAckMessage(receiver);

            // Wait until the process is actually gone.
            // We need it because 1) kill is async and 2) the ack is sent before the kill anyway
            waitUntil("Process still running",
                    () -> !DumpProtoUtils.processExists(FGS0.getPackageName()));

            // The service record should now be deleted.
            assertServiceNotRunning(FGS0);

            // Make sure onTimeout() won't happen. (If it did, onTimeout() would send a message,
            // which would break the below ensureNoMoreMessages().)

            // Wait for the timeout + extra duration
            Thread.sleep(SHORTENED_TIMEOUT + 5000);

            receiver.ensureNoMoreMessages();
        }
    }

    /**
     * This is a test to ensure the assumption of {@link #testServiceIsNotSticky} is correct --
     * that is, even when a process dies, its sticky services should still have ServiceRecords.
     */
    @Test
    public void testNonShortServiceCanBeSticky() throws Exception {
        try (Receiver<ShortFgsMessage> receiver = new Receiver<>(sContext, TAG)) {

            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    Service.START_STICKY);

            // Wait for the method name message.
            waitForMethodCall(receiver, FGS0, "onStartCommand");

            // Tell the helper to kill itself.
            ShortFgsMessageReceiver.sendMessage(newMessage().setDoKillProcess(true));

            waitForAckMessage(receiver);

            // Wait until the process is actually gone.
            // We need it because 1) kill is async and 2) the ack is sent before the kill anyway
            waitUntil("Process still running",
                    () -> !DumpProtoUtils.processExists(FGS0.getPackageName()));

            // Because the service _is_ sticky, the ServiceRecord should still exist.
            assertServiceRunning(FGS0);

            // Stop the service.
            sContext.stopService(new Intent().setComponent(FGS0));

            waitUntil("Service still running",
                    () -> DumpProtoUtils.findServiceRecord(FGS0) == null);

            // We ignore any remaining broadcasts.
        }
    }
}
