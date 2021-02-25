/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib;

import static android.content.Context.BIND_AUTO_CREATE;

import static com.android.eventlib.QueryService.EARLIEST_LOG_TIME_KEY;
import static com.android.eventlib.QueryService.EVENT_KEY;
import static com.android.eventlib.QueryService.QUERIER_KEY;
import static com.android.eventlib.QueryService.TIMEOUT_KEY;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link EventQuerier} used to query a single other process.
 */
public class
    RemoteEventQuerier<E extends Event, F extends EventLogsQuery> implements EventQuerier<E> {

    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final String LOG_TAG = "RemoteEventQuerier";
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private final String mPackageName;
    private final EventLogsQuery<E, F> mEventLogsQuery;
    // Each client gets a random ID
    private final long id = UUID.randomUUID().getMostSignificantBits();

    public RemoteEventQuerier(String packageName, EventLogsQuery<E, F> eventLogsQuery) {
        mPackageName = packageName;
        mEventLogsQuery = eventLogsQuery;
    }

    private final ServiceConnection connection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    mQuery.set(IQueryService.Stub.asInterface(service));
                    mConnectionCountdown.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName className) {
                    Log.i(LOG_TAG, "Service disconnected from " + className);
                }
            };

    @Override
    public E get(Instant earliestLogTime) {
        ensureInitialised();
        Bundle data = createRequestBundle();
        try {
            Bundle resultMessage = mQuery.get().get(id, data);
            E e = (E) resultMessage.getSerializable(EVENT_KEY);
            while (e != null && !mEventLogsQuery.filterAll(e)) {
                resultMessage = mQuery.get().getNext(id, data);
                e = (E) resultMessage.getSerializable(EVENT_KEY);
            }
            return e;
        } catch (RemoteException e) {
            throw new AssertionError("Error making cross-process call", e);
        }
    }

    @Override
    public E next(Instant earliestLogTime) {
        ensureInitialised();
        Bundle data = createRequestBundle();
        try {
            Bundle resultMessage = mQuery.get().next(id, data);
            E e = (E) resultMessage.getSerializable(EVENT_KEY);
            while (e != null && !mEventLogsQuery.filterAll(e)) {
                resultMessage = mQuery.get().next(id, data);
                e = (E) resultMessage.getSerializable(EVENT_KEY);
            }
            return e;
        } catch (RemoteException e) {
            throw new AssertionError("Error making cross-process call", e);
        }
    }

    @Override
    public E poll(Instant earliestLogTime, Duration timeout) {
        ensureInitialised();
        Instant endTime = Instant.now().plus(timeout);
        Bundle data = createRequestBundle();
        Duration remainingTimeout = Duration.between(Instant.now(), endTime);
        data.putSerializable(TIMEOUT_KEY, remainingTimeout);
        try {
            Bundle resultMessage = mQuery.get().poll(id, data);
            E e = (E) resultMessage.getSerializable(EVENT_KEY);
            while (e != null && !mEventLogsQuery.filterAll(e)) {
                remainingTimeout = Duration.between(Instant.now(), endTime);
                data.putSerializable(TIMEOUT_KEY, remainingTimeout);
                resultMessage = mQuery.get().poll(id, data);
                e = (E) resultMessage.getSerializable(EVENT_KEY);
            }
            return e;
        } catch (RemoteException e) {
            throw new AssertionError("Error making cross-process call", e);
        }
    }

    private Bundle createRequestBundle() {
        Bundle data = new Bundle();
        data.putSerializable(EARLIEST_LOG_TIME_KEY, EventLogs.sEarliestLogTime);
        return data;
    }

    private AtomicReference<IQueryService> mQuery = new AtomicReference<>();
    private CountDownLatch mConnectionCountdown;

    private void ensureInitialised() {
        if (mQuery.get() != null) {
            return;
        }

        blockingConnectOrFail();
        Bundle data = new Bundle();
        data.putSerializable(QUERIER_KEY, mEventLogsQuery);

        try {
            mQuery.get().init(id, data);
        } catch (RemoteException e) {
            throw new AssertionError("Error making cross-process call", e);
        }
    }

    private void blockingConnectOrFail() {
        mConnectionCountdown = new CountDownLatch(1);
        Intent intent = new Intent();
        intent.setPackage(mPackageName);
        intent.setClassName(mPackageName, "com.android.eventlib.QueryService");

        boolean didBind;
        if (mEventLogsQuery.getUserHandle() != null) {
            didBind = sContext.bindServiceAsUser(
                    intent, connection, /* flags= */ BIND_AUTO_CREATE,
                    mEventLogsQuery.getUserHandle());
        } else {
            didBind = sContext.bindService(intent, connection, /* flags= */ BIND_AUTO_CREATE);
        }

        if (didBind) {
            try {
                mConnectionCountdown.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new AssertionError("Interrupted while binding to service", e);
            }
        }

        if (mQuery.get() == null) {
            throw new AssertionError("Tried to bind but failed");
        }
    }
}
