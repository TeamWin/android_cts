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
import android.net.Uri;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSession;
import android.view.contentcapture.ContentCaptureSessionId;
import android.view.contentcapture.ViewNode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

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
     * Asserts the invariants of a child session.
     */
    public static void assertChildSessionContext(@NonNull Session session,
            @NonNull String expectedUri) {
        assertChildSessionContext(session);
        assertThat(session.context.getUri()).isEqualTo(Uri.parse(expectedUri));
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
    public static ViewNode assertViewWithUnknownParentAppeared(
            @NonNull List<ContentCaptureEvent> events, int index, @NonNull View expectedView) {
        return assertViewWithUnknownParentAppeared(events, index, expectedView,
                /* expectedText= */ null);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event, without checking for parent id.
     */
    public static ViewNode assertViewWithUnknownParentAppeared(
            @NonNull List<ContentCaptureEvent> events, int index, @NonNull View expectedView,
            @Nullable String expectedText) {
        final ContentCaptureEvent event = getEvent(events, index);
        assertWithMessage("wrong event type at index %s: %s", index, event).that(event.getType())
                .isEqualTo(TYPE_VIEW_APPEARED);
        final ViewNode node = event.getViewNode();
        assertThat(node).isNotNull();
        assertWithMessage("invalid time on %s (%s)", event, index).that(event.getEventTime())
                .isAtLeast(MY_EPOCH);
        assertWithMessage("wrong class on %s (%s)", event, index).that(node.getClassName())
                .isEqualTo(expectedView.getClass().getName());
        assertWithMessage("wrong autofill id on %s (%s)", event, index).that(node.getAutofillId())
                .isEqualTo(expectedView.getAutofillId());

        if (expectedText != null) {
            assertWithMessage("wrong text on %s (%s)", event, index).that(node.getText().toString())
                    .isEqualTo(expectedText);
        }
        // TODO(b/119638958): test more fields, like resource id
        return node;
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event.
     */
    public static void assertViewAppeared(@NonNull List<ContentCaptureEvent> events, int index,
            @NonNull View expectedView, @Nullable AutofillId expectedParentId,
            @Nullable String expectedText) {
        final ViewNode node = assertViewWithUnknownParentAppeared(events, index, expectedView,
                expectedText);
        assertWithMessage("wrong parent autofill id on %s (%s)", events.get(index), index)
            .that(node.getParentAutofillId()).isEqualTo(expectedParentId);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event.
     */
    public static void assertViewAppeared(@NonNull List<ContentCaptureEvent> events, int index,
            @NonNull View expectedView, @Nullable AutofillId expectedParentId) {
        assertViewAppeared(events, index, expectedView, expectedParentId, /* expectedText= */ null);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event.
     */
    public static void assertViewAppeared(@NonNull List<ContentCaptureEvent> events, int index,
            @NonNull ContentCaptureSessionId expectedSessionId,
            @NonNull View expectedView, @Nullable AutofillId expectedParentId) {
        assertViewAppeared(events, index, expectedView, expectedParentId);
        assertSessionId(expectedSessionId, expectedView);
    }

    /**
     * Asserts that the events received by the service optionally contains the
     * {@code TYPE_VIEW_DISAPPEARED} events, as they might have not been generated if the views
     * disappeared after the activity stopped.
     *
     * @param events events received by the service.
     * @param minimumSize size of events received if activity stopped before views disappeared
     * @param expectedIds ids of views that might have disappeared.
     */
    // TODO(b/122315042): remove this method if we could make it deterministic
    public static void assertViewsOptionallyDisappeared(@NonNull List<ContentCaptureEvent> events,
            int minimumSize, @NonNull AutofillId... expectedIds) {
        final int actualSize = events.size();
        final int optionalSize = expectedIds.length;
        if (actualSize == minimumSize) {
            // Activity stopped before TYPE_VIEW_DISAPPEARED were sent.
            return;
        }

        assertThat(events).hasSize(minimumSize + optionalSize);
        final ArrayList<AutofillId> actualIds = new ArrayList<>(optionalSize);
        final StringBuilder errors = new StringBuilder();
        for (int i = 0; i < optionalSize; i++) {
            final int index = minimumSize + i;
            final ContentCaptureEvent event = getEvent(events, index);
            if (event.getType() != TYPE_VIEW_DISAPPEARED) {
                errors.append("Invalid event at index ").append(index).append(": ").append(event)
                        .append('\n');
                continue;
            }
            actualIds.add(event.getId());
        }
        assertThat(actualIds).containsExactly((Object[]) expectedIds);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_APPEARED} event, without checking for parent
     */
    public static void assertViewWithUnknownParentAppeared(
            @NonNull List<ContentCaptureEvent> events, int index,
            @NonNull ContentCaptureSessionId expectedSessionId, @NonNull View expectedView) {
        assertViewWithUnknownParentAppeared(events, index, expectedView);
        assertSessionId(expectedSessionId, expectedView);
    }

    /**
     * Asserts the contents of a {@link #TYPE_VIEW_DISAPPEARED} event.
     */
    public static void assertViewDisappeared(@NonNull List<ContentCaptureEvent> events, int index,
            @NonNull AutofillId expectedId) {
        final ContentCaptureEvent event = getEvent(events, index);
        assertWithMessage("wrong event type at index %s: %s", index, event).that(event.getType())
                .isEqualTo(TYPE_VIEW_DISAPPEARED);
        assertWithMessage("invalid time on %s (index %s)", event, index).that(event.getEventTime())
            .isAtLeast(MY_EPOCH);
        assertWithMessage("event %s (index %s) should not have a ViewNode", event, index)
                .that(event.getViewNode()).isNull();
        assertWithMessage("event %s (index %s) should not have text", event, index)
            .that(event.getText()).isNull();
        assertWithMessage("event %s (index %s) should not have flags", event, index)
            .that(event.getFlags()).isEqualTo(0);
        assertWithMessage("event %s (index %s) should not have a ViewNode", event, index)
            .that(event.getViewNode()).isNull();
        assertWithMessage("wrong autofillId on event %s (index %s)", event, index)
            .that(event.getId()).isEqualTo(expectedId);
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
    public static void assertViewTextChanged(@NonNull List<ContentCaptureEvent> events, int index,
            @NonNull AutofillId expectedId, @NonNull String expectedText) {
        final ContentCaptureEvent event = getEvent(events, index);
        assertWithMessage("wrong event at index %s: %s", index, event).that(event.getType())
                .isEqualTo(TYPE_VIEW_TEXT_CHANGED);
        assertWithMessage("Wrong id on %s (%s)", event, index).that(event.getId())
                .isEqualTo(expectedId);
        assertWithMessage("Wrong text on %s (%s)", event, index).that(event.getText().toString())
                .isEqualTo(expectedText);
    }

    /**
     * Asserts the order a session was created or destroyed.
     */
    public static void assertLifecycleOrder(int expectedOrder, @NonNull Session session,
            @NonNull LifecycleOrder type) {
        switch(type) {
            case CREATION:
                assertWithMessage("Wrong order of creation for session %s", session)
                    .that(session.creationOrder).isEqualTo(expectedOrder);
                break;
            case DESTRUCTION:
                assertWithMessage("Wrong order of destruction for session %s", session)
                    .that(session.destructionOrder).isEqualTo(expectedOrder);
                break;
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    /**
     * Gets the event at the given index, failing with the user-friendly message if necessary...
     */
    @NonNull
    public static ContentCaptureEvent getEvent(@NonNull List<ContentCaptureEvent> events,
            int index) {
        assertWithMessage("events is null").that(events).isNotNull();
        final ContentCaptureEvent event = events.get(index);
        assertWithMessage("no event at index %s (size %s): %s", index, events.size(), events)
                .that(event).isNotNull();
        return event;
    }

    private Assertions() {
        throw new UnsupportedOperationException("contain static methods only");
    }

    public enum LifecycleOrder {
        CREATION, DESTRUCTION
    }
}
