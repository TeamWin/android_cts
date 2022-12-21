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
import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.compat.CompatChanges;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordRejectedResult;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.voiceinteraction.common.Utils;
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

/**
 * Tests for {@link HotwordDetectionService}.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public class HotwordDetectionServiceTest {

    private static final String TAG = "HotwordDetectionServiceTest";
    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";
    private static final Long CLEAR_CHIP_MS = 10000L;
    private static final String PRIVACY_CHIP_PKG = "com.android.systemui";
    private static final String PRIVACY_CHIP_ID = "privacy_chip";

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
        // Change screen off timeout to 10 minutes.
        sDefaultScreenOffTimeoutValue = SystemUtil.runShellCommand(
                "settings get system screen_off_timeout");
        SystemUtil.runShellCommand("settings put system screen_off_timeout 600000");
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
        mService = (CtsBasicVoiceInteractionService) CtsBasicVoiceInteractionService.getService();
        // Check the test can get the service
        Objects.requireNonNull(mService);
        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        SystemClock.sleep(5_000);
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
        assertThat(HotwordDetectionService.getMaxCustomInitializationStatus()).isEqualTo(2);
    }

    @Test
    public void testHotwordDetectionService_validHotwordDetectionComponentName_triggerSuccess()
            throws Throwable {
        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetector();

        mService.waitHotwordDetectionServiceInitializedCalledOrException();

        // verify callback result
        assertThat(mService.getHotwordDetectionServiceInitializedResult()).isEqualTo(
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
        mService.waitHotwordDetectionServiceInitializedCalledOrException();

        // Verify IllegalStateException throws
        assertThat(mService.isCreateDetectorSecurityExceptionThrow()).isTrue();
    }

    @Test
    public void testVoiceInteractionService_holdBindHotwordDetectionPermission_triggerFailure()
            throws Throwable {
        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetectorHoldBindHotwordDetectionPermission();

        // Wait the result and verify expected result
        mService.waitHotwordDetectionServiceInitializedCalledOrException();

        // Verify IllegalStateException throws
        assertThat(mService.isCreateDetectorSecurityExceptionThrow()).isTrue();
    }

    @Test
    public void testVoiceInteractionService_disallowCreateAlwaysOnHotwordDetectorTwice()
            throws Throwable {
        final boolean enableMultipleHotwordDetectors = CompatChanges.isChangeEnabled(
                Helper.MULTIPLE_ACTIVE_HOTWORD_DETECTORS);
        Log.d(TAG, "enableMultipleHotwordDetectors = " + enableMultipleHotwordDetectors);
        assumeTrue("Not support multiple hotword detectors", enableMultipleHotwordDetectors);

        Thread.sleep(CLEAR_CHIP_MS);

        // Create first AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

        // Create second AlwaysOnHotwordDetector, it will get the IllegalStateException due to
        // the previous AlwaysOnHotwordDetector is not destroy.
        mService.createAlwaysOnHotwordDetector();

        // Wait the result and verify expected result
        mService.waitHotwordDetectionServiceInitializedCalledOrException();

        // Verify IllegalStateException throws
        assertThat(mService.isCreateDetectorIllegalStateExceptionThrow()).isTrue();

        alwaysOnHotwordDetector.destroy();
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDsp_success() throws Throwable {
        Thread.sleep(CLEAR_CHIP_MS);
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();
        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                    /* status= */ 0, /* soundModelHandle= */ 100, /* captureAvailable= */ true,
                    /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                    /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                    Helper.createFakeAudioFormat(), new byte[1024],
                    Helper.createFakeKeyphraseRecognitionExtraList());

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();

            verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
            // Verify microphone indicator
            verifyMicrophoneChip(/* shouldBePresent= */ true);

            // destroy detector
            alwaysOnHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDsp_rejection() throws Throwable {
        Thread.sleep(CLEAR_CHIP_MS);
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            // pass null data parameter
            alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                    /* status= */ 0, /* soundModelHandle= */ 100, /* captureAvailable= */ true,
                    /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                    /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                    Helper.createFakeAudioFormat(), null,
                    Helper.createFakeKeyphraseRecognitionExtraList());

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            HotwordRejectedResult rejectedResult =
                    mService.getHotwordServiceOnRejectedResult();

            assertThat(rejectedResult).isEqualTo(Helper.REJECTED_RESULT);

            // Verify microphone indicator
            verifyMicrophoneChip(/* shouldBePresent= */ false);

            // destroy detector
            alwaysOnHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromDsp_timeout() throws Throwable {
        Thread.sleep(CLEAR_CHIP_MS);

        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();
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
                    /* status= */ 0, /* soundModelHandle= */ 100, /* captureAvailable= */ true,
                    /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                    /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                    Helper.createFakeAudioFormat(), new byte[1024],
                    Helper.createFakeKeyphraseRecognitionExtraList());

            // wait onError() called and verify the result
            mService.waitOnErrorCalled();

            // Verify microphone indicator
            verifyMicrophoneChip(/* shouldBePresent= */ false);

            // destroy detector
            alwaysOnHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testHotwordDetectionService_destroyDspDetector_activeDetectorRemoved()
            throws Throwable {
        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();
        // destroy detector
        alwaysOnHotwordDetector.destroy();

        try {
            adoptShellPermissionIdentityForHotword();

            assertThrows(IllegalStateException.class, () -> {
                // Can no longer use the detector because it is in an invalid state
                alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                        /* status= */ 0, /* soundModelHandle= */ 100, /* captureAvailable= */ true,
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
        Thread.sleep(CLEAR_CHIP_MS);

        // Create AlwaysOnHotwordDetector
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = createAlwaysOnHotwordDetector();

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

            verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
            // Verify microphone indicator
            verifyMicrophoneChip(/* shouldBePresent= */ true);

            // destroy detector
            alwaysOnHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_onDetectFromMic_success() throws Throwable {
        Thread.sleep(CLEAR_CHIP_MS);

        // Create SoftwareHotwordDetector
        HotwordDetector softwareHotwordDetector = createSoftwareHotwordDetector();
        try {
            adoptShellPermissionIdentityForHotword();

            mService.initDetectRejectLatch();
            softwareHotwordDetector.startRecognition();

            // wait onDetected() called and verify the result
            mService.waitOnDetectOrRejectCalled();
            AlwaysOnHotwordDetector.EventPayload detectResult =
                    mService.getHotwordServiceOnDetectedResult();

            verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
            // Verify microphone indicator
            verifyMicrophoneChip(/* shouldBePresent= */ true);

            softwareHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private HotwordDetector createSoftwareHotwordDetector() throws Throwable {
        // Create SoftwareHotwordDetector
        mService.createSoftwareHotwordDetector();

        mService.waitHotwordDetectionServiceInitializedCalledOrException();

        // verify callback result
        assertThat(mService.getHotwordDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        HotwordDetector softwareHotwordDetector = mService.getSoftwareHotwordDetector();
        Objects.requireNonNull(softwareHotwordDetector);

        return softwareHotwordDetector;
    }

    private AlwaysOnHotwordDetector createAlwaysOnHotwordDetector() throws Throwable {
        // Create AlwaysOnHotwordDetector and wait ready.
        mService.createAlwaysOnHotwordDetector();

        mService.waitHotwordDetectionServiceInitializedCalledOrException();

        // verify callback result
        assertThat(mService.getHotwordDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        return alwaysOnHotwordDetector;
    }

    // TODO: Implement HotwordDetectedResult#equals to override the Bundle equality check; then
    // simply check that the HotwordDetectedResults are equal.
    private void verifyDetectedResult(AlwaysOnHotwordDetector.EventPayload detectedResult,
            HotwordDetectedResult expectedDetectedResult) {
        HotwordDetectedResult hotwordDetectedResult = detectedResult.getHotwordDetectedResult();
        ParcelFileDescriptor audioStream = detectedResult.getAudioStream();
        assertThat(hotwordDetectedResult).isNotNull();
        assertThat(hotwordDetectedResult.getAudioChannel())
                .isEqualTo(expectedDetectedResult.getAudioChannel());
        assertThat(hotwordDetectedResult.getConfidenceLevel())
                .isEqualTo(expectedDetectedResult.getConfidenceLevel());
        assertThat(hotwordDetectedResult.isHotwordDetectionPersonalized())
                .isEqualTo(expectedDetectedResult.isHotwordDetectionPersonalized());
        assertThat(hotwordDetectedResult.getHotwordDurationMillis())
                .isEqualTo(expectedDetectedResult.getHotwordDurationMillis());
        assertThat(hotwordDetectedResult.getHotwordOffsetMillis())
                .isEqualTo(expectedDetectedResult.getHotwordOffsetMillis());
        assertThat(hotwordDetectedResult.getHotwordPhraseId())
                .isEqualTo(expectedDetectedResult.getHotwordPhraseId());
        assertThat(hotwordDetectedResult.getPersonalizedScore())
                .isEqualTo(expectedDetectedResult.getPersonalizedScore());
        assertThat(hotwordDetectedResult.getScore()).isEqualTo(expectedDetectedResult.getScore());
        assertThat(audioStream).isNull();
    }

    private void adoptShellPermissionIdentityForHotword() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD);
    }

    private void verifyMicrophoneChip(boolean shouldBePresent) throws Exception {
        if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TODO ntmyren: test TV indicator
        } else if (sPkgMgr.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            // TODO ntmyren: test Auto indicator
        } else {
            verifyMicrophoneChipHandheld(shouldBePresent);
        }
    }

    private void verifyMicrophoneChipHandheld(boolean shouldBePresent) throws Exception {
        // If the change Id is not present, then isChangeEnabled will return true. To bypass this,
        // the change is set to "false" if present.
        if (SystemUtil.callWithShellPermissionIdentity(() -> CompatChanges.isChangeEnabled(
                Helper.PERMISSION_INDICATORS_NOT_PRESENT, Process.SYSTEM_UID))) {
            return;
        }
        // Ensure the privacy chip is present (or not)
        UiDevice device = UiDevice.getInstance(sInstrumentation);
        final boolean chipFound = device.wait(Until.hasObject(
                By.res(PRIVACY_CHIP_PKG, PRIVACY_CHIP_ID)), CLEAR_CHIP_MS);
        assertEquals("chip display state", shouldBePresent, chipFound);
    }
}
