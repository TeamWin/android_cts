/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.voiceinteraction.cts;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.MANAGE_SOUND_TRIGGER;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.WAIT_TIMEOUT_IN_MS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.ModuleProperties;
import android.media.soundtrigger.SoundTriggerInstrumentation.ModelSession;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.util.Log;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Tests for {@link AlwaysOnHotwordDetector} APIs.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detector")
public class AlwaysOnHotwordDetectorTest {

    private static final String TAG = "AlwaysOnHotwordDetectorTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";
    // This is not exposed as an API.
    // TODO(b/273567812)
    private static final String MANAGE_VOICE_KEYPHRASES =
        "android.permission.MANAGE_VOICE_KEYPHRASES";
    protected final Context mContext = getInstrumentation().getTargetContext();

    private static final int KEYPHRASE_ID = 1234;

    private CtsBasicVoiceInteractionService mService;
    private SoundTrigger.Keyphrase[] mKeyphrases;

    private Object mLock = new Object();
    private SoundTriggerInstrumentation mInjection = null;
    private ModelSession mModelSession = null;
    private RecognitionSession mRecognitionSession = null;

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(mContext, getTestVoiceInteractionService());

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Before
    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be
        // able to get service
        mService = (CtsBasicVoiceInteractionService) CtsBasicVoiceInteractionService.getService();
        // Limited to single keyphrase
        mKeyphrases = new SoundTrigger.Keyphrase[] {new SoundTrigger.Keyphrase(KEYPHRASE_ID,
                SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER,
                mService.KEYPHRASE_LOCALE,
                mService.KEYPHRASE_TEXT,
                new int[] {mContext.getUserId()}
                )};

        // Hook up SoundTriggerInstrumentation to inject/observe STHAL operations.
        runWithShellPermissionIdentity(() -> {
            mInjection = mContext.getSystemService(SoundTriggerManager.class)
                    .attachInstrumentation(
                        (Runnable r) -> r.run(),
                        (ModelSession modelSession) -> {
                            synchronized (mLock) {
                                mModelSession = modelSession;
                                mModelSession.setModelCallback((Runnable r) -> r.run(),
                                        (RecognitionSession recogSession) -> {
                                            synchronized (mLock) {
                                                mRecognitionSession = recogSession;
                                                mLock.notifyAll();
                                            }
                                        });
                            }
                        });
        }, MANAGE_SOUND_TRIGGER, RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD);
        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(mService);
        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        // TODO delete this
        SystemClock.sleep(5_000);
    }

    @After
    public void tearDown() {
        runWithShellPermissionIdentity(() ->
                mService.createKeyphraseModelManager().setModelDatabaseForTestEnabled(false),
            MANAGE_VOICE_KEYPHRASES);
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        mService = null;
    }

    // TODO(b/272118137)
    // Splitting into several tests relies on detach being plumbed to AOHD.
    @Test
    public void testAlwaysOnHotwordDetector_startRecognitionWithData() throws Exception {

        // Wait onAvailabilityChanged() callback called following AOHD creation.
        mService.initAvailabilityChangeLatch();

        // Override enrolled model with a custom model for test purposes.
        runWithShellPermissionIdentity(() -> {
            final var manager = mService.createKeyphraseModelManager();
            manager.setModelDatabaseForTestEnabled(true);
            manager.updateKeyphraseSoundModel(new SoundTrigger.KeyphraseSoundModel(
                        new UUID(5,7), new UUID(7,5), /* data= */ null, mKeyphrases));
        }, MANAGE_VOICE_KEYPHRASES);

        // Create alwaysOnHotwordDetector and wait onHotwordDetectionServiceInitialized() callback
        mService.createAlwaysOnHotwordDetector(/* useExecutor= */ true, /* mainThread= */ true);

        // verify callback result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);

        // The AlwaysOnHotwordDetector should be created correctly
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);
        // verify we have entered the ENROLLED state
        mService.waitAvailabilityChangedCalled();
        assertThat(mService.getHotwordDetectionServiceAvailabilityResult()).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // Grab permissions for more than a single call since we get callbacks
        getInstrumentation().getUiAutomation().
                adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD,
                        MANAGE_HOTWORD_DETECTION);

        // Start recognition
        boolean startRecognitionThrowException = true;
        try {
            synchronized (mLock) {
                alwaysOnHotwordDetector.startRecognition(0,
                        new byte[]{1, 2, 3, 4, 5});
                mLock.wait(WAIT_TIMEOUT_IN_MS);
                // We received model load, recog start
                assertThat(mModelSession).isNotNull();
                assertThat(mRecognitionSession).isNotNull();
                mService.initDetectRejectLatch();
                mRecognitionSession.triggerRecognitionEvent(
                        new byte[] {0x11, 0x22},
                        /* must include the keyphrase id enrolled by AOHD */
                        Arrays.asList(new SoundTrigger.KeyphraseRecognitionExtra(KEYPHRASE_ID,
                                SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER,
                                10 /* coarse confidence */))
                        );
            }
            // TODO(b/272791099)
            // can't verify callback data because HDS will reject the injected event.
            mService.waitOnDetectOrRejectCalled();
            startRecognitionThrowException = false;
        } catch (UnsupportedOperationException | IllegalStateException e) {
            startRecognitionThrowException = true;
        } finally {
            alwaysOnHotwordDetector.destroy();
        }
        // verify recognition result
        assertThat(startRecognitionThrowException).isFalse();
    }
}
