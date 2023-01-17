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

package android.voiceinteraction.cts.services;

import static android.Manifest.permission.BIND_HOTWORD_DETECTION_SERVICE;
import static android.Manifest.permission.BIND_VISUAL_QUERY_DETECTION_SERVICE;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.voiceinteraction.cts.testcore.Helper.WAIT_TIMEOUT_IN_MS;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.os.Handler;
import android.os.HandlerThread;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.SandboxedDetectionServiceBase;
import android.service.voice.VisualQueryDetector;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The {@link VoiceInteractionService} included a basic HotwordDetectionService for testing.
 */
public class CtsBasicVoiceInteractionService extends BaseVoiceInteractionService {
    private static final String TAG = "CtsBasicVoiceInteractionService";

    private final Handler mHandler;

    // The CountDownLatch waits for a service Availability change result
    private CountDownLatch mAvailabilityChangeLatch;
    // The CountDownLatch waits for a service detect or reject result
    private CountDownLatch mOnDetectRejectLatch;
    // The CountDownLatch waits for a service onError called
    private CountDownLatch mOnErrorLatch;
    // The CountDownLatch waits for vqds
    private CountDownLatch mOnQueryFinishRejectLatch;

    private AlwaysOnHotwordDetector.EventPayload mDetectedResult;
    private HotwordRejectedResult mRejectedResult;
    private ArrayList<String> mStreamedQueries = new ArrayList<>();
    private String mCurrentQuery = "";

    public CtsBasicVoiceInteractionService() {
        HandlerThread handlerThread = new HandlerThread("CtsBasicVoiceInteractionService");
        handlerThread.start();
        mHandler = Handler.createAsync(handlerThread.getLooper());
    }

