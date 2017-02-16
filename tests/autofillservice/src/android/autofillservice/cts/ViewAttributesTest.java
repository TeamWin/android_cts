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

import static org.testng.Assert.assertThrows;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ViewAttributesTest {
    @Rule
    public final ActivityTestRule<ViewAttributesTestActivity> mActivityRule =
            new ActivityTestRule<>(ViewAttributesTestActivity.class);

    private ViewAttributesTestActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void checkDefault() {
        assertThat(mActivity.findViewById(R.id.firstLevelDefault).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkInherit() {
        assertThat(mActivity.findViewById(R.id.firstLevelInherit).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkAuto() {
        assertThat(mActivity.findViewById(R.id.firstLevelAuto).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_AUTO);
    }

    @Test
    public void checkManual() {
        assertThat(mActivity.findViewById(R.id.firstLevelManual).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_MANUAL);
    }

    @Test
    public void checkNestedDefault() {
        assertThat(mActivity.findViewById(R.id.manualContainerDefault).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkNestedInherit() {
        assertThat(mActivity.findViewById(R.id.manualContainerInherit).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_INHERIT);
    }

    @Test
    public void checkNestedAuto() {
        assertThat(mActivity.findViewById(R.id.manualContainerAuto).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_AUTO);
    }

    @Test
    public void checkNestedManual() {
        assertThat(mActivity.findViewById(R.id.manualContainerManual).getAutoFillMode()).isEqualTo(
                View.AUTO_FILL_MODE_MANUAL);
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
