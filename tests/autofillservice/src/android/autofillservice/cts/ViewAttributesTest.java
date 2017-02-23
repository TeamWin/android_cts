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

import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.support.annotation.NonNull;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.autofill.AutoFillValue;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ViewAttributesTest extends AutoFillServiceTestCase {
    @Rule
    public final ActivityTestRule<ViewAttributesTestActivity> mActivityRule =
            new ActivityTestRule<>(ViewAttributesTestActivity.class);

    private ViewAttributesTestActivity mActivity;
    private EditText mFirstLevelDefault;
    private EditText mFirstLevelManual;
    private EditText mFirstLevelAuto;
    private EditText mFirstLevelInherit;
    private EditText mManualContainerInherit;
    private EditText mManualContainerDefault;
    private EditText mManualContainerManual;
    private EditText mManualContainerAuto;
    private OneTimeTextWatcher mFirstLevelDefaultTextWatcher;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
        mFirstLevelDefault = (EditText) mActivity.findViewById(R.id.firstLevelDefault);
        mFirstLevelManual = (EditText) mActivity.findViewById(R.id.firstLevelManual);
        mFirstLevelAuto = (EditText) mActivity.findViewById(R.id.firstLevelAuto);
        mFirstLevelInherit = (EditText) mActivity.findViewById(R.id.firstLevelInherit);
        mManualContainerDefault = (EditText) mActivity.findViewById(R.id.manualContainerDefault);
        mManualContainerManual = (EditText) mActivity.findViewById(R.id.manualContainerManual);
        mManualContainerAuto = (EditText) mActivity.findViewById(R.id.manualContainerAuto);
        mManualContainerInherit = (EditText) mActivity.findViewById(R.id.manualContainerInherit);
    }

    /**
     * Sets the expectation for an auto-fill request, so it can be asserted through
     * {@link #assertAutoFilled()} later.
     */
    void expectAutoFill() {
        mFirstLevelDefaultTextWatcher = new OneTimeTextWatcher("firstLevelDefault",
                mFirstLevelDefault, "filled");
        mFirstLevelDefault.addTextChangedListener(mFirstLevelDefaultTextWatcher);
    }

    /**
     * Asserts the activity was auto-filled with the values passed to
     * {@link #expectAutoFill()}.
     */
    void assertAutoFilled() throws Exception {
        mFirstLevelDefaultTextWatcher.assertAutoFilled();
    }

    /**
     * Try to auto-fill by moving the focus to {@code field}. If the field should trigger an
     * auto-fill the auto-fill UI should show up.
     *
     * @param field    The field to move the focus to
     * @param expectUI If the auto-fill UI is expected to show up
     *
     * @throws Exception If something unexpected happened
     */
    private void checkFieldBehavior(@NonNull EditText field, boolean expectUI) throws Exception {
        // Make sure the requestFocus triggers a change
        if (field == mFirstLevelManual) {
            mActivity.runOnUiThread(() -> mFirstLevelDefault.requestFocus());
        } else {
            mActivity.runOnUiThread(() -> mFirstLevelManual.requestFocus());
        }

        enableService();

        try {
            final InstrumentedAutoFillService.Replier replier =
                    new InstrumentedAutoFillService.Replier();
            InstrumentedAutoFillService.setReplier(replier);

            replier.addResponse(new CannedFillResponse.Builder()
                    .addDataset(new CannedFillResponse.CannedDataset.Builder()
                            .setField("firstLevelDefault", AutoFillValue.forText("filled"))
                            .setField("firstLevelManual", AutoFillValue.forText("filled"))
                            .setField("firstLevelAuto", AutoFillValue.forText("filled"))
                            .setField("firstLevelInherit", AutoFillValue.forText("filled"))
                            .setField("manualContainerDefault", AutoFillValue.forText("filled"))
                            .setField("manualContainerManual", AutoFillValue.forText("filled"))
                            .setField("manualContainerAuto", AutoFillValue.forText("filled"))
                            .setField("manualContainerInherit", AutoFillValue.forText("filled"))
                            .setPresentation(createPresentation("dataset"))
                            .build())
                    .build());

            expectAutoFill();

            mActivity.runOnUiThread(() -> field.requestFocus());

            Throwable exceptionDuringAutoFillTrigger = null;
            try {
                waitUntilConnected();

                sUiBot.selectDataset("dataset");
            } catch (Throwable e) {
                if (expectUI) {
                    throw e;
                } else {
                    exceptionDuringAutoFillTrigger = e;
                }
            }

            if (expectUI) {
                assertAutoFilled();
            } else {
                assertThat(exceptionDuringAutoFillTrigger).isNotNull();
            }

            waitUntilDisconnected();
        } finally {
            disableService();
        }
    }

    @Test
    public void checkDefaultValue() {
        assertThat(mFirstLevelDefault.getAutoFillMode()).isEqualTo(View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkInheritValue() {
        assertThat(mFirstLevelInherit.getAutoFillMode()).isEqualTo(View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkAutoValue() {
        assertThat(mFirstLevelAuto.getAutoFillMode()).isEqualTo(View.AUTO_FILL_MODE_AUTO);
    }

    @Test
    public void checkManualValue() {
        assertThat(mFirstLevelManual.getAutoFillMode()).isEqualTo(View.AUTO_FILL_MODE_MANUAL);
    }

    @Test
    public void checkNestedDefaultValue() {
        assertThat(mManualContainerDefault.getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkNestedInheritValue() {
        assertThat(mManualContainerInherit.getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkNestedAutoValue() {
        assertThat(mManualContainerAuto.getAutoFillMode()).isEqualTo(View.AUTO_FILL_MODE_AUTO);
    }

    @Test
    public void checkNestedManualValue() {
        assertThat(mManualContainerManual.getAutoFillMode()).isEqualTo(View.AUTO_FILL_MODE_MANUAL);
    }

    @Test
    public void checkDefaultBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelDefault, true);
    }

    @Test
    public void checkInheritBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelInherit, true);
    }

    @Test
    public void checkAutoBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelAuto, true);
    }

    @Test
    public void checkManualBehavior() throws Exception {
        checkFieldBehavior(mFirstLevelManual, false);
    }

    @Test
    public void checkNestedDefaultBehavior() throws Exception {
        checkFieldBehavior(mManualContainerDefault, false);
    }

    @Test
    public void checkNestedInheritBehavior() throws Exception {
        checkFieldBehavior(mManualContainerInherit, false);
    }

    @Test
    public void checkNestedAutoBehavior() throws Exception {
        checkFieldBehavior(mManualContainerAuto, true);
    }

    @Test
    public void checkNestedManualBehavior() throws Exception {
        checkFieldBehavior(mManualContainerManual, false);
    }

    @Test
    public void checkSet() {
        View v = mActivity.findViewById(R.id.firstLevelDefault);

        v.setAutoFillMode(View.AUTO_FILL_MODE_MANUAL);
        assertThat(v.getAutoFillMode()).isEqualTo(View.AUTO_FILL_MODE_MANUAL);
    }

    @Test
    public void checkIllegalSet() throws Exception {
        View v = mActivity.findViewById(R.id.firstLevelDefault);

        assertThrows(IllegalArgumentException.class, () -> v.setAutoFillMode(-1));
    }
}
