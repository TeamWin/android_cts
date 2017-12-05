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

import static com.android.cts.mockime.MockImeSession.MOCK_IME_SETTINGS_FILE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputBinding;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Mock IME for end-to-end tests.
 */
public final class MockIme extends InputMethodService {

    private static final String TAG = "MockIme";

    static ComponentName getComponentName(@NonNull String packageName) {
        return new ComponentName(packageName, MockIme.class.getName());
    }

    static String getImeId(@NonNull String packageName) {
        return new ComponentName(packageName, MockIme.class.getName()).flattenToShortString();
    }

    @Nullable
    private ImeSettings mSettings;

    private final AtomicReference<String> mImeEventActionName = new AtomicReference<>();

    @Nullable
    String getImeEventActionName() {
        return mImeEventActionName.get();
    }

    private class MockInputMethodImpl extends InputMethodImpl {
        @Override
        public void showSoftInput(int flags, ResultReceiver resultReceiver) {
            getTracer().showSoftInput(flags, resultReceiver,
                    () -> super.showSoftInput(flags, resultReceiver));
        }

        @Override
        public void hideSoftInput(int flags, ResultReceiver resultReceiver) {
            getTracer().hideSoftInput(flags, resultReceiver,
                    () -> super.hideSoftInput(flags, resultReceiver));
        }

        @Override
        public void attachToken(IBinder token) {
            getTracer().attachToken(token, () -> super.attachToken(token));
        }

        @Override
        public void bindInput(InputBinding binding) {
            getTracer().bindInput(binding, () -> super.bindInput(binding));
        }

        @Override
        public void unbindInput() {
            getTracer().unbindInput(() -> super.unbindInput());
        }
    }

    @Nullable
    private ImeSettings readSettings() {
        try (InputStream is = openFileInput(MOCK_IME_SETTINGS_FILE)) {
            Parcel parcel = null;
            try {
                parcel = Parcel.obtain();
                final byte[] buffer = new byte[4096];
                while (true) {
                    final int numRead = is.read(buffer);
                    if (numRead <= 0) {
                        break;
                    }
                    parcel.unmarshall(buffer, 0, numRead);
                }
                parcel.setDataPosition(0);
                return new ImeSettings(parcel);
            } finally {
                if (parcel != null) {
                    parcel.recycle();
                }
            }
        } catch (IOException e) {
        }
        return null;
    }

    @Override
    public void onCreate() {
        getTracer().onCreate(() -> {
            super.onCreate();
            mSettings = readSettings();
            if (mSettings == null) {
                throw new IllegalStateException("Settings file is not found. "
                        + "Make sure MockImeSession.create() is used to launch Mock IME.");
            }
            mImeEventActionName.set(mSettings.getEventCallbackActionName());
        });
    }

