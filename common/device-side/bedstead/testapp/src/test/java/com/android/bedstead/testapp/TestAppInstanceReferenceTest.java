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

import static android.Manifest.permission.INTERACT_ACROSS_USERS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.PollingCheck;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(BedsteadJUnit4.class)
public class TestAppInstanceReferenceTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final UserReference sUser = sTestApis.users().instrumented();

    private TestAppProvider mTestAppProvider;

    private static final String INTENT_ACTION = "com.android.bedstead.testapp.test_action";
    private static final IntentFilter INTENT_FILTER = new IntentFilter(INTENT_ACTION);
    private static final Intent INTENT = new Intent(INTENT_ACTION);
    private static final String INTENT_ACTION_2 = "com.android.bedstead.testapp.test_action2";
    private static final IntentFilter INTENT_FILTER_2 = new IntentFilter(INTENT_ACTION_2);
    private static final Intent INTENT_2 = new Intent(INTENT_ACTION_2);

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
        TestApp testApp = mTestAppProvider.any();
        TestAppInstanceReference testAppInstance = testApp.instance(sUser);

        assertThat(testAppInstance.activities().any()).isNotNull();
    }

    @Test
    public void uninstall_uninstalls() {
        TestApp testApp = mTestAppProvider.any();
        TestAppInstanceReference testAppInstance = testApp.install(sUser);

        testAppInstance.uninstall();

        Package pkg = sTestApis.packages().find(testApp.packageName()).resolve();
        if (pkg != null) {
            assertThat(pkg.installedOnUsers()).doesNotContain(sUser);
        }
    }

    @Test
    public void autoclose_uninstalls() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            // Intentionally empty
        }

        Package pkg = sTestApis.packages().find(testApp.packageName()).resolve();
        if (pkg != null) {
            assertThat(pkg.installedOnUsers()).doesNotContain(sUser);
        }
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

            PollingCheck.waitFor(() -> testApp.reference().runningProcess(sUser) != null);
        }
    }

    // We cannot test that after stopKeepAlive it does not restart, as we'd have to wait an
    // unbounded amount of time

    @Test
    public void stop_processIsNotRunning() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.activities().any().start();

            testAppInstance.stop();

            assertThat(testApp.reference().runningProcesses()).isEmpty();
        }
    }

    @Test
    public void stop_previouslyCalledKeepAlive_processDoesNotRestart() {
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.activities().any().start();
            testAppInstance.keepAlive();

            testAppInstance.stop();

            assertThat(testApp.reference().runningProcesses()).isEmpty();
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
        TestApp testApp = mTestAppProvider.any();
        try (TestAppInstanceReference testAppInstance = testApp.install(sUser)) {
            testAppInstance.activities().any().start();

            assertThat(testAppInstance.process()).isNotNull();
        }
    }
}
