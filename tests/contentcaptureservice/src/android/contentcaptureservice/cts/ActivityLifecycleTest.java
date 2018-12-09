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

import static android.contentcaptureservice.cts.Helper.TAG;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_PAUSED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_RESUMED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_STARTED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_ACTIVITY_STOPPED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.contentcaptureservice.cts.CtsSmartSuggestionsService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.contentcapture.ContentCaptureEvent;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class ActivityLifecycleTest extends AbstractContentCaptureIntegrationTest {

    // TODO(b/119638958): move to superclass ?
    @Rule
    public final ActivityTestRule<BlankActivity> activityRule = new ActivityTestRule<>(
            BlankActivity.class, false, false);

    private BlankActivity mActivity;

    // TODO(b/119638958): move to superclass ?
    private void launchActivity() {
        Log.d(TAG, "Launching BlankActivity");

        mActivity = activityRule.launchActivity(new Intent(sContext, BlankActivity.class));
    }

    // TODO(b/119638958): rename once we add moar tests
    @Test
    public void testIt() throws Exception {
        enableService();

        // TODO(b/119638958): move to super class
        final ActivityWatcher watcher = mActivitiesWatcher.watch(BlankActivity.class);

        launchActivity();
        watcher.waitFor(RESUMED);

        mActivity.finish();
        watcher.waitFor(DESTROYED);

        final CtsSmartSuggestionsService service = CtsSmartSuggestionsService.getInstance();
        try {
            final Session session = service.getFinishedSession(BlankActivity.class);

            // TODO(b/119638958): create custom Truth assertions for expect component name and
            // events
            assertThat(session.context.getActivityComponent())
                    .isEqualTo(mActivity.getComponentName());

            final List<ContentCaptureEvent> events = session.getEvents();
            Log.v(TAG, "events: " + events);
            assertThat(events).hasSize(4);
            assertLifecycleEvent(events.get(0), TYPE_ACTIVITY_STARTED);
            assertLifecycleEvent(events.get(1), TYPE_ACTIVITY_RESUMED);
            assertLifecycleEvent(events.get(2), TYPE_ACTIVITY_PAUSED);
            assertLifecycleEvent(events.get(3), TYPE_ACTIVITY_STOPPED);
        } finally {
            // TODO(b/119638958): move to @Rule SafeCleaner
            CtsSmartSuggestionsService.assertNoExceptions();
        }
    }
}
