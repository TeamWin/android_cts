/*
 * Copyright 2017 The Android Open Source Project
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

import static android.autofillservice.cts.Helper.assertFillEventForContextCommitted;
import static android.autofillservice.cts.Helper.assertFillEventForFieldsDetected;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.service.autofill.FillResponse.FLAG_TRACK_CONTEXT_COMMITED;

import static com.google.common.truth.Truth.assertWithMessage;

import android.provider.Settings;
import android.service.autofill.FillEventHistory;
import android.service.autofill.FillEventHistory.Event;
import android.service.autofill.UserData;
import android.view.autofill.AutofillId;
import android.widget.EditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class FieldsClassificationTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<GridActivity> mActivityRule =
            new AutofillActivityTestRule<GridActivity>(GridActivity.class);

    private GridActivity mActivity;
    private int mEnabledBefore;


    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    // TODO(b/67867469): remove once feature is stable
    @Before
    public void enableFeature() {
        mEnabledBefore = Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.AUTOFILL_FEATURE_FIELD_DETECTION, 0);
        if (mEnabledBefore == 1) {
            // Already enabled, ignore.
            return;
        }
        final OneTimeSettingsListener observer = new OneTimeSettingsListener(mContext,
                Settings.Secure.AUTOFILL_FEATURE_FIELD_DETECTION);
        runShellCommand("settings put secure %s %s default",
                Settings.Secure.AUTOFILL_FEATURE_FIELD_DETECTION, 1);
        observer.assertCalled();
    }

    // TODO(b/67867469): remove once feature is stable
    @After
    public void restoreFeatureStatus() {
        if (mEnabledBefore == 1) {
            // Already enabled, ignore.
            return;
        }
        final OneTimeSettingsListener observer = new OneTimeSettingsListener(mContext,
                Settings.Secure.AUTOFILL_FEATURE_FIELD_DETECTION);
        runShellCommand("settings put secure %s %s default",
                Settings.Secure.AUTOFILL_FEATURE_FIELD_DETECTION, mEnabledBefore);
        observer.assertCalled();
    }

    @Test
    public void testFullHit() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mActivity.getAutofillManager()
                .setUserData(new UserData.Builder("myId", "FULLY").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field = mActivity.getCell(1, 1);
        final AutofillId fieldId = field.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFillResponseFlags(FLAG_TRACK_CONTEXT_COMMITED)
                .setFieldClassificationIds(fieldId)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        sUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field);

        // Simulate user input
        mActivity.focusCell(1, 1);
        mActivity.setText(1, 1, "fully");

        // Finish context.
        mActivity.getAutofillManager().commit();

        // Assert results
        final FillEventHistory history =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        final List<Event> events = history.getEvents();
        assertWithMessage("Wrong number of events: %s", events).that(events.size()).isEqualTo(1);
        assertFillEventForFieldsDetected(events.get(0), "myId", 0);
    }

    @Test
    public void testMiss() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mActivity.getAutofillManager()
                .setUserData(new UserData.Builder("myId", "ABCDEF").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field = mActivity.getCell(1, 1);
        final AutofillId fieldId = field.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFillResponseFlags(FLAG_TRACK_CONTEXT_COMMITED)
                .setFieldClassificationIds(fieldId)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        sUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field);

        // Simulate user input
        mActivity.setText(1, 1, "xyz");

        // Finish context.
        mActivity.getAutofillManager().commit();

        // Assert results
        final FillEventHistory history =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        final List<Event> events = history.getEvents();
        assertWithMessage("Wrong number of events: %s", events).that(events.size()).isEqualTo(1);
        assertFillEventForContextCommitted(events.get(0));
    }

    @Test
    public void testNoUserInput() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mActivity.getAutofillManager()
                .setUserData(new UserData.Builder("myId", "FULLY").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field = mActivity.getCell(1, 1);
        final AutofillId fieldId = field.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFillResponseFlags(FLAG_TRACK_CONTEXT_COMMITED)
                .setFieldClassificationIds(fieldId)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        sUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field);

        // Finish context.
        mActivity.getAutofillManager().commit();

        // Assert results
        final FillEventHistory history =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        final List<Event> events = history.getEvents();
        assertWithMessage("Wrong number of events: %s", events).that(events.size()).isEqualTo(1);
        assertFillEventForContextCommitted(events.get(0));
    }

    /*
     * TODO(b/67867469): other scenarios:
     *
     * - Multipartition (for example, one response with FieldsDetection, others with datasets,
     *   saveinfo, and/or ignoredIds)
     * - make sure detectable fields don't trigger a new partition
     * - test partial hit (for example, 'fool' instead of 'full'
     * - multiple fields
     * - multiple value
     * - combinations of above items
     */
}
