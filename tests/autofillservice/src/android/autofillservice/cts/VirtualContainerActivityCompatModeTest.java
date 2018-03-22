/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.Helper.hasAutofillFeature;
import static android.autofillservice.cts.InstrumentedAutoFillServiceCompatMode.SERVICE_NAME;
import static android.autofillservice.cts.InstrumentedAutoFillServiceCompatMode.SERVICE_PACKAGE;
import static android.autofillservice.cts.VirtualContainerView.ID_URL_BAR;
import static android.autofillservice.cts.VirtualContainerView.ID_URL_BAR2;
import static android.autofillservice.cts.common.SettingsHelper.NAMESPACE_GLOBAL;
import static android.provider.Settings.Global.AUTOFILL_COMPAT_MODE_ALLOWED_PACKAGES;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.common.SettingsHelper;
import android.autofillservice.cts.common.SettingsStateChangerRule;
import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test case for an activity containing virtual children but using the A11Y compat mode to implement
 * the Autofill APIs.
 */
public class VirtualContainerActivityCompatModeTest extends VirtualContainerActivityTest {
    private static final Context sContext = InstrumentationRegistry.getContext();

    @ClassRule
    public static final SettingsStateChangerRule sCompatModeChanger = new SettingsStateChangerRule(
            sContext, NAMESPACE_GLOBAL, AUTOFILL_COMPAT_MODE_ALLOWED_PACKAGES,
            SERVICE_PACKAGE + "[my_url_bar]");

    public VirtualContainerActivityCompatModeTest() {
        super(true);
    }

    @After
    public void resetCompatMode() {
        sContext.getApplicationContext().setAutofillCompatibilityEnabled(false);
    }

    @Override
    protected void preActivityCreated() {
        sContext.getApplicationContext().setAutofillCompatibilityEnabled(true);
    }

    @Override
    protected void postActivityLaunched(VirtualContainerActivity activity) {
        // Set our own compat mode as well..
        activity.mCustomView.setCompatMode(true);
    }

    @Override
    protected void enableService() {
        Helper.enableAutofillService(getContext(), SERVICE_NAME);
        InstrumentedAutoFillServiceCompatMode.setIgnoreUnexpectedRequests(false);
    }

    @Override
    protected void disableService() {
        if (!hasAutofillFeature()) return;

        Helper.disableAutofillService(getContext(), SERVICE_NAME);
        InstrumentedAutoFillServiceCompatMode.setIgnoreUnexpectedRequests(true);
    }

    @Override
    protected void assertUrlBarIsSanitized(ViewNode urlBar) {
        assertTextIsSanitized(urlBar);
        assertThat(urlBar.getWebDomain()).isEqualTo("dev.null");
        assertThat(urlBar.getWebScheme()).isEqualTo("ftp");
    }

    @Test
    public void testMultipleUrlBars_firstDoesNotExist() throws Exception {
        SettingsHelper.syncSet(sContext, NAMESPACE_GLOBAL, AUTOFILL_COMPAT_MODE_ALLOWED_PACKAGES,
                SERVICE_PACKAGE + "[first_am_i,my_url_bar]");

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude", createPresentation("DUDE"))
                .build());

        // Trigger autofill.
        focusToUsername();
        assertDatasetShown(mActivity.mUsername, "DUDE");

        // Make sure input was sanitized.
        final FillRequest request = sReplier.getNextFillRequest();
        final ViewNode urlBar = findNodeByResourceId(request.structure, ID_URL_BAR);

        assertUrlBarIsSanitized(urlBar);
    }

    @Test
    public void testMultipleUrlBars_bothExist() throws Exception {
        SettingsHelper.syncSet(sContext, NAMESPACE_GLOBAL, AUTOFILL_COMPAT_MODE_ALLOWED_PACKAGES,
                SERVICE_PACKAGE + "[my_url_bar,my_url_bar2]");

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude", createPresentation("DUDE"))
                .build());

        // Trigger autofill.
        focusToUsername();
        assertDatasetShown(mActivity.mUsername, "DUDE");

        // Make sure input was sanitized.
        final FillRequest request = sReplier.getNextFillRequest();
        final ViewNode urlBar = findNodeByResourceId(request.structure, ID_URL_BAR);
        final ViewNode urlBar2 = findNodeByResourceId(request.structure, ID_URL_BAR2);

        assertUrlBarIsSanitized(urlBar);
        assertTextIsSanitized(urlBar2);
    }
}
