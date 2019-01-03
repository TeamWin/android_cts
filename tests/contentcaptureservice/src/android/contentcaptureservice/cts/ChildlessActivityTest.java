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
        Log.v(mTag, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        // Should be empty because the root view is not important for content capture without a
        // child that is important.
        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(mTag, "events: " + events);
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
        Log.v(mTag, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        // Should be empty because the root view is not important for content capture without a
        // child that is important.
        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(mTag, "events: " + events);
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
        Log.v(mTag, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(mTag, "events: " + events);
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
        Log.v(mTag, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(mTag, "events: " + events);
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
        Log.v(mTag, "main session id: " + mainSessionId);

        final ContentCaptureSession childSession = mainSession
                .createContentCaptureSession(new ContentCaptureContext.Builder()
                        .setUri(Uri.parse("http://child")).build());
        final ContentCaptureSessionId childSessionId = childSession.getContentCaptureSessionId();
        Log.v(mTag, "child session id: " + childSessionId);

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
    public void testManuallyManageChildlessSiblingSessions() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(mTag, "main session id: " + mainSessionId);

        // Create 1st session
        final ContentCaptureContext context1 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session1")).build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(mTag, "child session id 1: " + childSessionId1);

        // Create 2nd session
        final ContentCaptureContext context2 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session2")).build();
        final ContentCaptureSession childSession2 = mainSession
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(mTag, "child session id 2: " + childSessionId2);

        // Close 1st session before opening 3rd
        childSession1.close();

        // Create 3nd session...
        final ContentCaptureContext context3 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session3")).build();
        final ContentCaptureSession childSession3 = mainSession
                .createContentCaptureSession(context3);
        final ContentCaptureSessionId childSessionId3 = childSession3.getContentCaptureSessionId();
        Log.v(mTag, "child session id 3: " + childSessionId3);

        // ...and close it right away
        childSession3.close();

        // Create 4nd session
        final ContentCaptureContext context4 = new ContentCaptureContext.Builder()
                .setUri(Uri.parse("http://session4")).build();
        final ContentCaptureSession childSession4 = mainSession
                .createContentCaptureSession(context4);
        final ContentCaptureSessionId childSessionId4 = childSession4.getContentCaptureSessionId();
        Log.v(mTag, "child session id 4: " + childSessionId4);

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

    private TextView newImportantChild(@NonNull Context context, @NonNull String text) {
        final TextView child = new TextView(context);
        child.setText(text);
        child.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_YES);
        return child;
    }
}
