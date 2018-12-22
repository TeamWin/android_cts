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

    private static ServiceWatcher sServiceWatcher;

    // TODO(b/119638958): reuse with allSessions
    /** Used by {@link #getOnlyFinishedSession()}. */
    private static ContentCaptureSessionId sFirstSessionId;

    private static final ArrayList<Throwable> sExceptions = new ArrayList<>();

    private final CountDownLatch mConnectedLatch = new CountDownLatch(1);
    private final CountDownLatch mDisconnectedLatch = new CountDownLatch(1);

    /**
     * List of all sessions started - never reset.
     */
    private final ArrayList<ContentCaptureSessionId> mAllSessions = new ArrayList<>();

    /**
     * Map of all sessions started but not finished yet - sessions are removed as they're finished.
     */
    private final ArrayMap<ContentCaptureSessionId, Session> mOpenSessions = new ArrayMap<>();

    /**
     * Map of all sessions finished.
     */
    private final ArrayMap<ContentCaptureSessionId, Session> mFinishedSessions = new ArrayMap<>();

    /**
     * Map of latches for sessions that started but haven't finished yet.
     */
    private final ArrayMap<ContentCaptureSessionId, CountDownLatch> mUnfinishedSessionLatches =
            new ArrayMap<>();

    @NonNull
    public static ServiceWatcher setServiceWatcher() {
        if (sServiceWatcher != null) {
            throw new IllegalStateException("There Can Be Only One!");
        }
        sServiceWatcher = new ServiceWatcher();
        return sServiceWatcher;
    }


    public static void resetStaticState() {
        sFirstSessionId = null;
        sExceptions.clear();
        // TODO(b/119638958): should probably set sInstance to null as well, but first we would need
        // to make sure each test unbinds the service.

        // TODO(b/119638958): each test should use a different service instance, but we need
        // to provide onConnected() / onDisconnected() methods first and then change the infra so
        // we can wait for those

        if (sServiceWatcher != null) {
            Log.wtf(TAG, "resetStaticState(): should not have sServiceWatcher");
            sServiceWatcher = null;
        }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate(): sServiceWatcher=" + sServiceWatcher);
        super.onCreate();

        if (sServiceWatcher == null) {
            addException("onCreate() without a watcher");
            return;
        }

        if (sServiceWatcher.mService != null) {
            addException("onCreate(): already created: %s", sServiceWatcher);
            return;
        }

        sServiceWatcher.mService = this;
        sServiceWatcher.mCreated.countDown();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy(): sServiceWatcher=" + sServiceWatcher);
        super.onDestroy();

        if (sServiceWatcher == null) {
            addException("onDestroy() without a watcher");
            return;
        }
        if (sServiceWatcher.mService == null) {
            addException("onDestroy(): no service on %s", sServiceWatcher);
            return;
        }
        sServiceWatcher.mDestroyed.countDown();
        sServiceWatcher.mService = null;
        sServiceWatcher = null;
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "onConnected(): sServiceWatcher=" + sServiceWatcher);

        if (mConnectedLatch.getCount() == 0) {
            addException("already connected: %s", mConnectedLatch);
        }
        mConnectedLatch.countDown();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "onDisconnected(): sServiceWatcher=" + sServiceWatcher);

        if (mDisconnectedLatch.getCount() == 0) {
            addException("already disconnected: %s", mConnectedLatch);
        }
        mDisconnectedLatch.countDown();
    }

    /**
     * Waits until the system calls {@link #onConnected()}.
     */
    public void waitUntilConnected() throws InterruptedException {
        await(mConnectedLatch, "not connected");
    }

    /**
     * Waits until the system calls {@link #onDisconnected()}.
     */
    public void waitUntilDisconnected() throws InterruptedException {
        await(mDisconnectedLatch, "not disconnected");
    }

    @Override
    public void onCreateContentCaptureSession(ContentCaptureContext context,
            ContentCaptureSessionId sessionId) {
        Log.i(TAG, "onCreateContentCaptureSession(ctx=" + context + ", id=" + sessionId
                + ", firstId=" + sFirstSessionId + ")");
        mAllSessions.add(sessionId);
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

    /**
     * Gets all sessions that have been created so far.
     */
    @NonNull
    public List<ContentCaptureSessionId> getAllSessionIds() {
        return Collections.unmodifiableList(mAllSessions);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);

        pw.print("sServiceWatcher: "); pw.println(sServiceWatcher);
        pw.print("sFirstSessionId: "); pw.println(sFirstSessionId);
        pw.print("sExceptions: "); pw.println(sExceptions);
        pw.print("mConnectedLatch: "); pw.println(mConnectedLatch);
        pw.print("mDisconnectedLatch: "); pw.println(mDisconnectedLatch);
        pw.print("mAllSessions: "); pw.println(mAllSessions);
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
                + ".\nAll=" + mAllSessions
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

    private static void addException(@NonNull String fmt, @Nullable Object...args) {
        final String msg = String.format(fmt, args);
        Log.e(TAG, msg);
        sExceptions.add(new IllegalStateException(msg));
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

    public static final class ServiceWatcher {

        private final CountDownLatch mCreated = new CountDownLatch(1);
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        private CtsContentCaptureService mService;

        @NonNull
        public CtsContentCaptureService waitOnCreate() throws InterruptedException {
            await(mCreated, "not created");

            if (mService == null) {
                throw new IllegalStateException("not created");
            }

            return mService;
        }

        public void waitOnDestroy() throws InterruptedException {
            await(mDestroyed, "not destroyed");
        }

        @Override
        public String toString() {
            return "mService: " + mService + " created: " + (mCreated.getCount() == 0)
                    + " destroyed: " + (mDestroyed.getCount() == 0);
        }
    }
}
