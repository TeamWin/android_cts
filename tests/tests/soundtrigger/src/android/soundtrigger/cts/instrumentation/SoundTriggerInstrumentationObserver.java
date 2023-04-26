/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.soundtrigger.cts.instrumentation;

import static android.Manifest.permission.MANAGE_SOUND_TRIGGER;

import static com.google.common.truth.Truth.assertThat;

import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerInstrumentation.GlobalCallback;
import android.media.soundtrigger.SoundTriggerInstrumentation.ModelCallback;
import android.media.soundtrigger.SoundTriggerInstrumentation.ModelSession;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionCallback;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.media.soundtrigger.SoundTriggerManager;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Supporting class to observe and attach to the SoundTrigger HAL via
 * {@link SoundTriggerInstrumentation}
 */
public class SoundTriggerInstrumentationObserver implements AutoCloseable {
    private static final String TAG = SoundTriggerInstrumentationObserver.class.getSimpleName();

    /**
     * Observer class exposing {@link ListenableFuture}'s on the {@link GlobalCallback} interface
     *
     * <p>This value gets replaced via {@link #attachInstrumentation}
     */
    private GlobalCallbackObserver mGlobalCallbackObserver;

    /**
     * Attaches to the SoundTrigger HAL instrumentation and registers listeners to HAL events
     *
     * This call requires {@link MANAGE_SOUND_TRIGGER}.
     * and then the permissions are revoked prior to returning.
     */
    public void attachInstrumentation() {
        Log.d(TAG, "attachInstrumentation");
        mGlobalCallbackObserver = new GlobalCallbackObserver();
        mGlobalCallbackObserver.attach();
    }

    /**
     * Observer class exposing {@link ListenableFuture}'s on the {@link ModelCallback} and
     * {@link RecognitionCallback}
     * interfaces.
     */
    public static class ModelSessionObserver implements AutoCloseable {
        private final Object mModelSessionLock = new Object();
        @GuardedBy("mModelSessionLock")
        private SettableFuture<RecognitionSession> mOnRecognitionStarted;
        @GuardedBy("mModelSessionLock")
        private SettableFuture<Void> mOnRecognitionStopped;
        @GuardedBy("mModelSessionLock")
        private SettableFuture<Void> mOnModelUnloaded;
        private final ModelSession mModelSession;

        private final RecognitionCallback mRecognitionCallback = () -> {
            Log.d(TAG, "RecognitionCallback.onRecognitionStopped");
            synchronized (mModelSessionLock) {
                if (mOnRecognitionStopped != null) {
                    mOnRecognitionStopped.set(null);
                }
            }
        };

        private final ModelCallback mModelCallback = new ModelCallback() {
            @Override
            public void onRecognitionStarted(@NonNull RecognitionSession recognitionSession) {
                Log.d(TAG, "ModelCallback.onRecognitionStarted");
                recognitionSession.setRecognitionCallback(Runnable::run, mRecognitionCallback);
                synchronized (mModelSessionLock) {
                    if (mOnRecognitionStarted != null) {
                        mOnRecognitionStarted.set(recognitionSession);
                    }
                }
            }

            @Override
            public void onModelUnloaded() {
                Log.d(TAG, "ModelCallback.onModelUnloaded");
                synchronized (mModelSessionLock) {
                    if (mOnModelUnloaded != null) {
                        mOnModelUnloaded.set(null);
                    }
                }
            }
        };

        private ModelSessionObserver(ModelSession modelSession) {
            mModelSession = modelSession;
            modelSession.setModelCallback(Runnable::run, mModelCallback);
        }

        public ModelSession getModelSession() {
            return mModelSession;
        }

        /**
         * Creates future to be completed on the next {@link ModelCallback#onRecognitionStarted}
         */
        public ListenableFuture<RecognitionSession> listenOnRecognitionStarted() {
            synchronized (mModelSessionLock) {
                if (mOnRecognitionStarted != null) {
                    assertThat(mOnRecognitionStarted.isDone()).isTrue();
                }
                mOnRecognitionStarted = SettableFuture.create();
                return mOnRecognitionStarted;
            }
        }

        /**
         * Creates future to be completed on the next
         * {@link RecognitionCallback#onRecognitionStopped}
         */
        public ListenableFuture<Void> listenOnRecognitionStopped() {
            synchronized (mModelSessionLock) {
                if (mOnRecognitionStopped != null) {
                    assertThat(mOnRecognitionStopped.isDone()).isTrue();
                }
                mOnRecognitionStopped = SettableFuture.create();
                return mOnRecognitionStopped;
            }
        }

        /**
         * Creates future to be completed on the next
         * {@link ModelCallback#onModelUnloaded}
         */
        public ListenableFuture<Void> listenOnModelUnloaded() {
            synchronized (mModelSessionLock) {
                if (mOnModelUnloaded != null) {
                    assertThat(mOnModelUnloaded.isDone()).isTrue();
                }
                mOnModelUnloaded = SettableFuture.create();
                return mOnModelUnloaded;
            }
        }

        @Override
        public void close() throws Exception {
            mModelSession.clearModelCallback();
            synchronized (mModelSessionLock) {
                if (mOnRecognitionStarted != null) {
                    mOnRecognitionStarted.cancel(true /* mayInterruptIfRunning */);
                    mOnRecognitionStarted = null;
                }
                if (mOnRecognitionStopped != null) {
                    mOnRecognitionStopped.cancel(true /* mayInterruptIfRunning */);
                    mOnRecognitionStopped = null;
                }
                if (mOnModelUnloaded != null) {
                    mOnModelUnloaded.cancel(true /* mayInterruptIfRunning */);
                    mOnModelUnloaded = null;
                }
            }
        }
    }

