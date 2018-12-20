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
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewDisappeared;
import static android.contentcaptureservice.cts.Assertions.assertViewWithUnknownParentAppeared;
import static android.contentcaptureservice.cts.Helper.TAG;
import static android.contentcaptureservice.cts.Helper.enableService;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSessionId;
import android.widget.TextView;

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
        enableService();

        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final CtsContentCaptureService service = CtsContentCaptureService.getInstance();
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
        enableService();

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

        final CtsContentCaptureService service = CtsContentCaptureService.getInstance();
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
        enableService();

        final ActivityWatcher watcher = startWatcher();

        // Child must be created inside the lambda because it needs to use the Activity context.
        final AtomicReference<TextView> childRef = new AtomicReference<>();

        ChildlessActivity.onRootView((activity, rootView) -> {
            final TextView text = new TextView(activity);
            text.setText("Important I am");
            text.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_YES);
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

        final CtsContentCaptureService service = CtsContentCaptureService.getInstance();
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
        assertThat(events).hasSize(5);

        // Assert just the relevant events
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(events.get(0), sessionId, child, rootId);
        assertViewWithUnknownParentAppeared(events.get(1), sessionId, activity.getRootView());
        // Ignore events 2 and 3 (intermediate parents appeared)
        assertViewDisappeared(events.get(4), child.getAutofillId());
    }
}
