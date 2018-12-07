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

import android.service.contentcapture.ContentCaptureEventsRequest;
import android.service.contentcapture.ContentCaptureService;
import android.service.contentcapture.InteractionContext;
import android.service.contentcapture.InteractionSessionId;
import android.util.ArrayMap;
import android.util.Log;
import android.view.contentcapture.ContentCaptureEvent;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

// TODO(b/119638958): if we don't move this service to a separate package, we need to handle the
// onXXXX methods in a separate thread
// Either way, we need to make sure its methods are thread safe
public class CtsSmartSuggestionsService extends ContentCaptureService {

    private static final String TAG = CtsSmartSuggestionsService.class.getSimpleName();

    public static final String SERVICE_NAME = MY_PACKAGE + "/."
            + CtsSmartSuggestionsService.class.getSimpleName();

    private static final CountDownLatch sInstanceLatch = new CountDownLatch(1);

    private static CtsSmartSuggestionsService sInstance;

    // TODO(b/119638958): add method to clear static state / call it from @Before
    private static final ArrayList<Throwable> sExceptions = new ArrayList<>();

    public static CtsSmartSuggestionsService getInstance() throws InterruptedException {
        await(sInstanceLatch, "Service not started");
        return sInstance;
    }

    private final ArrayMap<InteractionSessionId, Session> mOpenSessions = new ArrayMap<>();

    private final ArrayMap<String, Session> mFinishedSessions = new ArrayMap<>();

    private final CountDownLatch mAtLeastOneSessionFinished = new CountDownLatch(1);

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate(): sInstance=" + sInstance);
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
        Log.d(TAG, "onDestroy(): sInstance=" + sInstance);
        super.onDestroy();

        if (this == sInstance) {
            sInstance = null;
        }
    }

    @Override
    public void onCreateInteractionSession(InteractionContext context,
            InteractionSessionId sessionId) {
        Log.d(TAG, "onCreateInteractionSession(ctx=" + context + ", id=" + sessionId + ")");

        safeRun(() -> {
            final Session session = mOpenSessions.get(sessionId);
            if (session != null) {
                throw new IllegalStateException("Already contains session for " + sessionId
                        + ": " + session);
            }
            mOpenSessions.put(sessionId, new Session(sessionId, context));
        });
    }

    @Override
    public void onDestroyInteractionSession(InteractionSessionId sessionId) {
        Log.d(TAG, "onDestroyInteractionSession(" + sessionId + ")");
        safeRun(() -> {
            final Session session = getExistingSession(sessionId);
            session.finished = true;
            mOpenSessions.remove(sessionId);
            final String className = session.context.getActivityComponent().getClassName();
            if (mFinishedSessions.containsKey(className)) {
                throw new IllegalStateException("Already destroyed " + className);
            } else {
                mFinishedSessions.put(className, session);
                mAtLeastOneSessionFinished.countDown();
            }
        });
    }

    @Override
    public void onContentCaptureEventsRequest(InteractionSessionId sessionId,
            ContentCaptureEventsRequest request) {
        final List<ContentCaptureEvent> events = request.getEvents();
        Log.d(TAG,
                "onContentCaptureEventsRequest(" + sessionId + "): " + events.size() + " events");
        safeRun(() -> {
            final Session session = getExistingSession(sessionId);
            session.mRequests.add(request);
        });
    }

    /**
     * Gets the finished session for the given activity.
     *
     * @throws IllegalStateException if the session didn't finish yet.
     */
    @NonNull
    public Session getFinishedSession(@NonNull Class<BlankActivity> clazz)
            throws InterruptedException {
        await(mAtLeastOneSessionFinished, "no session finished yet");

        final String className = clazz.getName();
        final Session session = mFinishedSessions.get(className);

        if (session == null) {
            throw new IllegalStateException("No session for " + className + ": " + mOpenSessions);
        }
        return session;
    }

    /**
     * Asserts that no exception was thrown while the service handlded requests.
     */
    public static void assertNoExceptions() throws Exception {
        if (sExceptions.isEmpty()) return;
        if (sExceptions.size() == 1) {
            throwException(sExceptions.get(0));
        }
        // TODO(b/119638958): use a MultipleExceptions class (from common)
        throw new AssertionError("Multiple exceptions: " + sExceptions);
    }

    private Session getExistingSession(@NonNull InteractionSessionId sessionId) {
        final Session session = mOpenSessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("No session with id" + sessionId);
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

    private static void throwException(Throwable t) throws Exception {
        if (t instanceof Exception) {
            throw (Exception) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw new Exception(t);
    }

    public final class Session {
        public final InteractionSessionId id;
        public final InteractionContext context;
        private final List<ContentCaptureEventsRequest> mRequests = new ArrayList<>();
        public boolean finished;

        private Session(InteractionSessionId id, InteractionContext context) {
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
