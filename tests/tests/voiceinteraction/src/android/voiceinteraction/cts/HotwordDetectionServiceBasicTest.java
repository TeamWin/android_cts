/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.DetectorFailure;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetectionServiceFailure;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordDetector.IllegalDetectorStateException;
import android.service.voice.HotwordRejectedResult;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link HotwordDetectionService}.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public class HotwordDetectionServiceBasicTest {

    private static final String TAG = "HotwordDetectionServiceTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final AppOpsManager mAppOpsManager = sInstrumentation.getContext()
            .getSystemService(AppOpsManager.class);

    private final AppOpsManager.OnOpNotedListener mOnOpNotedListener =
            (op, uid, pkgName, attributionTag, flags, result) -> {
                Log.d(TAG, "Get OnOpNotedListener callback op = " + op);
                if (AppOpsManager.OPSTR_RECORD_AUDIO.equals(op)) {
                    mLatch.countDown();
                }
            };

    private CtsBasicVoiceInteractionService mService;

    private static String sWasIndicatorEnabled;
    private static String sDefaultScreenOffTimeoutValue;
    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final PackageManager sPkgMgr = sInstrumentation.getContext().getPackageManager();

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(
                    getInstrumentation().getTargetContext(), getTestVoiceInteractionService());

    @Rule
    public DisableAnimationRule mDisableAnimationRule = new DisableAnimationRule();

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @BeforeClass
    public static void enableIndicators() {
        sWasIndicatorEnabled = Helper.getIndicatorEnabledState();
        Helper.setIndicatorEnabledState(Boolean.toString(true));
    }

    @AfterClass
    public static void resetIndicators() {
        Helper.setIndicatorEnabledState(sWasIndicatorEnabled);
    }

    @BeforeClass
    public static void extendScreenOffTimeout() throws Exception {
        // Change screen off timeout to 20 minutes.
        sDefaultScreenOffTimeoutValue = SystemUtil.runShellCommand(
                "settings get system screen_off_timeout");
        SystemUtil.runShellCommand("settings put system screen_off_timeout 1200000");
    }

    @AfterClass
    public static void restoreScreenOffTimeout() {
        SystemUtil.runShellCommand(
                "settings put system screen_off_timeout " + sDefaultScreenOffTimeoutValue);
    }

    @Before
    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected,
        // the test should be able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check the test can get the service
        Objects.requireNonNull(mService);

        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        // This also waits for mic indicator disappear
        SystemClock.sleep(10_000);
    }

    @After
    public void tearDown() {
        mService = null;
    }

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Test
    public void testHotwordDetectionService_getMaxCustomInitializationStatus()
            throws Throwable {
        // TODO: not use Deprecated method
        assertThat(HotwordDetectionService.getMaxCustomInitializationStatus()).isEqualTo(2);
    }

    @Test
    public void testHotwordDetectionService_validHotwordDetectionComponentName_triggerSuccess()
            throws Throwable {
        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetector();

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        // TODO: not use Deprecated variable
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);

        // The AlwaysOnHotwordDetector should be created correctly
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        alwaysOnHotwordDetector.destroy();
    }

    @Test
    public void testVoiceInteractionService_withoutManageHotwordDetectionPermission_triggerFailure()
            throws Throwable {
        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetectorWithoutManageHotwordDetectionPermission();

        // Wait the result and verify expected result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // Verify SecurityException throws
        assertThat(mService.isCreateDetectorSecurityExceptionThrow()).isTrue();
    }

    @Test
    public void testVoiceInteractionService_holdBindHotwordDetectionPermission_triggerFailure()
            throws Throwable {
        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetectorHoldBindHotwordDetectionPermission();

        // Wait the result and verify expected result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // Verify SecurityException throws
        assertThat(mService.isCreateDetectorSecurityExceptionThrow()).isTrue();
    }

    @Test
    public void testVoiceInteractionService_disallowCreateAlwaysOnHotwordDetectorTwice()
            throws Throwable {
        final boolean enableMultipleHotwordDetectors = Helper.isEnableMultipleDetectors();
        assumeTrue("Not support multiple hotword detectors", enableMultipleHotwordDetectors);

        // Create first AlwaysOnHotwordDetector, it's fine.
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);

        // Create second AlwaysOnHotwordDetector, it will get the IllegalStateException due to
        // the previous AlwaysOnHotwordDetector is not destroy.
        mService.createAlwaysOnHotwordDetector();
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // Verify IllegalStateException throws
        assertThat(mService.isCreateDetectorIllegalStateExceptionThrow()).isTrue();

        alwaysOnHotwordDetector.destroy();
    }

    @Test
    public void testVoiceInteractionService_disallowCreateSoftwareHotwordDetectorTwice()
            throws Throwable {
        final boolean enableMultipleHotwordDetectors = Helper.isEnableMultipleDetectors();
        assumeTrue("Not support multiple hotword detectors", enableMultipleHotwordDetectors);

        // Create first SoftwareHotwordDetector and wait the HotwordDetectionService ready
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                false);

        // Create second SoftwareHotwordDetector, it will get the IllegalStateException due to
        // the previous SoftwareHotwordDetector is not destroy.
        mService.createSoftwareHotwordDetector();
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // Verify IllegalStateException throws
        assertThat(mService.isCreateDetectorIllegalStateExceptionThrow()).isTrue();

        softwareHotwordDetector.destroy();
    }

    @Test
    public void testHotwordDetectionService_processDied_triggerOnError() throws Throwable {
        // Create first AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);

        mService.initOnErrorLatch();

        // Use AlwaysOnHotwordDetector to test process died of HotwordDetectionService
        runWithShellPermissionIdentity(() -> {
            PersistableBundle persistableBundle = new PersistableBundle();
            persistableBundle.putInt(Helper.KEY_TEST_SCENARIO,
                    Helper.EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_CRASH);
            alwaysOnHotwordDetector.updateState(
                    persistableBundle,
                    Helper.createFakeSharedMemoryData());
        }, MANAGE_HOTWORD_DETECTION);

        mService.waitOnErrorCalled();

        // ActivityManager will schedule a timer to restart the HotwordDetectionService due to
        // we crash the service in this test case. It may impact the other test cases when
        // ActivityManager restarts the HotwordDetectionService again. Add the sleep time to wait
        // ActivityManager to restart the HotwordDetectionService, so that the service can be
        // destroyed after finishing this test case.
        Thread.sleep(5000);

        alwaysOnHotwordDetector.destroy();
    }

    @Test
    public void testHotwordDetectionService_processDied_triggerOnFailure() throws Throwable {
        // Create alwaysOnHotwordDetector with onFailure callback
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ true);

        try {
            mService.initOnFailureLatch();

            // Use AlwaysOnHotwordDetector to test process died of HotwordDetectionService
            runWithShellPermissionIdentity(() -> {
                PersistableBundle persistableBundle = new PersistableBundle();
                persistableBundle.putInt(Helper.KEY_TEST_SCENARIO,
                        Helper.EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_CRASH);
                alwaysOnHotwordDetector.updateState(
                        persistableBundle,
                        Helper.createFakeSharedMemoryData());
            }, MANAGE_HOTWORD_DETECTION);

            mService.waitOnFailureCalled();

            verifyHotwordDetectionServiceFailure(mService.getDetectorFailure(),
                    HotwordDetectionServiceFailure.ERROR_CODE_BINDING_DIED);

            // ActivityManager will schedule a timer to restart the HotwordDetectionService due to
            // we crash the service in this test case. It may impact the other test cases when
            // ActivityManager restarts the HotwordDetectionService again. Add the sleep time to
            // wait
            // ActivityManager to restart the HotwordDetectionService, so that the service can be
            // destroyed after finishing this test case.
            Thread.sleep(5000);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDspTimeout_triggerOnFailure()
            throws Throwable {
        // Create alwaysOnHotwordDetector with onFailure callback
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ true);

        try {
            // Update HotwordDetectionService options to delay detection, to cause a timeout
            runWithShellPermissionIdentity(() -> {
                PersistableBundle options = Helper.createFakePersistableBundleData();
                options.putInt(Utils.KEY_DETECTION_DELAY_MS, 5000);
                alwaysOnHotwordDetector.updateState(options,
                        Helper.createFakeSharedMemoryData());
            });

            adoptShellPermissionIdentityForHotword();

            mService.initOnFailureLatch();

            alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                    /* status= */ 0, /* soundModelHandle= */ 100,
                    /* halEventReceivedMillis */ 12345, /* captureAvailable= */ true,
                    /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                    /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                    Helper.createFakeAudioFormat(), new byte[1024],
                    Helper.createFakeKeyphraseRecognitionExtraList());

            // wait onFailure() called and verify the result
            mService.waitOnFailureCalled();

            verifyHotwordDetectionServiceFailure(mService.getDetectorFailure(),
                    HotwordDetectionServiceFailure.ERROR_CODE_DETECT_TIMEOUT);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();

            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDspSecurityException_onFailure()
            throws Throwable {
        // Create alwaysOnHotwordDetector with onFailure callback
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ true);

        try {
            mService.initOnFailureLatch();

            runWithShellPermissionIdentity(() -> {
                alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                        /* status= */ 0, /* soundModelHandle= */ 100,
                        /* halEventReceivedMillis */ 12345, /* captureAvailable= */ true,
                        /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                        /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                        Helper.createFakeAudioFormat(), new byte[1024],
                        Helper.createFakeKeyphraseRecognitionExtraList());
            });

            mService.waitOnFailureCalled();

            verifyHotwordDetectionServiceFailure(mService.getDetectorFailure(),
                    HotwordDetectionServiceFailure.ERROR_CODE_ON_DETECTED_SECURITY_EXCEPTION);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
        }
    }

    @Test
    public void testHotwordDetectionService_onDetectFromExternalSourceSecurityException_onFailure()
            throws Throwable {
        // Create alwaysOnHotwordDetector with onFailure callback
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ true);

        try {
            mService.initOnFailureLatch();

            runWithShellPermissionIdentity(() -> {
                alwaysOnHotwordDetector.startRecognition(Helper.createFakeAudioStream(),
                        Helper.createFakeAudioFormat(), Helper.createFakePersistableBundleData());
            });

            mService.waitOnFailureCalled();

            verifyHotwordDetectionServiceFailure(mService.getDetectorFailure(),
                    HotwordDetectionServiceFailure.ERROR_CODE_ON_DETECTED_SECURITY_EXCEPTION);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromMicSecurityException_onFailure()
            throws Throwable {
        // Create SoftwareHotwordDetector with onFailure callback
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                true);

        try {
            mService.initOnFailureLatch();

            runWithShellPermissionIdentity(() -> {
                softwareHotwordDetector.startRecognition();
            });

            // wait onFailure() called and verify the result
            mService.waitOnFailureCalled();

            verifyHotwordDetectionServiceFailure(mService.getDetectorFailure(),
                    HotwordDetectionServiceFailure.ERROR_CODE_ON_DETECTED_SECURITY_EXCEPTION);
        } finally {
            // destroy detector
            softwareHotwordDetector.destroy();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_createDetectorTwiceQuickly_triggerSuccess()
            throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                false);
        // destroy software hotword detector
        softwareHotwordDetector.destroy();

        // Create AlwaysOnHotwordDetector
        startWatchingNoted();
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);
        try {
            adoptShellPermissionIdentityForHotword();

            verifyOnDetectFromDspSuccess(alwaysOnHotwordDetector);

            // Verify RECORD_AUDIO noted
            verifyRecordAudioNote(/* shouldNote= */ true);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDsp_success() throws Throwable {
        startWatchingNoted();
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);
        try {
            adoptShellPermissionIdentityForHotword();

            verifyOnDetectFromDspSuccess(alwaysOnHotwordDetector);

            // Verify RECORD_AUDIO noted
            verifyRecordAudioNote(/* shouldNote= */ true);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDsp_rejection() throws Throwable {
        startWatchingNoted();
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);
        try {
            mService.initDetectRejectLatch();
            runWithShellPermissionIdentity(() -> {
                // pass null data parameter
                alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                        /* status= */ 0, /* soundModelHandle= */ 100,
                        /* halEventReceivedMillis */ 12345, /* captureAvailable= */ true,
                        /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                        /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                        Helper.createFakeAudioFormat(), null,
                        Helper.createFakeKeyphraseRecognitionExtraList());
            });
            // wait onRejected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            HotwordRejectedResult rejectedResult =
                    mService.getHotwordServiceOnRejectedResult();

            assertThat(rejectedResult).isEqualTo(Helper.REJECTED_RESULT);

            // Verify RECORD_AUDIO does not note
            verifyRecordAudioNote(/* shouldNote= */ false);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
            stopWatchingNoted();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDsp_timeout() throws Throwable {
        startWatchingNoted();
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);
        // Update HotwordDetectionService options to delay detection, to cause a timeout
        runWithShellPermissionIdentity(() -> {
            PersistableBundle options = Helper.createFakePersistableBundleData();
            options.putInt(Utils.KEY_DETECTION_DELAY_MS, 5000);
            alwaysOnHotwordDetector.updateState(options,
                    Helper.createFakeSharedMemoryData());
        });
        try {
            adoptShellPermissionIdentityForHotword();

            mService.initOnErrorLatch();
            alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                    /* status= */ 0, /* soundModelHandle= */ 100,
                    /* halEventReceivedMillis */ 12345, /* captureAvailable= */ true,
                    /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                    /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                    Helper.createFakeAudioFormat(), new byte[1024],
                    Helper.createFakeKeyphraseRecognitionExtraList());

            // wait onError() called and verify the result
            mService.waitOnErrorCalled();

            // Verify RECORD_AUDIO does not note
            verifyRecordAudioNote(/* shouldNote= */ false);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    public void testHotwordDetectionService_destroyDspDetector_activeDetectorRemoved()
            throws Throwable {
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);
        // destroy detector
        alwaysOnHotwordDetector.destroy();
        try {
            adoptShellPermissionIdentityForHotword();

            assertThrows(IllegalStateException.class, () -> {
                // Can no longer use the detector because it is in an invalid state
                alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                        /* status= */ 0, /* soundModelHandle= */ 100,
                        /* halEventReceivedMillis */ 12345, /* captureAvailable= */ true,
                        /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                        /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                        Helper.createFakeAudioFormat(), new byte[1024],
                        Helper.createFakeKeyphraseRecognitionExtraList());
            });
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testHotwordDetectionService_onDetectFromExternalSource_success() throws Throwable {
        startWatchingNoted();
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);
        try {
            adoptShellPermissionIdentityForHotword();

            ParcelFileDescriptor audioStream = Helper.createFakeAudioStream();
            mService.initDetectRejectLatch();
            alwaysOnHotwordDetector.startRecognition(audioStream,
                    Helper.createFakeAudioFormat(),
                    Helper.createFakePersistableBundleData());

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();

            Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);

            // Verify RECORD_AUDIO noted
            verifyRecordAudioNote(/* shouldNote= */ true);
        } finally {
            // destroy detector
            alwaysOnHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromMic_success() throws Throwable {
        startWatchingNoted();
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                false);
        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            softwareHotwordDetector.startRecognition();

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();

            Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);

            // Verify RECORD_AUDIO noted
            verifyRecordAudioNote(/* shouldNote= */ true);
        } finally {
            softwareHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            stopWatchingNoted();
        }
    }

    @Test
    public void testHotwordDetectionService_destroySoftwareDetector_activeDetectorRemoved()
            throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                false);

        // Destroy SoftwareHotwordDetector
        softwareHotwordDetector.destroy();

        try {
            adoptShellPermissionIdentityForHotword();
            // Can no longer use the detector because it is in an invalid state
            assertThrows(IllegalDetectorStateException.class,
                    softwareHotwordDetector::startRecognition);
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onStopDetection() throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                false);
        try {
            adoptShellPermissionIdentityForHotword();

            // The HotwordDetectionService can't report any result after recognition is stopped. So
            // restart it after stopping; then the service can report a special result.
            softwareHotwordDetector.startRecognition();
            softwareHotwordDetector.stopRecognition();
            mService.initDetectRejectLatch();
            softwareHotwordDetector.startRecognition();

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();
            Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT_AFTER_STOP_DETECTION);
        } finally {
            softwareHotwordDetector.destroy();
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_concurrentCapture() throws Throwable {
        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                false);

        try {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                AudioRecord record =
                        new AudioRecord.Builder()
                                .setAudioAttributes(
                                        new AudioAttributes.Builder()
                                                .setInternalCapturePreset(
                                                        MediaRecorder.AudioSource.MIC).build())
                                .setAudioFormat(
                                        new AudioFormat.Builder()
                                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                                .build())
                                .setBufferSizeInBytes(10240) // something large enough to not fail
                                .build();
                assertThat(record.getState()).isEqualTo(AudioRecord.STATE_INITIALIZED);

                try {
                    record.startRecording();

                    mService.initDetectRejectLatch();
                    softwareHotwordDetector.startRecognition();
                    mService.waitOnDetectOrRejectCalled();
                    AlwaysOnHotwordDetector.EventPayload detectResult =
                            mService.getHotwordServiceOnDetectedResult();
                    Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
                    // TODO: Test that it still works after restarting the process or killing audio
                    //  server.
                } finally {
                    record.release();
                }
            });
        } finally {
            softwareHotwordDetector.destroy();
        }
    }

    @Test
    @RequiresDevice
    public void testMultipleDetectors_onDetectFromDspAndMic_success() throws Throwable {
        assumeTrue("Not support multiple hotword detectors",
                Helper.isEnableMultipleDetectors());

        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);

        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector(/* useOnFailure= */
                false);

        try {
            adoptShellPermissionIdentityForHotword();
            // Test AlwaysOnHotwordDetector to be able to detect well
            verifyOnDetectFromDspSuccess(alwaysOnHotwordDetector);

            // Test SoftwareHotwordDetector to be able to detect well
            verifySoftwareDetectorDetectSuccess(softwareHotwordDetector);
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            // Destroy the always on detector
            alwaysOnHotwordDetector.destroy();

            // Destroy the software detector
            softwareHotwordDetector.destroy();
        }
    }

    @Test
    public void testHotwordDetectionService_onHotwordDetectionServiceRestarted() throws Throwable {
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector =
                createAlwaysOnHotwordDetector(/* useOnFailure= */ false);

        mService.initOnHotwordDetectionServiceRestartedLatch();
        // force re-start by shell command
        runShellCommand("cmd voiceinteraction restart-detection");

        // wait onHotwordDetectionServiceRestarted() called
        mService.waitOnHotwordDetectionServiceRestartedCalled();

        // Destroy the always on detector
        alwaysOnHotwordDetector.destroy();
    }

    private void verifyOnDetectFromDspSuccess(AlwaysOnHotwordDetector alwaysOnHotwordDetector)
            throws Throwable {
        mService.initDetectRejectLatch();
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100,
                /* halEventReceivedMillis */ 12345, /* captureAvailable= */ true,
                /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), new byte[1024],
                Helper.createFakeKeyphraseRecognitionExtraList());

        // wait onDetected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                mService.getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    private void verifySoftwareDetectorDetectSuccess(HotwordDetector softwareHotwordDetector)
            throws Exception {
        mService.initDetectRejectLatch();
        softwareHotwordDetector.startRecognition();

        // wait onDetected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                mService.getHotwordServiceOnDetectedResult();
        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
    }

    private void verifyHotwordDetectionServiceFailure(DetectorFailure detectorFailure,
            int errorCode) throws Throwable {
        assertThat(detectorFailure).isNotNull();
        assertThat(detectorFailure).isInstanceOf(HotwordDetectionServiceFailure.class);
        assertThat(((HotwordDetectionServiceFailure) detectorFailure).getErrorCode()).isEqualTo(
                errorCode);
    }

    /**
     * Create software hotword detector and wait for ready
     */
    private HotwordDetector createSoftwareHotwordDetector(boolean useOnFailure) throws Throwable {
        // Create SoftwareHotwordDetector
        if (useOnFailure) {
            mService.createSoftwareHotwordDetectorWithOnFailureCallback(/* useExecutor= */
                    false, /* runOnMainThread= */ false);
        } else {
            mService.createSoftwareHotwordDetector();
        }

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        // TODO: not use Deprecated variable
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        HotwordDetector softwareHotwordDetector = mService.getSoftwareHotwordDetector();
        Objects.requireNonNull(softwareHotwordDetector);

        return softwareHotwordDetector;
    }

    /**
     * Create AlwaysOnHotwordDetector and wait for ready
     */
    private AlwaysOnHotwordDetector createAlwaysOnHotwordDetector(boolean useOnFailure)
            throws Throwable {
        // Create AlwaysOnHotwordDetector and wait ready.
        if (useOnFailure) {
            mService.createAlwaysOnHotwordDetectorWithOnFailureCallback(/* useExecutor= */
                    false, /* runOnMainThread= */ false);
        } else {
            mService.createAlwaysOnHotwordDetector();
        }

        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // verify callback result
        // TODO: not use Deprecated variable
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        return alwaysOnHotwordDetector;
    }

    private void adoptShellPermissionIdentityForHotword() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD);
    }

    private void startWatchingNoted() {
        runWithShellPermissionIdentity(() -> {
            if (mAppOpsManager != null) {
                mAppOpsManager.startWatchingNoted(new String[]{AppOpsManager.OPSTR_RECORD_AUDIO},
                        mOnOpNotedListener);
            }
        });
    }

    private void stopWatchingNoted() {
        runWithShellPermissionIdentity(() -> {
            if (mAppOpsManager != null) {
                mAppOpsManager.stopWatchingNoted(mOnOpNotedListener);
            }
        });
    }

    private void verifyRecordAudioNote(boolean shouldNote) throws Exception {
        if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TODO: test TV indicator
        } else if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            // TODO: test Auto indicator
        } else if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            // The privacy chips/indicators are not implemented on Wear
        } else {
            boolean isNoted = mLatch.await(Helper.CLEAR_CHIP_MS, TimeUnit.MILLISECONDS);
            assertThat(isNoted).isEqualTo(shouldNote);
        }
    }
}
