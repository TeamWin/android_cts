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

package android.telephony.mockmodem;

import static android.hardware.radio.ims.EpsFallbackReason.NO_NETWORK_RESPONSE;
import static android.hardware.radio.ims.EpsFallbackReason.NO_NETWORK_TRIGGER;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_INVALID;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_NO_NETWORK_RESPONSE;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_NO_NETWORK_TRIGGER;

import android.telephony.ims.feature.MmTelFeature;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockImsService {
    private static final String TAG = "MockImsService";
    private static final int INVALID = -1;

    public static final int LATCH_WAIT_FOR_SRVCC_CALL_INFO = 0;
    public static final int LATCH_WAIT_FOR_TRIGGER_EPS_FALLBACK = 1;
    private static final int LATCH_MAX = 2;

    private final CountDownLatch[] mLatches = new CountDownLatch[LATCH_MAX];
    private final List<MockSrvccCall> mSrvccCalls = new ArrayList<>();
    private int mEpsFallbackReason = INVALID;

    public MockImsService() {
        for (int i = 0; i < LATCH_MAX; i++) {
            mLatches[i] = new CountDownLatch(1);
        }
    }

    /**
     * Sets SRVCC call information.
     * @param srvccCalls The list of call information.
     */
    public void setSrvccCallInfo(android.hardware.radio.ims.SrvccCall[] srvccCalls) {
        mSrvccCalls.clear();

        if (srvccCalls != null) {
            for (android.hardware.radio.ims.SrvccCall call : srvccCalls) {
                mSrvccCalls.add(new MockSrvccCall(call));
            }
        }
        countDownLatch(LATCH_WAIT_FOR_SRVCC_CALL_INFO);
    }

    /** @return The list of {@link MockSrvccCall} instances. */
    public List<MockSrvccCall> getSrvccCalls() {
        return mSrvccCalls;
    }

    /**
     * Sets the reason that caused EPS fallback.
     *
     * @param reason The reason that caused EPS fallback.
     */
    public void setEpsFallbackReason(int reason) {
        mEpsFallbackReason = reason;
        countDownLatch(LATCH_WAIT_FOR_TRIGGER_EPS_FALLBACK);
    }

    /**
     * Returns the reason that caused EPS fallback.
     *
     * @return the reason that caused EPS fallback.
     */
    public @MmTelFeature.EpsFallbackReason int getEpsFallbackReason() {
        switch (mEpsFallbackReason) {
            case NO_NETWORK_TRIGGER: return EPS_FALLBACK_REASON_NO_NETWORK_TRIGGER;
            case NO_NETWORK_RESPONSE: return EPS_FALLBACK_REASON_NO_NETWORK_RESPONSE;
            default: return EPS_FALLBACK_REASON_INVALID;
        }
    }

    /**
     * Clears the EPS fallback reason.
     */
    public void resetEpsFallbackReason() {
        mEpsFallbackReason = INVALID;
    }

    private void countDownLatch(int latchIndex) {
        synchronized (mLatches) {
            mLatches[latchIndex].countDown();
        }
    }

    private void resetLatch(int latchIndex) {
        synchronized (mLatches) {
            mLatches[latchIndex] = new CountDownLatch(1);
        }
    }

    /**
     * Waits for the event of voice service.
     *
     * @param latchIndex The index of the event.
     * @param waitMs The timeout in milliseconds.
     * @return {@code true} if the event happens.
     */
    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLatches) {
                latch = mLatches[latchIndex];
            }
            long startTime = System.currentTimeMillis();
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
            Log.i(TAG, "Latch " + latchIndex + " took "
                    + (System.currentTimeMillis() - startTime) + " ms to count down.");
        } catch (InterruptedException e) {
        }
        synchronized (mLatches) {
            mLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    /**
     * Resets the CountDownLatches
     */
    public void resetAllLatchCountdown() {
        synchronized (mLatches) {
            for (int i = 0; i < LATCH_MAX; i++) {
                mLatches[i] = new CountDownLatch(1);
            }
        }
    }
}
