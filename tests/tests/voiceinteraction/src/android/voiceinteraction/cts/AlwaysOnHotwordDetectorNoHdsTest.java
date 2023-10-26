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

package android.voiceinteraction.cts;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;
import static android.voiceinteraction.cts.testcore.Helper.MANAGE_VOICE_KEYPHRASES;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseArray;
import static android.voiceinteraction.cts.testcore.Helper.createKeyphraseRecognitionExtraList;
import static android.voiceinteraction.cts.testcore.Helper.waitForFutureDoneAndAssertSuccessful;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.hardware.soundtrigger.SoundTrigger;
import android.media.soundtrigger.SoundTriggerInstrumentation.RecognitionSession;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.soundtrigger.cts.instrumentation.SoundTriggerInstrumentationObserver;
import android.util.Log;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedClassRule;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceOverrideEnrollmentRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.CtsDownstreamingTest;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tests for {@link AlwaysOnHotwordDetector} APIs. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detector")
public class AlwaysOnHotwordDetectorNoHdsTest {

    private static final String TAG = "AlwaysOnHotwordDetectorNoHdsTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    private static final int WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS = 750;

    private static final Context sContext = getInstrumentation().getTargetContext();
    private static final SoundTrigger.Keyphrase[] KEYPHRASE_ARRAY = createKeyphraseArray(sContext);

    private final SoundTriggerInstrumentationObserver mInstrumentationObserver =
            new SoundTriggerInstrumentationObserver();

    private AtomicBoolean mOpNoted;

    private static final boolean SYSPROP_HOTWORD_DETECTION_SERVICE_REQUIRED =
            SystemProperties.getBoolean("ro.hotword.detection_service_required", false);

    private final AppOpsManager.OnOpNotedListener mOnOpNotedListener =
            (op, uid, pkgName, attributionTag, flags, result) -> {
                Log.d(TAG, "Get OnOpNotedListener callback op = " + op + ", uid = " + uid);
                // We adopt ShellPermissionIdentity to pass the permission check, so the uid should
                // be the shell uid.
                if (Process.SHELL_UID == uid) {
                    mOpNoted.set(true);
                }
            };

    public static final class RequiredApiLevelRule implements TestRule {
        private final int mRequiredApiLevel;
        private final boolean mIsRequiredApiLevel;

