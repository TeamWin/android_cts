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

package android.soundtrigger.cts;

import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_SOUND_TRIGGER;
import static android.Manifest.permission.RECORD_AUDIO;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.AppOpsManager;
import android.content.Context;
import android.media.soundtrigger.SoundTriggerDetector.Callback;
import android.media.soundtrigger.SoundTriggerDetector.EventPayload;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerInstrumentation.ModelSession;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.media.soundtrigger.SoundTriggerManager;
import android.media.soundtrigger.SoundTriggerManager.Model;
import android.os.Handler;
import android.os.HandlerThread;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@RunWith(AndroidJUnit4.class)
public class SoundTriggerManagerTest {
    private static final String TAG = "SoundTriggerManagerTest";
    private static final Context sContext = getInstrumentation().getTargetContext();
    private static final int TIMEOUT = 5000;
    private static final UUID MODEL_UUID = new UUID(5, 7);
    private static Model sModel = Model.create(MODEL_UUID, new UUID(7, 5), new byte[0], 1);

    private SoundTriggerManager mRealManager = null;
    private SoundTriggerManager mManager = null;
    private SoundTriggerInstrumentation mInstrumentation = null;

    private final SoundTriggerInstrumentationObserver mInstrumentationObserver =
            new SoundTriggerInstrumentationObserver();

    private final CountDownLatch mDetectedLatch = new CountDownLatch(1);

    private Handler mHandler = null;

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Before
    public void setup() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mRealManager = sContext.getSystemService(SoundTriggerManager.class);
        mInstrumentationObserver.attachInstrumentation();
        mManager = mRealManager.createManagerForTestModule();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @After
    public void tearDown() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        try {
            mManager.deleteModel(MODEL_UUID);
        } catch (Exception e) {
        }
        if (mHandler != null) {
            mHandler.getLooper().quit();
        }

        // Clean up any unexpected HAL state
        try {
            mInstrumentationObserver.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    public static <V> V waitForFutureDoneAndAssertSuccessful(Future<V> future) {
        try {
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError("future failed to complete", e);
        }
    }

    private void prepareForRecognition() {
        mManager.updateModel(sModel);
        assertThat(mManager.loadSoundModel(sModel.getSoundModel())).isEqualTo(0);
    }

    private boolean startRecognitionForTest() {
        var thread = new HandlerThread("SoundTriggerDetectorHandler");
        thread.start();
        mHandler = new Handler(thread.getLooper());
        var detector =
                mManager.createSoundTriggerDetector(
                        MODEL_UUID,
                        new Callback() {
                            @Override
                            public void onAvailabilityChanged(int status) {}

                            @Override
                            public void onDetected(EventPayload eventPayload) {
                                mDetectedLatch.countDown();
                            }

                            @Override
                            public void onError() {}

                            @Override
                            public void onRecognitionPaused() {}

                            @Override
                            public void onRecognitionResumed() {}
                        },
                        mHandler);
        return detector.startRecognition(/* flags= */ 0);
    }

    private void getSoundTriggerPermissions() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER);
    }

    @Test
    public void testStartRecognitionFails_whenMissingRecordPermission() {
        getSoundTriggerPermissions();
        prepareForRecognition();
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER);
        assertThat(startRecognitionForTest()).isFalse();
    }

    @Test
    public void testStartRecognitionFails_whenMissingHotwordPermission() {
        getSoundTriggerPermissions();
        prepareForRecognition();
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(RECORD_AUDIO, MANAGE_SOUND_TRIGGER);
        assertThat(startRecognitionForTest()).isFalse();
    }

    @Test
    public void testStartRecognitionSucceeds_whenHoldingPermissions() throws Exception {
        getSoundTriggerPermissions();
        ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                mInstrumentationObserver.listenOnRecognitionStarted();

        prepareForRecognition();
        assertThat(startRecognitionForTest()).isTrue();

        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                onRecognitionStartedFuture);
        assertThat(recognitionSession).isNotNull();

        recognitionSession.triggerRecognitionEvent(new byte[] {0x11}, null);
        assertThat(mDetectedLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRecognitionEvent_notesAppOps() throws Exception {
        getSoundTriggerPermissions();
        ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                mInstrumentationObserver.listenOnRecognitionStarted();

        prepareForRecognition();
        assertThat(startRecognitionForTest()).isTrue();
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                onRecognitionStartedFuture);

        assertThat(recognitionSession).isNotNull();

        var ambientLatch = new CountDownLatch(1);
        AppOpsManager appOpsManager =
                sContext.getSystemService(AppOpsManager.class);
        final String[] OPS_TO_WATCH =
                new String[] {
                    AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO
                };

        runWithShellPermissionIdentity(() ->
                appOpsManager.startWatchingNoted(
                        OPS_TO_WATCH,
                        (op, uid, pkgName, attributionTag, flags, result) -> {
                            if (Objects.equals(
                                    op, AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO)) {
                                ambientLatch.countDown();
                            }
                        }));

        // Grab permissions again since we transitioned out of shell identity
        getSoundTriggerPermissions();

        recognitionSession.triggerRecognitionEvent(new byte[] {0x11}, null);
        assertThat(mDetectedLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(ambientLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testAttachInvalidSession_whenNoDspAvailable() {
        getSoundTriggerPermissions();
        if (mManager.listModuleProperties().size() == 1) {
            assertThrows(
                    IllegalStateException.class,
                    () -> mRealManager.loadSoundModel(sModel.getSoundModel()));
        }
    }

    @Test
    public void testNullModuleProperties_whenNoDspAvailable() {
        getSoundTriggerPermissions();
        if (mManager.listModuleProperties().size() == 1) {
            assertThat(mRealManager.getModuleProperties()).isNull();
        }
    }

    @Test
    public void testAttachThrows_whenMissingRecordPermission() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER);
        assertThrows(
                SecurityException.class,
                () -> mRealManager.createManagerForTestModule());
    }

    @Test
    public void testAttachThrows_whenMissingCaptureHotwordPermission() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(RECORD_AUDIO, MANAGE_SOUND_TRIGGER);
        assertThrows(
                SecurityException.class,
                () -> mRealManager.createManagerForTestModule());
    }

    // TODO test behavior when RECORD_AUDIO is lost for recognition
}
