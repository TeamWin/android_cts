/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.contentcaptureservice.cts.Assertions.assertDecorViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertSessionPaused;
import static android.contentcaptureservice.cts.Assertions.assertSessionResumed;
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewTreeFinished;
import static android.contentcaptureservice.cts.Assertions.assertViewTreeStarted;
import static android.contentcaptureservice.cts.Assertions.assertViewWithUnknownParentAppeared;
import static android.contentcaptureservice.cts.Assertions.assertVirtualViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertVirtualViewDisappeared;
import static android.contentcaptureservice.cts.Assertions.assertVirtualViewsDisappeared;
import static android.contentcaptureservice.cts.Helper.await;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.contentcaptureservice.cts.common.DoubleVisitor;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSession;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class CustomViewActivityTest extends
        AbstractContentCaptureIntegrationTest<CustomViewActivity> {

    private static final String TAG = CustomViewActivityTest.class.getSimpleName();

    private static final ActivityTestRule<CustomViewActivity> sActivityRule =
            new ActivityTestRule<>(CustomViewActivity.class, false, false);

    public CustomViewActivityTest() {
        super(CustomViewActivity.class);
    }

    @Override
    protected ActivityTestRule<CustomViewActivity> getActivityTestRule() {
        return sActivityRule;
    }

    /**
     * Baseline / sanity check test for the other tests.
     */
    @Test
    public void testLifecycle() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final CustomViewActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        activity.assertDefaultEvents(session);
    }

    /**
     * Tests when the view has virtual children but it doesn't return right away and calls
     * the session notification methods instead - this is wrong because the main view will be
     * notified last, but we cannot prevent the apps from doing so...
     */
    @Test
    public void testVirtualView_wrongWay() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        CustomViewActivity.setCustomViewDelegate((customView, structure) -> {
            Log.d(TAG, "delegate running on " + Thread.currentThread());
            final AutofillId customViewId = customView.getAutofillId();
            Log.d(TAG, "customViewId: " + customViewId);
            final ContentCaptureSession session = customView.getContentCaptureSession();

            final ViewStructure child = session.newVirtualViewStructure(customViewId, 1);
            child.setText("child");
            final AutofillId childId = child.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 1)).isEqualTo(childId);
            Log.d(TAG, "nofifying child appeared: " + childId);
            session.notifyViewAppeared(child);

            Log.d(TAG, "nofifying child disappeared: " + childId);
            session.notifyViewDisappeared(childId);
        });

        final CustomViewActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        assertRightActivity(session, session.id, activity);

        final View grandpa1 = (View) activity.mCustomView.getParent();
        final View grandpa2 = (View) grandpa1.getParent();
        final View decorView = activity.getDecorView();
        final AutofillId customViewId = activity.mCustomView.getAutofillId();
        Log.v(TAG, "assertJustInitialViewsAppeared(): grandpa1=" + grandpa1.getAutofillId()
                + ", grandpa2=" + grandpa2.getAutofillId() + ", decor="
                + decorView.getAutofillId() + "customView=" + customViewId);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events(" + events.size() + "): " + events);
        final int additionalEvents = 2;

        assertThat(events.size()).isAtLeast(CustomViewActivity.MIN_EVENTS + additionalEvents);

        // Assert just the relevant events
        assertSessionResumed(events, 0);
        assertViewTreeStarted(events, 1);
        assertDecorViewAppeared(events, 2, decorView);
        assertViewAppeared(events, 3, grandpa2, decorView.getAutofillId());
        assertViewAppeared(events, 4, grandpa1, grandpa2.getAutofillId());

        final ContentCaptureSession mainSession = activity.mCustomView.getContentCaptureSession();
        assertVirtualViewAppeared(events, 5, mainSession, customViewId, 1, "child");
        assertVirtualViewDisappeared(events, 6, customViewId, mainSession, 1);

        // This is the "wrong" part - the parent is notified last
        assertViewWithUnknownParentAppeared(events, 7, session.id, activity.mCustomView);

        assertViewTreeFinished(events, 8);
        assertSessionPaused(events, 9);

        activity.assertInitialViewsDisappeared(events, additionalEvents);
    }

    /**
     * Tests when the view has virtual children, but those children are leaf nodes.
     */
    @Test
    public void testVirtualView_oneLevel() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final CountDownLatch asyncLatch = setAsyncDelegate((customView, structure) -> {
            Log.d(TAG, "delegate running on " + Thread.currentThread());
            final AutofillId customViewId = customView.getAutofillId();
            Log.d(TAG, "customViewId: " + customViewId);
            final ContentCaptureSession session = customView.getContentCaptureSession();

            final ViewStructure child1 = session.newVirtualViewStructure(customViewId, 1);
            child1.setText("child1");
            final AutofillId child1Id = child1.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 1)).isEqualTo(child1Id);
            Log.d(TAG, "nofifying child1 appeared: " + child1Id);
            session.notifyViewAppeared(child1);
            final ViewStructure child2 = session.newVirtualViewStructure(customViewId, 2);
            child2.setText("child2");
            final AutofillId child2Id = child2.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 2)).isEqualTo(child2Id);
            Log.d(TAG, "nofifying child2 appeared: " + child2Id);
            session.notifyViewAppeared(child2);
            Log.d(TAG, "nofifying child2 disappeared: " + child2Id);
            session.notifyViewDisappeared(child2Id);
            Log.d(TAG, "nofifying child1 disappeared: " + child1Id);
            session.notifyViewDisappeared(child1Id);
        });

        final CustomViewActivity activity = launchActivity();
        watcher.waitFor(RESUMED);
        await(asyncLatch, "async onProvide");

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        assertRightActivity(session, session.id, activity);

        final int additionalEvents = 3;
        final List<ContentCaptureEvent> events = activity.assertInitialViewsAppeared(session,
                additionalEvents);

        final AutofillId customViewId = activity.mCustomView.getAutofillId();
        final ContentCaptureSession mainSession = activity.mCustomView.getContentCaptureSession();

        final int i = CustomViewActivity.MIN_EVENTS;

        assertVirtualViewAppeared(events, i, mainSession, customViewId, 1, "child1");
        assertVirtualViewAppeared(events, i + 1, mainSession, customViewId, 2, "child2");
        assertVirtualViewsDisappeared(events, i + 2, customViewId, mainSession, 2, 1);

        activity.assertInitialViewsDisappeared(events, additionalEvents);
        // TODO(b/122315042): assert views disappeared
    }

    /**
     * Tests when the view has virtual children, and some of those children have children too.
     */
    @Test
    public void testVirtualView_multipleLevels() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final CountDownLatch asyncLatch = setAsyncDelegate((customView, structure) -> {
            Log.d(TAG, "delegate running on " + Thread.currentThread());
            final AutofillId customViewId = customView.getAutofillId();
            Log.d(TAG, "customViewId: " + customViewId);
            final ContentCaptureSession session = customView.getContentCaptureSession();

            // Child 1
            final ViewStructure c1 = session.newVirtualViewStructure(customViewId, 1);
            c1.setText("c1");
            final AutofillId c1Id = c1.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 1)).isEqualTo(c1Id);
            Log.d(TAG, "nofifying c1 appeared: " + c1Id);
            session.notifyViewAppeared(c1);

            // Child 1, grandchildren 1
            final ViewStructure c1g1 = session.newVirtualViewStructure(c1Id, 11);
            c1g1.setText("c1g1");
            final AutofillId c1g1Id = c1g1.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 11)).isEqualTo(c1g1Id);
            Log.d(TAG, "nofifying c1g1 appeared: " + c1g1Id);
            session.notifyViewAppeared(c1g1);

            // Child 1, grandchildren 2
            final ViewStructure c1g2 = session.newVirtualViewStructure(c1Id, 12);
            c1g2.setText("c1g2");
            final AutofillId c1g2Id = c1g2.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 12)).isEqualTo(c1g2Id);
            Log.d(TAG, "nofifying c1g2 appeared: " + c1g2Id);
            session.notifyViewAppeared(c1g2);

            final ViewStructure c2 = session.newVirtualViewStructure(customViewId, 2);
            c2.setText("c2");
            final AutofillId c2Id = c2.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 2)).isEqualTo(c2Id);
            Log.d(TAG, "nofifying c2 appeared: " + c2Id);
            session.notifyViewAppeared(c2);

            // Child 2, grandchildren 1 - not removed
            final ViewStructure c2g1 = session.newVirtualViewStructure(c2Id, 21);
            c2g1.setText("c2g1");
            final AutofillId c2g1Id = c2g1.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 21)).isEqualTo(c2g1Id);
            Log.d(TAG, "nofifying c2g1 appeared: " + c2g1Id);
            session.notifyViewAppeared(c2g1);

            // Child 2, grandchildren 1, grandgrandchild1 (on purpose)
            final ViewStructure c2g1gg1 = session.newVirtualViewStructure(c2g1Id,
                    211);
            c2g1gg1.setText("c2g1gg1");
            final AutofillId c2g1ggt1Id = c2g1gg1.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 211)).isEqualTo(c2g1ggt1Id);
            Log.d(TAG, "nofifying c2g1gg1 appeared: " + c2g1ggt1Id);
            session.notifyViewAppeared(c2g1gg1);

            // Child 3 - not removed (on purpose)
            final ViewStructure c3 = session.newVirtualViewStructure(customViewId, 3);
            c3.setText("c3");
            final AutofillId c3Id = c3.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 3)).isEqualTo(c3Id);
            Log.d(TAG, "nofifying c3 appeared: " + c3Id);
            session.notifyViewAppeared(c3);

            // Remove children (although not all of them, on purpose)
            Log.d(TAG, "nofifying c2g1 disappeared: " + c2g1Id);
            session.notifyViewDisappeared(c2g1Id);
            Log.d(TAG, "nofifying c2 disappeared: " + c2Id);
            session.notifyViewDisappeared(c2Id);
            // Remove child1 grandchildren before and after
            Log.d(TAG, "nofifying c1g1 disappeared: " + c1g1Id);
            session.notifyViewDisappeared(c1g1Id);
            Log.d(TAG, "nofifying c1 disappeared: " + c1Id);
            session.notifyViewDisappeared(c1Id);
            Log.d(TAG, "nofifying c1g2 disappeared: " + c1g2Id);
            session.notifyViewDisappeared(c1g2Id);

        });

        final CustomViewActivity activity = launchActivity();
        watcher.waitFor(RESUMED);
        await(asyncLatch, "async onProvide");
        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        assertRightActivity(session, session.id, activity);

        final int additionalEvents = 7;
        final List<ContentCaptureEvent> events = activity.assertInitialViewsAppeared(session,
                additionalEvents);

        final AutofillId customViewId = activity.mCustomView.getAutofillId();
        final ContentCaptureSession mainSession = activity.mCustomView.getContentCaptureSession();

        final int i = CustomViewActivity.MIN_EVENTS;

        assertVirtualViewAppeared(events, i, mainSession, customViewId, 1, "c1");
        assertVirtualViewAppeared(events, i + 1, mainSession, customViewId, 11, "c1g1");
        assertVirtualViewAppeared(events, i + 2, mainSession, customViewId, 12, "c1g2");
        assertVirtualViewAppeared(events, i + 3, mainSession, customViewId, 2, "c2");
        assertVirtualViewAppeared(events, i + 4, mainSession, customViewId, 21, "c2g1");
        assertVirtualViewAppeared(events, i + 5, mainSession, customViewId, 211, "c2g1gg1");
        assertVirtualViewAppeared(events, i + 6, mainSession, customViewId, 3, "c3");
        assertVirtualViewsDisappeared(events, i + 7, customViewId, mainSession, 21, 2, 11, 1, 12);

        activity.assertInitialViewsDisappeared(events, additionalEvents);
        // TODO(b/122315042): assert other views disappeared
    }

    // TODO(b/123540602): add tests for multiple sessions

    @Test
    public void testVirtualView_batchDisappear() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final CountDownLatch asyncLatch = setAsyncDelegate((customView, structure) -> {
            Log.d(TAG, "delegate running on " + Thread.currentThread());
            final AutofillId customViewId = customView.getAutofillId();
            Log.d(TAG, "customViewId: " + customViewId);
            final ContentCaptureSession session = customView.getContentCaptureSession();

            final ViewStructure child1 = session.newVirtualViewStructure(customViewId, 1);
            child1.setText("child1");
            final AutofillId child1Id = child1.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 1)).isEqualTo(child1Id);
            Log.d(TAG, "nofifying child1 appeared: " + child1Id);
            session.notifyViewAppeared(child1);

            final ViewStructure child2 = session.newVirtualViewStructure(customViewId, 2);
            child2.setText("child2");
            final AutofillId child2Id = child2.getAutofillId();
            assertThat(session.newAutofillId(customViewId, 2)).isEqualTo(child2Id);
            Log.d(TAG, "nofifying child2 appeared: " + child2Id);
            session.notifyViewAppeared(child2);

            final long[] childrenIds = {2, 1};
            Log.d(TAG, "nofifying both children disappeared: " + Arrays.toString(childrenIds));
            session.notifyViewsDisappeared(customViewId, childrenIds);
        });

        final CustomViewActivity activity = launchActivity();
        watcher.waitFor(RESUMED);
        await(asyncLatch, "async onProvide");
        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        assertRightActivity(session, session.id, activity);


        final int additionalEvents = 3;
        final List<ContentCaptureEvent> events = activity.assertInitialViewsAppeared(session,
                additionalEvents);

        final AutofillId customViewId = activity.mCustomView.getAutofillId();
        final ContentCaptureSession mainSession = activity.mCustomView.getContentCaptureSession();

        final int i = CustomViewActivity.MIN_EVENTS;

        assertVirtualViewAppeared(events, i, mainSession, customViewId, 1, "child1");
        assertVirtualViewAppeared(events, i + 1, mainSession, customViewId, 2, "child2");
        assertVirtualViewsDisappeared(events, i + 2, customViewId, mainSession, 2, 1);

        activity.assertInitialViewsDisappeared(events, additionalEvents);
        // TODO(b/122315042): assert other views disappeared
    }

    /**
     * Sets a delegate that will generate the events asynchronously,
     * after {@code onProvideContentCaptureStructure()} returns.
     */
    private CountDownLatch setAsyncDelegate(
            @NonNull DoubleVisitor<CustomView, ViewStructure> delegate) {
        final CountDownLatch asyncLatch = new CountDownLatch(1);
        CustomViewActivity.setCustomViewDelegate(
                (customView, structure) -> new Handler(Looper.getMainLooper())
                        .post(() -> {
                            delegate.visit(customView, structure);
                            asyncLatch.countDown();
                        }));
        return asyncLatch;
    }
}
