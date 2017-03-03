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

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
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
public class LoginActivity extends AbstractAutoFillActivity {

    private static final String TAG = "LoginActivity";
    private static String WELCOME_TEMPLATE = "Welcome to the new activity, %s!";
    private static final long LOGIN_TIMEOUT_MS = 1000;

    static final String AUTHENTICATION_MESSAGE = "Authentication failed. D'OH!";
    static final String ID_USERNAME_LABEL = "username_label";
    static final String ID_USERNAME = "username";
    static final String ID_PASSWORD_LABEL = "password_label";
    static final String ID_PASSWORD = "password";
    static final String ID_LOGIN = "login";
    static final String ID_OUTPUT = "output";

    private TextView mUsernameLabel;
    private EditText mUsernameEditText;
    private TextView mPasswordLabel;
    private EditText mPasswordEditText;
    private TextView mOutput;
    private Button mLoginButton;
    private Button mClearButton;
    private FillExpectation mExpectation;

    // State used to synchronously get the result of a login attempt.
    private CountDownLatch mLoginLatch;
    private String mLoginMessage;

    /**
     * Gets the expected welcome message for a given username.
     */
    static String getWelcomeMessage(String username) {
        return String.format(WELCOME_TEMPLATE,  username);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.login_activity);

        mLoginButton = (Button) findViewById(R.id.login);
        mClearButton = (Button) findViewById(R.id.clear);
        mUsernameLabel = (TextView) findViewById(R.id.username_label);
        mUsernameEditText = (EditText) findViewById(R.id.username);
        mPasswordLabel = (TextView) findViewById(R.id.password_label);
        mPasswordEditText = (EditText) findViewById(R.id.password);
        mOutput = (TextView) findViewById(R.id.output);

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
    private void login() {
        final String username = mUsernameEditText.getText().toString();
        final String password = mPasswordEditText.getText().toString();
        final boolean valid = username.equals(password);

        if (valid) {
            Log.d(TAG, "login ok: " + username);
            final Intent intent = new Intent(this, WelcomeActivity.class);
            final String message = getWelcomeMessage(username);
            intent.putExtra(WelcomeActivity.EXTRA_MESSAGE, message);
            setLoginMessage(message);
            startActivity(intent);
            finish();
        } else {
            Log.d(TAG, "login failed: " + AUTHENTICATION_MESSAGE);
            mOutput.setText(AUTHENTICATION_MESSAGE);
            setLoginMessage(AUTHENTICATION_MESSAGE);
        }
    }

    private void setLoginMessage(String message) {
        Log.d(TAG, "setLoginMessage(): " + message);
        if (mLoginLatch != null) {
            mLoginMessage = message;
            mLoginLatch.countDown();
        }
    }

    /**
     * Sets the expectation for an auto-fill request, so it can be asserted through
     * {@link #assertAutoFilled()} later.
     */
    void expectAutoFill(String username, String password) {
        mExpectation = new FillExpectation(username, password);
        mUsernameEditText.addTextChangedListener(mExpectation.ccUsernameWatcher);
        mPasswordEditText.addTextChangedListener(mExpectation.ccPasswordWatcher);
    }

    /**
     * Asserts the activity was auto-filled with the values passed to
     * {@link #expectAutoFill(String, String)}.
     */
    void assertAutoFilled() throws Exception {
        assertWithMessage("expectAutoFill() not called").that(mExpectation).isNotNull();
        mExpectation.ccUsernameWatcher.assertAutoFilled();
        mExpectation.ccPasswordWatcher.assertAutoFilled();
    }

    /**
     * Visits the {@code username_label} in the UiThread.
     */
    void onUsernameLabel(ViewVisitor<TextView> v) {
        syncRunOnUiThread(() -> {
            v.visit(mUsernameLabel);
        });
    }

    /**
     * Visits the {@code username} in the UiThread.
     */
    void onUsername(ViewVisitor<EditText> v) {
        syncRunOnUiThread(() -> {
            v.visit(mUsernameEditText);
        });
    }

    /**
     * Gets the {@code username} view.
     */
    EditText getUsername() {
        return mUsernameEditText;
    }

    /**
     * Visits the {@code password_label} in the UiThread.
     */
    void onPasswordLabel(ViewVisitor<TextView> v) {
        syncRunOnUiThread(() -> {
            v.visit(mPasswordLabel);
        });
    }

    /**
     * Visits the {@code password} in the UiThread.
     */
    void onPassword(ViewVisitor<EditText> v) {
        syncRunOnUiThread(() -> {
            v.visit(mPasswordEditText);
        });
    }

    /**
     * Gets the {@code password} view.
     */
    EditText getPassword() {
        return mPasswordEditText;
    }

    /**
     * Taps the login button in the UI thread.
     */
    String tapLogin() throws Exception {
        mLoginLatch = new CountDownLatch(1);
        syncRunOnUiThread(() -> {
            mLoginButton.performClick();
        });
        boolean called = mLoginLatch.await(LOGIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) waiting for login", LOGIN_TIMEOUT_MS)
                .that(called).isTrue();
        return mLoginMessage;
    }

    /**
     * Sets the window flags.
     */
    void setFlags(int flags) {
        Log.d(TAG, "setFlags():" + flags);
        syncRunOnUiThread(() -> {
            getWindow().setFlags(flags, flags);
        });
    }

    /**
     * Holder for the expected auto-fill values.
     */
    private final class FillExpectation {
        private final OneTimeTextWatcher ccUsernameWatcher;
        private final OneTimeTextWatcher ccPasswordWatcher;

        private FillExpectation(String username, String password) {
            ccUsernameWatcher = new OneTimeTextWatcher("username", mUsernameEditText, username);
            ccPasswordWatcher = new OneTimeTextWatcher("password", mPasswordEditText, password);
        }
    }
}
