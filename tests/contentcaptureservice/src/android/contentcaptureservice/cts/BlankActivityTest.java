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
import static android.contentcaptureservice.cts.Helper.TAG;
import static android.contentcaptureservice.cts.Helper.enableService;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.contentcapture.ContentCaptureEvent;

import org.junit.Test;

import java.util.List;

public class BlankActivityTest extends AbstractContentCaptureIntegrationTest<BlankActivity> {

    private static final ActivityTestRule<BlankActivity> sActivityRule = new ActivityTestRule<>(
            BlankActivity.class, false, false);

    public BlankActivityTest() {
        super(BlankActivity.class);
    }

    @Override
    protected ActivityTestRule<BlankActivity> getActivityTestRule() {
        return sActivityRule;
    }

    // TODO(b/119638958): rename once we add moar tests
    @Test
    public void testIt() throws Exception {
        enableService();

        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final CtsContentCaptureService service = CtsContentCaptureService.getInstance();
        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        assertRightActivity(session, session.id, activity);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);
        assertThat(events).isEmpty();
    }
}
