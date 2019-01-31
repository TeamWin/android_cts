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

import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertSessionId;
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewsOptionallyDisappeared;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSessionId;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

public class LoginActivity extends AbstractRootViewActivity {

    private static final String TAG = LoginActivity.class.getSimpleName();

    TextView mUsernameLabel;
    EditText mUsername;
    TextView mPasswordLabel;
    EditText mPassword;

    @Override
    protected void setContentViewOnCreate(Bundle savedInstanceState) {
        setContentView(R.layout.login_activity);

        mUsernameLabel = findViewById(R.id.username_label);
        mUsername = findViewById(R.id.username);
        mPasswordLabel = findViewById(R.id.password_label);
        mPassword = findViewById(R.id.password);
    }

    @Override
    public void assertDefaultEvents(@NonNull Session session) {
        final LoginActivity activity = this;
        final ContentCaptureSessionId sessionId = session.id;
        assertRightActivity(session, sessionId, activity);

        // Sanity check
        assertSessionId(sessionId, activity.mUsernameLabel);
        assertSessionId(sessionId, activity.mUsername);
        assertSessionId(sessionId, activity.mPassword);
        assertSessionId(sessionId, activity.mPasswordLabel);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        // TODO(b/119638528): ideally it should be 5 so it reflects just the views defined
        // in the layout - right now it's generating events for 2 intermediate parents
        // (android:action_mode_bar_stub and android:content), we should try to create an
        // activity without them

        final AutofillId rootId = activity.getRootView().getAutofillId();

        final int minEvents = 7;
        assertThat(events.size()).isAtLeast(minEvents);
        assertViewAppeared(events, 0, sessionId, activity.mUsernameLabel, rootId);
        assertViewAppeared(events, 1, sessionId, activity.mUsername, rootId);
        assertViewAppeared(events, 2, sessionId, activity.mPasswordLabel, rootId);
        assertViewAppeared(events, 3, sessionId, activity.mPassword, rootId);

        // TODO(b/119638528): get rid of those intermediated parents
        final View grandpa1 = activity.getGrandParent();
        final View grandpa2 = activity.getGrandGrandParent();
        final View decorView = activity.getDecorView();

        assertViewAppeared(events, 4, sessionId, activity.getRootView(),
                grandpa1.getAutofillId());
        assertViewAppeared(events, 5, grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events, 6, grandpa2, decorView.getAutofillId());

        assertViewsOptionallyDisappeared(events, minEvents,
                rootId,
                grandpa1.getAutofillId(), grandpa2.getAutofillId(),
                // decorView.getAutofillId(), // TODO(b/122315042): figure out why it's not
                // generated
                activity.mUsernameLabel.getAutofillId(), activity.mUsername.getAutofillId(),
                activity.mPasswordLabel.getAutofillId(), activity.mPassword.getAutofillId()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "AutofillIds: " + "usernameLabel=" + mUsernameLabel.getAutofillId()
                + ", username=" + mUsername.getAutofillId()
                + ", passwordLabel=" + mPasswordLabel.getAutofillId()
                + ", password=" + mPassword.getAutofillId());
    }
}
