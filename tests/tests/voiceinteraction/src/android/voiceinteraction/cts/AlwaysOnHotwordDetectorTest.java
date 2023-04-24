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
import static android.Manifest.permission.DEVICE_POWER;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.POWER_SAVER;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.SOUND_TRIGGER_RUN_IN_BATTERY_SAVER;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.service.voice.SoundTriggerFailure.ERROR_CODE_MODULE_DIED;
import static android.service.voice.SoundTriggerFailure.ERROR_CODE_RECOGNITION_RESUME_FAILED;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.MANAGE_VOICE_KEYPHRASES;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseRecognitionExtraList;
import static android.voiceinteraction.cts.testcore.Helper.createKeyprhaseArray;
import static android.voiceinteraction.cts.testcore.Helper.waitForFutureDoneAndAssertSuccessful;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.hardware.soundtrigger.SoundTrigger;
import android.media.soundtrigger.SoundTriggerInstrumentation;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.os.BatterySaverPolicyConfig;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver;
import android.util.Log;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedClassRule;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceOverrideEnrollmentRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.RequiredFeatureRule;

import com.google.common.util.concurrent.ListenableFuture;

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

    private final SoundTriggerInstrumentationObserver mInstrumentationObserver =
            new SoundTriggerInstrumentationObserver();

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
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, MANAGE_HOTWORD_DETECTION,
                        SOUND_TRIGGER_RUN_IN_BATTERY_SAVER);
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
        // Requires MANAGE_SOUND_TRIGGER
        runWithShellPermissionIdentity(() ->
                mInstrumentationObserver.attachInstrumentation());
    }

    @After
    public void tearDown() {
        // Destroy the framework session
        if (mAlwaysOnHotwordDetector != null) {
            mAlwaysOnHotwordDetector.destroy();
        }

        // Clean up any unexpected HAL state
        try {
            mInstrumentationObserver.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Clear the service state
        getService().resetState();
        // Drop any permissions we may still have
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testStartRecognition_success() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        // Start recognition
        ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                mInstrumentationObserver.listenOnRecognitionStarted();
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                onRecognitionStartedFuture);
        assertThat(recognitionSession).isNotNull();

        getService().initDetectRejectLatch();
        recognitionSession.triggerRecognitionEvent(new byte[]{0x11, 0x22},
                createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    @Test
    public void testHalIsDead_onFailureReceived() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        // We don't get callbacks if we don't have a recognition started
        ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                mInstrumentationObserver.listenOnRecognitionStarted();
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        assertThat(waitForFutureDoneAndAssertSuccessful(onRecognitionStartedFuture)).isNotNull();

        // Cause a restart
        getService().initOnFailureLatch();
        mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation().triggerRestart();
        getService().waitOnFailureCalled();
        var failure = getService().getSoundTriggerFailure();
        assertThat(failure.getErrorCode()).isEqualTo(ERROR_CODE_MODULE_DIED);
    }

    @Test
    public void testRecognitionResumedFailed_onFailureReceived() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                mInstrumentationObserver.listenOnRecognitionStarted();
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                onRecognitionStartedFuture);
        assertThat(recognitionSession).isNotNull();

        instrumentation.setResourceContention(true);

        getService().initOnRecognitionPausedLatch();
        // Induce a recognition pause
        recognitionSession.triggerAbortRecognition();
        getService().waitOnRecognitionPausedCalled();

        getService().initOnFailureLatch();
        // Framework will attempt to resume recognition, but will fail due to set contention
        instrumentation.triggerOnResourcesAvailable();

        getService().waitOnFailureCalled();
        var failure = getService().getSoundTriggerFailure();
        assertThat(failure.getErrorCode()).isEqualTo(ERROR_CODE_RECOGNITION_RESUME_FAILED);
        instrumentation.setResourceContention(false);
    }

    @ApiTest(apis = {"AlwaysOnHotwordDetector.Callback#onRecognitionPaused",
            "AlwaysOnHotwordDetector.Callback#onRecognitionResumed"})
    @Test
    public void testAbortRecognitionAndOnResourceAvailable_recognitionPausedAndResumed()
            throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        SoundTriggerInstrumentation instrumentation =
                mInstrumentationObserver.getGlobalCallbackObserver().getInstrumentation();
        ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                mInstrumentationObserver.listenOnRecognitionStarted();
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
        RecognitionSession recognitionSession = waitForFutureDoneAndAssertSuccessful(
                onRecognitionStartedFuture);
        assertThat(recognitionSession).isNotNull();

        getService().initOnRecognitionPausedLatch();
        // Prevent unexpected start recognition
        // TODO (b/275079746) - after fix, ensure that we don't have
        // an unexpected start recognition, or an onError following
        // an abort recognition.
        instrumentation.setResourceContention(true);
        // Induce a recognition pause
        recognitionSession.triggerAbortRecognition();
        // Unexpected onError will be received here as well
        getService().waitOnRecognitionPausedCalled();

        getService().initOnRecognitionResumedLatch();
        // This will trigger resources available
        instrumentation.setResourceContention(false);
        // mInstrumentation.triggerOnResourcesAvailable();
        getService().waitOnRecognitionResumedCalled();
    }

    @Test
    public void testStartRecognitionNoFlagBatterySaverAllEnabled_noRecognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                    mInstrumentationObserver.listenOnRecognitionStarted();
            mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(onRecognitionStartedFuture)).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_ENABLED, no onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager, PowerManager.SOUND_TRIGGER_MODE_ALL_ENABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            assertThat(getService().waitNoOnRecognitionPausedCalled()).isTrue();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @Test
    public void testStartRecognitionNoFlagBatterySaverCriticalOnly_recognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                    mInstrumentationObserver.listenOnRecognitionStarted();
            mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(onRecognitionStartedFuture)).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_CRITICAL_ONLY, onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_CRITICAL_ONLY);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            getService().waitOnRecognitionPausedCalled();

            // disable battery saver, onRecognitionResumed called
            getService().initOnRecognitionResumedLatch();
            BatteryUtils.enableBatterySaver(/* isEnabled= */ false);
            getService().waitOnRecognitionResumedCalled();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @Test
    public void testStartRecognitionNoFlagBatterySaverAllDisabled_recognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                    mInstrumentationObserver.listenOnRecognitionStarted();
            mAlwaysOnHotwordDetector.startRecognition(0, new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(onRecognitionStartedFuture)).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_DISABLED, onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            getService().waitOnRecognitionPausedCalled();

            // disable battery saver, onRecognitionResumed called
            getService().initOnRecognitionResumedLatch();
            BatteryUtils.enableBatterySaver(/* isEnabled= */ false);
            getService().waitOnRecognitionResumedCalled();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @Test
    public void testStartRecognitionWithFlagBatterySaverAllEnabled_noRecognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        try {
            ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                    mInstrumentationObserver.listenOnRecognitionStarted();
            mAlwaysOnHotwordDetector.startRecognition(
                    AlwaysOnHotwordDetector.RECOGNITION_FLAG_RUN_IN_BATTERY_SAVER,
                    new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(onRecognitionStartedFuture)).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_ENABLED, no onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager, PowerManager.SOUND_TRIGGER_MODE_ALL_ENABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            assertThat(getService().waitNoOnRecognitionPausedCalled()).isTrue();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @Test
    public void testStartRecognitionWithFlagBatterySaverCriticalOnly_noRecognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();
        try {
            ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                    mInstrumentationObserver.listenOnRecognitionStarted();
            mAlwaysOnHotwordDetector.startRecognition(
                    AlwaysOnHotwordDetector.RECOGNITION_FLAG_RUN_IN_BATTERY_SAVER,
                    new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(onRecognitionStartedFuture)).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_CRITICAL_ONLY, no onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_CRITICAL_ONLY);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            assertThat(getService().waitNoOnRecognitionPausedCalled()).isTrue();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @Test
    public void testStartRecognitionWithFlagBatterySaverAllDisabled_recognitionPaused()
            throws Exception {
        BatteryUtils.assumeBatterySaverFeature();

        final PowerManager powerManager = sContext.getSystemService(PowerManager.class);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        try {
            ListenableFuture<RecognitionSession> onRecognitionStartedFuture =
                    mInstrumentationObserver.listenOnRecognitionStarted();
            mAlwaysOnHotwordDetector.startRecognition(
                    AlwaysOnHotwordDetector.RECOGNITION_FLAG_RUN_IN_BATTERY_SAVER,
                    new byte[]{1, 2, 3, 4, 5});
            assertThat(
                    waitForFutureDoneAndAssertSuccessful(onRecognitionStartedFuture)).isNotNull();

            BatteryUtils.runDumpsysBatteryUnplug();

            // enable battery saver with SOUND_TRIGGER_MODE_ALL_DISABLED, onRecognitionPaused
            // called
            getService().initOnRecognitionPausedLatch();
            setSoundTriggerPowerSaveMode(powerManager,
                    PowerManager.SOUND_TRIGGER_MODE_ALL_DISABLED);
            BatteryUtils.enableBatterySaver(/* isEnabled= */ true);
            getService().waitOnRecognitionPausedCalled();

            // disable battery saver, onRecognitionResumed called
            getService().initOnRecognitionResumedLatch();
            BatteryUtils.enableBatterySaver(/* isEnabled= */ false);
            getService().waitOnRecognitionResumedCalled();
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
            BatteryUtils.resetBatterySaver();
        }
    }

    @Test
    public void testAfterDestroy_detectorIsInvalid() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        adoptSoundTriggerPermissions();
        mAlwaysOnHotwordDetector.destroy();

        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.startRecognition());
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.stopRecognition());
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.getParameter(
                        AlwaysOnHotwordDetector.MODEL_PARAM_THRESHOLD_FACTOR));
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.setParameter(
                        AlwaysOnHotwordDetector.MODEL_PARAM_THRESHOLD_FACTOR, 10));
        assertThrows(IllegalStateException.class, () ->
                mAlwaysOnHotwordDetector.queryParameter(
                        AlwaysOnHotwordDetector.MODEL_PARAM_THRESHOLD_FACTOR));
    }

    private static void setSoundTriggerPowerSaveMode(PowerManager powerManager, int mode) {
        final BatterySaverPolicyConfig newFullPolicyConfig =
                new BatterySaverPolicyConfig.Builder(powerManager.getFullPowerSavePolicy())
                        .setSoundTriggerMode(mode)
                        .build();
        runWithShellPermissionIdentity(
                () -> powerManager.setFullPowerSavePolicy(newFullPolicyConfig), DEVICE_POWER,
                POWER_SAVER);
    }
}
