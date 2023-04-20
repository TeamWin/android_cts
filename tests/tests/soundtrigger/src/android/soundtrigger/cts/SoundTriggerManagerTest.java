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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SoundTriggerManagerTest {
    private static final String TAG = "SoundTriggerManagerTest";
    private static final Context sContext = getInstrumentation().getTargetContext();
    private static final int TIMEOUT = 5000;
    private static final UUID MODEL_UUID = new UUID(5, 7);
    private static Model sModel = Model.create(MODEL_UUID, new UUID(7, 5), new byte[] {0x11}, 1);

    private SoundTriggerManager mManager = null;
    private AppOpsManager mAppOpsManager = null;
    private SoundTriggerInstrumentation mInstrumentation = null;

    private final Object mLock = new Object();

    private Handler mHandler = null;
    private ModelSession mModelSession = null;
    private RecognitionSession mRecognitionSession = null;

    private CountDownLatch mSessionLatch;
    private CountDownLatch mDetectedLatch;

    @Before
    public void setup() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER, RECORD_AUDIO);

        // The fake STHAL should always be available
        mManager =
                sContext.getSystemService(SoundTriggerManager.class).createManagerForTestModule();
        mManager.updateModel(sModel);

        var thread = new HandlerThread("SoundTriggerDetectorHandler");
        thread.start();
        mHandler = new Handler(thread.getLooper());

        mAppOpsManager = sContext.getSystemService(AppOpsManager.class);
        mSessionLatch = new CountDownLatch(1);
        mDetectedLatch = new CountDownLatch(1);

        // Hook up SoundTriggerInstrumentation to inject/observe STHAL operations.
        mInstrumentation =
                mManager.attachInstrumentation(
                        Runnable::run,
                        (ModelSession modelSession) -> {
                            synchronized (mLock) {
                                modelSession.setModelCallback(
                                        Runnable::run,
                                        (RecognitionSession recogSession) -> {
                                            synchronized (mLock) {
                                                mRecognitionSession = recogSession;
                                                mSessionLatch.countDown();
                                            }
                                        });
                                mModelSession = modelSession;
                            }
                        });
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @After
    public void tearDown() {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        runWithShellPermissionIdentity(() -> mManager.deleteModel(MODEL_UUID));
        mInstrumentation.triggerRestart();
    }

    private boolean startRecognitionForTest() {
        var res = mManager.loadSoundModel(sModel.getSoundModel());
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
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(CAPTURE_AUDIO_HOTWORD, MANAGE_SOUND_TRIGGER);
        assertThat(startRecognitionForTest()).isFalse();
    }

    @Test
    public void testAttachInvalidSession_whenNoDspAvailable() {
        getSoundTriggerPermissions();
        if (mManager.listModuleProperties().size() == 1) {
            var manager = sContext.getSystemService(SoundTriggerManager.class);
            assertThrows(
                    IllegalStateException.class,
                    () -> manager.loadSoundModel(sModel.getSoundModel()));
        }
    }

    @Test
    public void testNullModuleProperties_whenNoDspAvailable() {
        getSoundTriggerPermissions();
        if (mManager.listModuleProperties().size() == 1) {
            var manager = sContext.getSystemService(SoundTriggerManager.class);
            assertThat(manager.getModuleProperties()).isNull();
        }
    }

    @Test
    public void testStartRecognitionSucceeds_whenHoldingPermissions() throws Exception {
        getSoundTriggerPermissions();
        assertThat(startRecognitionForTest()).isTrue();
        // Verify that the session is set up correctly
        assertThat(mSessionLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        synchronized (mLock) {
            mRecognitionSession.triggerRecognitionEvent(new byte[] {0x11}, null);
        }
        assertThat(mDetectedLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }
}
