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

import static com.google.common.truth.Truth.assertThat;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

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

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void checkTextViewNoHint() {
        assertThat(mActivity.findViewById(R.id.textViewNoHint).getAutofillHints()).isNull();
    }

    @Test
    public void checkTextViewHintCustom() {
        assertThat(mActivity.findViewById(R.id.textViewHintCustom).getAutofillHints()).isEqualTo(
                new String[]{mActivity.getString(R.string.new_password_label)});
    }

    @Test
    public void checkTextViewPassword() {
        assertThat(mActivity.findViewById(R.id.textViewPassword).getAutofillHints()).isEqualTo(
                new String[]{View.AUTOFILL_HINT_PASSWORD});
    }

    @Test
    public void checkTextViewPhoneName() {
        assertThat(mActivity.findViewById(R.id.textViewPhoneName).getAutofillHints()).isEqualTo(
                new String[]{View.AUTOFILL_HINT_PHONE, View.AUTOFILL_HINT_USERNAME});
    }

    @Test
    public void checkTextViewHintsFromArray() {
        assertThat(mActivity.findViewById(R.id.textViewHintsFromArray).getAutofillHints()).isEqualTo(
                new String[]{"yesterday", "today", "tomorrow", "never"});
    }

    @Test
    public void checkSetAutoFill() {
        View v = mActivity.findViewById(R.id.textViewNoHint);

        v.setAutofillHints(null);
        assertThat(v.getAutofillHints()).isNull();

        v.setAutofillHints(new String[0]);
        assertThat(v.getAutofillHints()).isNull();

        v.setAutofillHints(new String[]{View.AUTOFILL_HINT_PASSWORD});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{View.AUTOFILL_HINT_PASSWORD});

        v.setAutofillHints(new String[]{"custom", "value"});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{"custom", "value"});

        v.setAutofillHints("more", "values");
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{"more", "values"});

        v.setAutofillHints(
                new String[]{View.AUTOFILL_HINT_PASSWORD, View.AUTOFILL_HINT_EMAIL_ADDRESS});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{View.AUTOFILL_HINT_PASSWORD,
                View.AUTOFILL_HINT_EMAIL_ADDRESS});
    }
}
