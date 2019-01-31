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
import static android.contentcaptureservice.cts.Helper.MY_PACKAGE;
import static android.contentcaptureservice.cts.Helper.componentNameFor;
import static android.contentcaptureservice.cts.Helper.newImportantView;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.contentcaptureservice.cts.common.DoubleVisitor;
import android.net.Uri;
import android.os.Bundle;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;
import android.view.contentcapture.UserDataRemovalRequest;
import android.view.contentcapture.UserDataRemovalRequest.UriRequest;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class LoginActivityTest extends AbstractContentCaptureIntegrationTest<LoginActivity> {

    private static final String TAG = LoginActivityTest.class.getSimpleName();

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
        Log.v(TAG, "session id: " + session.id);

        activity.assertDefaultEvents(session);
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
            Log.i(TAG, "Setting root view (" + rootView + ") session to " + childSession);
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
        Log.v(TAG, "session ids: main=" + mainSessionId + ", child=" + childSessionId);

        // Sanity checks
        assertSessionId(childSessionId, activity.getRootView());
        assertSessionId(childSessionId, activity.mUsernameLabel);
        assertSessionId(childSessionId, activity.mUsername);
        assertSessionId(childSessionId, activity.mPassword);
        assertSessionId(childSessionId, activity.mPasswordLabel);

        // Get the sessions
        final Session mainSession = service.getFinishedSession(mainSessionId);
        final Session childSession = service.getFinishedSession(childSessionId);

        assertRightActivity(mainSession, mainSessionId, activity);
        assertRightRelationship(mainSession, childSession);

        // Sanity check
        final List<ContentCaptureSessionId> allSessionIds = service.getAllSessionIds();
        assertThat(allSessionIds).containsExactly(mainSessionId, childSessionId);

        /*
         *  Asserts main session
         */

        // Checks context
        assertMainSessionContext(mainSession, activity);

        // Check events
        final List<ContentCaptureEvent> mainEvents = mainSession.getEvents();
        Log.v(TAG, "events for main session: " + mainEvents);

        // TODO(b/119638528): ideally it should be empty - right now it's generating events for 2
        // intermediate parents (android:action_mode_bar_stub and android:content), we should try to
        // create an activity without them
        final int minMainEvents = 2;
        assertThat(mainEvents.size()).isAtLeast(minMainEvents);
        final View grandpa1 = activity.getGrandParent();
        final View grandpa2 = activity.getGrandGrandParent();
        final View decorView = activity.getDecorView();
        assertViewAppeared(mainEvents, 0, grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(mainEvents, 1, grandpa2, decorView.getAutofillId());
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
        Log.v(TAG, "events for child session: " + childEvents);
        final AutofillId rootId = activity.getRootView().getAutofillId();
        final int minChildEvents = 5;
        assertThat(childEvents.size()).isAtLeast(minChildEvents);
        assertViewAppeared(childEvents, 0, childSessionId, activity.mUsernameLabel, rootId);
        assertViewAppeared(childEvents, 1, childSessionId, activity.mUsername, rootId);
        assertViewAppeared(childEvents, 2, childSessionId, activity.mPasswordLabel, rootId);
        assertViewAppeared(childEvents, 3, childSessionId, activity.mPassword, rootId);
        assertViewAppeared(childEvents, 4, childSessionId, activity.getRootView(),
                grandpa1.getAutofillId());
        assertViewsOptionallyDisappeared(childEvents, minChildEvents,
                rootId,
                activity.mUsernameLabel.getAutofillId(), activity.mUsername.getAutofillId(),
                activity.mPasswordLabel.getAutofillId(), activity.mPassword.getAutofillId()
        );
    }

    @Ignore("not implemented yet, pending on b/122595322")
    @Test
    public void testSimpleLifecycle_serviceDisabledActivity() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        // Disable activity
        service.setActivityContentCaptureEnabled(componentNameFor(LoginActivity.class), false);

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> sessionIds = service.getAllSessionIds();
        assertThat(sessionIds).isEmpty();

        // TODO(b/122595322): should also test events after re-enabling it
    }

    // TODO(b/122595322): same tests for disabled by package, explicity whitelisted, etc...

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
        Log.v(TAG, "events: " + events);

        final AutofillId rootId = activity.getRootView().getAutofillId();

        final int minEvents = 9;
        assertThat(events.size()).isAtLeast(minEvents);

        assertViewAppeared(events, 0, activity.mUsernameLabel, rootId);
        assertViewAppeared(events, 1, activity.mUsername, rootId, "user");
        assertViewAppeared(events, 2, activity.mPasswordLabel, rootId);
        assertViewAppeared(events, 3, activity.mPassword, rootId, "");
        // TODO(b/119638528): get rid of those intermediated parents
        final View grandpa1 = activity.getGrandParent();
        final View grandpa2 = activity.getGrandGrandParent();
        final View decorView = activity.getDecorView();

        assertViewAppeared(events, 4, activity.getRootView(), grandpa1.getAutofillId());
        assertViewAppeared(events, 5, grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events, 6, grandpa2, decorView.getAutofillId());

        assertViewTextChanged(events, 7, activity.mUsername.getAutofillId(), "USER");
        assertViewTextChanged(events, 8, activity.mPassword.getAutofillId(), "PASS");

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
        Log.v(TAG, "events: " + events);

        final AutofillId rootId = activity.getRootView().getAutofillId();

        final int minEvents = 10;
        assertThat(events.size()).isAtLeast(minEvents);

        assertViewAppeared(events, 0, activity.mUsernameLabel, rootId);
        assertViewAppeared(events, 1, activity.mUsername, rootId, "");
        assertViewAppeared(events, 2, activity.mPasswordLabel, rootId);
        assertViewAppeared(events, 3, activity.mPassword, rootId, "");
        // TODO(b/119638528): get rid of those intermediated parents
        final View grandpa1 = activity.getGrandParent();
        final View grandpa2 = activity.getGrandGrandParent();
        final View decorView = activity.getDecorView();

        assertViewAppeared(events, 4, activity.getRootView(), grandpa1.getAutofillId());
        assertViewAppeared(events, 5, grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events, 6, grandpa2, decorView.getAutofillId());

        assertViewTextChanged(events, 7, activity.mUsername.getAutofillId(), "ab");
        assertViewTextChanged(events, 8, activity.mPassword.getAutofillId(), "de");
        assertViewTextChanged(events, 9, activity.mUsername.getAutofillId(), "abc");

        assertViewsOptionallyDisappeared(events, minEvents,
                rootId,
                grandpa1.getAutofillId(),
                grandpa2.getAutofillId(),
                activity.mUsernameLabel.getAutofillId(), activity.mUsername.getAutofillId(),
                activity.mPasswordLabel.getAutofillId(), activity.mPassword.getAutofillId()
        );
    }

    @Test
    public void testDisabledByFlagSecure() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> activity.getWindow()
                .addFlags(WindowManager.LayoutParams.FLAG_SECURE));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        assertThat((session.context.getFlags()
                & ContentCaptureContext.FLAG_DISABLED_BY_FLAG_SECURE) != 0).isTrue();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        assertThat(events).isEmpty();
    }

    @Test
    public void testDisabledByApp() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> activity.getContentCaptureManager()
                .setContentCaptureEnabled(false));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        assertThat(activity.getContentCaptureManager().isContentCaptureEnabled()).isFalse();

        activity.syncRunOnUiThread(() -> activity.mUsername.setText("D'OH"));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        assertThat((session.context.getFlags()
                & ContentCaptureContext.FLAG_DISABLED_BY_APP) != 0).isTrue();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        assertThat(events).isEmpty();
    }

    @Test
    public void testDisabledFlagSecureAndByApp() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> {
            activity.getContentCaptureManager().setContentCaptureEnabled(false);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        });

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        assertThat(activity.getContentCaptureManager().isContentCaptureEnabled()).isFalse();
        activity.syncRunOnUiThread(() -> activity.mUsername.setText("D'OH"));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        assertThat((session.context.getFlags()
                & ContentCaptureContext.FLAG_DISABLED_BY_APP) != 0).isTrue();
        assertThat((session.context.getFlags()
                & ContentCaptureContext.FLAG_DISABLED_BY_FLAG_SECURE) != 0).isTrue();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        assertThat(events).isEmpty();
    }

    @Ignore("not implemented yet, pending on b/122595322")
    @Test
    public void testDisabledByFlagSecureAndService() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> activity.getWindow()
                .addFlags(WindowManager.LayoutParams.FLAG_SECURE));

        // Disable activity
        service.setActivityContentCaptureEnabled(componentNameFor(LoginActivity.class), false);

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> sessionIds = service.getAllSessionIds();
        assertThat(sessionIds).isEmpty();
    }

    @Ignore("not implemented yet, pending on b/122595322")
    @Test
    public void testDisabledByAppAndAndService() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> activity.getContentCaptureManager()
                .setContentCaptureEnabled(false));

        // Disable activity
        service.setActivityContentCaptureEnabled(componentNameFor(LoginActivity.class), false);

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        assertThat(activity.getContentCaptureManager().isContentCaptureEnabled()).isFalse();

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> sessionIds = service.getAllSessionIds();
    }

    @Test
    public void testUserDataRemovalRequest_forEverything() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        LoginActivity.onRootView((activity, rootView) -> activity.getContentCaptureManager()
                .removeUserData(new UserDataRemovalRequest.Builder().forEverything()
                        .build()));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        UserDataRemovalRequest request = service.getRemovalRequest();
        assertThat(request).isNotNull();
        assertThat(request.isForEverything()).isTrue();
        assertThat(request.getUriRequests()).isNull();
        assertThat(request.getPackageName()).isEqualTo(MY_PACKAGE);
    }

    @Test
    public void testUserDataRemovalRequest_oneUri() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final Uri uri = Uri.parse("com.example");

        LoginActivity.onRootView((activity, rootView) -> activity.getContentCaptureManager()
                .removeUserData(new UserDataRemovalRequest.Builder()
                        .addUri(uri, false)
                        .build()));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        UserDataRemovalRequest request = service.getRemovalRequest();
        assertThat(request).isNotNull();
        assertThat(request.isForEverything()).isFalse();
        assertThat(request.getPackageName()).isEqualTo(MY_PACKAGE);

        final List<UserDataRemovalRequest.UriRequest> requests = request.getUriRequests();
        assertThat(requests.size()).isEqualTo(1);

        final UriRequest actualRequest = requests.get(0);
        assertThat(actualRequest.getUri()).isEqualTo(uri);
        assertThat(actualRequest.isRecursive()).isFalse();
    }

    @Test
    public void testUserDataRemovalRequest_manyUris() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final Uri uri1 = Uri.parse("com.example");
        final Uri uri2 = Uri.parse("com.example2");

        LoginActivity.onRootView((activity, rootView) -> activity.getContentCaptureManager()
                .removeUserData(new UserDataRemovalRequest.Builder()
                        .addUri(uri1, false)
                        .addUri(uri2, true)
                        .build()));

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final UserDataRemovalRequest request = service.getRemovalRequest();
        assertThat(request).isNotNull();
        assertThat(request.isForEverything()).isFalse();
        assertThat(request.getPackageName()).isEqualTo(MY_PACKAGE);

        // felipeal: change it here so it checks URI getters
        final List<UserDataRemovalRequest.UriRequest> requests = request.getUriRequests();
        assertThat(requests.size()).isEqualTo(2);

        final UriRequest actualRequest1 = requests.get(0);
        assertThat(actualRequest1.getUri()).isEqualTo(uri1);
        assertThat(actualRequest1.isRecursive()).isFalse();

        final UriRequest actualRequest2 = requests.get(1);
        assertThat(actualRequest2.getUri()).isEqualTo(uri2);
        assertThat(actualRequest2.isRecursive()).isTrue();
    }

    @Test
    public void testAddChildren_rightAway() throws Exception {
        addChildrenTest(/* afterAnimation= */ false);
    }

    @Test
    public void testAddChildren_afterAnimation() throws Exception {
        addChildrenTest(/* afterAnimation= */ true);
    }

    private void addChildrenTest(boolean afterAnimation) throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();
        final View[] children = new View[2];

        final DoubleVisitor<AbstractRootViewActivity, LinearLayout> visitor = (activity,
                rootView) -> {
            final TextView child1 = newImportantView(activity, "c1");
            children[0] = child1;
            Log.v(TAG, "Adding child1(" + child1.getAutofillId() + "): " + child1);
            rootView.addView(child1);
            final TextView child2 = newImportantView(activity, "c1");
            children[1] = child2;
            Log.v(TAG, "Adding child2(" + child2.getAutofillId() + "): " + child2);
            rootView.addView(child2);
        };
        if (afterAnimation) {
            LoginActivity.onAnimationComplete(visitor);
        } else {
            LoginActivity.onRootView(visitor);
        }

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        final ContentCaptureSessionId sessionId = session.id;
        assertRightActivity(session, sessionId, activity);

        // Sanity check
        assertSessionId(sessionId, activity.mUsernameLabel);
        assertSessionId(sessionId, activity.mUsername);
        assertSessionId(sessionId, activity.mPassword);
        assertSessionId(sessionId, activity.mPasswordLabel);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);

        final AutofillId rootId = activity.getRootView().getAutofillId();

        final int minEvents = 9; // TODO(b/123540067): get rid of those intermediated parents
        assertThat(events.size()).isAtLeast(minEvents);
        assertViewAppeared(events, 0, sessionId, activity.mUsernameLabel, rootId);
        assertViewAppeared(events, 1, sessionId, activity.mUsername, rootId);
        assertViewAppeared(events, 2, sessionId, activity.mPasswordLabel, rootId);
        assertViewAppeared(events, 3, sessionId, activity.mPassword, rootId);
        if (afterAnimation) {
            // TODO(b/123540067): get rid of those intermediated parents
            final View grandpa1 = activity.getGrandParent();
            final View grandpa2 = activity.getGrandGrandParent();
            final View decorView = activity.getDecorView();

            assertViewAppeared(events, 4, sessionId, activity.getRootView(),
                    grandpa1.getAutofillId());
            assertViewAppeared(events, 5, grandpa1, grandpa2.getAutofillId());
            assertViewAppeared(events, 6, grandpa2, decorView.getAutofillId());
            assertViewAppeared(events, 7, sessionId, children[0], rootId);
            assertViewAppeared(events, 8, sessionId, children[1], rootId);
        } else {
            assertViewAppeared(events, 4, sessionId, children[0], rootId);
            assertViewAppeared(events, 5, sessionId, children[1], rootId);
        }
    }

    // TODO(b/119638528): add moar test cases for different sessions:
    // - session1 on rootView, session2 on children
    // - session1 on rootView, session2 on child1, session3 on child2
    // - combination above where the CTS test explicitly finishes a session

    // TODO(b/119638528): add moar test cases for different scenarios, like:
    // - dynamically adding /
    // - removing views
    // - pausing / resuming activity
    // - changing text
    // - secure flag with child sessions
    // - making sure events are flushed when activity pause / resume
}
