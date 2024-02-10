/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.sensitivecontentprotection.cts;

import static android.view.flags.Flags.FLAG_SENSITIVE_CONTENT_APP_PROTECTION_API;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.View;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link TextView} translation related APIs.
 */
@RunWith(AndroidJUnit4.class)
public class ViewUnitTest {
    private static final String TAG = "ViewTest";

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION_API)
    public void testContentSensitivity() {
        View view = new View(mContext);

        // Test default state.
        assertThat(view.getContentSensitivity()).isEqualTo(View.CONTENT_SENSITIVITY_AUTO);
        assertThat(view.isContentSensitive()).isFalse();

        // Test setting to NO.
        view.setContentSensitivity(View.CONTENT_SENSITIVITY_NOT_SENSITIVE);
        assertThat(view.getContentSensitivity())
                .isEqualTo(View.CONTENT_SENSITIVITY_NOT_SENSITIVE);
        assertThat(view.isContentSensitive()).isFalse();

        // Test setting to YES.
        view.setContentSensitivity(View.CONTENT_SENSITIVITY_SENSITIVE);
        assertThat(view.getContentSensitivity()).isEqualTo(View.CONTENT_SENSITIVITY_SENSITIVE);
        assertThat(view.isContentSensitive()).isTrue();

        // Test setting back to AUTO.
        view.setContentSensitivity(View.CONTENT_SENSITIVITY_AUTO);
        assertThat(view.getContentSensitivity()).isEqualTo(View.CONTENT_SENSITIVITY_AUTO);
        assertThat(view.isContentSensitive()).isFalse();
    }
}
