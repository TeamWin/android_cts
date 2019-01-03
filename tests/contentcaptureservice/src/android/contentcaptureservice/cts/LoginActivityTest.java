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

import static android.contentcaptureservice.cts.Assertions.assertChildSessionContext;
import static android.contentcaptureservice.cts.Assertions.assertMainSessionContext;
import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertRightRelationship;
import static android.contentcaptureservice.cts.Assertions.assertSessionId;
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewTextChanged;
import static android.contentcaptureservice.cts.Assertions.assertViewsOptionallyDisappeared;
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
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(mTag, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        // Sanity check
        assertSessionId(sessionId, activity.mUsernameLabel);
        assertSessionId(sessionId, activity.mUsername);
        assertSessionId(sessionId, activity.mPassword);
        assertSessionId(sessionId, activity.mPasswordLabel);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(mTag, "events: " + events);
        // TODO(b/119638958): ideally it should be 5 so it reflects just the views defined
        // in the layout - right now it's generating events for 2 intermediate parents
        // (android:action_mode_bar_stub and android:content), we should try to create an
        // activity without them

        final AutofillId rootId = activity.getRootView().getAutofillId();

        final int minEvents = 7;
        assertThat(events.size()).isAtLeast(minEvents);
        assertViewAppeared(events.get(0), sessionId, activity.mUsernameLabel, rootId);
        assertViewAppeared(events.get(1), sessionId, activity.mUsername, rootId);
        assertViewAppeared(events.get(2), sessionId, activity.mPasswordLabel, rootId);
        assertViewAppeared(events.get(3), sessionId, activity.mPassword, rootId);

        // TODO(b/119638958): get rid of those intermediated parents
        final View grandpa1 = (View) activity.getRootView().getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = (View) grandpa2.getParent();

        assertViewAppeared(events.get(4), sessionId, activity.getRootView(),
                grandpa1.getAutofillId());
        assertViewAppeared(events.get(5), grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events.get(6), grandpa2, decorView.getAutofillId());

        assertViewsOptionallyDisappeared(events, minEvents,
                rootId,
                grandpa1.getAutofillId(), grandpa2.getAutofillId(), decorView.getAutofillId(),
                activity.mUsernameLabel.getAutofillId(), activity.mUsername.getAutofillId(),
                activity.mPasswordLabel.getAutofillId(), activity.mPassword.getAutofillId()
        );
    }

    @Test
    public void testSimpleLifecycle_rootViewSession() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final Uri uri = Uri.parse("file://dev/null");
        final Bundle bundle = new Bundle();
        bundle.putString("DUDE", "SWEET");
        final ContentCaptureContext clientContext = new ContentCaptureContext.Builder()
                .setUri(uri).setExtras(bundle).build();

        final AtomicReference<ContentCaptureSession> mainSessionRef = new AtomicReference<>();
        final AtomicReference<ContentCaptureSession> childSessionRef = new AtomicReference<>();

        LoginActivity.onRootView((activity, rootView) -> {
            final ContentCaptureSession mainSession = rootView.getContentCaptureSession();
            mainSessionRef.set(mainSession);
            final ContentCaptureSession childSession = mainSession
                    .createContentCaptureSession(clientContext);
            childSessionRef.set(childSession);
            Log.i(mTag, "Setting root view (" + rootView + ") session to " + childSession);
            rootView.setContentCaptureSession(childSession);
        });

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final ContentCaptureSessionId mainSessionId =
                mainSessionRef.get().getContentCaptureSessionId();
        final ContentCaptureSessionId childSessionId =
                childSessionRef.get().getContentCaptureSessionId();
        Log.v(mTag, "session ids: main=" + mainSessionId + ", child=" + childSessionId);

        // Sanity checks
        assertSessionId(childSessionId, activity.getRootView());
        assertSessionId(childSessionId, activity.mUsernameLabel);
        assertSessionId(childSessionId, activity.mUsername);
        assertSessionId(childSessionId, activity.mPassword);
        assertSessionId(childSessionId, activity.mPasswordLabel);

        // Get the sessions
        final List<ContentCaptureSessionId> allSessionIds = service.getAllSessionIds();
        assertThat(allSessionIds).containsExactly(mainSessionId, childSessionId);
        final Session mainSession = service.getFinishedSession(mainSessionId);
        final Session childSession = service.getFinishedSession(childSessionId);
        assertRightActivity(mainSession, mainSessionId, activity);
        assertRightRelationship(mainSession, childSession);

        /*
         *  Asserts main session
         */

        // Checks context
        assertMainSessionContext(mainSession, activity);

        // Check events
        final List<ContentCaptureEvent> mainEvents = mainSession.getEvents();
        Log.v(mTag, "events for main session: " + mainEvents);

        // TODO(b/119638958): ideally it should be empty - right now it's generating events for 2
        // intermediate parents (android:action_mode_bar_stub and android:content), we should try to
        // create an activity without them
        final int minMainEvents = 2;
        assertThat(mainEvents.size()).isAtLeast(minMainEvents);
        final View grandpa1 = (View) activity.getRootView().getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = (View) grandpa2.getParent();
        assertViewAppeared(mainEvents.get(0), grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(mainEvents.get(1), grandpa2, decorView.getAutofillId());
        assertViewsOptionallyDisappeared(mainEvents, minMainEvents,
                grandpa1.getAutofillId(), grandpa2.getAutofillId()
                // decorView.getAutofillId(), // TODO(b/122315042): figure out why it's not
                // generated
        );


        /*
         *  Asserts child session
         */

        // Checks context
        assertChildSessionContext(childSession, "file://dev/null");

        final Bundle extras = childSession.context.getExtras();
        assertThat(extras.keySet()).containsExactly("DUDE");
        assertThat(extras.getString("DUDE")).isEqualTo("SWEET");

        // Check events
        final List<ContentCaptureEvent> childEvents = childSession.getEvents();
        Log.v(mTag, "events for child session: " + childEvents);
        final AutofillId rootId = activity.getRootView().getAutofillId();
        final int minChildEvents = 5;
        assertThat(childEvents.size()).isAtLeast(minChildEvents);
        assertViewAppeared(childEvents.get(0), childSessionId, activity.mUsernameLabel, rootId);
        assertViewAppeared(childEvents.get(1), childSessionId, activity.mUsername, rootId);
        assertViewAppeared(childEvents.get(2), childSessionId, activity.mPasswordLabel, rootId);
        assertViewAppeared(childEvents.get(3), childSessionId, activity.mPassword, rootId);
        assertViewAppeared(childEvents.get(4), childSessionId, activity.getRootView(),
                grandpa1.getAutofillId());
        assertViewsOptionallyDisappeared(childEvents, minChildEvents,
                rootId,
                activity.mUsernameLabel.getAutofillId(), activity.mUsername.getAutofillId(),
                activity.mPasswordLabel.getAutofillId(), activity.mPassword.getAutofillId()
        );
    }

    @Test
    public void testTextChanged() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> ((LoginActivity) activity).mUsername
                .setText("user"));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.syncRunOnUiThread(() -> {
            activity.mUsername.setText("USER");
            activity.mPassword.setText("PASS");
        });

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(mTag, "events: " + events);

        final AutofillId rootId = activity.getRootView().getAutofillId();

        final int minEvents = 9;
        assertThat(events.size()).isAtLeast(minEvents);

        assertViewAppeared(events.get(0), activity.mUsernameLabel, rootId);
        assertViewAppeared(events.get(1), activity.mUsername, rootId, "user");
        assertViewAppeared(events.get(2), activity.mPasswordLabel, rootId);
        assertViewAppeared(events.get(3), activity.mPassword, rootId, "");
        // TODO(b/119638958): get rid of those intermediated parents
        final View grandpa1 = (View) activity.getRootView().getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = (View) grandpa2.getParent();

        assertViewAppeared(events.get(4), activity.getRootView(), grandpa1.getAutofillId());
        assertViewAppeared(events.get(5), grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events.get(6), grandpa2, decorView.getAutofillId());

        assertViewTextChanged(events.get(7), activity.mUsername.getAutofillId(), "USER");
        assertViewTextChanged(events.get(8), activity.mPassword.getAutofillId(), "PASS");

        assertViewsOptionallyDisappeared(events, minEvents,
                rootId,
                grandpa1.getAutofillId(), grandpa2.getAutofillId(),
                // decorView.getAutofillId(), // TODO(b/122315042): figure out why it's not
                // generated
                activity.mUsernameLabel.getAutofillId(), activity.mUsername.getAutofillId(),
                activity.mPasswordLabel.getAutofillId(), activity.mPassword.getAutofillId()
        );
    }

    @Test
    public void testTextChangeBuffer() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> ((LoginActivity) activity).mUsername
                .setText(""));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.syncRunOnUiThread(() -> {
            activity.mUsername.setText("a");
            activity.mUsername.setText("ab");

            activity.mPassword.setText("d");
            activity.mPassword.setText("de");

            activity.mUsername.setText("abc");
        });

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(mTag, "events: " + events);

        final AutofillId rootId = activity.getRootView().getAutofillId();

        final int minEvents = 10;
        assertThat(events.size()).isAtLeast(minEvents);

        assertViewAppeared(events.get(0), activity.mUsernameLabel, rootId);
        assertViewAppeared(events.get(1), activity.mUsername, rootId, "");
        assertViewAppeared(events.get(2), activity.mPasswordLabel, rootId);
        assertViewAppeared(events.get(3), activity.mPassword, rootId, "");
        // TODO(b/119638958): get rid of those intermediated parents
        final View grandpa1 = (View) activity.getRootView().getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = (View) grandpa2.getParent();

        assertViewAppeared(events.get(4), activity.getRootView(), grandpa1.getAutofillId());
        assertViewAppeared(events.get(5), grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events.get(6), grandpa2, decorView.getAutofillId());

        assertViewTextChanged(events.get(7), activity.mUsername.getAutofillId(), "ab");
        assertViewTextChanged(events.get(8), activity.mPassword.getAutofillId(), "de");
        assertViewTextChanged(events.get(9), activity.mUsername.getAutofillId(), "abc");

        assertViewsOptionallyDisappeared(events, minEvents,
                rootId,
                grandpa1.getAutofillId(), grandpa2.getAutofillId(),
                // decorView.getAutofillId(), // TODO(b/122315042): figure out why it's not
                // generated
                activity.mUsernameLabel.getAutofillId(), activity.mUsername.getAutofillId(),
                activity.mPasswordLabel.getAutofillId(), activity.mPassword.getAutofillId()
        );

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
