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

import static android.contentcaptureservice.cts.Assertions.LifecycleOrder.CREATION;
import static android.contentcaptureservice.cts.Assertions.LifecycleOrder.DESTRUCTION;
import static android.contentcaptureservice.cts.Assertions.assertChildSessionContext;
import static android.contentcaptureservice.cts.Assertions.assertLifecycleOrder;
import static android.contentcaptureservice.cts.Assertions.assertMainSessionContext;
import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewDisappeared;
import static android.contentcaptureservice.cts.Assertions.assertViewWithUnknownParentAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewsOptionallyDisappeared;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.net.Uri;
import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ChildlessActivityTest
        extends AbstractContentCaptureIntegrationTest<ChildlessActivity> {

    private static final String TAG = ChildlessActivityTest.class.getSimpleName();

    private static final ActivityTestRule<ChildlessActivity> sActivityRule = new ActivityTestRule<>(
            ChildlessActivity.class, false, false);

    public ChildlessActivityTest() {
        super(ChildlessActivity.class);
    }

    @Override
    protected ActivityTestRule<ChildlessActivity> getActivityTestRule() {
        return sActivityRule;
    }

    @Before
    @After
    public void resetActivityStaticState() {
        ChildlessActivity.onRootView(null);
    }

    @Test
    public void testDefaultLifecycle() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        // Should be empty because the root view is not important for content capture without a
        // child that is important.
        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        assertThat(events).isEmpty();
    }

    @Test
    public void testAddAndRemoveNoImportantChild() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        // Child must be created inside the lambda because it needs to use the Activity context.
        final AtomicReference<TextView> childRef = new AtomicReference<>();

        ChildlessActivity.onRootView((activity, rootView) -> {
            final TextView child = new TextView(activity);
            child.setText("VIEW, Y U NO IMPORTANT?");
            child.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO);

            rootView.addView(child);
        });

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        // Remove view
        final TextView child = childRef.get();
        activity.syncRunOnUiThread(() -> activity.getRootView().removeView(child));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        // Should be empty because the root view is not important for content capture without a
        // child that is important.
        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        assertThat(events).isEmpty();
    }

    @Test
    public void testAddAndRemoveImportantChild() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        // TODO(b/120494182): Child must be created inside the lambda because it needs to use the
        // Activity context.
        final AtomicReference<TextView> childRef = new AtomicReference<>();

        ChildlessActivity.onRootView((activity, rootView) -> {
            final TextView text = newImportantChild(activity, "Important I am");
            rootView.addView(text);
            childRef.set(text);
        });

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        // Remove view
        final TextView child = childRef.get();
        activity.syncRunOnUiThread(() -> activity.getRootView().removeView(child));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        // TODO(b/119638958): ideally it should be 3 so it reflects just the views defined
        // in the layout - right now it's generating events for 2 intermediate parents
        // (android:action_mode_bar_stub and android:content), we should try to create an
        // activity without them
        assertThat(events.size()).isAtLeast(5);

        // Assert just the relevant events
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(events.get(0), sessionId, child, rootId);
        assertViewWithUnknownParentAppeared(events.get(1), sessionId, activity.getRootView());
        // Ignore events 2 and 3 (intermediate parents appeared)
        assertViewDisappeared(events.get(4), child.getAutofillId());
    }

    @Test
    public void testAddImportantChildAfterSessionStarted() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final TextView child = newImportantChild(activity, "Important I am");
        activity.runOnUiThread(() -> activity.getRootView().addView(child));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        // TODO(b/119638958): ideally it should be 3 so it reflects just the views defined
        // in the layout - right now it's generating events for 2 intermediate parents
        // (android:action_mode_bar_stub and android:content), we should try to create an
        // activity without them
        assertThat(events.size()).isAtLeast(4);

        // Assert just the relevant events
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(events.get(0), sessionId, child, rootId);
    }

    @Test
    public void testAddAndRemoveImportantChildOnDifferentSession() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        final ContentCaptureSession childSession = mainSession
                .createContentCaptureSession(new ContentCaptureContext.Builder()
                        .setUri(Uri.parse("http://child")).build());
        final ContentCaptureSessionId childSessionId = childSession.getContentCaptureSessionId();
        Log.v(TAG, "child session id: " + childSessionId);

        final TextView child = newImportantChild(activity, "Important I am");
        child.setContentCaptureSession(childSession);
        activity.runOnUiThread(() -> activity.getRootView().addView(child));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> sessionIds = service.getAllSessionIds();
        assertThat(sessionIds).containsExactly(mainSessionId, childSessionId).inOrder();

        // Assert sessions
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);
        final List<ContentCaptureEvent> mainEvents = mainTestSession.getEvents();
        // TODO(b/119638958): ideally it should have only one event for the root view ,
        // right now it's generating events for 2 intermediate parents
        // (android:action_mode_bar_stub and android:content), we should try to create an
        // activity without them
        assertThat(mainEvents.size()).isAtLeast(3);
        assertViewWithUnknownParentAppeared(mainEvents.get(0), mainSessionId,
                activity.getRootView());

        final Session childTestSession = service.getFinishedSession(childSessionId);
        assertChildSessionContext(childTestSession, "http://child");
        final List<ContentCaptureEvent> childEvents = childTestSession.getEvents();
        final int minEvents = 1;
        assertThat(mainEvents.size()).isAtLeast(minEvents);
        assertViewAppeared(childEvents.get(0), childSessionId, child,
                activity.getRootView().getAutofillId());
        assertViewsOptionallyDisappeared(childEvents, minEvents, child.getAutofillId());
    }

    /**
     * Tests scenario where new sessions are added from the main session, but they're not nested
     * neither have views attached to them.
     */
    @Test
    public void testDinamicallyManageChildlessSiblingSessions() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        // Create 1st session
        final ContentCaptureContext context1 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session1")).build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(TAG, "child session id 1: " + childSessionId1);

        // Create 2nd session
        final ContentCaptureContext context2 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session2")).build();
        final ContentCaptureSession childSession2 = mainSession
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(TAG, "child session id 2: " + childSessionId2);

        // Close 1st session before opening 3rd
        childSession1.close();

        // Create 3nd session...
        final ContentCaptureContext context3 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session3")).build();
        final ContentCaptureSession childSession3 = mainSession
                .createContentCaptureSession(context3);
        final ContentCaptureSessionId childSessionId3 = childSession3.getContentCaptureSessionId();
        Log.v(TAG, "child session id 3: " + childSessionId3);

        // ...and close it right away
        childSession3.close();

        // Create 4nd session
        final ContentCaptureContext context4 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session4")).build();
        final ContentCaptureSession childSession4 = mainSession
                .createContentCaptureSession(context4);
        final ContentCaptureSessionId childSessionId4 = childSession4.getContentCaptureSessionId();
        Log.v(TAG, "child session id 4: " + childSessionId4);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> receivedIds = service.getAllSessionIds();
        assertThat(receivedIds).containsExactly(
                mainSessionId,
                childSessionId1,
                childSessionId2,
                childSessionId3,
                childSessionId4)
            .inOrder();

        // Assert main sessions info
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);
        assertThat(mainTestSession.getEvents()).isEmpty();

        final Session childTestSession1 = service.getFinishedSession(childSessionId1);
        assertChildSessionContext(childTestSession1, "http://session1");
        assertThat(childTestSession1.getEvents()).isEmpty();

        final Session childTestSession2 = service.getFinishedSession(childSessionId2);
        assertChildSessionContext(childTestSession2, "http://session2");
        assertThat(childTestSession2.getEvents()).isEmpty();

        final Session childTestSession3 = service.getFinishedSession(childSessionId3);
        assertChildSessionContext(childTestSession3, "http://session3");
        assertThat(childTestSession3.getEvents()).isEmpty();

        final Session childTestSession4 = service.getFinishedSession(childSessionId4);
        assertChildSessionContext(childTestSession4, "http://session4");
        assertThat(childTestSession4.getEvents()).isEmpty();

        // Assert lifecycle methods were called in the right order
        assertLifecycleOrder(1, mainTestSession,   CREATION);
        assertLifecycleOrder(2, childTestSession1, CREATION);
        assertLifecycleOrder(3, childTestSession2, CREATION);
        assertLifecycleOrder(4, childTestSession1, DESTRUCTION);
        assertLifecycleOrder(5, childTestSession3, CREATION);
        assertLifecycleOrder(6, childTestSession3, DESTRUCTION);
        assertLifecycleOrder(7, childTestSession4, CREATION);
        assertLifecycleOrder(8, childTestSession2, DESTRUCTION);
        assertLifecycleOrder(9, childTestSession4, DESTRUCTION);
        assertLifecycleOrder(10, mainTestSession,  DESTRUCTION);
    }

    @Test
    public void testDinamicallyAddOneChildOnAnotherSession_manuallyCloseSession() throws Exception {
        dinamicallyAddOneChildOnAnotherSessionTest(/* manuallyCloseSession= */ true);
    }

    @Test
    public void testDinamicallyAddOneChildOnAnotherSession_autoCloseSession() throws Exception {
        dinamicallyAddOneChildOnAnotherSessionTest(/* manuallyCloseSession= */ false);
    }

    /**
     * Tests scenario where just 1 session with 1 dinamically added view is created.
     */
    private void dinamicallyAddOneChildOnAnotherSessionTest(boolean manuallyCloseSession)
            throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);
        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        // Create session
        final ContentCaptureSession childSession = mainSession
                .createContentCaptureSession(new ContentCaptureContext.Builder()
                        .setUri(Uri.parse("http://child_session")).build());
        final ContentCaptureSessionId childSessionId = childSession.getContentCaptureSessionId();
        Log.v(TAG, "child session: " + childSessionId);

        final TextView child = addChild(activity, childSession, "Sweet O'Mine");
        if (manuallyCloseSession) {
            waitAndClose(childSession);
        }

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> receivedIds = service.getAllSessionIds();
        assertThat(receivedIds).containsExactly(mainSessionId, childSessionId).inOrder();

        // Assert main session
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);
        // TODO(b/119638958): ideally it should be empty, but has intermediate parents stuff...
        // assertThat(mainTestSession.getEvents()).isEmpty();

        // Assert child session
        final Session childTestSession = service.getFinishedSession(childSessionId);
        assertChildSessionContext(childTestSession, "http://child_session");
        final List<ContentCaptureEvent> childEvents = childTestSession.getEvents();
        assertThat(childEvents.size()).isAtLeast(1);
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(childEvents.get(0), child, rootId);

        // Assert lifecycle methods were called in the right order
        assertLifecycleOrder(1, mainTestSession,  CREATION);
        assertLifecycleOrder(2, childTestSession, CREATION);
        assertLifecycleOrder(3, childTestSession, DESTRUCTION);
        assertLifecycleOrder(4, mainTestSession, DESTRUCTION);
    }

    /**
     * Tests scenario where new sessions with children are added from the main session.
     */
    @Test
    public void testDinamicallyManageSiblingSessions() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);
        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        // Create 1st session
        final ContentCaptureContext context1 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session1")).build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(TAG, "child session id 1: " + childSessionId1);

        // Session 1, child 1
        final TextView s1c1 = addChild(activity, childSession1, "s1c1");

        // Create 2nd session
        final ContentCaptureContext context2 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session2")).build();
        final ContentCaptureSession childSession2 = mainSession
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(TAG, "child session id 2: " + childSessionId2);

        final TextView s2c1 = addChild(activity, childSession2, "s2c1");
        final TextView s2c2 = addChild(activity, childSession2, "s2c2");

        // Close 1st session before opening 3rd
        waitAndClose(childSession1);

        // Create 3nd session...
        final ContentCaptureContext context3 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session3")).build();
        final ContentCaptureSession childSession3 = mainSession
                .createContentCaptureSession(context3);
        final ContentCaptureSessionId childSessionId3 = childSession3.getContentCaptureSessionId();
        Log.v(TAG, "child session id 3: " + childSessionId3);

        final TextView s3c1 = addChild(activity, childSession3, "s3c1");
        final TextView s3c2 = addChild(activity, childSession3, "s3c2");
        waitAndRemoveView(activity, s3c1);
        final TextView s3c3 = addChild(activity, childSession3, "s3c3");

        // ...and close it right away
        waitAndClose(childSession3);

        // Create 4nd session
        final ContentCaptureContext context4 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session4")).build();
        final ContentCaptureSession childSession4 = mainSession
                .createContentCaptureSession(context4);
        final ContentCaptureSessionId childSessionId4 = childSession4.getContentCaptureSessionId();
        Log.v(TAG, "child session id 4: " + childSessionId4);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> receivedIds = service.getAllSessionIds();
        assertThat(receivedIds).containsExactly(
                mainSessionId,
                childSessionId1,
                childSessionId2,
                childSessionId3,
                childSessionId4)
            .inOrder();

        // Assert main sessions info
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);

        final Session childTestSession1 = service.getFinishedSession(childSessionId1);
        assertChildSessionContext(childTestSession1, "http://session1");
        final List<ContentCaptureEvent> events1 = childTestSession1.getEvents();
        Log.v(TAG, "events1: " + events1);
        assertThat(events1.size()).isAtLeast(1);
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(events1.get(0), s1c1, rootId);

        final Session childTestSession2 = service.getFinishedSession(childSessionId2);
        final List<ContentCaptureEvent> events2 = childTestSession2.getEvents();
        assertChildSessionContext(childTestSession2, "http://session2");
        Log.v(TAG, "events2: " + events2);
        assertThat(events2.size()).isAtLeast(2);
        assertViewAppeared(events2.get(0), s2c1, rootId);
        assertViewAppeared(events2.get(1), s2c2, rootId);

        final Session childTestSession3 = service.getFinishedSession(childSessionId3);
        assertChildSessionContext(childTestSession3, "http://session3");
        List<ContentCaptureEvent> events3 = childTestSession3.getEvents();
        Log.v(TAG, "events3: " + events3);
        assertThat(events3.size()).isAtLeast(4);
        assertViewAppeared(events3.get(0), s3c1, rootId);
        assertViewAppeared(events3.get(1), s3c2, rootId);
        assertViewDisappeared(events3.get(2), s3c1.getAutofillId());
        assertViewAppeared(events3.get(3), s3c3, rootId);

        final Session childTestSession4 = service.getFinishedSession(childSessionId4);
        assertChildSessionContext(childTestSession4, "http://session4");
        assertThat(childTestSession4.getEvents()).isEmpty();

        // Assert lifecycle methods were called in the right order
        assertLifecycleOrder(1, mainTestSession,   CREATION);
        assertLifecycleOrder(2, childTestSession1, CREATION);
        assertLifecycleOrder(3, childTestSession2, CREATION);
        assertLifecycleOrder(4, childTestSession1, DESTRUCTION);
        assertLifecycleOrder(5, childTestSession3, CREATION);
        assertLifecycleOrder(6, childTestSession3, DESTRUCTION);
        assertLifecycleOrder(7, childTestSession4, CREATION);
        assertLifecycleOrder(8, childTestSession2, DESTRUCTION);
        assertLifecycleOrder(9, childTestSession4, DESTRUCTION);
        assertLifecycleOrder(10, mainTestSession,  DESTRUCTION);
    }

    @Test
    public void testNestedSessions_simplestScenario() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        // Create child session
        final ContentCaptureContext childContext = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://child")).build();
        final ContentCaptureSession childSession = mainSession
                .createContentCaptureSession(childContext);
        final ContentCaptureSessionId childSessionId = childSession.getContentCaptureSessionId();
        Log.v(TAG, "child session id: " + childSessionId);

        // Create grand child session
        final ContentCaptureContext grandChild = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://grandChild")).build();
        final ContentCaptureSession grandChildSession = childSession
                .createContentCaptureSession(grandChild);
        final ContentCaptureSessionId grandChildSessionId = grandChildSession
                .getContentCaptureSessionId();
        Log.v(TAG, "child session id: " + grandChildSessionId);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> receivedIds = service.getAllSessionIds();
        assertThat(receivedIds).containsExactly(
                mainSessionId,
                childSessionId,
                grandChildSessionId)
            .inOrder();

        // Assert sessions
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);
        assertThat(mainTestSession.getEvents()).isEmpty();

        final Session childTestSession = service.getFinishedSession(childSessionId);
        assertChildSessionContext(childTestSession, "http://child");
        assertThat(childTestSession.getEvents()).isEmpty();

        final Session grandChildTestSession = service.getFinishedSession(grandChildSessionId);
        assertChildSessionContext(grandChildTestSession, "http://grandChild");
        assertThat(grandChildTestSession.getEvents()).isEmpty();

        // Assert lifecycle methods were called in the right order
        assertLifecycleOrder(1, mainTestSession, CREATION);
        assertLifecycleOrder(2, childTestSession, CREATION);
        assertLifecycleOrder(3, grandChildTestSession, CREATION);
        assertLifecycleOrder(4, grandChildTestSession, DESTRUCTION);
        assertLifecycleOrder(5, childTestSession, DESTRUCTION);
        assertLifecycleOrder(6, mainTestSession,  DESTRUCTION);
    }

    /**
     * Tests scenario where new sessions are added from each other session, but they're not nested
     * neither have views attached to them.
     *
     * <p>This test actions are exactly the same as
     * {@link #testDinamicallyManageChildlessSiblingSessions()}, except for session nesting (and
     * order of lifecycle events).
     */
    @Test
    public void testDinamicallyManageChildlessNestedSessions() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        // Create 1st session
        final ContentCaptureContext context1 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session1")).build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(TAG, "child session id 1: " + childSessionId1);

        // Create 2nd session
        final ContentCaptureContext context2 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session2")).build();
        final ContentCaptureSession childSession2 = childSession1
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(TAG, "child session id 2: " + childSessionId2);

        // Close 1st session before opening 3rd
        childSession1.close();

        // Create 3nd session...
        final ContentCaptureContext context3 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session3")).build();
        final ContentCaptureSession childSession3 = mainSession
                .createContentCaptureSession(context3);
        final ContentCaptureSessionId childSessionId3 = childSession3.getContentCaptureSessionId();
        Log.v(TAG, "child session id 3: " + childSessionId3);

        // ...and close it right away
        childSession3.close();

        // Create 4nd session
        final ContentCaptureContext context4 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session4")).build();
        final ContentCaptureSession childSession4 = mainSession
                .createContentCaptureSession(context4);
        final ContentCaptureSessionId childSessionId4 = childSession4.getContentCaptureSessionId();
        Log.v(TAG, "child session id 4: " + childSessionId4);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> receivedIds = service.getAllSessionIds();
        assertThat(receivedIds).containsExactly(
                mainSessionId,
                childSessionId1,
                childSessionId2,
                childSessionId3,
                childSessionId4)
            .inOrder();

        // Assert main sessions info
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);
        assertThat(mainTestSession.getEvents()).isEmpty();

        final Session childTestSession1 = service.getFinishedSession(childSessionId1);
        assertChildSessionContext(childTestSession1, "http://session1");
        assertThat(childTestSession1.getEvents()).isEmpty();

        final Session childTestSession2 = service.getFinishedSession(childSessionId2);
        assertChildSessionContext(childTestSession2, "http://session2");
        assertThat(childTestSession2.getEvents()).isEmpty();

        final Session childTestSession3 = service.getFinishedSession(childSessionId3);
        assertChildSessionContext(childTestSession3, "http://session3");
        assertThat(childTestSession3.getEvents()).isEmpty();

        final Session childTestSession4 = service.getFinishedSession(childSessionId4);
        assertChildSessionContext(childTestSession4, "http://session4");
        assertThat(childTestSession4.getEvents()).isEmpty();

        // Assert lifecycle methods were called in the right order
        assertLifecycleOrder(1, mainTestSession,   CREATION);
        assertLifecycleOrder(2, childTestSession1, CREATION);
        assertLifecycleOrder(3, childTestSession2, CREATION);
        assertLifecycleOrder(4, childTestSession1, DESTRUCTION);
        assertLifecycleOrder(5, childTestSession2, DESTRUCTION);
        assertLifecycleOrder(6, childTestSession3, CREATION);
        assertLifecycleOrder(7, childTestSession3, DESTRUCTION);
        assertLifecycleOrder(8, childTestSession4, CREATION);
        assertLifecycleOrder(9, childTestSession4, DESTRUCTION);
        assertLifecycleOrder(10, mainTestSession,  DESTRUCTION);
    }

    /* TODO(b/119638528): add more scenarios for nested sessions, such as:
     * - add views to the children sessions
     * - s1 -> s2 -> s3 and main -> s4; close(s1) then generate events on view from s3
     * - s1 -> s2 -> s3 and main -> s4; close(s2) then generate events on view from s3
     * - s1 -> s2 and s3->s4 -> s4
     * - etc
     */

    private TextView newImportantChild(@NonNull Context context, @NonNull String text) {
        final TextView child = new TextView(context);
        child.setText(text);
        child.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_YES);
        return child;
    }

    private TextView addChild(@NonNull ChildlessActivity activity,
            @NonNull ContentCaptureSession session, @NonNull String text) {
        final TextView child = newImportantChild(activity, text);
        child.setContentCaptureSession(session);
        Log.i(TAG, "adding " + child.getAutofillId() + " on session "
                + session.getContentCaptureSessionId());
        activity.runOnUiThread(() -> activity.getRootView().addView(child));
        return child;
    }

    // TODO(b/119638958): these method are used in cases where we cannot close a session because we
    // would miss intermediate evets, so we need to sleep. This is a hack (it's slow and flaky):
    // ideally we should block and wait until the service receives the event, but right now
    // we don't get the service events until after the activity is finished, so we cannot do that...
    private void waitAndClose(@NonNull ContentCaptureSession session) {
        Log.d(TAG, "sleeping for 1s before closing " + session.getContentCaptureSessionId());
        SystemClock.sleep(1_000);
        session.close();
    }
    private void waitAndRemoveView(@NonNull ChildlessActivity activity, @NonNull View view) {
        Log.d(TAG, "sleeping for 1s before removing " + view.getAutofillId());
        SystemClock.sleep(1_000);
        activity.syncRunOnUiThread(() -> activity.getRootView().removeView(view));
    }
}
