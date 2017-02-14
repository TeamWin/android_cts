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

import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.autofill.AutoFillValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Activity that has the following fields:
 *
 * <ul>
 *   <li>Username EditText (id: username, no input-type)
 *   <li>Password EditText (id: "username", input-type textPassword)
 *   <li>Clear Button
 *   <li>Login Button
 * </ul>
 */
public class LoginActivity extends Activity {

    private static final String TAG = "LoginActivity";

    static final String ID_USERNAME = "username";
    static final String ID_PASSWORD = "password";

    private EditText mUsernameEditText;
    private EditText mPasswordEditText;
    private TextView mOutput;
    private Button mLoginButton;
    private Button mClearButton;
    private AutoFillExpectation mAutoFillExpectation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.login_activity);

        mLoginButton = (Button) findViewById(R.id.login);
        mClearButton = (Button) findViewById(R.id.clear);
        mUsernameEditText = (EditText) findViewById(R.id.username);
        mPasswordEditText = (EditText) findViewById(R.id.password);
        mOutput = (TextView) findViewById(R.id.output);

        // TODO(b/33197203): remove login / clear button if not used by the tests (currently,
        // they're only used for debugging)
        mLoginButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
        mClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                resetFields();
            }
        });
    }

    /**
     * Resets the values of the input fields.
     */
    private void resetFields() {
        mUsernameEditText.setText("");
        mPasswordEditText.setText("");
        mOutput.setText("");
    }

    /**
     * Emulates a login action.
     */
    // TODO(b/33197203): check if username / password matches and either show error message
    // or redirect to a welcome 'user' page
    private void login() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("Username:").append(mUsernameEditText.getText()).append(":\n");
        buffer.append("Password:").append(mPasswordEditText.getText()).append(":\n");
        final String output = buffer.toString();
        Log.d(TAG, "login(): " + output);
        mOutput.setText(output);
    }

    /**
     * Sets the expectation for an auto-fill request, so it can be asserted through
     * {@link #assertAutoFilled()} later.
     */
    void expectAutoFill(String username, String password) {
        mAutoFillExpectation = new AutoFillExpectation(username, password);
        mUsernameEditText
                .addTextChangedListener(new MyTextWatcher(mAutoFillExpectation.usernameLatch));
        mPasswordEditText
                .addTextChangedListener(new MyTextWatcher(mAutoFillExpectation.passwordLatch));
    }

    /**
     * Asserts the activity was auto-filled with the values passed to
     * {@link #expectAutoFill(String, String)}.
     */
    void assertAutoFilled() throws Exception {
        assertWithMessage("expectAutoFill() not called").that(mAutoFillExpectation).isNotNull();
        assertField("username", mUsernameEditText,
                mAutoFillExpectation.usernameLatch, mAutoFillExpectation.expectedUsername);
        assertField("password", mPasswordEditText,
                mAutoFillExpectation.passwordLatch, mAutoFillExpectation.expectedPassword);
    }

    /**
     * Visits the {@code username} in the UiThread.
     */
    void onUsername(ViewVisitor<EditText> v) {
        runOnUiThread(() -> {
            v.visit(mUsernameEditText);
        });
    }

    /**
     * Visits the {@code username} in the UiThread.
     */
    void onPassword(ViewVisitor<EditText> v) {
        runOnUiThread(() -> {
            v.visit(mPasswordEditText);
        });
    }

    /**
     * Asserts the value of an input field, using a latch to make sure it was set.
     */
    private void assertField(String name, EditText field, CountDownLatch latch,
            String expectedValue) throws Exception {
        final boolean set = latch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) for auto-fill of field %s", FILL_TIMEOUT_MS, name)
                .that(set).isTrue();
        final String actualValue = field.getText().toString();
        assertWithMessage("Wrong auto-fill value for field %s", name).that(actualValue)
                .isEqualTo(expectedValue);
    }

    /**
     * Holder for the expected auto-fill values.
     */
    private final class AutoFillExpectation {
        private final CountDownLatch usernameLatch = new CountDownLatch(1);
        private final CountDownLatch passwordLatch = new CountDownLatch(1);
        private final String expectedUsername;
        private final String expectedPassword;

        private AutoFillExpectation(String username, String password) {
            expectedUsername = username;
            expectedPassword = password;
        }
    }

    private class MyTextWatcher implements TextWatcher {
        private final CountDownLatch latch;

        private MyTextWatcher(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            Log.d(TAG, "onTextChanged(): " + s);
            latch.countDown();
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
