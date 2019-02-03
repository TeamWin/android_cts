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

import static android.contentcaptureservice.cts.Assertions.assertRightActivity;
import static android.contentcaptureservice.cts.Assertions.assertViewAppeared;

import static com.google.common.truth.Truth.assertThat;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.util.Log;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSessionId;
import android.view.contentcapture.ViewNode;

import androidx.annotation.NonNull;

import java.util.List;

public class BlankWithTitleActivity extends AbstractContentCaptureActivity {

    private static final String TAG = BlankWithTitleActivity.class.getSimpleName();

    @Override
    public void assertDefaultEvents(@NonNull Session session) {
        final ContentCaptureSessionId sessionId = session.id;
        assertRightActivity(session, sessionId, this);

        final List<ContentCaptureEvent> events = session.getEvents();
        Log.v(TAG, "events: " + events);

        final int minEvents = 1;
        // TODO(b/119638528): somehow asset the grandparents...
        assertThat(events.size()).isAtLeast(minEvents);

        final ViewNode title = assertViewAppeared(events, 0);
        assertThat(title.getText()).isEqualTo("Blanka");
    }
}