        RequiredApiLevelRule(int requiredApiLevel) {
            mRequiredApiLevel = requiredApiLevel;
            mIsRequiredApiLevel = isRequiredApiLevel(mRequiredApiLevel);
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    if (!mIsRequiredApiLevel) {
                        Log.d(TAG, "skipping "
                                + description.getClassName() + "#" + description.getMethodName()
                                + " because it requires API level " + mRequiredApiLevel);
                        assumeTrue("Device is not API level'" + mRequiredApiLevel,
                                mIsRequiredApiLevel);
                        return;
                    }
                    base.evaluate();
                }
            };
        }

        @Override
        public String toString() {
            return "RequiredApiLevelRule[" + mRequiredApiLevel + ", " + mIsRequiredApiLevel + "]";
        }

        static boolean isRequiredApiLevel(int requiredApiLevel) {
            return ApiLevelUtil.isAtLeast(requiredApiLevel);
        }
    }

    // For destroying in teardown
    private AlwaysOnHotwordDetector mAlwaysOnHotwordDetector = null;

    @Rule(order = 1)
    public RequiredApiLevelRule REQUIRES_API_RULE = new RequiredApiLevelRule(
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

    @Rule(order = 2)
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Rule(order = 3)
    public VoiceInteractionServiceOverrideEnrollmentRule mEnrollOverrideRule =
            new VoiceInteractionServiceOverrideEnrollmentRule(getService());

    @ClassRule
    public static final VoiceInteractionServiceConnectedClassRule sServiceRule =
            new VoiceInteractionServiceConnectedClassRule(
                    sContext, getTestVoiceInteractionServiceName());


    @BeforeClass
    public static void setupClass() {
        // TODO(b/276393203) delete this
        SystemClock.sleep(8_000);
    }

    @Before
    public void setup() {
        // Hook up SoundTriggerInstrumentation to inject/observe STHAL operations.
        // Requires MANAGE_SOUND_TRIGGER
        runWithShellPermissionIdentity(mInstrumentationObserver::attachInstrumentation);
        runWithShellPermissionIdentity(
                () -> {
                    sContext.getSystemService(AppOpsManager.class)
                            .startWatchingNoted(
                                    new String[] {
                                        AppOpsManager.OPSTR_RECORD_AUDIO,
                                        AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE,
                                        AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO,
                                    },
                                    mOnOpNotedListener);
                });
        mOpNoted = new AtomicBoolean(false);
    }

    @After
    public void tearDown() {
        runWithShellPermissionIdentity(
                () ->
                        sContext.getSystemService(AppOpsManager.class)
                                .stopWatchingNoted(mOnOpNotedListener));
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

    @CddTest(requirements = {"9.8.2/H-4-1"})
    @Test
    @CtsDownstreamingTest
    public void testStartRecognition_success() throws Exception {
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        startAndTriggerRecognition();
    }

    @CddTest(requirements = {"9.8.2/H-4-1"})
    @Test
    @FlakyTest(bugId = 295591542)
    @CtsDownstreamingTest
    public void ifExemptionEnabled_startRecognition_noRecordOpsNoted() throws Exception {
        assumeFalse(SYSPROP_HOTWORD_DETECTION_SERVICE_REQUIRED);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        startAndTriggerRecognition();

        // in case of any late arriving callbacks
        SystemClock.sleep(WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS);
        assertThat(mOpNoted.get()).isFalse();
    }

    @CddTest(requirements = {"9.8.2/H-4-1"})
    @Test
    @CtsDownstreamingTest
    public void ifExemptionDisabled_startRecognition_RecordOpsNoted() throws Exception {
        assumeTrue(SYSPROP_HOTWORD_DETECTION_SERVICE_REQUIRED);
        createAndEnrollAlwaysOnHotwordDetector();
        // Grab permissions for more than a single call since we get callbacks
        adoptSoundTriggerPermissions();

        startAndTriggerRecognition();

        // in case of any late arriving callbacks
        SystemClock.sleep(WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS);
        assertThat(mOpNoted.get()).isTrue();
    }

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
                        RECORD_AUDIO,
                        CAPTURE_AUDIO_HOTWORD,
                        MANAGE_HOTWORD_DETECTION,
                        MANAGE_VOICE_KEYPHRASES);
    }

    private void createAndEnrollAlwaysOnHotwordDetector() throws InterruptedException {
        mAlwaysOnHotwordDetector = null;
        // Wait onAvailabilityChanged() callback called following AOHD creation.
        getService().initAvailabilityChangeLatch();

        // Load appropriate keyphrase model
        // Required for the model to enter the enrolled state
        runWithShellPermissionIdentity(
                () ->
                        mEnrollOverrideRule
                                .getModelManager()
                                .updateKeyphraseSoundModel(
                                        new SoundTrigger.KeyphraseSoundModel(
                                                new UUID(5, 7),
                                                new UUID(7, 5),
                                                /* data= */ null,
                                                KEYPHRASE_ARRAY)),
                MANAGE_VOICE_KEYPHRASES);

        // Create alwaysOnHotwordDetector
        getService()
                .createAlwaysOnHotwordDetectorNoHotwordDetectionService(
                        /* useExecutor= */ true, /* runOnMainThread= */ true);
        try {
            // Bad naming, this waits for AOHD creation
            getService().waitSandboxedDetectionServiceInitializedCalledOrException();
        } finally {
            // Get the AlwaysOnHotwordDetector instance even if there is an error happened to avoid
            // that we don't destroy the detector in tearDown method. It may be null here. We will
            // check the status below.
            mAlwaysOnHotwordDetector = getService().getAlwaysOnHotwordDetector();
        }

        // Verify that detector creation didn't throw
        assertThat(getService().isCreateDetectorIllegalStateExceptionThrow()).isFalse();
        assertThat(getService().isCreateDetectorSecurityExceptionThrow()).isFalse();

        assertThat(mAlwaysOnHotwordDetector).isNotNull();

        // verify we have entered the ENROLLED state
        getService().waitAvailabilityChangedCalled();
        assertThat(getService().getHotwordDetectionServiceAvailabilityResult())
                .isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
    }

    private void startAndTriggerRecognition() throws InterruptedException {
        // Start recognition
        mAlwaysOnHotwordDetector.startRecognition(0, new byte[] {1, 2, 3, 4, 5});
        RecognitionSession recognitionSession =
                waitForFutureDoneAndAssertSuccessful(
                        mInstrumentationObserver.getOnRecognitionStartedFuture());
        assertThat(recognitionSession).isNotNull();

        // Trigger recognition
        getService().initDetectRejectLatch();
        recognitionSession.triggerRecognitionEvent(
                new byte[] {0x11, 0x22}, createKeyphraseRecognitionExtraList());
        getService().waitOnDetectOrRejectCalled();

        // Validate that we got a result
        AlwaysOnHotwordDetector.EventPayload detectResult =
                getService().getHotwordServiceOnDetectedResult();
        assertThat(detectResult).isNotNull();
    }
}
