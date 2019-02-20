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

import android.autofillservice.cts.AbstractAutoFillActivity;
import android.autofillservice.cts.AutoFillServiceTestCase;
import android.autofillservice.cts.Helper;
import android.autofillservice.cts.augmented.CtsAugmentedAutofillService.AugmentedReplier;
import android.content.AutofillOptions;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;
import android.view.autofill.AutofillManager;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

// Must be public because of the @ClassRule
public abstract class AugmentedAutofillAutoActivityLaunchTestCase
        <A extends AbstractAutoFillActivity> extends AutoFillServiceTestCase.AutoActivityLaunch<A> {

    private static final String TAG = AugmentedAutofillAutoActivityLaunchTestCase.class
            .getSimpleName();

    protected static AugmentedReplier sAugmentedReplier;
    protected AugmentedUiBot mAugmentedUiBot;

    @BeforeClass
    public static void allowAugmentedAutofill() {
        sContext.getApplicationContext()
                .setAutofillOptions(AutofillOptions.forWhitelistingItself());
        allowOverlays();
    }

    @AfterClass
    public static void resetAllowAugmentedAutofill() {
        sContext.getApplicationContext().setAutofillOptions(null);
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

    @Override
    protected int getSmartSuggestionMode() {
        return AutofillManager.FLAG_SMART_SUGGESTION_SYSTEM;
    }

    protected void enableAugmentedService() {
        enableAugmentedService(/* whitelistSelf= */ true);
    }

    protected void enableAugmentedService(boolean whitelistSelf) {
        AugmentedHelper.setAugmentedService(CtsAugmentedAutofillService.SERVICE_NAME);

        // TODO(b/124456706): instead of sleeping it should wait for onConnected()
        SystemClock.sleep(1000);

        if (whitelistSelf) {
            Log.d(TAG, "Whitelisting " + Helper.MY_PACKAGE + " for augmented autofill");
            final ArraySet<String> packages = new ArraySet<>(1);
            packages.add(Helper.MY_PACKAGE);
            getAutofillManager().setAugmentedAutofillWhitelist(packages, /* activities= */ null);
        }
    }
}
