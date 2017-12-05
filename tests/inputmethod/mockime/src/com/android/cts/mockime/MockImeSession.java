/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.mockime;

import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;

import com.android.compatibility.common.util.PollingCheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Represents an active Mock IME session, which provides basic primitives to write end-to-end tests
 * for IME APIs.
 *
 * <p>To use {@link MockIme} via {@link MockImeSession}, you need to </p>
 * <p>Public methods are not thread-safe.</p>
 */
public class MockImeSession implements AutoCloseable {
    private final String mImeEventActionName =
            "com.android.cts.mockime.action.IME_EVENT." + SystemClock.elapsedRealtimeNanos();

    /** Setting file name to store initialization settings for {@link MockIme}. */
    static final String MOCK_IME_SETTINGS_FILE = "mockimesettings.data";

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    @NonNull
    private final Context mContext;

    private final HandlerThread mHandlerThread = new HandlerThread("EventReceiver");

    private final static class EventStore {
        private final static int INITIAL_ARRAY_SIZE = 32;

        @NonNull
        public final ImeEvent[] mArray;
        public int mLength;

        public EventStore() {
            mArray = new ImeEvent[INITIAL_ARRAY_SIZE];
            mLength = 0;
        }

        public EventStore(EventStore src, int newLength) {
            mArray = new ImeEvent[newLength];
            mLength = src.mLength;
            System.arraycopy(src.mArray, 0, mArray, 0, src.mLength);
        }

        public EventStore add(ImeEvent event) {
            if (mLength + 1 <= mArray.length) {
                mArray[mLength] = event;
                ++mLength;
                return this;
            } else {
                return new EventStore(this, mLength * 2).add(event);
            }
        }

        public ImeEventStream.ImeEventArray takeSnapshot() {
            return new ImeEventStream.ImeEventArray(mArray, mLength);
        }
    }

    private static final class MockImeEventReceiver extends BroadcastReceiver {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        @NonNull
        private EventStore mCurrentEventStore = new EventStore();

        @NonNull
        private final String mActionName;

        public MockImeEventReceiver(@NonNull String actionName) {
            mActionName = actionName;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(mActionName, intent.getAction())) {
                synchronized (mLock) {
                    mCurrentEventStore =
                            mCurrentEventStore.add(ImeEvent.fromBundle(intent.getExtras()));
                }
            }
        }

