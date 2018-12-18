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

import static android.contentcaptureservice.cts.Helper.MY_PACKAGE;
import static android.contentcaptureservice.cts.Helper.await;

import static com.google.common.truth.Truth.assertWithMessage;

import android.service.contentcapture.ContentCaptureEventsRequest;
import android.service.contentcapture.ContentCaptureService;
import android.util.ArrayMap;
import android.util.Log;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureSessionId;
import android.view.contentcapture.ViewNode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

// TODO(b/119638958): if we don't move this service to a separate package, we need to handle the
// onXXXX methods in a separate thread
// Either way, we need to make sure its methods are thread safe

public class CtsContentCaptureService extends ContentCaptureService {

    private static final String TAG = CtsContentCaptureService.class.getSimpleName();

    public static final String SERVICE_NAME = MY_PACKAGE + "/."
            + CtsContentCaptureService.class.getSimpleName();

    private static final CountDownLatch sInstanceLatch = new CountDownLatch(1);

    private static CtsContentCaptureService sInstance;

    /** Used by {@link #getOnlyFinishedSession()}. */
    private static ContentCaptureSessionId sFirstSessionId;

    // TODO(b/119638958): add method to clear static state / call it from @Before
    private static final ArrayList<Throwable> sExceptions = new ArrayList<>();

    public static CtsContentCaptureService getInstance() throws InterruptedException {
        await(sInstanceLatch, "Service not started");
        return sInstance;
    }

    private final ArrayMap<ContentCaptureSessionId, Session> mOpenSessions = new ArrayMap<>();
    private final ArrayMap<ContentCaptureSessionId, Session> mFinishedSessions = new ArrayMap<>();
    private final ArrayMap<ContentCaptureSessionId, CountDownLatch> mUnfinishedSessionLatches =
            new ArrayMap<>();

    public static void resetStaticState() {
        sFirstSessionId = null;
        sExceptions.clear();
        // TODO(b/119638958): should probably set sInstance to null as well, but first we would need
        // to make sure each test unbinds the service.
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate(): sInstance=" + sInstance);
        super.onCreate();

        if (sInstance == null) {
            sInstance = this;
            sInstanceLatch.countDown();
        } else {
            Log.e(TAG, "onCreate(): already created:" + sInstance);
            sExceptions.add(new IllegalStateException("onCreate() again"));
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy(): sInstance=" + sInstance);
        super.onDestroy();

        if (this == sInstance) {
            sInstance = null;
        }
    }

    @Override
    public void onCreateContentCaptureSession(ContentCaptureContext context,
            ContentCaptureSessionId sessionId) {
        Log.i(TAG, "onCreateContentCaptureSession(ctx=" + context + ", id=" + sessionId
                + ", firstId=" + sFirstSessionId + ")");
        if (sFirstSessionId == null) {
            sFirstSessionId = sessionId;
        }

        safeRun(() -> {
            final Session session = mOpenSessions.get(sessionId);
            if (session != null) {
                throw new IllegalStateException("Already contains session for " + sessionId
                        + ": " + session);
            }
            mUnfinishedSessionLatches.put(sessionId, new CountDownLatch(1));
            mOpenSessions.put(sessionId, new Session(sessionId, context));
        });
    }

    @Override
    public void onDestroyContentCaptureSession(ContentCaptureSessionId sessionId) {
        Log.i(TAG, "onDestroyContentCaptureSession(" + sessionId + ")");
        safeRun(() -> {
            final Session session = getExistingSession(sessionId);
            session.finished = true;
            mOpenSessions.remove(sessionId);
            if (mFinishedSessions.containsKey(sessionId)) {
                throw new IllegalStateException("Already destroyed " + sessionId);
            } else {
                mFinishedSessions.put(sessionId, session);
                final CountDownLatch latch = getUnfinishedSessionLatch(sessionId);
                latch.countDown();
            }
        });
    }

    @Override
    public void onContentCaptureEventsRequest(ContentCaptureSessionId sessionId,
            ContentCaptureEventsRequest request) {
        final List<ContentCaptureEvent> events = request.getEvents();
        final int size = events.size();
        Log.i(TAG, "onContentCaptureEventsRequest(" + sessionId + "): " + size + " events");
        for (int i = 0; i < size; i++) {
            final ContentCaptureEvent event = events.get(i);
            final StringBuilder msg = new StringBuilder("  ").append(i).append(": ").append(event);
            final ViewNode node = event.getViewNode();
            if (node != null) {
                msg.append(", parent=").append(node.getParentAutofillId());
            }
            Log.v(TAG, msg.toString());
        }
        safeRun(() -> {
            final Session session = getExistingSession(sessionId);
            session.mRequests.add(request);
        });
    }

    /**
     * Gets the finished session for the given session id.
     *
     * @throws IllegalStateException if the session didn't finish yet.
     */
    @NonNull
    public Session getFinishedSession(@NonNull ContentCaptureSessionId sessionId)
            throws InterruptedException {
        final CountDownLatch latch = getUnfinishedSessionLatch(sessionId);
        await(latch, "session %s not finished yet", sessionId);

        final Session session = mFinishedSessions.get(sessionId);
        if (session == null) {
            throwIllegalSessionStateException("No finished session for id %s", sessionId);
        }
        return session;
    }

    /**
     * Gets the finished session when only one session is expected.
     *
     * <p>Should be used when the test case doesn't known in advance the id of the session.
     */
    @NonNull
    public Session getOnlyFinishedSession() throws InterruptedException {
        // TODO(b/119638958): add some assertions to make sure There Can Be Only One!
        assertWithMessage("No session yet").that(sFirstSessionId).isNotNull();
        return getFinishedSession(sFirstSessionId);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);

        pw.print("sInstance: "); pw.println(sInstance);
        pw.print("sInstanceLatch: "); pw.println(sInstanceLatch);
        pw.print("sFirstSessionId: "); pw.println(sFirstSessionId);
        pw.print("sExceptions: "); pw.println(sExceptions);
        pw.print("mOpenSessions: "); pw.println(mOpenSessions);
        pw.print("mFinishedSessions: "); pw.println(mFinishedSessions);
        pw.print("mUnfinishedSessionLatches: "); pw.println(mUnfinishedSessionLatches);
    }