    /**
     * Create AlwaysOnHotwordDetector.
     */
    public void createAlwaysOnHotwordDetector() {
        Log.i(TAG, "createAlwaysOnHotwordDetector!!!!");
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            AlwaysOnHotwordDetector.Callback callback = new AlwaysOnHotwordDetector.Callback() {
                @Override
                public void onAvailabilityChanged(int status) {
                    Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                    mAvailabilityStatus = status;
                    if (mAvailabilityChangeLatch != null) {
                        mAvailabilityChangeLatch.countDown();
                    }
                }

                @Override
                public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                    Log.i(TAG, "onDetected");
                    mDetectedResult = eventPayload;
                    if (mOnDetectRejectLatch != null) {
                        mOnDetectRejectLatch.countDown();
                    }
                }

                @Override
                public void onRejected(@NonNull HotwordRejectedResult result) {
                    Log.i(TAG, "onRejected");
                    mRejectedResult = result;
                    if (mOnDetectRejectLatch != null) {
                        mOnDetectRejectLatch.countDown();
                    }
                }

                @Override
                public void onError() {
                    Log.i(TAG, "onError");
                    if (mOnErrorLatch != null) {
                        mOnErrorLatch.countDown();
                    }
                }

                @Override
                public void onRecognitionPaused() {
                    Log.i(TAG, "onRecognitionPaused");
                }

                @Override
                public void onRecognitionResumed() {
                    Log.i(TAG, "onRecognitionResumed");
                }

                @Override
                public void onHotwordDetectionServiceInitialized(int status) {
                    Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                    if (status != HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS) {
                        return;
                    }
                    mInitializedStatus = status;
                    if (mServiceTriggerLatch != null) {
                        mServiceTriggerLatch.countDown();
                    }
                }

                @Override
                public void onHotwordDetectionServiceRestarted() {
                    Log.i(TAG, "onHotwordDetectionServiceRestarted");
                }
            };
            mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetector(callback);
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Create an AlwaysOnHotwordDetector but doesn't hold MANAGE_HOTWORD_DETECTION
     */
    public void createAlwaysOnHotwordDetectorWithoutManageHotwordDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateAlwaysOnHotwordDetector(mNoOpHotwordDetectorCallback)));
    }

    /**
     * Create an AlwaysOnHotwordDetector but doesn't hold MANAGE_HOTWORD_DETECTION but hold
     * BIND_HOTWORD_DETECTION_SERVICE.
     */
    public void createAlwaysOnHotwordDetectorHoldBindHotwordDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateAlwaysOnHotwordDetector(mNoOpHotwordDetectorCallback),
                BIND_HOTWORD_DETECTION_SERVICE));
    }

    public void createSoftwareHotwordDetector() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            HotwordDetector.Callback callback = new HotwordDetector.Callback() {
                @Override
                public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                    Log.i(TAG, "onDetected");
                    mDetectedResult = eventPayload;
                    if (mOnDetectRejectLatch != null) {
                        mOnDetectRejectLatch.countDown();
                    }
                }

                @Override
                public void onError() {
                    Log.i(TAG, "onError");
                }

                @Override
                public void onRecognitionPaused() {
                    Log.i(TAG, "onRecognitionPaused");
                }

                @Override
                public void onRecognitionResumed() {
                    Log.i(TAG, "onRecognitionResumed");
                }

                @Override
                public void onRejected(HotwordRejectedResult result) {
                    Log.i(TAG, "onRejected");
                    mRejectedResult = result;
                    if (mOnDetectRejectLatch != null) {
                        mOnDetectRejectLatch.countDown();
                    }
                }

                @Override
                public void onHotwordDetectionServiceInitialized(int status) {
                    Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                    if (status != HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS) {
                        return;
                    }
                    mInitializedStatus = status;
                    if (mServiceTriggerLatch != null) {
                        mServiceTriggerLatch.countDown();
                    }
                }

                @Override
                public void onHotwordDetectionServiceRestarted() {
                    Log.i(TAG, "onHotwordDetectionServiceRestarted");
                }
            };
            mSoftwareHotwordDetector = callCreateSoftwareHotwordDetector(callback);
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Create a VisualQueryDetector but doesn't hold MANAGE_HOTWORD_DETECTION but hold
     * BIND_VISUAL_QUERY_DETECTION_SERVICE.
     */
    public void createVisualQueryDetectorHoldBindVisualQueryDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateVisualQueryDetector(mNoOpVisualQueryDetectorCallback),
                BIND_VISUAL_QUERY_DETECTION_SERVICE));
    }

    public void createVisualQueryDetector() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            VisualQueryDetector.Callback callback = new VisualQueryDetector.Callback() {
                @Override
                public void onQueryDetected(@NonNull String transcribedText) {
                    Log.i(TAG, "onQueryDetected");
                    mCurrentQuery += transcribedText;
                }

                @Override
                public void onQueryRejected() {
                    Log.i(TAG, "onQueryRejected");
                    // mStreamedQueries are used to store previously streamed queries for testing
                    // reason, regardless of the queries being rejected or finished.
                    mStreamedQueries.add(mCurrentQuery);
                    mCurrentQuery = "";
                    if (mOnQueryFinishRejectLatch != null) {
                        mOnQueryFinishRejectLatch.countDown();
                    }
                }

                @Override
                public void onQueryFinished() {

                    Log.i(TAG, "onQueryFinished");
                    mStreamedQueries.add(mCurrentQuery);
                    mCurrentQuery = "";
                    if (mOnQueryFinishRejectLatch != null) {
                        mOnQueryFinishRejectLatch.countDown();
                    }
                }

                @Override
                public void onVisualQueryDetectionServiceInitialized(int status) {
                    Log.i(TAG, "onVisualQueryDetectionServiceInitialized status = " + status);
                    if (status != SandboxedDetectionServiceBase.INITIALIZATION_STATUS_SUCCESS) {
                        return;
                    }
                    mInitializedStatus = status;
                    if (mServiceTriggerLatch != null) {
                        mServiceTriggerLatch.countDown();
                    }
                }

                @Override
                public void onVisualQueryDetectionServiceRestarted() {
                    Log.i(TAG, "onVisualQueryDetectionServiceRestarted");
                }

                @Override
                public void onError() {
                    Log.i(TAG, "onError");
                }
            };
            mVisualQueryDetector = callCreateVisualQueryDetector(callback);
        }, MANAGE_HOTWORD_DETECTION)); //Permission placeholder - Don't really need any
    }

    /**
     * Create a CountDownLatch that is used to wait for availability change
     */
    public void initAvailabilityChangeLatch() {
        mAvailabilityChangeLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for onDetected() or onRejected() result
     */
    public void initDetectRejectLatch() {
        mOnDetectRejectLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that wait for onQueryFinished() or onQueryRejected() result
     */
    public void initQueryFinishRejectLatch(int numQueries) {
        mOnQueryFinishRejectLatch = new CountDownLatch(numQueries);
    }

    /**
     * Create a CountDownLatch that is used to wait for onError()
     */
    public void initOnErrorLatch() {
        mOnErrorLatch = new CountDownLatch(1);
    }

    /**
     * Returns the onDetected() result.
     */
    public AlwaysOnHotwordDetector.EventPayload getHotwordServiceOnDetectedResult() {
        return mDetectedResult;
    }

    /**
     * Returns the OnRejected() result.
     */
    public HotwordRejectedResult getHotwordServiceOnRejectedResult() {
        return mRejectedResult;
    }

    /**
     * Returns the OnQueryDetected() result.
     */
    public ArrayList<String> getStreamedQueriesResult() {
        return mStreamedQueries;
    }

    /**
     * Wait for onAvailabilityChanged() callback called.
     */
    public void waitAvailabilityChangedCalled() throws InterruptedException {
        Log.d(TAG, "waitAvailabilityChangedCalled(), latch=" + mAvailabilityChangeLatch);
        if (mAvailabilityChangeLatch == null
                || !mAvailabilityChangeLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mAvailabilityChangeLatch = null;
            throw new AssertionError("HotwordDetectionService get ability fail.");
        }
        mAvailabilityChangeLatch = null;
    }

    /**
     * Return the result for onAvailabilityChanged().
     */
    public int getHotwordDetectionServiceAvailabilityResult() {
        return mAvailabilityStatus;
    }

    /**
     * Wait for onDetected() or OnRejected() callback called.
     */
    public void waitOnDetectOrRejectCalled() throws InterruptedException {
        Log.d(TAG, "waitOnDetectOrRejectCalled(), latch=" + mOnDetectRejectLatch);
        if (mOnDetectRejectLatch == null
                || !mOnDetectRejectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnDetectRejectLatch = null;
            throw new AssertionError("onDetected or OnRejected() fail.");
        }
        mOnDetectRejectLatch = null;
    }

    /**
     * Wait for onQueryFinished() or OnQueryRejected() callback called.
     */
    public void waitOnQueryFinishedRejectCalled() throws InterruptedException {
        Log.d(TAG, "waitOnQueryFinishedRejectCalled(), latch=" + mOnQueryFinishRejectLatch);
        if (mOnQueryFinishRejectLatch == null
                || !mOnQueryFinishRejectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnQueryFinishRejectLatch = null;
            throw new AssertionError("onDetected or OnRejected() fail.");
        }
        mOnQueryFinishRejectLatch = null;
    }

    /**
     * Wait for onError() callback called.
     */
    public void waitOnErrorCalled() throws InterruptedException {
        Log.d(TAG, "waitOnErrorCalled(), latch=" + mOnErrorLatch);
        if (mOnErrorLatch == null
                || !mOnErrorLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnErrorLatch = null;
            throw new AssertionError("OnError() fail.");
        }
        mOnErrorLatch = null;
    }
}
