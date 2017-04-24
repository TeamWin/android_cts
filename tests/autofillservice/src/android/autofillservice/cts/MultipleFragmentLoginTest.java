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

import static android.autofillservice.cts.FragmentContainerActivity.FRAGMENT_TAG;
import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.eventually;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MultipleFragmentLoginTest extends AutoFillServiceTestCase {
    private static final String LOG_TAG = MultipleFragmentLoginTest.class.getSimpleName();
    @Rule
    public final ActivityTestRule<FragmentContainerActivity> mActivityRule =
            new ActivityTestRule<>(FragmentContainerActivity.class);
    private FragmentContainerActivity mActivity;
    private EditText mEditText1;
    private EditText mEditText2;

    @Before
    public void init() {
        mActivity = mActivityRule.getActivity();
        mEditText1 = mActivity.findViewById(R.id.editText1);
        mEditText2 = mActivity.findViewById(R.id.editText2);
    }

    @Test
    public void loginOnTwoFragments() throws Exception {
        enableService();
        try {
            // Set expectations.
            sReplier.addResponse(new CannedFillResponse.Builder()
                    .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, "editText1").build());

            final InstrumentedAutoFillService.FillRequest[] request =
                    new InstrumentedAutoFillService.FillRequest[1];

            // Trigger autofill
            eventually(() -> {
                mActivity.syncRunOnUiThread(() -> {
                    mEditText2.requestFocus();
                    mEditText1.requestFocus();
                });

                try {
                    request[0] = sReplier.getNextFillRequest();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, (int) (FILL_TIMEOUT_MS * 2));

            assertThat(findNodeByResourceId(request[0].structure, "editText1")).isNotNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText2")).isNotNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText3")).isNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText4")).isNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText5")).isNull();

            Log.i(LOG_TAG, "Switching Fragments");

            // Replacing the fragment focused a previously unknown view which triggers a new
            // partition
            sReplier.addResponse(new CannedFillResponse.Builder()
                    .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, "editText3").build());

            mActivity.syncRunOnUiThread(
                    () -> mActivity.getFragmentManager().beginTransaction().replace(
                            R.id.rootContainer, new FragmentWithMoreEditTexts(),
                            FRAGMENT_TAG).commitNow());

            request[0] = sReplier.getNextFillRequest();

            assertThat(findNodeByResourceId(request[0].structure, "editText1")).isNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText2")).isNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText3")).isNotNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText4")).isNotNull();
            assertThat(findNodeByResourceId(request[0].structure, "editText5")).isNotNull();
        } finally {
            disableService();
        }
    }
}
