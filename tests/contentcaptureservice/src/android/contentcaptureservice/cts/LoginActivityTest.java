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
import static android.contentcaptureservice.cts.Assertions.assertViewTextChanged;
import static android.contentcaptureservice.cts.Helper.TAG;
import static android.contentcaptureservice.cts.Helper.enableService;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.net.Uri;
import android.os.Bundle;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureManager;
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class LoginActivityTest extends AbstractContentCaptureIntegrationTest<LoginActivity> {

    private static final ActivityTestRule<LoginActivity> sActivityRule = new ActivityTestRule<>(
            LoginActivity.class, false, false);

    public LoginActivityTest() {
        super(LoginActivity.class);
    }

    @Override
    protected ActivityTestRule<LoginActivity> getActivityTestRule() {
        return sActivityRule;
    }

    @Before
    @After
    public void resetActivityStaticState() {
        LoginActivity.onRootView(null);
    }

    @Test
    public void testSimpleLifecycle_defaultSession() throws Exception {
        enableService();

        final ActivityWatcher watcher = startWatcher();

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final CtsContentCaptureService service = CtsContentCaptureService.getInstance();
        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        // Sanity check
        assertSessionId(sessionId, activity.mUsernameLabel);
        assertSessionId(sessionId, activity.mUsername);
        assertSessionId(sessionId, activity.mPassword);
        assertSessionId(sessionId, activity.mPasswordLabel);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        // TODO(b/119638958): ideally it should be 5 so it reflects just the views defined
        // in the layout - right now it's generating events for 2 intermediate parents
        // (android:action_mode_bar_stub and android:content), we should try to create an
        // activity without them

        final AutofillId rootId = activity.mRootView.getAutofillId();

        assertThat(events).hasSize(7);
        assertViewAppeared(events.get(0), sessionId, activity.mUsernameLabel, rootId);
        assertViewAppeared(events.get(1), sessionId, activity.mUsername, rootId);
        assertViewAppeared(events.get(2), sessionId, activity.mPasswordLabel, rootId);
        assertViewAppeared(events.get(3), sessionId, activity.mPassword, rootId);

        // TODO(b/119638958): get rid of those intermediated parents
        final View grandpa1 = (View) activity.mRootView.getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = (View) grandpa2.getParent();

        assertViewAppeared(events.get(4), sessionId, activity.mRootView, grandpa1.getAutofillId());
        assertViewAppeared(events.get(5), grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events.get(6), grandpa2, decorView.getAutofillId());
    }

    @Test
    public void testSimpleLifecycle_rootViewSession() throws Exception {
        enableService();

        final ActivityWatcher watcher = startWatcher();

        final Uri uri = Uri.parse("file://dev/null");
        final Bundle bundle = new Bundle();
        bundle.putString("DUDE", "SWEET");
        final ContentCaptureContext clientContext = new ContentCaptureContext.Builder()
                .setUri(uri).setExtras(bundle).build();

        LoginActivity.onRootView((activity, rootView) -> {
            final ContentCaptureManager cm = activity.getContentCaptureManager();
            final ContentCaptureSession session = cm.createContentCaptureSession(clientContext);
            Log.i(TAG, "Setting root view (" + rootView + ") session to " + session);
            rootView.setContentCaptureSession(session);
        });

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final ContentCaptureSessionId sessionId = activity.mRootView.getContentCaptureSession()
                .getContentCaptureSessionId();
        Log.v(TAG, "session id: " + sessionId);

        final CtsContentCaptureService service = CtsContentCaptureService.getInstance();
        final Session session = service.getFinishedSession(sessionId);

        assertRightActivity(session, sessionId, activity);

        // Checks context
        assertThat(session.context.getUri()).isEqualTo(uri);
        final Bundle extras = session.context.getExtras();
        assertThat(extras.keySet()).containsExactly("DUDE");
        assertThat(extras.getString("DUDE")).isEqualTo("SWEET");

        // Sanity check
        assertSessionId(sessionId, activity.mUsernameLabel);
        assertSessionId(sessionId, activity.mUsername);
        assertSessionId(sessionId, activity.mPassword);
        assertSessionId(sessionId, activity.mPasswordLabel);

        // Check events
        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        // TODO(b/119638958): ideally it should be 5 so it reflects just the views defined
        // in the layout - right now it's generating events for 2 intermediate parents
        // (android:action_mode_bar_stub and android:content), we should try to create an
        // activity without them

        final AutofillId rootId = activity.mRootView.getAutofillId();

        assertThat(events).hasSize(7);
        assertViewAppeared(events.get(0), sessionId, activity.mUsernameLabel, rootId);
        assertViewAppeared(events.get(1), sessionId, activity.mUsername, rootId);
        assertViewAppeared(events.get(2), sessionId, activity.mPasswordLabel, rootId);
        assertViewAppeared(events.get(3), sessionId, activity.mPassword, rootId);

        // TODO(b/119638958): get rid of those intermediate parents
        final View grandpa1 = (View) activity.mRootView.getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = (View) grandpa2.getParent();

        assertViewAppeared(events.get(4), sessionId, activity.mRootView, grandpa1.getAutofillId());
        assertViewAppeared(events.get(5), grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events.get(6), grandpa2, decorView.getAutofillId());

        // TODO(b/119638958): right now we're testing all events that happened after the activity
        // is finished, but we should test intermediate steps (like asserting all views appear
        // after the acitivty starts, then all of them disappear when it's finished
    }

    @Test
    public void testTextChanged() throws Exception {
        enableService();

        // TODO(b/119638958): move to super class
        final ActivityWatcher watcher = mActivitiesWatcher.watch(LoginActivity.class);

        LoginActivity.onRootView((activity, rootView) -> ((LoginActivity) activity).mUsername
                .setText("user"));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.syncRunOnUiThread(() -> activity.mUsername.setText("USER"));
        activity.syncRunOnUiThread(() -> activity.mPassword.setText("PASS"));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final CtsContentCaptureService service = CtsContentCaptureService.getInstance();
        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);

        final AutofillId rootId = activity.mRootView.getAutofillId();

        assertThat(events).hasSize(9);
        assertViewAppeared(events.get(0), activity.mUsernameLabel, rootId);
        assertViewAppeared(events.get(1), activity.mUsername, rootId, "user");
        assertViewAppeared(events.get(2), activity.mPasswordLabel, rootId);
        assertViewAppeared(events.get(3), activity.mPassword, rootId, "");
        // TODO(b/119638958): get rid of those intermediated parents
        final View grandpa1 = (View) activity.mRootView.getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = (View) grandpa2.getParent();

        assertViewAppeared(events.get(4), activity.mRootView, grandpa1.getAutofillId());
        assertViewAppeared(events.get(5), grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events.get(6), grandpa2, decorView.getAutofillId());

        // TODO(b/119638958): VIEW_DISAPPEARED events should be send before the activity
        // stopped - if we don't deprecate the latter, we should change the manager to make sure
        // they're send in that order (or dropped)

        assertViewTextChanged(events.get(7), "USER", activity.mUsername.getAutofillId());
        assertViewTextChanged(events.get(8), "PASS", activity.mUsername.getAutofillId());
    }

    // TODO(b/119638958): add moar test cases for different sessions:
    // - session1 on rootView, session2 on children
    // - session1 on rootView, session2 on child1, session3 on child2
    // - combination above where the CTS test explicitly finishes a session

    // TODO(b/119638958): add moar test cases for different scenarios, like:
    // - dynamically adding /
    // - removing views
    // - pausing / resuming activity
    // - changing text
    // - FLAG_SECURE
    // - making sure events are flushed when activity pause / resume
}
