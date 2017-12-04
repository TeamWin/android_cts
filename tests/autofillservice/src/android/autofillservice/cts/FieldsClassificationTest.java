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
import static android.autofillservice.cts.Helper.assertFillEventForFieldsClassification;
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
    public void testHit_oneUserData_oneDetectableField() throws Exception {
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
        mActivity.setText(1, 1, "fully");

        // Finish context.
        mActivity.getAutofillManager().commit();

        // Assert results
        final FillEventHistory history =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        final List<Event> events = history.getEvents();
        assertWithMessage("Wrong number of events: %s", events).that(events.size()).isEqualTo(1);
        assertFillEventForFieldsClassification(events.get(0), fieldId, "myId", 1000000);
    }

    @Test
    public void testHit_manyUserData_oneDetectableField_bestMatchIsFirst() throws Exception {
        manyUserData_oneDetectableField(true);
    }

    @Test
    public void testHit_manyUserData_oneDetectableField_bestMatchIsSecond() throws Exception {
        manyUserData_oneDetectableField(false);
    }

    private void manyUserData_oneDetectableField(boolean firstMatch) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final String expectedId;
        final String typedText;
        if (firstMatch) {
            expectedId = "1stId";
            typedText = "IAM111";
        } else {
            expectedId = "2ndId";
            typedText = "IAM222";
        }
        mActivity.getAutofillManager().setUserData(new UserData.Builder("1stId", "Iam1ST")
                .add("2ndId", "Iam2ND").build());
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
        mActivity.setText(1, 1, typedText);

        // Finish context.
        mActivity.getAutofillManager().commit();

        // Assert results
        final FillEventHistory history =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        final List<Event> events = history.getEvents();
        assertWithMessage("Wrong number of events: %s", events).that(events.size()).isEqualTo(1);
        // Matches 4 of 6 chars - 66.6666%
        assertFillEventForFieldsClassification(events.get(0), fieldId, expectedId, 666666);
    }

    @Test
    public void testHit_oneUserData_manyDetectableFields() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mActivity.getAutofillManager()
                .setUserData(new UserData.Builder("myId", "FULLY").build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field1 = mActivity.getCell(1, 1);
        final AutofillId fieldId1 = field1.getAutofillId();
        final EditText field2 = mActivity.getCell(1, 2);
        final AutofillId fieldId2 = field2.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFillResponseFlags(FLAG_TRACK_CONTEXT_COMMITED)
                .setFieldClassificationIds(fieldId1, fieldId2)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        sUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field1);

        // Simulate user input
        mActivity.setText(1, 1, "fully"); // 100%
        mActivity.setText(1, 2, "fooly"); // 60%

        // Finish context.
        mActivity.getAutofillManager().commit();

        // Assert results
        final FillEventHistory history =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        final List<Event> events = history.getEvents();
        assertWithMessage("Wrong number of events: %s", events).that(events.size()).isEqualTo(1);
        assertFillEventForFieldsClassification(events.get(0),
                new AutofillId[] { fieldId1, fieldId2 },
                new String[] { "myId", "myId" },
                new int[] { 1000000, 600000 });
    }

    @Test
    public void testHit_manyUserData_manyDetectableFields() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        mActivity.getAutofillManager()
                .setUserData(new UserData.Builder("myId", "FULLY")
                        .add("otherId", "EMPTY")
                        .build());
        final MyAutofillCallback callback = mActivity.registerCallback();
        final EditText field1 = mActivity.getCell(1, 1);
        final AutofillId fieldId1 = field1.getAutofillId();
        final EditText field2 = mActivity.getCell(1, 2);
        final AutofillId fieldId2 = field2.getAutofillId();
        final EditText field3 = mActivity.getCell(2, 1);
        final AutofillId fieldId3 = field3.getAutofillId();
        final EditText field4 = mActivity.getCell(2, 2);
        final AutofillId fieldId4 = field4.getAutofillId();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFillResponseFlags(FLAG_TRACK_CONTEXT_COMMITED)
                .setFieldClassificationIds(fieldId1, fieldId2)
                .build());

        // Trigger autofill
        mActivity.focusCell(1, 1);
        sReplier.getNextFillRequest();

        sUiBot.assertNoDatasets();
        callback.assertUiUnavailableEvent(field1);

        // Simulate user input
        mActivity.setText(1, 1, "fully"); // 100%
        mActivity.setText(1, 2, "empty"); // 100%
        mActivity.setText(2, 1, "fooly"); // 60%
        mActivity.setText(2, 2, "emppy"); // 80%

        // Finish context.
        mActivity.getAutofillManager().commit();

        // Assert results
        final FillEventHistory history =
                InstrumentedAutoFillService.peekInstance().getFillEventHistory();
        final List<Event> events = history.getEvents();
        assertWithMessage("Wrong number of events: %s", events).that(events.size()).isEqualTo(1);
        assertFillEventForFieldsClassification(events.get(0),
                new AutofillId[] { fieldId1, fieldId2, fieldId3, fieldId4 },
                new String[] { "myId", "otherId", "myId", "otherId" },
                new int[] { 1000000, 1000000, 600000, 800000});
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
     * v test partial hit (for example, 'fool' instead of 'full'
     * v multiple fields
     * v multiple value
     * - combinations of above items
     */
}
