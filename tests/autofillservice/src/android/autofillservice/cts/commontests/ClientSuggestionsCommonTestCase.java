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
package android.autofillservice.cts.commontests;

import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.ID_EMPTY;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.getContext;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.role.RoleManager;
import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.ClientSuggestionsActivity;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.ClientAutofillRequestCallback;
import android.autofillservice.cts.testcore.OneTimeTextWatcher;
import android.autofillservice.cts.testcore.UiBot;
import android.os.Bundle;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This is the test case covering most scenarios - other test cases will cover characteristics
 * specific to that test's activity (for example, custom views).
 */
public abstract class ClientSuggestionsCommonTestCase
        extends AutoFillServiceTestCase.AutoActivityLaunch<ClientSuggestionsActivity> {

    private static final String TAG = "ClientSuggestions";
    protected ClientSuggestionsActivity mActivity;
    protected ClientAutofillRequestCallback.Replier mClientReplier;

    private static final String APP_PACKAGE_NAME = "android.autofillservice.cts";

    @ClassRule
    public static final DefaultBrowserRule sDeviceState = new DefaultBrowserRule();

    static class DefaultBrowserRule implements TestRule {

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    final String previousValue = getBrowserRole();
                    setBrowserRoleForPackage(APP_PACKAGE_NAME);

                    try {
                        base.evaluate();
                    } finally {
                        final String currentValue = getBrowserRole();
                        if (!Objects.equals(previousValue, currentValue)) {
                            // remove the role holder before restore to avoid the error
                            // "RUNNER ERROR: Instrumentation run failed due to 'Process crashed.'"
                            // That because when role holder is changed, activtiy process will be
                            // killed, then the test process will be interrupted.
                            removeBrowserRole(APP_PACKAGE_NAME);
                            setBrowserRoleForPackage(previousValue);
                        }
                    }
                }
            };
        }

        public void removeBrowserRole(@Nullable String value) {
            final CallbackFuture future = new CallbackFuture("removeRoleHolderAsUser");
            try {
                runWithShellPermissionIdentity(() -> {
                    getRoleManager().removeRoleHolderAsUser(RoleManager.ROLE_BROWSER, value,
                            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, Process.myUserHandle(),
                            sContext.getMainExecutor(), future);
                });
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void setBrowserRoleForPackage(@Nullable String value) {
            final CallbackFuture future = new CallbackFuture("addBrowserRoleHolder");
            try {
                runWithShellPermissionIdentity(() -> {
                    getRoleManager().addRoleHolderAsUser(RoleManager.ROLE_BROWSER, value,
                            RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, Process.myUserHandle(),
                            sContext.getMainExecutor(), future);
                });
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Nullable
        public String getBrowserRole() {
            try {
                return callWithShellPermissionIdentity(
                        () -> {
                            List<String> roleHolders = getRoleManager().getRoleHolders(
                                    RoleManager.ROLE_BROWSER);
                            return (roleHolders == null || roleHolders.isEmpty()) ? null
                                    : roleHolders.get(0);
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private RoleManager getRoleManager() {
            return getContext().getSystemService(RoleManager.class);
        }
    }

    private static class CallbackFuture extends CompletableFuture<Boolean>
            implements Consumer<Boolean> {
        String mMethodName;

        CallbackFuture(String methodName) {
            mMethodName = methodName;
        }

        @Override
        public void accept(Boolean successful) {
            complete(successful);
        }
    }

    protected ClientSuggestionsCommonTestCase() {}

    protected ClientSuggestionsCommonTestCase(UiBot inlineUiBot) {
        super(inlineUiBot);
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mClientReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        sReplier.assertOnFillRequestNotCalled();
        mClientReplier.assertReceivedRequest();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutoFillNoDatasets_fallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertReceivedRequest();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @AppModeFull(reason = "testAutoFillNoDatasets_fallbackDefaultService() is enough")
    public void testManualRequestAfterFallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertReceivedRequest();

        // The dataset shown.
        mUiBot.assertDatasets("The Dude");

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "DUDE")
                .setField(ID_PASSWORD, "SWEET")
                .setPresentation("THE DUDE", isInlineMode())
                .build());

        // Trigger autofill.
        mUiBot.waitForWindowChange(() -> mActivity.forceAutofillOnUsername());
        sReplier.getNextFillRequest();
        mClientReplier.assertNoUnhandledFillRequests();

        mActivity.expectAutoFill("DUDE", "SWEET");

        // Select the dataset.
        mUiBot.selectDataset("THE DUDE");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @AppModeFull(reason = "testAutoFillNoDatasets_fallbackDefaultService() is enough")
    public void testNewFieldAddedAfterFallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertReceivedRequest();

        // The dataset shown.
        mUiBot.assertDatasets("The Dude");

        // Try again, in a field that was added after the first request
        final EditText child = new EditText(mActivity);
        child.setId(R.id.empty);
        mActivity.addChild(child, ID_EMPTY);
        final OneTimeTextWatcher watcher = new OneTimeTextWatcher("child", child,
                "new view on the block");
        child.addTextChangedListener(watcher);
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setField(ID_EMPTY, "new view on the block")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mActivity.syncRunOnUiThread(() -> child.requestFocus());
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        mClientReplier.assertNoUnhandledFillRequests();

        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");
        mUiBot.waitForIdle();

        // Check the results.
        mActivity.assertAutoFilled();
        watcher.assertAutoFilled();
    }

    @Test
    @Ignore("b/288109790")
    public void testNoDatasetsAfterFallbackDefaultService() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(NO_RESPONSE);
        mClientReplier.addResponse(NO_RESPONSE);

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        mClientReplier.assertReceivedRequest();
        sReplier.getNextFillRequest();

        // Make sure UI is not shown.
        mUiBot.assertNoDatasetsEver();
    }

    @Test
    @AppModeFull(reason = "testAutoFillOneDataset() is enough")
    public void testAutoFillNoDatasets() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        setEmptyClientResponse();

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);

        mClientReplier.assertReceivedRequest();

        // Make sure UI is not shown.
        mUiBot.assertNoDatasetsEver();
    }

    @Test
    @AppModeFull(reason = "testAutoFillOneDataset() is enough")
    public void testNewFieldAddedAfterFirstRequest() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        setEmptyClientResponse();

        // Trigger autofill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mClientReplier.assertReceivedRequest();

        // Make sure UI is not shown.
        mUiBot.assertNoDatasetsEver();

        // Try again, in a field that was added after the first request
        final EditText child = new EditText(mActivity);
        child.setId(R.id.empty);
        mActivity.addChild(child, ID_EMPTY);
        final OneTimeTextWatcher watcher = new OneTimeTextWatcher("child", child,
                "new view on the block");
        child.addTextChangedListener(watcher);
        mClientReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setField(ID_EMPTY, "new view on the block")
                .setPresentation("The Dude", isInlineMode())
                .build());

        mActivity.syncRunOnUiThread(() -> child.requestFocus());

        mClientReplier.assertReceivedRequest();
        mActivity.expectAutoFill("dude", "sweet");

        // Select the dataset.
        mUiBot.selectDataset("The Dude");

        // Check the results.
        // Check username and password fields
        mActivity.assertAutoFilled();
        // Check the new added field
        watcher.assertAutoFilled();
    }

    @NonNull
    @Override
    protected AutofillActivityTestRule<ClientSuggestionsActivity> getActivityRule() {
        return new AutofillActivityTestRule<ClientSuggestionsActivity>(
                ClientSuggestionsActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
                mClientReplier = mActivity.getReplier();
            }
        };
    }

    private void setEmptyClientResponse() {
        mClientReplier.addResponse(new CannedFillResponse.Builder()
                .setExtras(new Bundle())
                .build());
    }
}
