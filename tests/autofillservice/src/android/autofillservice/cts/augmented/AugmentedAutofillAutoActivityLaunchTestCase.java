/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.autofillservice.cts.augmented;

import static android.autofillservice.cts.Helper.allowOverlays;
import static android.autofillservice.cts.Helper.disallowOverlays;
import static android.provider.Settings.Global.AUTOFILL_SMART_SUGGESTION_EMULATION_FLAGS;

import android.autofillservice.cts.AbstractAutoFillActivity;
import android.autofillservice.cts.AutoFillServiceTestCase;
import android.autofillservice.cts.augmented.CtsAugmentedAutofillService.AugmentedReplier;
import android.view.autofill.AutofillManager;

import com.android.compatibility.common.util.SettingsStateChangerRule;
import com.android.compatibility.common.util.SettingsUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

// Must be public because of the @ClassRule
public abstract class AugmentedAutofillAutoActivityLaunchTestCase
        <A extends AbstractAutoFillActivity> extends AutoFillServiceTestCase.AutoActivityLaunch<A> {

    @ClassRule
    public static final SettingsStateChangerRule sFeatureEnabler = new SettingsStateChangerRule(
            sContext, SettingsUtils.NAMESPACE_GLOBAL, AUTOFILL_SMART_SUGGESTION_EMULATION_FLAGS,
            Integer.toString(AutofillManager.FLAG_SMART_SUGGESTION_SYSTEM));

    protected static AugmentedReplier sAugmentedReplier;
    protected AugmentedUiBot mAugmentedUiBot;

    @BeforeClass
    public static void allowAugmentedAutofillWindow() {
        allowOverlays();
    }

    @AfterClass
    public static void disallowAugmentedAutofillWindow() {
        disallowOverlays();
    }

    @Before
    public void setFixtures() {
        sAugmentedReplier = CtsAugmentedAutofillService.getAugmentedReplier();
        sAugmentedReplier.reset();
        mAugmentedUiBot = new AugmentedUiBot(mUiBot);
        mSafeCleanerRule
            .run(() -> sAugmentedReplier.assertNoUnhandledFillRequests())
            .add(() -> { return sAugmentedReplier.getExceptions(); });
    }

    @After
    public void resetService() {
        AugmentedHelper.resetAugmentedService();
    }

    protected void enableAugmentedService() {
        AugmentedHelper.setAugmentedService(CtsAugmentedAutofillService.SERVICE_NAME);
    }
}
