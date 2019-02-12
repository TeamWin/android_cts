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
import static android.contentcaptureservice.cts.Assertions.assertDecorViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertLifecycleOrder;
import static android.contentcaptureservice.cts.Assertions.assertMainSessionContext;
import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewDisappeared;
import static android.contentcaptureservice.cts.Assertions.assertViewHierarchyFinished;
import static android.contentcaptureservice.cts.Assertions.assertViewHierarchyStarted;
import static android.contentcaptureservice.cts.Assertions.assertViewsDisappeared;
import static android.contentcaptureservice.cts.Assertions.assertViewsOptionallyDisappeared;
import static android.contentcaptureservice.cts.Helper.newImportantView;
import static android.contentcaptureservice.cts.Helper.sContext;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.LocusId;
import android.contentcaptureservice.cts.CtsContentCaptureService.DisconnectListener;
import android.contentcaptureservice.cts.CtsContentCaptureService.ServiceWatcher;
import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.contentcaptureservice.cts.common.ActivityLauncher;
import android.net.Uri;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureManager;
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.DeviceConfigStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull(reason = "BlankWithTitleActivityTest is enough")
public class ChildlessActivityTest
        extends AbstractContentCaptureIntegrationTest<ChildlessActivity> {

    private static final String TAG = ChildlessActivityTest.class.getSimpleName();

    private static final ActivityTestRule<ChildlessActivity> sActivityRule = new ActivityTestRule<>(
            ChildlessActivity.class, false, false);

    private static final DeviceConfigStateManager sDeviceConfigManager =
            new DeviceConfigStateManager(sContext, DeviceConfig.NAMESPACE_CONTENT_CAPTURE,
            ContentCaptureManager.DEVICE_CONFIG_PROPERTY_SERVICE_EXPLICITLY_ENABLED);

    private static final RuleChain sMyRules = RuleChain
            .outerRule(new DeviceConfigStateChangerRule(sDeviceConfigManager, "true"))
            .around(sActivityRule);

    public ChildlessActivityTest() {
        super(ChildlessActivity.class);
    }

    @Override
    protected ActivityTestRule<ChildlessActivity> getActivityTestRule() {
        return sActivityRule;
    }

    @Override
    protected TestRule getMainTestRule() {
        return sMyRules;
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
        Log.v(TAG, "session id: " + session.id);

        activity.assertDefaultEvents(session);
    }

    @Test
    public void testGetContentCapture_disabledWhenNoService() throws Exception {
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        assertThat(activity.getContentCaptureManager().isContentCaptureEnabled()).isFalse();

        activity.finish();
        watcher.waitFor(DESTROYED);
    }

    @Test
    public void testGetContentCapture_enabledWhenNoService() throws Exception {
        enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        assertThat(activity.getContentCaptureManager().isContentCaptureEnabled()).isTrue();

        activity.finish();
        watcher.waitFor(DESTROYED);

    }

    @Test
    public void testLaunchAnotherActivity() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher1 = startWatcher();

        // Launch and finish 1st activity
        final ChildlessActivity activity1 = launchActivity();
        watcher1.waitFor(RESUMED);
        activity1.finish();
        watcher1.waitFor(DESTROYED);

        // Launch and finish 2nd activity
        final ActivityLauncher<LoginActivity> anotherActivityLauncher = new ActivityLauncher<>(
                sContext, mActivitiesWatcher, LoginActivity.class);
        final ActivityWatcher watcher2 = anotherActivityLauncher.getWatcher();
        final LoginActivity activity2 = anotherActivityLauncher.launchActivity();
        watcher2.waitFor(RESUMED);
        activity2.finish();
        watcher2.waitFor(DESTROYED);

        // Assert the sessions
        final List<ContentCaptureSessionId> sessionIds = service.getAllSessionIds();
        assertThat(sessionIds).hasSize(2);
        final ContentCaptureSessionId sessionId1 = sessionIds.get(0);
        Log.v(TAG, "session id1: " + sessionId1);
        final ContentCaptureSessionId sessionId2 = sessionIds.get(1);
        Log.v(TAG, "session id2: " + sessionId2);

        final Session session1 = service.getFinishedSession(sessionId1);
        activity1.assertDefaultEvents(session1);

        final Session session2 = service.getFinishedSession(sessionId2);
        activity2.assertDefaultEvents(session2);
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
        Log.v(TAG, "events(" + events.size() + "): " + events);
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
            final TextView text = newImportantView(activity, "Important I am");
            rootView.addView(text);
            childRef.set(text);
        });

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        // Remove view
        final LinearLayout rootView = activity.getRootView();
        final TextView child = childRef.get();
        activity.syncRunOnUiThread(() -> rootView.removeView(child));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events(" + events.size() + "): " + events);

        final AutofillId rootId = rootView.getAutofillId();

        final View grandpa1 = activity.getGrandParent();
        final View grandpa2 = activity.getGrandGrandParent();
        final View decorView = activity.getDecorView();

        // Assert just the relevant events
        assertThat(events.size()).isAtLeast(8);
        assertViewHierarchyStarted(events, 0);
        assertDecorViewAppeared(events, 1, decorView);
        assertViewAppeared(events, 2, grandpa2, decorView.getAutofillId());
        assertViewAppeared(events, 3, grandpa1, grandpa2.getAutofillId());
        assertViewAppeared(events, 4, sessionId, rootView, grandpa1.getAutofillId());
        assertViewAppeared(events, 5, sessionId, child, rootId);
        assertViewHierarchyFinished(events, 6);
        assertViewDisappeared(events, 7, child.getAutofillId());
    }

    @Test
    public void testAddImportantChildAfterSessionStarted() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        // Add View
        final LinearLayout rootView = activity.getRootView();
        final TextView child = newImportantView(activity, "Important I am");
        activity.runOnUiThread(() -> rootView.addView(child));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        final ContentCaptureSessionId sessionId = session.id;
        Log.v(TAG, "session id: " + sessionId);

        assertRightActivity(session, sessionId, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events(" + events.size() + "): " + events);

        final View grandpa = activity.getGrandParent();

        // Assert just the relevant events

        // NOTE: there's no TYPE_INITIAL_VIEW_HIERARCHY_XXX events because there was no important
        // view initially
        assertThat(events.size()).isAtLeast(2);
        // TODO(b/122959591): figure out the child is coming first
        assertViewAppeared(events, 0, sessionId, child, rootView.getAutofillId());
        assertViewAppeared(events, 1, sessionId, rootView, grandpa.getAutofillId());
    }

    @Test
    public void testAddAndRemoveImportantChildOnDifferentSession() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        final LinearLayout rootView = activity.getRootView();
        final View grandpa = activity.getGrandParent();

        final ContentCaptureSession mainSession = rootView.getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        final ContentCaptureSession childSession = mainSession
                .createContentCaptureSession(newContentCaptureContextBuilder("child")
                        .build());
        final ContentCaptureSessionId childSessionId = childSession.getContentCaptureSessionId();
        Log.v(TAG, "child session id: " + childSessionId);

        final TextView child = newImportantView(activity, "Important I am");
        final AutofillId childId = child.getAutofillId();
        Log.v(TAG, "childId: " + childId);
        child.setContentCaptureSession(childSession);
        activity.runOnUiThread(() -> rootView.addView(child));

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> sessionIds = service.getAllSessionIds();
        assertThat(sessionIds).containsExactly(mainSessionId, childSessionId).inOrder();


        // Assert sessions
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);
        final List<ContentCaptureEvent> mainEvents = mainTestSession.getEvents();
        Log.v(TAG, "mainEvents(" + mainEvents.size() + "): " + mainEvents);

        // NOTE: there's no TYPE_INITIAL_VIEW_HIERARCHY_XXX events because there was no important
        // view initially
        assertThat(mainEvents.size()).isAtLeast(1);
        assertViewAppeared(mainEvents, 0, mainSessionId, rootView, grandpa.getAutofillId());

        final Session childTestSession = service.getFinishedSession(childSessionId);
        assertChildSessionContext(childTestSession, "child");
        final List<ContentCaptureEvent> childEvents = childTestSession.getEvents();
        Log.v(TAG, "childEvents(" + childEvents.size() + "): " + childEvents);
        final int minEvents = 1;
        assertThat(mainEvents.size()).isAtLeast(minEvents);
        assertViewAppeared(childEvents, 0, childSessionId, child, rootView.getAutofillId());
        assertViewsOptionallyDisappeared(childEvents, minEvents, childId);
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
        final ContentCaptureContext context1 = newContentCaptureContextBuilder("session1")
                .build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(TAG, "child session id 1: " + childSessionId1);

        // Create 2nd session
        final ContentCaptureContext context2 = newContentCaptureContextBuilder("session2")
                .build();
        final ContentCaptureSession childSession2 = mainSession
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(TAG, "child session id 2: " + childSessionId2);

        // Close 1st session before opening 3rd
        childSession1.close();

        // Create 3nd session...
        final ContentCaptureContext context3 = newContentCaptureContextBuilder("session3")
                .build();
        final ContentCaptureSession childSession3 = mainSession
                .createContentCaptureSession(context3);
        final ContentCaptureSessionId childSessionId3 = childSession3.getContentCaptureSessionId();
        Log.v(TAG, "child session id 3: " + childSessionId3);

        // ...and close it right away
        childSession3.close();

        // Create 4nd session
        final ContentCaptureContext context4 = newContentCaptureContextBuilder("session4")
                .build();
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
        assertChildSessionContext(childTestSession1, "session1");

        final Session childTestSession2 = service.getFinishedSession(childSessionId2);
        assertChildSessionContext(childTestSession2, "session2");

        final Session childTestSession3 = service.getFinishedSession(childSessionId3);
        assertChildSessionContext(childTestSession3, "session3");

        final Session childTestSession4 = service.getFinishedSession(childSessionId4);
        assertChildSessionContext(childTestSession4, "session4");

        // Gets all events first so they're all logged before the assertions
        final List<ContentCaptureEvent> mainEvents = mainTestSession.getEvents();
        final List<ContentCaptureEvent> events1 = childTestSession1.getEvents();
        final List<ContentCaptureEvent> events2 = childTestSession2.getEvents();
        final List<ContentCaptureEvent> events3 = childTestSession3.getEvents();
        final List<ContentCaptureEvent> events4 = childTestSession4.getEvents();
        Log.v(TAG, "mainEvents(" + mainEvents.size() + "): " + mainEvents);
        Log.v(TAG, "events1(" + events1.size() + "): " + events1);
        Log.v(TAG, "events2(" + events2.size() + "): " + events2);
        Log.v(TAG, "events3(" + events3.size() + "): " + events3);
        Log.v(TAG, "events4(" + events4.size() + "): " + events4);

        assertThat(mainEvents).isEmpty();
        assertThat(events1).isEmpty();
        assertThat(events2).isEmpty();
        assertThat(events3).isEmpty();
        assertThat(events4).isEmpty();

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
                .createContentCaptureSession(
                        newContentCaptureContextBuilder("child_session").build());
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
        // TODO(b/123540067): ideally it should be empty, but has intermediate parents stuff...
        // assertThat(mainTestSession.getEvents()).isEmpty();

        // Assert child session
        final Session childTestSession = service.getFinishedSession(childSessionId);
        assertChildSessionContext(childTestSession, "child_session");
        final List<ContentCaptureEvent> childEvents = childTestSession.getEvents();
        assertThat(childEvents.size()).isAtLeast(1);
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(childEvents, 0, child, rootId);

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
        final ContentCaptureContext context1 = newContentCaptureContextBuilder("session1")
                .build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(TAG, "child session id 1: " + childSessionId1);

        // Session 1, child 1
        final TextView s1c1 = addChild(activity, childSession1, "s1c1");

        // Create 2nd session
        final ContentCaptureContext context2 = newContentCaptureContextBuilder("session2")
                .build();
        final ContentCaptureSession childSession2 = mainSession
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(TAG, "child session id 2: " + childSessionId2);

        final TextView s2c1 = addChild(activity, childSession2, "s2c1");
        final TextView s2c2 = addChild(activity, childSession2, "s2c2");

        // Close 1st session before opening 3rd
        waitAndClose(childSession1);

        // Create 3nd session...
        final ContentCaptureContext context3 = newContentCaptureContextBuilder("session3")
                .build();
        final ContentCaptureSession childSession3 = mainSession
                .createContentCaptureSession(context3);
        final ContentCaptureSessionId childSessionId3 = childSession3.getContentCaptureSessionId();
        Log.v(TAG, "child session id 3: " + childSessionId3);

        final TextView s3c1 = addChild(activity, childSession3, "s3c1");
        final TextView s3c2 = addChild(activity, childSession3, "s3c2");
        waitAndRemoveViews(activity, s3c1);
        final TextView s3c3 = addChild(activity, childSession3, "s3c3");

        // ...and close it right away
        waitAndClose(childSession3);

        // Create 4nd session
        final ContentCaptureContext context4 = newContentCaptureContextBuilder("session4")
                .build();
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
        final List<ContentCaptureEvent> mainEvents = mainTestSession.getEvents();
        Log.v(TAG, "main session events(" + mainEvents.size() + "): " + mainEvents);

        // Gets all events first so they're all logged before the assertions
        final Session childTestSession1 = service.getFinishedSession(childSessionId1);
        assertChildSessionContext(childTestSession1, "session1");
        final List<ContentCaptureEvent> events1 = childTestSession1.getEvents();
        Log.v(TAG, "events1(" + events1.size() + "): " + events1);

        final Session childTestSession2 = service.getFinishedSession(childSessionId2);
        final List<ContentCaptureEvent> events2 = childTestSession2.getEvents();
        assertChildSessionContext(childTestSession2, "session2");
        Log.v(TAG, "events2(" + events2.size() + "): " + events2);
        final Session childTestSession3 = service.getFinishedSession(childSessionId3);
        assertChildSessionContext(childTestSession3, "session3");
        List<ContentCaptureEvent> events3 = childTestSession3.getEvents();
        Log.v(TAG, "events3(" + events3.size() + "): " + events3);

        // TODO(b/123540067): ideally should be empty, but it has 2 grandparents
        assertThat(mainEvents).hasSize(2);

        assertThat(events1.size()).isAtLeast(1);
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(events1, 0, s1c1, rootId);

        assertThat(events2.size()).isAtLeast(2);
        assertViewAppeared(events2, 0, s2c1, rootId);
        assertViewAppeared(events2, 1, s2c2, rootId);

        assertThat(events3.size()).isAtLeast(4);
        assertViewAppeared(events3, 0, s3c1, rootId);
        assertViewAppeared(events3, 1, s3c2, rootId);
        assertViewDisappeared(events3, 2, s3c1.getAutofillId());
        assertViewAppeared(events3, 3, s3c3, rootId);

        final Session childTestSession4 = service.getFinishedSession(childSessionId4);
        assertChildSessionContext(childTestSession4, "session4");
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
        final ContentCaptureContext childContext = newContentCaptureContextBuilder("child")
                .build();
        final ContentCaptureSession childSession = mainSession
                .createContentCaptureSession(childContext);
        final ContentCaptureSessionId childSessionId = childSession.getContentCaptureSessionId();
        Log.v(TAG, "child session id: " + childSessionId);

        // Create grand child session
        final ContentCaptureContext grandChild = newContentCaptureContextBuilder("grandChild")
                .build();
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
        assertChildSessionContext(childTestSession, "child");
        assertThat(childTestSession.getEvents()).isEmpty();

        final Session grandChildTestSession = service.getFinishedSession(grandChildSessionId);
        assertChildSessionContext(grandChildTestSession, "grandChild");
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
        final ContentCaptureContext context1 = newContentCaptureContextBuilder("session1")
                .build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(TAG, "child session id 1: " + childSessionId1);

        // Create 2nd session
        final ContentCaptureContext context2 = newContentCaptureContextBuilder("session2")
                .build();
        final ContentCaptureSession childSession2 = childSession1
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(TAG, "child session id 2: " + childSessionId2);

        // Close 1st session before opening 3rd
        childSession1.close();

        // Create 3nd session...
        final ContentCaptureContext context3 = newContentCaptureContextBuilder("session3")
                .build();
        final ContentCaptureSession childSession3 = mainSession
                .createContentCaptureSession(context3);
        final ContentCaptureSessionId childSessionId3 = childSession3.getContentCaptureSessionId();
        Log.v(TAG, "child session id 3: " + childSessionId3);

        // ...and close it right away
        childSession3.close();

        // Create 4nd session
        final ContentCaptureContext context4 = newContentCaptureContextBuilder("session4")
                .build();
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
        assertChildSessionContext(childTestSession1, "session1");
        assertThat(childTestSession1.getEvents()).isEmpty();

        final Session childTestSession2 = service.getFinishedSession(childSessionId2);
        assertChildSessionContext(childTestSession2, "session2");
        assertThat(childTestSession2.getEvents()).isEmpty();

        final Session childTestSession3 = service.getFinishedSession(childSessionId3);
        assertChildSessionContext(childTestSession3, "session3");
        assertThat(childTestSession3.getEvents()).isEmpty();

        final Session childTestSession4 = service.getFinishedSession(childSessionId4);
        assertChildSessionContext(childTestSession4, "session4");
        assertThat(childTestSession4.getEvents()).isEmpty();

        // Assert lifecycle methods were called in the right order
        assertLifecycleOrder(1, mainTestSession,   CREATION);
        assertLifecycleOrder(2, childTestSession1, CREATION);
        assertLifecycleOrder(3, childTestSession2, CREATION);
        assertLifecycleOrder(4, childTestSession2, DESTRUCTION);
        assertLifecycleOrder(5, childTestSession1, DESTRUCTION);
        assertLifecycleOrder(6, childTestSession3, CREATION);
        assertLifecycleOrder(7, childTestSession3, DESTRUCTION);
        assertLifecycleOrder(8, childTestSession4, CREATION);
        assertLifecycleOrder(9, childTestSession4, DESTRUCTION);
        assertLifecycleOrder(10, mainTestSession,  DESTRUCTION);
    }

    /**
     * Tests scenario where views from different session are removed in sequence - they should not
     * have been batched.
     */
    @Test
    public void testRemoveChildrenFromDifferentSessions() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final ChildlessActivity activity = launchActivity();
        watcher.waitFor(RESUMED);
        final ContentCaptureSession mainSession = activity.getRootView().getContentCaptureSession();
        final ContentCaptureSessionId mainSessionId = mainSession.getContentCaptureSessionId();
        Log.v(TAG, "main session id: " + mainSessionId);

        // Create 1st session
        final ContentCaptureContext context1 = newContentCaptureContextBuilder("session1")
                .build();
        final ContentCaptureSession childSession1 = mainSession
                .createContentCaptureSession(context1);
        final ContentCaptureSessionId childSessionId1 = childSession1.getContentCaptureSessionId();
        Log.v(TAG, "child session id 1: " + childSessionId1);

        // Session 1, child 1
        final TextView s1c1 = addChild(activity, childSession1, "s1c1");
        final AutofillId s1c1Id = s1c1.getAutofillId();
        Log.v(TAG, "childrens from session1: " + s1c1Id);

        // Create 2nd session
        final ContentCaptureContext context2 = newContentCaptureContextBuilder("session2")
                .build();
        final ContentCaptureSession childSession2 = mainSession
                .createContentCaptureSession(context2);
        final ContentCaptureSessionId childSessionId2 = childSession2.getContentCaptureSessionId();
        Log.v(TAG, "child session id 2: " + childSessionId2);

        final TextView s2c1 = addChild(activity, childSession2, "s2c1");
        final AutofillId s2c1Id = s2c1.getAutofillId();
        final TextView s2c2 = addChild(activity, childSession2, "s2c2");
        final AutofillId s2c2Id = s2c2.getAutofillId();
        Log.v(TAG, "childrens from session2: " + s2c1Id + ", " + s2c2Id);

        // Remove views - should generate one batch event for s2 and one single event for s1
        waitAndRemoveViews(activity, s2c1, s2c2, s1c1);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final List<ContentCaptureSessionId> receivedIds = service.getAllSessionIds();
        assertThat(receivedIds).containsExactly(
                mainSessionId,
                childSessionId1,
                childSessionId2)
            .inOrder();

        // Assert main sessions info
        final Session mainTestSession = service.getFinishedSession(mainSessionId);
        assertMainSessionContext(mainTestSession, activity);
        final List<ContentCaptureEvent> mainEvents = mainTestSession.getEvents();
        Log.v(TAG, "mainEvents(" + mainEvents.size() + "): " + mainEvents);

        // Logs events before asserting
        final Session childTestSession1 = service.getFinishedSession(childSessionId1);
        assertChildSessionContext(childTestSession1, "session1");
        final List<ContentCaptureEvent> events1 = childTestSession1.getEvents();
        Log.v(TAG, "events1(" + events1.size() + "): " + events1);
        final Session childTestSession2 = service.getFinishedSession(childSessionId2);
        final List<ContentCaptureEvent> events2 = childTestSession2.getEvents();
        assertChildSessionContext(childTestSession2, "session2");
        Log.v(TAG, "events2(" + events2.size() + "): " + events2);

        // Assert children
        assertThat(events1.size()).isAtLeast(2);
        final AutofillId rootId = activity.getRootView().getAutofillId();
        assertViewAppeared(events1, 0, s1c1, rootId);
        assertViewDisappeared(events1, 1, s1c1Id);

        assertThat(events2.size()).isAtLeast(3);
        assertViewAppeared(events2, 0, s2c1, rootId);
        assertViewAppeared(events2, 1, s2c2, rootId);
        assertViewsDisappeared(events2, 2, s2c1Id, s2c2Id);
    }

    /* TODO(b/119638528): add more scenarios for nested sessions, such as:
     * - add views to the children sessions
     * - s1 -> s2 -> s3 and main -> s4; close(s1) then generate events on view from s3
     * - s1 -> s2 -> s3 and main -> s4; close(s2) then generate events on view from s3
     * - s1 -> s2 and s3->s4 -> s4
     * - etc
     */

    private enum DisabledReason {
        BY_API,
        BY_SETTINGS,
        BY_DEVICE_CONFIG
    }

    private void setFeatureEnabled(@NonNull ContentCaptureManager mgr,
            @NonNull DisabledReason reason, boolean enabled) {
        switch (reason) {
            case BY_API:
                mgr.setContentCaptureFeatureEnabled(enabled);
                break;
            case BY_SETTINGS:
                setFeatureEnabled(Boolean.toString(enabled));
                break;
            case BY_DEVICE_CONFIG:
                setFeatureEnabledByDeviceConfig(Boolean.toString(enabled));
                break;
            default:
                throw new IllegalArgumentException("invalid reason: " + reason);
        }
    }
    @Test
    public void testIsContentCaptureFeatureEnabled_notService() throws Exception {
        final ContentCaptureManager mgr = getContentCaptureManagerHack();
        assertThrows(SecurityException.class,  () -> mgr.isContentCaptureFeatureEnabled());
    }

    @Test
    public void testSetContentCaptureFeatureEnabled_disabledBySettings() throws Exception {
        setContentCaptureFeatureEnabledTest_disabled(DisabledReason.BY_SETTINGS);
    }

    private void setContentCaptureFeatureEnabledTest_disabled(@NonNull DisabledReason reason)
            throws Exception {
        final ContentCaptureManager mgr = getContentCaptureManagerHack();

        final CtsContentCaptureService service = enableService();
        assertThat(mgr.isContentCaptureFeatureEnabled()).isTrue();
        final DisconnectListener disconnectedListener = service.setOnDisconnectListener();

        setFeatureEnabled(mgr, reason, /* enabled= */ false);

        disconnectedListener.waitForOnDisconnected();
        assertThat(mgr.isContentCaptureFeatureEnabled()).isFalse();
        assertThat(mgr.isContentCaptureEnabled()).isFalse();

        final ActivityWatcher watcher = startWatcher();
        final ChildlessActivity activity = launchActivity();

        watcher.waitFor(RESUMED);
        activity.finish();
        watcher.waitFor(DESTROYED);

        assertThat(service.getAllSessionIds()).isEmpty();
    }

    @Test
    public void testSetContentCaptureFeatureEnabled_disabledThenReEnabledBySettings()
            throws Exception {
        setContentCaptureFeatureEnabledTest_disabledThenReEnabled(DisabledReason.BY_SETTINGS);
    }

    private void setContentCaptureFeatureEnabledTest_disabledThenReEnabled(
            @NonNull DisabledReason reason) throws Exception {
        final ContentCaptureManager mgr = getContentCaptureManagerHack();

        final CtsContentCaptureService service1 = enableService();
        assertThat(mgr.isContentCaptureFeatureEnabled()).isTrue();
        final DisconnectListener disconnectedListener = service1.setOnDisconnectListener();

        setFeatureEnabled(mgr, reason, /* enabled= */ false);
        disconnectedListener.waitForOnDisconnected();

        assertThat(mgr.isContentCaptureFeatureEnabled()).isFalse();
        assertThat(mgr.isContentCaptureEnabled()).isFalse();

        // Launch and finish 1st activity while it's disabled
        final ActivityWatcher watcher1 = startWatcher();
        final ChildlessActivity activity1 = launchActivity();
        watcher1.waitFor(RESUMED);
        activity1.finish();
        watcher1.waitFor(DESTROYED);

        // Re-enable feature
        final ServiceWatcher reconnectionWatcher = CtsContentCaptureService.setServiceWatcher();
        setFeatureEnabled(mgr, reason, /* enabled= */ true);
        final CtsContentCaptureService service2 = reconnectionWatcher.waitOnCreate();
        assertThat(mgr.isContentCaptureFeatureEnabled()).isTrue();

        // Launch and finish 2nd activity while it's enabled
        final ActivityLauncher<CustomViewActivity> launcher2 = new ActivityLauncher<>(
                sContext, mActivitiesWatcher, CustomViewActivity.class);
        final ActivityWatcher watcher2 = launcher2.getWatcher();
        final CustomViewActivity activity2 = launcher2.launchActivity();
        watcher2.waitFor(RESUMED);
        activity2.finish();
        watcher2.waitFor(DESTROYED);

        assertThat(service1.getAllSessionIds()).isEmpty();
        final Session session = service2.getOnlyFinishedSession();
        activity2.assertDefaultEvents(session);
    }

    @Test
    public void testSetContentCaptureFeatureEnabled_notService() throws Exception {
        final ContentCaptureManager mgr = getContentCaptureManagerHack();
        assertThrows(SecurityException.class,  () -> mgr.setContentCaptureFeatureEnabled(true));
    }

    @Test
    public void testSetContentCaptureFeatureEnabled_disabledByApi() throws Exception {
        setContentCaptureFeatureEnabledTest_disabled(DisabledReason.BY_API);
    }

    @Test
    public void testSetContentCaptureFeatureEnabled_disabledThenReEnabledByApi()
            throws Exception {
        setContentCaptureFeatureEnabledTest_disabledThenReEnabled(DisabledReason.BY_API);
    }

    @Test
    public void testSetContentCaptureFeatureEnabled_disabledByDeviceConfig() throws Exception {
        setContentCaptureFeatureEnabledTest_disabled(DisabledReason.BY_DEVICE_CONFIG);
        // Reset service, otherwise it will reconnect when the deviceConfig value is reset
        // on cleanup, which will cause the test to fail
        Helper.resetService();
    }

    @Test
    public void testSetContentCaptureFeatureEnabled_disabledThenReEnabledByDeviceConfig()
            throws Exception {
        setContentCaptureFeatureEnabledTest_disabledThenReEnabled(DisabledReason.BY_DEVICE_CONFIG);
        // Reset service, otherwise it will reconnect when the deviceConfig value is reset
        // on cleanup, which will cause the test to fail
        Helper.resetService();
    }

    // TODO(b/123406031): add tests that mix feature_enabled with user_restriction_enabled (and
    // make sure mgr.isContentCaptureFeatureEnabled() returns only the state of the 1st)

    private TextView addChild(@NonNull ChildlessActivity activity,
            @NonNull ContentCaptureSession session, @NonNull String text) {
        final TextView child = newImportantView(activity, text);
        child.setContentCaptureSession(session);
        Log.i(TAG, "adding " + child.getAutofillId() + " on session "
                + session.getContentCaptureSessionId());
        activity.runOnUiThread(() -> activity.getRootView().addView(child));
        return child;
    }

    // TODO(b/123024698): these method are used in cases where we cannot close a session because we
    // would miss intermediate events, so we need to sleep. This is a hack (it's slow and flaky):
    // ideally we should block and wait until the service receives the event, but right now
    // we don't get the service events until after the activity is finished, so we cannot do that...
    private void waitAndClose(@NonNull ContentCaptureSession session) {
        Log.d(TAG, "sleeping for 1s before closing " + session.getContentCaptureSessionId());
        SystemClock.sleep(1_000);
        session.close();
    }

    private void waitAndRemoveViews(@NonNull ChildlessActivity activity, @NonNull View... views) {
        Log.d(TAG, "sleeping for 1s before removing " + Arrays.toString(views));
        SystemClock.sleep(1_000);
        activity.syncRunOnUiThread(() -> {
            for (View view : views) {
                activity.getRootView().removeView(view);
            }
        });
    }

    // TODO(b/120494182): temporary hack to get the manager, which currently is only available on
    // Activity contexts (and would be null from sContext)
    @NonNull
    private ContentCaptureManager getContentCaptureManagerHack() throws InterruptedException {
        final AtomicReference<ContentCaptureManager> ref = new AtomicReference<>();
        LoginActivity.onRootView(
                (activity, rootView) -> ref.set(activity.getContentCaptureManager()));

        final ActivityLauncher<LoginActivity> launcher = new ActivityLauncher<>(
                sContext, mActivitiesWatcher, LoginActivity.class);
        final ActivityWatcher watcher = launcher.getWatcher();
        final LoginActivity activity = launcher.launchActivity();
        watcher.waitFor(RESUMED);
        activity.finish();
        watcher.waitFor(DESTROYED);

        final ContentCaptureManager mgr = ref.get();
        assertThat(mgr).isNotNull();

        return mgr;
    }

    private void setFeatureEnabledByDeviceConfig(@Nullable String value) {
        Log.d(TAG, "setFeatureEnabledByDeviceConfig(): " + value);

        sDeviceConfigManager.set(value);
    }

    @NonNull
    private ContentCaptureContext.Builder newContentCaptureContextBuilder(@NonNull String id) {
        return new ContentCaptureContext.Builder(new LocusId(Uri.parse(id)));
    }
}
