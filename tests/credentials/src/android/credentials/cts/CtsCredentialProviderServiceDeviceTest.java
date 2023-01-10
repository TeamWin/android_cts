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

package android.credentials.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.credentials.CreateCredentialException;
import android.credentials.CreateCredentialRequest;
import android.credentials.CreateCredentialResponse;
import android.credentials.CredentialManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CtsCredentialProviderServiceDeviceTest {
    public static final String CTS_PACKAGE_NAME =
            CtsNoOpCredentialProviderService.class.getPackage().getName();
    private static final String NAMESPACE_CREDENTIAL_MANAGER = "credential_manager";
    private static final String ENABLE_CREDENTIAL_MANAGER = "enable_credential_manager";
    private static final String CTS_SERVICE_NAME = CTS_PACKAGE_NAME + "/."
            + CtsNoOpCredentialProviderService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final int TEMPORARY_SERVICE_DURATION = 5000;

    @Rule
    public final DeviceConfigStateChangerRule mLookAllTheseRules =
            new DeviceConfigStateChangerRule(getInstrumentation().getTargetContext(),
                    NAMESPACE_CREDENTIAL_MANAGER,
                    ENABLE_CREDENTIAL_MANAGER,
                    "true");
    @Rule
    public ActivityScenarioRule mActivieyScenarioRule =
            new ActivityScenarioRule(TestCredentialActivity.class);

    @Before
    public void setUp() {
        assumeTrue("VERSION.SDK_INT=" + VERSION.SDK_INT,
                VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE);
        clearTestableCredentialProviderService();
        bindToTestService();
    }

    @After
    public void tearDown() {
        clearTestableCredentialProviderService();
    }

    @Test
    public void testGetCredentialManager_shouldSucceed() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            CredentialManager credentialManager = activity.getCredentialManager();
            assertThat(credentialManager).isNotNull();
        });
    }

    @Test
    public void testCreatePasswordCredentialRequest_shouldSucceed() {
        Bundle empty = new Bundle();
        CreateCredentialRequest request = new CreateCredentialRequest("PASSWORD", empty, empty,
                false);
        OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback =
                new OutcomeReceiver<CreateCredentialResponse, CreateCredentialException>() {
                    @Override
                    public void onResult(CreateCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(CreateCredentialException e) {
                        throw new RuntimeException("Create Credential Failed.");
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            CredentialManager credentialManager = activity.getCredentialManager();
            credentialManager.executeCreateCredential(request, activity, null,
                    Executors.newSingleThreadExecutor(), callback);
        });
    }

    private void bindToTestService() {
        // On Manager, bind to test service
        assertThat(getCredentialProviderServiceComponent()).isNotEqualTo(CTS_SERVICE_NAME);
        setTestableCredentialProviderService(CTS_SERVICE_NAME);
        assertThat(CTS_SERVICE_NAME).contains(getCredentialProviderServiceComponent());
    }

    private String getCredentialProviderServiceComponent() {
        return runShellCommand("cmd credential_manager get-bound-package %d", USER_ID);
    }

    private void setTestableCredentialProviderService(String service) {
        // TODO: should support multiple services when ready.
        runShellCommand("cmd credential_manager set-temporary-service %d %s %d",
                USER_ID, service, TEMPORARY_SERVICE_DURATION);
    }

    private void clearTestableCredentialProviderService() {
        runShellCommand("cmd credential_manager set-temporary-service %d", USER_ID);
    }
}

