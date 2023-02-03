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

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.credentials.ClearCredentialStateException;
import android.credentials.ClearCredentialStateRequest;
import android.credentials.CreateCredentialException;
import android.credentials.CreateCredentialRequest;
import android.credentials.CreateCredentialResponse;
import android.credentials.CredentialManager;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.credentials.GetCredentialResponse;
import android.credentials.cts.testcore.DeviceConfigStateRequiredRule;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CtsCredentialProviderServiceDeviceTest {
    public static final String CTS_PACKAGE_NAME =
            CtsNoOpCredentialProviderService.class.getPackage().getName();
    public static final String CREDENTIAL_SERVICE = "credential_service";
    private static final String NAMESPACE_CREDENTIAL_MANAGER = "credential_manager";
    private static final String CTS_SERVICE_NAME = CTS_PACKAGE_NAME + "/" + CTS_PACKAGE_NAME + "/"
            + CtsNoOpCredentialProviderService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final int TEMPORARY_SERVICE_DURATION = 10000;
    private static final String TAG = "CtsCredentialProviderServiceDeviceTest";
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";

    private CredentialManager mCredentialManager;
    private final Context mContext = getInstrumentation().getContext();

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(TestCredentialActivity.class);

    // Sets up the feature flag rule and the device flag rule
    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_CREDENTIALS);

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigStateRequiredRule =
            new DeviceConfigStateRequiredRule(DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL, mContext, "true");

    @Before
    public void setUp() {

        Log.i(TAG, "Enabling service from scratch for " + CTS_SERVICE_NAME);
        Log.i(TAG, "Enabling CredentialManager flags as well...");
        enableCredentialManagerDeviceFeature(mContext);
        mCredentialManager = (CredentialManager) mContext.getSystemService(
                Context.CREDENTIAL_SERVICE);
        assumeTrue("VERSION.SDK_INT=" + VERSION.SDK_INT,
                VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE);
        clearTestableCredentialProviderService();
        bindToTestService();
    }

    @After
    public void tearDown() {
        Log.i(TAG, "Disabling credman services and device feature flag");
        //disableCredentialManagerDeviceFeature(mContext);
        clearTestableCredentialProviderService();
    }

    @Test
    public void testGetCredentialManager_shouldSucceed() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        activityScenario.onActivity(activity -> {

            assertThat(mCredentialManager).isNotNull();
        });
    }

    // TODO for all 'valid success' cases, mock credential manager the current success case
    // TODO (rightly) flips an error bit since we have test inputs
    @Test
    public void testGetCredentialRequest_serviceNotSetUp_onErrorInvoked()
            throws InterruptedException {
        AtomicReference<GetCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        GetCredentialRequest request = new GetCredentialRequest.Builder(empty)
                .addCredentialOption(new CredentialOption(
                "type", empty, empty, false)).build();
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            mCredentialManager.getCredential(request, activity, null,
                    Executors.newSingleThreadExecutor(), callback);
        });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get().getClass()).isEqualTo(
                GetCredentialException.class);
        // TODO add a null check for the case when the feature exists but remains false
    }

    @Test
    public void testGetCredentialRequest_nullRequest_throwsNPE() {
        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.getCredential(null, activity, null,
                    Executors.newSingleThreadExecutor(), callback));
        });
    }

    @Test
    public void testCreatePasswordCredentialRequest_serviceNotSetUp_onErrorInvoked()
            throws InterruptedException {
        AtomicReference<CreateCredentialException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        CreateCredentialRequest request = new CreateCredentialRequest("PASSWORD", empty, empty,
                false);
        OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull CreateCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            mCredentialManager.createCredential(request, activity, null,
                    Executors.newSingleThreadExecutor(), callback);
        });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get().getClass()).isEqualTo(
                CreateCredentialException.class);
    }

    @Test
    public void testCreatePasswordCredentialRequest_nullRequest_throwsNPE() {
        OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull CreateCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        // Do nothing
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.createCredential(null, activity, null,
                            Executors.newSingleThreadExecutor(), callback));
        });
    }

    @Test
    public void testClearCredentialRequest_serviceNotSetUp_onErrorInvoked()
            throws InterruptedException {
        AtomicReference<ClearCredentialStateException> loadedResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Bundle empty = new Bundle();
        ClearCredentialStateRequest request = new ClearCredentialStateRequest(empty);
        OutcomeReceiver<Void, ClearCredentialStateException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Void response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialStateException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }
                };

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {
            mCredentialManager.clearCredentialState(request, null,
                    Executors.newSingleThreadExecutor(), callback);
        });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get().getClass()).isEqualTo(
                ClearCredentialStateException.class);
    }

    @Test
    public void testClearCredentialRequest_nullRequest_throwsNPE() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.clearCredentialState(null, null,
                    Executors.newSingleThreadExecutor(), null));
        });
    }

    /**
     * Enable the main credential manager feature.
     * If this is off, any underlying changes for autofill-credentialManager integrations are off.
     */
    public static void enableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, true);
    }

    public static void disableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, false);
    }

    /**
     * Enable Credential Manager related autofill changes
     */
    public static void setCredentialManagerFeature(@NonNull Context context, boolean enabled) {
        setDeviceConfig(context,
                DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER, enabled);
    }

    /**
     * Set device config to set flag values.
     */
    public static void setDeviceConfig(
            @NonNull Context context, @NonNull String feature, boolean value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_CREDENTIAL, feature);
        setDeviceConfig(deviceConfigStateManager, String.valueOf(value));
    }

    public static void setDeviceConfig(@NonNull DeviceConfigStateManager deviceConfigStateManager,
            @Nullable String value) {
        final String previousValue = deviceConfigStateManager.get();
        if (TextUtils.isEmpty(value) && TextUtils.isEmpty(previousValue)
                || TextUtils.equals(previousValue, value)) {
            Log.v(TAG, "No changed in config: " + deviceConfigStateManager);
            return;
        }

        deviceConfigStateManager.set(value);
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

