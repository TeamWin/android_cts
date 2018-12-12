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

import static android.contentcaptureservice.cts.Assertions.assertLifecycleEvent;
import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;
import static android.contentcaptureservice.cts.Assertions.assertViewDisappeared;
import static android.contentcaptureservice.cts.Helper.TAG;
import static android.contentcaptureservice.cts.Helper.enableService;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_PAUSED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_RESUMED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_STARTED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_STOPPED;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsSmartSuggestionsService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;

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

    // TODO(b/119638958): rename once we add moar tests
    @Test
    public void testIt() throws Exception {
        enableService();

        // TODO(b/119638958): move to super class
        final ActivityWatcher watcher = mActivitiesWatcher.watch(LoginActivity.class);

        final LoginActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final CtsSmartSuggestionsService service = CtsSmartSuggestionsService.getInstance();
        try {
            final Session session = service.getFinishedSession(LoginActivity.class);

            assertRightActivity(session, activity);

            final List<ContentCaptureEvent> events = session.getEvents();
            Log.v(TAG, "events: " + events);
            // TODO(b/119638958): ideally it should be 14 so it reflects just the views defined
            // in the layout - right now it's generating events for 2 intermediate parents
            // (android:action_mode_bar_stub and android:content), we should try to create an
            // activity without them

            final AutofillId rootId = activity.mRootView.getAutofillId();

            assertThat(events).hasSize(18);
            assertLifecycleEvent(events.get(0), TYPE_ACTIVITY_STARTED);
            assertLifecycleEvent(events.get(1), TYPE_ACTIVITY_RESUMED);
            assertViewAppeared(events.get(2), activity.mUsernameLabel, rootId);
            assertViewAppeared(events.get(3), activity.mUsername, rootId);
            assertViewAppeared(events.get(4), activity.mPasswordLabel, rootId);
            assertViewAppeared(events.get(5), activity.mPassword, rootId);
            // TODO(b/119638958): get rid of those intermediated parents
            final View grandpa1 = (View) activity.mRootView.getParent();
            final View grandpa2 = (View) grandpa1.getParent();
            final View decorView = (View) grandpa2.getParent();

            assertViewAppeared(events.get(6), activity.mRootView, grandpa1.getAutofillId());
            assertViewAppeared(events.get(7), grandpa1, grandpa2.getAutofillId());
            assertViewAppeared(events.get(8), grandpa2, decorView.getAutofillId());

            // TODO(b/119638958): VIEW_DISAPPEARED events should be send before the activity
            // stopped - if we don't deprecate the latter, we should change the manager to make sure
            // they're send in that order (or dropped)
            assertLifecycleEvent(events.get(9), TYPE_ACTIVITY_PAUSED);
            assertLifecycleEvent(events.get(10), TYPE_ACTIVITY_STOPPED);

            assertViewDisappeared(events.get(11), grandpa2.getAutofillId());
            assertViewDisappeared(events.get(12), grandpa1.getAutofillId());
            assertViewDisappeared(events.get(13), activity.mRootView.getAutofillId());
            assertViewDisappeared(events.get(14), activity.mUsernameLabel.getAutofillId());
            assertViewDisappeared(events.get(15), activity.mUsername.getAutofillId());
            assertViewDisappeared(events.get(16), activity.mPasswordLabel.getAutofillId());
            assertViewDisappeared(events.get(17), activity.mPassword.getAutofillId());
        } finally {
            // TODO(b/119638958): move to @Rule SafeCleaner
            CtsSmartSuggestionsService.assertNoExceptions();
        }
    }
}
