/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.autofill.AutofillId;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public final class UsernameOnlyActivity extends AbstractAutoFillActivity {

    private static final String TAG = "UsernameOnlyActivity";

    private TextView mUsernameTextView;
    private EditText mUsernameEditText;
    private Button mNextButton;
    private Button mSameButton;
    private Button mFinishButton;

    private AutofillId mPasswordAutofillId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentView());

        mUsernameTextView = findViewById(R.id.username_label);
        mUsernameEditText = findViewById(R.id.username);
        mNextButton = findViewById(R.id.next);
        mNextButton.setOnClickListener((v) -> next());
        mSameButton = findViewById(R.id.same);
        mSameButton.setOnClickListener((v) -> same());
        mFinishButton = findViewById(R.id.finish);
        mFinishButton.setOnClickListener((v) -> finish());
    }

    protected int getContentView() {
        return R.layout.username_only_activity;
    }

    void focusOnUsername() {
        syncRunOnUiThread(() -> mUsernameEditText.requestFocus());
    }

    void requestManualAutofillOnUsername() {
        syncRunOnUiThread(() -> getAutofillManager().requestAutofill(mUsernameEditText));
    }

    void setUsername(String username) {
        syncRunOnUiThread(() -> mUsernameEditText.setText(username));
    }

    AutofillId getUsernameAutofillId() {
        return mUsernameEditText.getAutofillId();
    }

    /**
     * Sets the autofill id of the password using the intent that launches the new activity, so it's
     * set before the view strucutre is generated.
     */
    void setPasswordAutofillId(AutofillId id) {
        mPasswordAutofillId = id;
    }

    void next() {
        final String username = mUsernameEditText.getText().toString();
        Log.v(TAG, "Going to next screen as user " + username + " and aid " + mPasswordAutofillId);
        final Intent intent = new Intent(this, PasswordOnlyActivity.class)
                .putExtra(PasswordOnlyActivity.EXTRA_USERNAME, username)
                .putExtra(PasswordOnlyActivity.EXTRA_PASSWORD_AUTOFILL_ID, mPasswordAutofillId);
        startActivity(intent);
        finish();
    }

    void same() {
        Log.v(TAG, "Using same activity for password; id=" + mPasswordAutofillId);
        syncRunOnUiThread(() -> {
            getAutofillManager().commit();
            mUsernameTextView.setText("Password");
            mUsernameEditText.setText(null);
            final ViewGroup parent = (ViewGroup) mUsernameEditText.getParent();
            if (mPasswordAutofillId != null) {
                // Must detach before changing the autofill id
                Log.v(TAG, "Setting password autofill id to " + mPasswordAutofillId);
                parent.removeView(mUsernameEditText);
                mUsernameEditText.setAutofillId(mPasswordAutofillId);
                parent.addView(mUsernameEditText);
            }
            // Set focus away from input field so it can trigger autofill again
            getWindow().getDecorView().getRootView().requestFocus();
        });
    }
}