        public ImeEventStream.ImeEventArray takeEventSnapshot() {
            synchronized (mLock) {
                return mCurrentEventStore.takeSnapshot();
            }
        }
    }
    private final MockImeEventReceiver mEventReceiver =
            new MockImeEventReceiver(mImeEventActionName);

    private final ImeEventStream mEventStream =
            new ImeEventStream(mEventReceiver::takeEventSnapshot);

    private static String executeShellCommand(
            @NonNull UiAutomation uiAutomation, @NonNull String command) {
        try (ParcelFileDescriptor.AutoCloseInputStream in =
                     new ParcelFileDescriptor.AutoCloseInputStream(
                             uiAutomation.executeShellCommand(command))) {
            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                final String line = br.readLine();
                return line != null ? line.trim() : "";
            }
        } catch (IOException e) {
            return "";
        }
    }

    @Nullable
    private String getCurrentInputMethodId() {
        // TODO: Replace this with IMM#getCurrentInputMethodIdForTesting()
        return Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
    }

    @Nullable
    private static void writeMockImeSettings(@NonNull Context context,
            @NonNull String imeEventActionName,
            @Nullable ImeSettings.Builder imeSettings) throws Exception {
        context.deleteFile(MOCK_IME_SETTINGS_FILE);
        try (OutputStream os = context.openFileOutput(MOCK_IME_SETTINGS_FILE, MODE_PRIVATE)) {
            Parcel parcel = null;
            try {
                parcel = Parcel.obtain();
                ImeSettings.writeToParcel(parcel, imeEventActionName, imeSettings);
                os.write(parcel.marshall());
            } finally {
                if (parcel != null) {
                    parcel.recycle();
                }
            }
            os.flush();
        }
    }

    private ComponentName getMockImeComponentName() {
        return MockIme.getComponentName(mContext.getPackageName());
    }

    private String getMockImeId() {
        return MockIme.getImeId(mContext.getPackageName());
    }

    private MockImeSession(Context context) {
        mContext = context;
    }

    private void initialize(@NonNull UiAutomation uiAutomation,
            @Nullable ImeSettings.Builder imeSettings) throws Exception {
        // Make sure that MockIME is not selected.
        mContext.getPackageManager().setComponentEnabledSetting(getMockImeComponentName(),
                COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);

        PollingCheck.check("Make sure that MockIME becomes unavailable", TIMEOUT, () ->
                mContext.getSystemService(InputMethodManager.class)
                        .getInputMethodList()
                        .stream()
                        .noneMatch(info -> getMockImeComponentName().equals(info.getComponent())));

        writeMockImeSettings(mContext, mImeEventActionName, imeSettings);

        mHandlerThread.start();
        mContext.registerReceiver(mEventReceiver,
                new IntentFilter(mImeEventActionName), null /* broadcastPermission */,
                new Handler(mHandlerThread.getLooper()));

        // Enable MockIME
        mContext.getPackageManager().setComponentEnabledSetting(
                getMockImeComponentName(), COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);

        PollingCheck.check("Wait until the Mock IME is recognized by the IMMS again",
                TIMEOUT,
                () -> mContext.getSystemService(InputMethodManager.class)
                        .getInputMethodList()
                        .stream()
                        .anyMatch(info -> getMockImeComponentName().equals(info.getComponent())));

        executeShellCommand(uiAutomation, "ime enable " + getMockImeId());
        executeShellCommand(uiAutomation, "ime set " + getMockImeId());

        PollingCheck.check("Make sure that MockIME becomes available", TIMEOUT,
                () -> getMockImeId().equals(getCurrentInputMethodId()));
    }

    /**
     * Creates a new Mock IME session. During this session, you can receive various events from
     * {@link MockIme}.
     *
     * @param context {@link Context} to be used to receive inter-process events from the
     *                {@link MockIme} (e.g. via {@link BroadcastReceiver}
     * @param uiAutomation {@link UiAutomation} object to change the device state that are typically
     *                     guarded by permissions.
     * @param imeSettings Key-value pairs to be passed to the {@link MockIme}.
     * @return A session object, with which you can retrieve event logs from the {@link MockIme} and
     *         can clean up the session.
     */
    @NonNull
    public static MockImeSession create(
            @NonNull Context context,
            @NonNull UiAutomation uiAutomation,
            @Nullable ImeSettings.Builder imeSettings) throws Exception {
        final MockImeSession client = new MockImeSession(context);
        client.initialize(uiAutomation, imeSettings);
        return client;
    }

    /**
     * @return {@link ImeEventStream} object that stores events sent from {@link MockIme} since the
     *         session is created.
     */
    public ImeEventStream openEventStream() {
        return mEventStream.copy();
    }

    /**
     * Closes the active session and de-selects {@link MockIme}. Currently which IME will be
     * selected next is up to the system.
     */
    public void close() throws Exception {
        final ComponentName mockImeComponent = MockIme.getComponentName(mContext.getPackageName());

        // Kill Mock IME process
        // TODO: Add a new Test API to make which IME will be selected next deterministic.
        mContext.getPackageManager().setComponentEnabledSetting(
                mockImeComponent, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);

        PollingCheck.check("Make sure that MockIME becomes unavailable", TIMEOUT, () ->
                mContext.getSystemService(InputMethodManager.class)
                        .getInputMethodList()
                        .stream()
                        .noneMatch(info -> getMockImeComponentName().equals(info.getComponent())));

        mContext.unregisterReceiver(mEventReceiver);
        mHandlerThread.quitSafely();
        mContext.deleteFile(MOCK_IME_SETTINGS_FILE);
    }
}
