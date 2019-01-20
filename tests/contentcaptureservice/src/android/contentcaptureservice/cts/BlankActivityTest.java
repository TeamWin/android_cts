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

import static android.contentcaptureservice.cts.CtsContentCaptureService.CONTENT_CAPTURE_SERVICE_COMPONENT_NAME;
import static android.contentcaptureservice.cts.Helper.resetService;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.DESTROYED;
import static android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;

import org.junit.Test;

public class BlankActivityTest extends AbstractContentCaptureIntegrationTest<BlankActivity> {

    private static final String TAG = BlankActivityTest.class.getSimpleName();

    private static final ActivityTestRule<BlankActivity> sActivityRule = new ActivityTestRule<>(
            BlankActivity.class, false, false);

    public BlankActivityTest() {
        super(BlankActivity.class);
    }

    @Override
    protected ActivityTestRule<BlankActivity> getActivityTestRule() {
        return sActivityRule;
    }

    @Test
    public void testSimpleSessionLifecycle() throws Exception {
        final CtsContentCaptureService service = enableService();
        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        activity.finish();
        watcher.waitFor(DESTROYED);

        final Session session = service.getOnlyFinishedSession();
        Log.v(TAG, "session id: " + session.id);

        activity.assertDefaultEvents(session);
    }

    @Test
    public void testGetServiceComponentName() throws Exception {
        final CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        final ActivityWatcher watcher = startWatcher();

        final BlankActivity activity = launchActivity();
        watcher.waitFor(RESUMED);

        assertThat(activity.getContentCaptureManager().getServiceComponentName())
                .isEqualTo(CONTENT_CAPTURE_SERVICE_COMPONENT_NAME);

        resetService();
        service.waitUntilDisconnected();

        assertThat(activity.getContentCaptureManager().getServiceComponentName())
                .isNotEqualTo(CONTENT_CAPTURE_SERVICE_COMPONENT_NAME);
    }

    @Test
    public void testOnConnectionEvents() throws Exception {
        final CtsContentCaptureService service = enableService();
        service.waitUntilConnected();

        resetService();
        service.waitUntilDisconnected();
    }
}
