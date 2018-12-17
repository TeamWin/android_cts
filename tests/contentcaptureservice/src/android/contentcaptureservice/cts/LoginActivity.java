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
package android.contentcaptureservice.cts;

import static android.contentcaptureservice.cts.Helper.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureManager;
import android.view.contentcapture.ContentCaptureSession;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LoginActivity extends AbstractContentCaptureActivity {

    static final String EXTRA_START_OPTIONS = "start_options";
    static final String EXTRA_CONTENT_CAPTURE_CONTEXT = "content_capture_context";

    static final String START_OPTION_ROOT_VIEW_SESSION = "set_root_view_session";

    LinearLayout mRootView;
    TextView mUsernameLabel;
    EditText mUsername;
    TextView mPasswordLabel;
    EditText mPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        mRootView = findViewById(R.id.root_view);
        mUsernameLabel = findViewById(R.id.username_label);
        mUsername = findViewById(R.id.username);
        mPasswordLabel = findViewById(R.id.password_label);
        mPassword = findViewById(R.id.password);

        final Intent intent = getIntent();
        if (intent != null) {
            final String startOptions = intent.getStringExtra(EXTRA_START_OPTIONS);
            if (startOptions != null) {
                Log.i(TAG, "start options: " + startOptions);
                switch (startOptions) {
                    case START_OPTION_ROOT_VIEW_SESSION:
                        final ContentCaptureManager cm = getContentCaptureManager();
                        final ContentCaptureContext context = intent
                                .getParcelableExtra(EXTRA_CONTENT_CAPTURE_CONTEXT);
                        if (context == null) {
                            throw new IllegalArgumentException(
                                    "NO " + EXTRA_CONTENT_CAPTURE_CONTEXT + " on " + intent);
                        }
                        final ContentCaptureSession session = cm
                                .createContentCaptureSession(context);
                        Log.i(TAG, "Setting root view (" + mRootView + ") session to " + session);
                        mRootView.setContentCaptureSession(session);
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Illegal option for " + EXTRA_START_OPTIONS + ": " + startOptions);
                }
            }
        }
    }
}
