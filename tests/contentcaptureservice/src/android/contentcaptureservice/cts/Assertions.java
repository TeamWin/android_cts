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

import static android.contentcaptureservice.cts.Helper.MY_EPOCH;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_VIEW_APPEARED;
import static android.view.contentcapture.ContentCaptureEvent.TYPE_VIEW_DISAPPEARED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.contentcaptureservice.cts.CtsSmartSuggestionsService.Session;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ViewNode;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper for common assertions.
 */
final class Assertions {

    /**
     * Asserts a session belongs to the right activity.
     */
    public static void assertRightActivity(@NonNull Session session, @NonNull Activity activity) {
        assertWithMessage("wrong activity for %s", session)
                .that(session.context.getActivityComponent())
                .isEqualTo(activity.getComponentName());
    }

    /**
     * Asserts an activity lifecycle event.
     *
     * @deprecated most like activity lifecycle events will go away.
     */
    @Deprecated
    public static void assertLifecycleEvent(@NonNull ContentCaptureEvent event, int expected) {
        assertWithMessage("wrong event: %s", event).that(event.getType()).isEqualTo(expected);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event.
     */
    public static void assertViewAppeared(@NonNull ContentCaptureEvent event,
            @NonNull View expectedView, @Nullable AutofillId expectedParentId) {
        assertWithMessage("wrong event: %s", event).that(event.getType())
                .isEqualTo(TYPE_VIEW_APPEARED);
        final ViewNode node = event.getViewNode();
        assertThat(node).isNotNull();
        assertWithMessage("invalid time on %s", event).that(event.getEventTime())
                .isAtLeast(MY_EPOCH);
        assertWithMessage("wrong class on %s", node).that(node.getClassName())
                .isEqualTo(expectedView.getClass().getName());
        assertWithMessage("wrong autofill id on %s", node).that(node.getAutofillId())
                .isEqualTo(expectedView.getAutofillId());
        assertWithMessage("wrong parent autofill id on %s", node).that(node.getParentAutofillId())
                .isEqualTo(expectedParentId);
        if (expectedView instanceof TextView) {
            assertWithMessage("wrong text id on %s", node).that(node.getText().toString())
                    .isEqualTo(((TextView) expectedView).getText().toString());
        }
        // TODO(b/119638958): test more fields, like resource id
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_DISAPPEARED} event.
     */
    public static void assertViewDisappeared(@NonNull ContentCaptureEvent event,
            @NonNull AutofillId expectedId) {
        assertWithMessage("wrong event: %s", event).that(event.getType())
                .isEqualTo(TYPE_VIEW_DISAPPEARED);
        assertWithMessage("invalid time on %s", event).that(event.getEventTime())
            .isAtLeast(MY_EPOCH);
        assertWithMessage("event %s should not have a ViewNode", event).that(event.getViewNode())
                .isNull();
        assertWithMessage("event %s should not have text", event).that(event.getText())
            .isNull();
        assertWithMessage("event %s should not have flags", event).that(event.getFlags())
            .isEqualTo(0);
        assertWithMessage("event %s should not have a ViewNode", event).that(event.getViewNode())
        .isNull();
        assertWithMessage("wrong autofillId on event %s", event).that(event.getId())
                .isEqualTo(expectedId);
    }

    private Assertions() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
