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

package com.android.bedstead.testapp;

import static android.app.admin.DevicePolicyManager.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION;
import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.S;

import static com.android.eventlib.truth.EventLogsSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(BedsteadJUnit4.class)
public class TestAppInstanceReferenceTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final UserReference sUser = TestApis.users().instrumented();
    private static final String INTENT_ACTION = "com.android.bedstead.testapp.test_action";
    private static final IntentFilter INTENT_FILTER = new IntentFilter(INTENT_ACTION);
    private static final Intent INTENT = new Intent(INTENT_ACTION);
    private static final String INTENT_ACTION_2 = "com.android.bedstead.testapp.test_action2";
    private static final IntentFilter INTENT_FILTER_2 = new IntentFilter(INTENT_ACTION_2);
    private static final Intent INTENT_2 = new Intent(INTENT_ACTION_2);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
    private TestAppProvider mTestAppProvider;

    @Before
    public void setup() {
        mTestAppProvider = new TestAppProvider();
    }

    @Test
    public void user_returnsUserReference() {
        TestApp testApp = mTestAppProvider.any();
        TestAppInstanceReference testAppInstance = testApp.instance(sUser);

        assertThat(testAppInstance.user()).isEqualTo(sUser);
    }

    @Test
    public void testApp_returnsTestApp() {
        TestApp testApp = mTestAppProvider.any();
        TestAppInstanceReference testAppInstance = testApp.instance(sUser);

        assertThat(testAppInstance.testApp()).isEqualTo(testApp);
    }

    @Test
    public void activities_any_returnsActivity() {
        TestApp testApp = mTestAppProvider.query().whereActivities().isNotEmpty().get();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            assertThat(testAppInstance.activities().any()).isNotNull();
        }
    }

    @Test
    public void uninstall_uninstalls() {
        TestApp testApp = mTestAppProvider.any();
        TestAppInstanceReference testAppInstance = testApp.install(sUser);

        testAppInstance.uninstall();

        assertThat(TestApis.packages().find(testApp.packageName())
                .installedOnUser(sUser)).isFalse();
    }

    @Test
    public void autoclose_uninstalls() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            // Intentionally empty
        }

        assertThat(TestApis.packages().find(testApp.packageName())
                .installedOnUser(sUser)).isFalse();
    }

    @Test
    public void keepAlive_notInstalled_throwsException() {
        TestApp testApp = mTestAppProvider.any();
        TestAppInstanceReference testAppInstance = testApp.instance(sUser);

        assertThrows(IllegalStateException.class, testAppInstance::keepAlive);
    }

    @Test
    public void killProcess_keepAlive_processIsRunningAgain() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.keepAlive();

            testAppInstance.process().kill();

            Poll.forValue("running process", () -> testApp.pkg().runningProcess(sUser))
                    .toNotBeNull()
                    .errorOnFail()
                    .await();
        }
    }

    // We cannot test that after stopKeepAlive it does not restart, as we'd have to wait an
    // unbounded amount of time

    @Test
    public void stop_processIsNotRunning() {
        TestApp testApp = mTestAppProvider.query().whereActivities().isNotEmpty().get();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.activities().any().start();

            testAppInstance.stop();

            assertThat(testApp.pkg().runningProcesses()).isEmpty();
        }
    }

    @Test
    public void stop_previouslyCalledKeepAlive_processDoesNotRestart() {
        TestApp testApp = mTestAppProvider.query().whereActivities().isNotEmpty().get();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.activities().any().start();
            testAppInstance.keepAlive();

            testAppInstance.stop();

            assertThat(testApp.pkg().runningProcesses()).isEmpty();
        }
    }

    @Test
    public void process_isNotRunning_returnsNull() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            assertThat(testAppInstance.process()).isNull();
        }
    }

    @Test
    public void process_isRunning_isNotNull() {
        TestApp testApp = mTestAppProvider.query().whereActivities().isNotEmpty().get();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.activities().any().start();

            Poll.forValue("TestApp process", testAppInstance::process)
                    .toNotBeNull()
                    .errorOnFail()
                    .await();
        }
    }

    @Test
    public void registerReceiver_receivesBroadcast() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.registerReceiver(INTENT_FILTER);

            sContext.sendBroadcast(INTENT);

            assertThat(testAppInstance.events().broadcastReceived()
                    .whereIntent().action().isEqualTo(INTENT_ACTION))
                    .eventOccurred();
        }
    }

    @Test
    public void registerReceiver_multipleIntentFilters_receivesAllMatchingBroadcasts() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.registerReceiver(INTENT_FILTER);
            testAppInstance.registerReceiver(INTENT_FILTER_2);

            sContext.sendBroadcast(INTENT);
            sContext.sendBroadcast(INTENT_2);

            assertThat(testAppInstance.events().broadcastReceived()
                    .whereIntent().action().isEqualTo(INTENT_ACTION))
                    .eventOccurred();
            assertThat(testAppInstance.events().broadcastReceived()
                    .whereIntent().action().isEqualTo(INTENT_ACTION_2))
                    .eventOccurred();
        }
    }

    @Test
    public void registerReceiver_processIsRunning() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {

            testAppInstance.registerReceiver(INTENT_FILTER);

            assertThat(testApp.pkg().runningProcess(sUser)).isNotNull();
        }
    }

    @Test
    public void stop_registeredReceiver_doesNotReceiveBroadcast() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.registerReceiver(INTENT_FILTER);

            testAppInstance.stop();
            sContext.sendBroadcast(INTENT);

            EventLogs<BroadcastReceivedEvent> logs =
                    BroadcastReceivedEvent.queryPackage(testApp.packageName())
                            .whereIntent().action().isEqualTo(INTENT_ACTION);
            assertThat(logs.poll(SHORT_TIMEOUT)).isNull();
        }
    }

    @Test
    public void unregisterReceiver_registeredReceiver_doesNotReceiveBroadcast() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.registerReceiver(INTENT_FILTER);

            testAppInstance.unregisterReceiver(INTENT_FILTER);
            sContext.sendBroadcast(INTENT);

            EventLogs<BroadcastReceivedEvent> logs =
                    BroadcastReceivedEvent.queryPackage(testApp.packageName())
                            .whereIntent().action().isEqualTo(INTENT_ACTION);
            assertThat(logs.poll(SHORT_TIMEOUT)).isNull();
        }
    }

    @Test
    public void unregisterReceiver_doesNotUnregisterOtherReceivers() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.registerReceiver(INTENT_FILTER);
            testAppInstance.registerReceiver(INTENT_FILTER_2);

            testAppInstance.unregisterReceiver(INTENT_FILTER);
            sContext.sendBroadcast(INTENT);
            sContext.sendBroadcast(INTENT_2);

            EventLogs<BroadcastReceivedEvent> logs =
                    BroadcastReceivedEvent.queryPackage(testApp.packageName())
                            .whereIntent().action().isEqualTo(INTENT_ACTION);
            EventLogs<BroadcastReceivedEvent> logs2 =
                    BroadcastReceivedEvent.queryPackage(testApp.packageName())
                            .whereIntent().action().isEqualTo(INTENT_ACTION_2);
            assertThat(logs.poll(SHORT_TIMEOUT)).isNull();
            assertThat(logs2.poll()).isNotNull();
        }
    }

    @Test
    public void keepAlive_processIsRunning() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {

            testAppInstance.keepAlive();

            assertThat(testApp.pkg().runningProcess(sUser)).isNotNull();
        }
    }

    @Test
    @Ignore("b/195626250 Disabled until logging surviving reboots is restored")
    public void registerReceiver_appIsKilled_stillReceivesBroadcast() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.registerReceiver(INTENT_FILTER);
            testApp.pkg().runningProcess(sUser).kill();
            Poll.forValue("running process", () -> testApp.pkg().runningProcess(sUser))
                    .toNotBeNull()
                    .errorOnFail()
                    .await();

            sContext.sendBroadcast(INTENT);

            EventLogs<BroadcastReceivedEvent> logs =
                    BroadcastReceivedEvent.queryPackage(testApp.packageName())
                            .whereIntent().action().isEqualTo(INTENT_ACTION);
            assertThat(logs.poll()).isNotNull();
        }
    }

    @Test
    @RequireSdkVersion(min = S, reason = "isSafeOperation only available on S+")
    public void devicePolicyManager_returnsUsableInstance() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            // Arbitrary call which does not require specific permissions to confirm no crash
            testAppInstance.devicePolicyManager()
                    .isSafeOperation(OPERATION_SAFETY_REASON_DRIVING_DISTRACTION);
        }
    }

    @Test
    public void userManager_returnsUsableInstance() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            // Arbitrary call which does not require specific permissions to confirm no crash
            testAppInstance.userManager().getUserProfiles();
        }
    }

    @Test
    @RequireSdkVersion(min = Q, reason = "Wifimanager API only available on Q+")
    public void wifiManager_returnsUsableInstance() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            // Arbitrary call which does not require specific permissions to confirm no crash
            testAppInstance.wifiManager().getMaxNumberOfNetworkSuggestionsPerApp();
        }
    }

    @Test
    public void hardwarePropertiesManager_returnsUsableInstance() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            // Arbitrary call - there are no methods on this service which don't require permissions
            assertThrows(SecurityException.class, () -> {
                testAppInstance.hardwarePropertiesManager().getCpuUsages();
            });
        }
    }

    @Test
    public void packageManager_returnsUsableInstance() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            assertThat(testAppInstance.packageManager().hasSystemFeature("")).isFalse();
        }
    }

    @Test
    public void crossProfileApps_returnsUsableInstance() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            assertThat(testAppInstance.crossProfileApps().getTargetUserProfiles()).isEmpty();
        }
    }

    @Test
    public void launcherApps_returnsUsableInstance() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            assertThat(testAppInstance.launcherApps().hasShortcutHostPermission()).isFalse();
        }
    }
}