    @NonNull
    private CountDownLatch getUnfinishedSessionLatch(final ContentCaptureSessionId sessionId) {
        final CountDownLatch latch = mUnfinishedSessionLatches.get(sessionId);
        if (latch == null) {
            throwIllegalSessionStateException("no latch for %s", sessionId);
        }
        return latch;
    }

    /**
     * Gets the exceptions that were thrown while the service handlded requests.
     */
    public static List<Throwable> getExceptions() throws Exception {
        return Collections.unmodifiableList(sExceptions);
    }

    private void throwIllegalSessionStateException(@NonNull String fmt, @Nullable Object...args) {
        throw new IllegalStateException(String.format(fmt, args)
                + ".\nOpen=" + mOpenSessions
                + ".\nLatches=" + mUnfinishedSessionLatches
                + ".\nFinished=" + mFinishedSessions);
    }

    private Session getExistingSession(@NonNull ContentCaptureSessionId sessionId) {
        final Session session = mOpenSessions.get(sessionId);
        if (session == null) {
            throwIllegalSessionStateException("No open session with id %s", sessionId);
        }
        if (session.finished) {
            throw new IllegalStateException("session already finished: " + session);
        }

        return session;
    }

    private void safeRun(@NonNull Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.e(TAG, "Exception handling service callback: " + t);
            sExceptions.add(t);
        }
    }

    public final class Session {
        public final ContentCaptureSessionId id;
        public final ContentCaptureContext context;
        private final List<ContentCaptureEventsRequest> mRequests = new ArrayList<>();
        public boolean finished;

        private Session(ContentCaptureSessionId id, ContentCaptureContext context) {
            this.id = id;
            this.context = context;
        }

        // TODO(b/119638958): currently we're only interested on all events, but eventually we
        // should track individual requests as well to make sure they're probably batch (it will
        // require adding a Settings to tune the buffer parameters.
        public List<ContentCaptureEvent> getEvents() {
            final List<ContentCaptureEvent> events = new ArrayList<>();
            for (ContentCaptureEventsRequest request : mRequests) {
                events.addAll(request.getEvents());
            }
            return Collections.unmodifiableList(events);
        }

        @Override
        public String toString() {
            return "[id=" + id + ", context=" + context + ", requests=" + mRequests.size()
                    + ", finished=" + finished + "]";
        }
    }
}
