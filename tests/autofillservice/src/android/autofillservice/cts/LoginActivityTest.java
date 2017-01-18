/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import android.autofillservice.cts.CannedFillResponse.CannedDataset;

@SmallTest
public class LoginActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final ActivityTestRule<LoginActivity> mActivityRule =
        new ActivityTestRule<LoginActivity>(LoginActivity.class);

    private LoginActivity mLoginActivity;

    @Before
    public void setActivity() {
        mLoginActivity = mActivityRule.getActivity();
    }

    @Test
    public void testAutoFillOneDataset() throws Exception {
        enableService();

        final CannedDataset.Builder dataset = new CannedDataset.Builder("The Dude");
        mLoginActivity.expectAutoFill(dataset, "dude", "sweet");

        InstrumentedAutoFillService.setFillResponse(new CannedFillResponse.Builder()
                .addDataset(dataset.build())
                .build());

        sUiBot.triggerFillRequest();
        sUiBot.selectDataset("The Dude");

        mLoginActivity.assertAutoFilled();
    }

    /*
     * TODO(b/33197203, b/33802548): test other scenarios
     *
     *  - no dataset
     *  - multiple datasets
     *  - response-level authentication (custom and fingerprint)
     *  - dataset-level authentication (custom and fingerprint)
     *
     *  Other assertions:
     *  - illegal state thrown on callback calls
     *  - system server state after calls (for example, no pending callback)
     */
}
