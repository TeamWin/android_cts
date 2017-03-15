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

import static android.view.View.IMPORTANT_FOR_AUTOFILL_AUTO;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_NO;
import static android.view.View.IMPORTANT_FOR_AUTOFILL_YES;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;

/**
 * An activity containing mostly widgets that should be removed from an auto-fill structure to
 * optimize it.
 */
public class FatActivity extends AbstractAutoFillActivity {

    static final String ID_CAPTCHA = "captcha";
    static final String ID_INPUT = "input";
    static final String ID_INPUT_CONTAINER = "input_container";
    static final String ID_IMAGE = "image";
    static final String ID_IMPORTANT_IMAGE = "important_image";

    private EditText mCaptcha;
    private EditText mInput;
    private ImageView mImage;
    private ImageView mImportantImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fat_activity);

        mCaptcha = (EditText) findViewById(R.id.captcha);
        mInput = (EditText) findViewById(R.id.input);
        mImage = (ImageView) findViewById(R.id.image);
        mImportantImage = (ImageView) findViewById(R.id.important_image);

        // Sanity check for importantForAutofill modes
        assertThat(mInput.getImportantForAutofill()).isEqualTo(IMPORTANT_FOR_AUTOFILL_YES);
        assertThat(mCaptcha.getImportantForAutofill()).isEqualTo(IMPORTANT_FOR_AUTOFILL_NO);
        assertThat(mImage.getImportantForAutofill()).isEqualTo(IMPORTANT_FOR_AUTOFILL_NO);
        assertThat(mImportantImage.getImportantForAutofill()).isEqualTo(IMPORTANT_FOR_AUTOFILL_YES);

    }

    /**
     * Visits the {@code input} in the UiThread.
     */
    void onInput(Visitor<EditText> v) {
        syncRunOnUiThread(() -> {
            v.visit(mInput);
        });
    }
}