    @Override
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
        getTracer().onConfigureWindow(win, isFullscreen, isCandidatesOnly,
                () -> super.onConfigureWindow(win, isFullscreen, isCandidatesOnly));
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return getTracer().onEvaluateFullscreenMode(() ->
                mSettings.fullscreenModeAllowed(false) && super.onEvaluateFullscreenMode());
    }

    private static final class KeyboardLayoutView extends LinearLayout {

        public KeyboardLayoutView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark, null));

            {
                final RelativeLayout layout = new RelativeLayout(getContext());
                final TextView textView = new TextView(getContext());
                final RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                textView.setLayoutParams(params);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                textView.setGravity(Gravity.CENTER);
                textView.setText(getImeId(getContext().getPackageName()));
                layout.addView(textView);
                addView(layout, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            }
        }
    }

    @Override
    public View onCreateInputView() {
        return getTracer().onCreateInputView(() -> new KeyboardLayoutView(this));
    }

    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        getTracer().onStartInput(editorInfo, restarting,
                () -> super.onStartInput(editorInfo, restarting));
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        getTracer().onStartInputView(editorInfo, restarting,
                () -> super.onStartInputView(editorInfo, restarting));
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        getTracer().onFinishInputView(finishingInput,
                () -> super.onFinishInputView(finishingInput));
    }

    @Override
    public void onFinishInput() {
        getTracer().onFinishInput(() -> super.onFinishInput());
    }

    @Override
    public void onDestroy() {
        getTracer().onDestroy(() -> super.onDestroy());
    }

    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
        return getTracer().onCreateInputMethodInterface(() -> new MockInputMethodImpl());
    }

    private final ThreadLocal<Tracer> mThreadLocalTracer = new ThreadLocal<>();

    private Tracer getTracer() {
        Tracer tracer = mThreadLocalTracer.get();
        if (tracer == null) {
            tracer = new Tracer(this);
            mThreadLocalTracer.set(tracer);
        }
        return tracer;
    }

    @NonNull
    private ImeState getState() {
        final boolean hasInputBinding = getCurrentInputBinding() != null;
        final boolean hasDummyInputConnectionConnection =
                !hasInputBinding
                        || getCurrentInputConnection() == getCurrentInputBinding().getConnection();
        return new ImeState(hasInputBinding, hasDummyInputConnectionConnection);
    }

    /**
     * Event tracing helper class for {@link MockIme}.
     */
    private static final class Tracer {

        @NonNull
        private final MockIme mIme;

        private final int mThreadId = Process.myTid();

        @NonNull
        private final String mThreadName =
                Thread.currentThread().getName() != null ? Thread.currentThread().getName() : "";

        private final boolean mIsMainThread =
                Looper.getMainLooper().getThread() == Thread.currentThread();

        private int mNestLevel = 0;

        private String mImeEventActionName;

        public Tracer(@NonNull MockIme mockIme) {
            mIme = mockIme;
        }

        private void sendEventInternal(@NonNull ImeEvent event) {
            final Intent intent = new Intent();
            intent.setPackage(mIme.getPackageName());
            if (mImeEventActionName == null) {
                mImeEventActionName = mIme.getImeEventActionName();
            }
            if (mImeEventActionName == null) {
                Log.e(TAG, "Tracer cannot be used before onCreate()");
                return;
            }
            intent.setAction(mImeEventActionName);
            intent.putExtras(event.toBundle());
            mIme.sendBroadcast(intent);
        }

        private void recordEventInternal(@NonNull String eventName, @NonNull Runnable runnable) {
            recordEventInternal(eventName, runnable, new Bundle());
        }

        private void recordEventInternal(@NonNull String eventName, @NonNull Runnable runnable,
                @NonNull Bundle arguments) {
            recordEventInternal(eventName, () -> { runnable.run(); return null; }, arguments);
        }

        private boolean recordEventInternal(@NonNull String eventName,
                @NonNull BooleanSupplier supplier) {
            return recordEventInternal(eventName, () -> supplier.getAsBoolean(), new Bundle());
        }

        private <T> T recordEventInternal(@NonNull String eventName,
                @NonNull Supplier<T> supplier) {
            return recordEventInternal(eventName, supplier, new Bundle());
        }

        private <T> T recordEventInternal(@NonNull String eventName,
                @NonNull Supplier<T> supplier, @NonNull Bundle arguments) {
            final ImeState enterState = mIme.getState();
            final long enterTimestamp = SystemClock.elapsedRealtimeNanos();
            final long enterWallTime = System.currentTimeMillis();
            final int nestLevel = mNestLevel;
            ++mNestLevel;
            T result;
            try {
                result = supplier.get();
            } finally {
                --mNestLevel;
            }
            final long exitTimestamp = SystemClock.elapsedRealtimeNanos();
            final long exitWallTime = System.currentTimeMillis();
            final ImeState exitState = mIme.getState();
            sendEventInternal(new ImeEvent(eventName, nestLevel, mThreadName,
                    mThreadId, mIsMainThread, enterTimestamp, exitTimestamp, enterWallTime,
                    exitWallTime, enterState, exitState, arguments));
            return result;
        }

        public void onCreate(@NonNull Runnable runnable) {
            recordEventInternal("onCreate", runnable);
        }

        public void onConfigureWindow(Window win, boolean isFullscreen,
                boolean isCandidatesOnly, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putBoolean("isFullscreen", isFullscreen);
            arguments.putBoolean("isCandidatesOnly", isCandidatesOnly);
            recordEventInternal("onConfigureWindow", runnable, arguments);
        }

        public boolean onEvaluateFullscreenMode(@NonNull BooleanSupplier runnable) {
            return recordEventInternal("onEvaluateFullscreenMode", runnable);
        }

        public View onCreateInputView(@NonNull Supplier<View> supplier) {
            return recordEventInternal("onCreateInputView", supplier);
        }

        public void onStartInput(EditorInfo editorInfo, boolean restarting,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", editorInfo);
            arguments.putBoolean("restarting", restarting);
            recordEventInternal("onStartInput", runnable, arguments);
        }

        public void onStartInputView(EditorInfo editorInfo, boolean restarting,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", editorInfo);
            arguments.putBoolean("restarting", restarting);
            recordEventInternal("onStartInputView", runnable, arguments);
        }

        public void onFinishInputView(boolean finishingInput, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putBoolean("finishingInput", finishingInput);
            recordEventInternal("onFinishInputView", runnable, arguments);
        }

        public void onFinishInput(@NonNull Runnable runnable) {
            recordEventInternal("onFinishInput", runnable);
        }

        public void onDestroy(@NonNull Runnable runnable) {
            recordEventInternal("onDestroy", runnable);
        }

        public void attachToken(IBinder token, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putBinder("token", token);
            recordEventInternal("attachToken", runnable, arguments);

        }

        public void bindInput(InputBinding binding, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("binding", binding);
            recordEventInternal("bindInput", runnable, arguments);
        }

        public void unbindInput(@NonNull Runnable runnable) {
            recordEventInternal("unbindInput", runnable);
        }

        public void showSoftInput(int flags, ResultReceiver resultReceiver,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("flags", flags);
            arguments.putParcelable("resultReceiver", resultReceiver);
            recordEventInternal("showSoftInput", runnable, arguments);
        }

        public void hideSoftInput(int flags, ResultReceiver resultReceiver,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("flags", flags);
            arguments.putParcelable("resultReceiver", resultReceiver);
            recordEventInternal("hideSoftInput", runnable, arguments);
        }

        public AbstractInputMethodImpl onCreateInputMethodInterface(
                @NonNull Supplier<AbstractInputMethodImpl> supplier) {
            return recordEventInternal("onCreateInputMethodInterface", supplier);
        }
    }
}
