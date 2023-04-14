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
import static android.service.voice.SoundTriggerFailure.ERROR_CODE_MODULE_DIED;
import static android.service.voice.SoundTriggerFailure.ERROR_CODE_RECOGNITION_RESUME_FAILED;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.MANAGE_VOICE_KEYPHRASES;
import static android.voiceinteraction.cts.testcore.Helper.WAIT_TIMEOUT_IN_MS;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseRecognitionExtraList;
import static android.voiceinteraction.cts.testcore.Helper.createKeyprhaseArray;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.hardware.soundtrigger.SoundTrigger;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerInstrumentation.ModelSession;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.media.soundtrigger.SoundTriggerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.util.Log;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedClassRule;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceOverrideEnrollmentRule;

import androidx.annotation.GuardedBy;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/** Tests for {@link AlwaysOnHotwordDetector} APIs. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detector")
public class AlwaysOnHotwordDetectorTest {

    private static final String TAG = "AlwaysOnHotwordDetectorTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    private static final Context sContext = getInstrumentation().getTargetContext();
    private static final SoundTrigger.Keyphrase[] KEYPHRASE_ARRAY = createKeyprhaseArray(sContext);

    private SoundTriggerInstrumentation mInstrumentation = null;

    private Object mLock = new Object();

    @GuardedBy("mLock")
    private ModelSession mModelSession = null;

    @GuardedBy("mLock")
    private RecognitionSession mRecognitionSession = null;

    // For destroying in teardown
    private AlwaysOnHotwordDetector mAlwaysOnHotwordDetector = null;

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Rule
    public VoiceInteractionServiceOverrideEnrollmentRule mEnrollOverrideRule =
            new VoiceInteractionServiceOverrideEnrollmentRule(getService());

    @ClassRule
    public static final VoiceInteractionServiceConnectedClassRule sServiceRule =
            new VoiceInteractionServiceConnectedClassRule(
                    sContext, getTestVoiceInteractionServiceName());

    private static String getTestVoiceInteractionServiceName() {
        Log.d(TAG, "getTestVoiceInteractionServiceName()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    private static CtsBasicVoiceInteractionService getService() {
        return (CtsBasicVoiceInteractionService) sServiceRule.getService();
    }

    private void adoptSoundTriggerPermissions() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, MANAGE_HOTWORD_DETECTION);
    }

    private void createAndEnrollAlwaysOnHotwordDetector() throws InterruptedException {
        mAlwaysOnHotwordDetector = null;
        // Wait onAvailabilityChanged() callback called following AOHD creation.
        getService().initAvailabilityChangeLatch();

        // Load appropriate keyphrase model
        // Required for the model to enter the enrolled state
        runWithShellPermissionIdentity(
                () -> mEnrollOverrideRule.getModelManager().updateKeyphraseSoundModel(
                        new SoundTrigger.KeyphraseSoundModel(new UUID(5, 7),
                                new UUID(7, 5), /* data= */ null, KEYPHRASE_ARRAY)),
                MANAGE_VOICE_KEYPHRASES);

        // Create alwaysOnHotwordDetector and wait onHotwordDetectionServiceInitialized() callback
        getService().createAlwaysOnHotwordDetectorWithOnFailureCallback(
                        /* useExecutor= */ true, /* mainThread= */ true);

        getService().waitSandboxedDetectionServiceInitializedCalledOrException();
        // Verify that detector creation didn't throw
        assertThat(getService().isCreateDetectorIllegalStateExceptionThrow()).isFalse();
        assertThat(getService().isCreateDetectorSecurityExceptionThrow()).isFalse();

        // verify callback result
        assertThat(getService().getSandboxedDetectionServiceInitializedResult())
                .isEqualTo(HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);

        // The AlwaysOnHotwordDetector should be created correctly
        mAlwaysOnHotwordDetector = getService().getAlwaysOnHotwordDetector();
        assertThat(mAlwaysOnHotwordDetector).isNotNull();

        // verify we have entered the ENROLLED state
        getService().waitAvailabilityChangedCalled();
        assertThat(getService().getHotwordDetectionServiceAvailabilityResult())
                .isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
    }

    @BeforeClass
    public static void setupClass() {
        // TODO(b/276393203) delete this
        SystemClock.sleep(8_000);
    }

    @Before
    public void setup() {
        // Hook up SoundTriggerInstrumentation to inject/observe STHAL operations.
        runWithShellPermissionIdentity(() -> {
            mInstrumentation = sContext.getSystemService(SoundTriggerManager.class)
                    .attachInstrumentation((Runnable r) -> r.run(), (ModelSession modelSession) -> {
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
    }

    @After
    public void tearDown() {
        // Clean up any unexpected HAL state
        mInstrumentation.triggerRestart();

        // Destroy the framework session
        if (mAlwaysOnHotwordDetector != null) {
            mAlwaysOnHotwordDetector.destroy();
        }

        // Clear the service state
        getService().resetState();
        // Drop any permissions we may still have
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testAlwaysOnHotwordDetector_startRecognitionWithData() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Start recognition
        synchronized (mLock) {
            mAlwaysOnHotwordDetector.startRecognition(0, new byte[] {1, 2, 3, 4, 5});
            mLock.wait(WAIT_TIMEOUT_IN_MS);
            // We received model load, recog start
            assertThat(mModelSession).isNotNull();
            assertThat(mRecognitionSession).isNotNull();
            getService().initDetectRejectLatch();
            mRecognitionSession.triggerRecognitionEvent(
                    new byte[] {0x11, 0x22},
                    createKeyphraseRecognitionExtraList());
        }
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    @Test
    public void isCorrectOnFailureReceived_onHalDeath() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        // We don't get callbacks if we don't have a recognition started
        synchronized (mLock) {
            mAlwaysOnHotwordDetector.startRecognition(0,
                    new byte[]{1, 2, 3, 4, 5});
            mLock.wait(WAIT_TIMEOUT_IN_MS);
            // We received model load, recog start
            assertThat(mModelSession).isNotNull();
            assertThat(mRecognitionSession).isNotNull();
        }

        // Cause a restart
        getService().initOnFailureLatch();
        mInstrumentation.triggerRestart();
        getService().waitOnFailureCalled();
        var failure = getService().getSoundTriggerFailure();
        assertThat(failure.getErrorCode()).isEqualTo(ERROR_CODE_MODULE_DIED);
    }

    // TODO(b/271391924) test recognition pause callback, since this test relies on it working
    @Test
    public void isCorrectOnFailureReceived_onResumeFailed() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        synchronized (mLock) {
            getService().initOnFailureLatch();
            mAlwaysOnHotwordDetector.startRecognition(0,
                    new byte[]{1, 2, 3, 4, 5});
            mLock.wait(WAIT_TIMEOUT_IN_MS);
            // We received model load, recog start
            assertThat(mModelSession).isNotNull();
            assertThat(mRecognitionSession).isNotNull();
            mInstrumentation.setResourceContention(true);
            // Induce a recognition pause
            mRecognitionSession.triggerAbortRecognition();
            // Framework will attempt to resume recognition, but will fail due to set contention
            mInstrumentation.triggerOnResourcesAvailable();
            getService().waitOnFailureCalled();
            var failure = getService().getSoundTriggerFailure();
            assertThat(failure.getErrorCode()).isEqualTo(ERROR_CODE_RECOGNITION_RESUME_FAILED);
            mInstrumentation.setResourceContention(false);
        }
    }
}
