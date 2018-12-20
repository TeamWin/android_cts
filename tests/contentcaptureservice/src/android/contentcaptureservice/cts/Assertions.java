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
import static android.view.contentcapture.ContentCaptureEvent.TYPE_VIEW_TEXT_CHANGED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.contentcaptureservice.cts.CtsContentCaptureService.Session;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;
import android.view.contentcapture.ViewNode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper for common assertions.
 */
final class Assertions {

    /**
     * Asserts a session belongs to the right activity.
     */
    public static void assertRightActivity(@NonNull Session session,
            @NonNull ContentCaptureSessionId expectedSessionId,
            @NonNull AbstractContentCaptureActivity activity) {
        assertWithMessage("wrong activity for %s", session)
                .that(session.context.getActivityComponent())
                .isEqualTo(activity.getComponentName());
        // TODO(b/119638958): merge both or replace check above by:
        //  assertMainSessionContext(session, activity);
        assertThat(session.id).isEqualTo(expectedSessionId);
    }

    /**
     * Asserts the context of a main session.
     */
    public static void assertMainSessionContext(@NonNull Session session,
            @NonNull AbstractContentCaptureActivity activity) {
        assertMainSessionContext(session, activity, /* flags= */ 0);
    }

    /**
     * Asserts the context of a main session.
     */
    public static void assertMainSessionContext(@NonNull Session session,
            @NonNull AbstractContentCaptureActivity activity, int expectedFlags) {
        assertWithMessage("no context on %s", session).that(session.context).isNotNull();
        assertWithMessage("wrong activity for %s", session)
                .that(session.context.getActivityComponent())
                .isEqualTo(activity.getComponentName());
        // TODO(b/121260224): add this assertion when it's set
        // assertWithMessage("context for session %s should have displayId", session)
        //        .that(session.context.getDisplayId()).isNotEqualTo(0);
        assertWithMessage("wrong task id for session %s", session)
                .that(session.context.getTaskId()).isEqualTo(activity.getRealTaskId());
        assertWithMessage("wrong flags on context for session %s", session)
                .that(session.context.getFlags()).isEqualTo(expectedFlags);
        assertWithMessage("context for session %s should not have URI", session)
                .that(session.context.getUri()).isNull();
        assertWithMessage("context for session %s should not have extras", session)
                .that(session.context.getExtras()).isNull();
    }

    /**
     * Asserts the invariants of a child session.
     */
    public static void assertChildSessionContext(@NonNull Session session) {
        assertWithMessage("no context on %s", session).that(session.context).isNotNull();
        assertWithMessage("context for session %s should not have component", session)
                .that(session.context.getActivityComponent()).isNull();
        assertWithMessage("context for session %s should not have displayId", session)
                .that(session.context.getDisplayId()).isEqualTo(0);
        assertWithMessage("context for session %s should not have taskId", session)
                .that(session.context.getTaskId()).isEqualTo(0);
        assertWithMessage("context for session %s should not have flags", session)
                .that(session.context.getFlags()).isEqualTo(0);
    }

    /**
     * Asserts a session belongs to the right parent
     */
    public static void assertRightRelationship(@NonNull Session parent, @NonNull Session child) {
        final ContentCaptureSessionId expectedParentId = parent.id;
        assertWithMessage("No id on parent session %s", parent).that(expectedParentId).isNotNull();
        assertWithMessage("No context on child session %s", child).that(child.context).isNotNull();
        final ContentCaptureSessionId actualParentId = child.context.getParentSessionId();
        assertWithMessage("No parent id on context %s of child session %s", child.context, child)
                .that(actualParentId).isNotNull();
        assertWithMessage("id of parent session doesn't match child").that(actualParentId)
                .isEqualTo(expectedParentId);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event, without checking for parent id.
     */
    public static ViewNode assertViewWithUnknownParentAppeared(@NonNull ContentCaptureEvent event,
            @NonNull View expectedView) {
        return assertViewWithUnknownParentAppeared(event, expectedView, /* expectedText= */ null);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event, without checking for parent id.
     */
    public static ViewNode assertViewWithUnknownParentAppeared(@NonNull ContentCaptureEvent event,
            @NonNull View expectedView, @Nullable String expectedText) {
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

        if (expectedText != null) {
            assertWithMessage("wrong text id on %s", node).that(node.getText().toString())
                    .isEqualTo(expectedText);
        }
        // TODO(b/119638958): test more fields, like resource id
        return node;
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event.
     */
    public static void assertViewAppeared(@NonNull ContentCaptureEvent event,
            @NonNull View expectedView, @Nullable AutofillId expectedParentId,
            @Nullable String expectedText) {
        final ViewNode node = assertViewWithUnknownParentAppeared(event, expectedView,
                expectedText);
        assertWithMessage("wrong parent autofill id on %s", node).that(node.getParentAutofillId())
                .isEqualTo(expectedParentId);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event.
     */
    public static void assertViewAppeared(@NonNull ContentCaptureEvent event,
            @NonNull View expectedView, @Nullable AutofillId expectedParentId) {
        assertViewAppeared(event, expectedView, expectedParentId, /* expectedText= */ null);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event.
     */
    public static void assertViewAppeared(@NonNull ContentCaptureEvent event,
            @NonNull ContentCaptureSessionId expectedSessionId,
            @NonNull View expectedView, @Nullable AutofillId expectedParentId) {
        assertViewAppeared(event, expectedView, expectedParentId);
        assertSessionId(expectedSessionId, expectedView);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event, without checking for parent
     */
    public static void assertViewWithUnknownParentAppeared(@NonNull ContentCaptureEvent event,
            @NonNull ContentCaptureSessionId expectedSessionId,
            @NonNull View expectedView) {
        assertViewWithUnknownParentAppeared(event, expectedView);
        assertSessionId(expectedSessionId, expectedView);
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

    /**
     * Asserts a view has the given session id.
     */
    public static void assertSessionId(@NonNull ContentCaptureSessionId expectedSessionId,
            @NonNull View view) {
        assertThat(expectedSessionId).isNotNull();
        final ContentCaptureSession session = view.getContentCaptureSession();
        assertWithMessage("no session for view %s", view).that(session).isNotNull();
        assertWithMessage("wrong session id for for view %s", view)
                .that(session.getContentCaptureSessionId()).isEqualTo(expectedSessionId);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_TEXT_CHANGED} event.
     */
    public static void assertViewTextChanged(@NonNull ContentCaptureEvent event,
            @NonNull String expectedText, @NonNull AutofillId expectedId) {
        assertWithMessage("wrong event: %s", event).that(event.getType())
                .isEqualTo(TYPE_VIEW_TEXT_CHANGED);
        assertWithMessage("Wrong text on %s", event).that(event.getText().toString())
                .isEqualTo(expectedText);
    }

    private Assertions() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