    /**
     * Observer class exposing {@link ListenableFuture}'s on the {@link GlobalCallback} interface
     */
    public static class GlobalCallbackObserver implements AutoCloseable {
        private final Object mGlobalObserverLock = new Object();
        @GuardedBy("mGlobalObserverLock")
        private SettableFuture<ModelSessionObserver> mOnModelLoaded;
        @GuardedBy("mGlobalObserverLock")
        private SettableFuture<Void> mOnClientAttached;
        @GuardedBy("mGlobalObserverLock")
        private SettableFuture<Void> mOnClientDetached;
        // do not expose as an API, only held for cleanup
        @GuardedBy("mGlobalObserverLock")
        private ModelSessionObserver mModelSessionObserver;
        private SoundTriggerInstrumentation mInstrumentation;

        private final GlobalCallback mGlobalCallback = new GlobalCallback() {
            @Override
            public void onModelLoaded(@NonNull ModelSession modelSession) {
                Log.d(TAG, "GlobalCallback.onModelLoaded");
                synchronized (mGlobalObserverLock) {
                    mModelSessionObserver = new ModelSessionObserver(modelSession);
                    if (mOnModelLoaded != null) {
                        mOnModelLoaded.set(mModelSessionObserver);
                    }
                }
            }

            @Override
            public void onClientAttached() {
                Log.d(TAG, "GlobalCallback.onClientAttached");
                synchronized (mGlobalObserverLock) {
                    if (mOnClientAttached != null) {
                        mOnClientAttached.set(null);
                    }
                }
            }

            @Override
            public void onClientDetached() {
                Log.d(TAG, "GlobalCallback.onClientDetached");
                synchronized (mGlobalObserverLock) {
                    if (mOnClientDetached != null) {
                        mOnClientDetached.set(null);
                    }
                }
            }
        };

        /**
         * Attaches to the SoundTrigger HAL instrumentation and registers listeners to HAL events
         */
        private void attach() {
            Log.d(TAG, "attach SoundTriggerInstrumentation");
            mInstrumentation = SoundTriggerManager.attachInstrumentation(
                    Runnable::run, mGlobalCallback);
        }

        /**
         * Instrumentation reference to the SoundTrigger HAL
         *
         * <p>Must call {@link #attach} first
         * <p>This value gets replaced via {@link #attach}
         */
        public SoundTriggerInstrumentation getInstrumentation() {
            assertThat(mInstrumentation).isNotNull();
            return mInstrumentation;
        }

        /**
         * Creates future to be completed on the next {@link GlobalCallback#onModelLoaded}
         */
        public ListenableFuture<ModelSessionObserver> listenOnModelLoaded() {
            synchronized (mGlobalObserverLock) {
                if (mOnModelLoaded != null) {
                    assertThat(mOnModelLoaded.isDone()).isTrue();
                }
                mOnModelLoaded = SettableFuture.create();
                return mOnModelLoaded;
            }
        }

        /**
         * Creates future to be completed on the next {@link GlobalCallback#onClientAttached()}
         */
        public ListenableFuture<Void> listenOnClientAttached() {
            synchronized (mGlobalObserverLock) {
                if (mOnClientAttached != null) {
                    assertThat(mOnClientAttached.isDone()).isTrue();
                }
                mOnClientAttached = SettableFuture.create();
                return mOnClientAttached;
            }
        }

        /**
         * Creates future to be completed on the next {@link GlobalCallback#onClientDetached()}
         */
        public ListenableFuture<Void> listenOnClientDetached() {
            synchronized (mGlobalObserverLock) {
                if (mOnClientDetached != null) {
                    assertThat(mOnClientDetached.isDone()).isTrue();
                }
                mOnClientDetached = SettableFuture.create();
                return mOnClientDetached;
            }
        }

        @Override
        public void close() throws Exception {
            synchronized (mGlobalObserverLock) {
                if (mModelSessionObserver != null) {
                    mModelSessionObserver.close();
                    mModelSessionObserver = null;
                }
                if (mOnModelLoaded != null) {
                    mOnModelLoaded.cancel(true /* mayInterruptIfRunning */);
                    mOnModelLoaded = null;
                }
                if (mOnClientAttached != null) {
                    mOnClientAttached.cancel(true /* mayInterruptIfRunning */);
                    mOnClientAttached = null;
                }
                if (mOnClientDetached != null) {
                    mOnClientDetached.cancel(true /* mayInterruptIfRunning */);
                    mOnClientDetached = null;
                }
            }
            if (mInstrumentation != null) {
                mInstrumentation.triggerRestart();
                mInstrumentation = null;
            }
        }
    }

    /**
     * Get the observer to {@link SoundTriggerInstrumentation} callbacks
     *
     * <p>Must call {@link #attachInstrumentation} first
     */
    public GlobalCallbackObserver getGlobalCallbackObserver() {
        assertThat(mGlobalCallbackObserver).isNotNull();
        return mGlobalCallbackObserver;
    }

    /**
     * Helper method for common listener of recognition started
     */
    public ListenableFuture<RecognitionSession> listenOnRecognitionStarted() {
        return Futures.transformAsync(getGlobalCallbackObserver().listenOnModelLoaded(),
                ModelSessionObserver::listenOnRecognitionStarted, Runnable::run);
    }

    @Override
    public void close() throws Exception {
        if (mGlobalCallbackObserver != null) {
            mGlobalCallbackObserver.close();
            mGlobalCallbackObserver = null;
        }
    }
}
