/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.wifiaware.testcase;

import android.content.Context;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.WifiRttManager;
import android.os.HandlerExecutor;
import android.util.Log;
import android.util.Pair;

import com.android.cts.verifier.R;
import com.android.cts.verifier.wifiaware.CallbackUtils;

import java.util.List;

/**
 * Test case for Discovery + Ranging:
 *
 * Subscribe test sequence:
 * 1. Attach
 *    wait for results (session)
 * 2. Subscribe
 *    wait for results (subscribe session)
 * 3. Wait for discovery with ranging
 * 4. Send message
 *    Wait for success
 * 5. Directed Range to Peer with PeerHandle
 * 6. Directed Range to Peer with MAC address
 * LAST. Destroy session
 *
 * Publish test sequence:
 * 1. Attach
 *    wait for results (session)
 * 2. Publish
 *    wait for results (publish session)
 * 3. Wait for rx message
 * 4. Directed Range to Peer with PeerHandler
 * 5. Directed Range to Peer with MAC address
 * LAST. Destroy session
 *
 * Validate that measured range is "reasonable" (i.e. 0 <= X <= SPECIFIED_LARGE_RANGE).
 */
public class DiscoveryWithRangingTestCase extends DiscoveryBaseTestCase {
    private static final String TAG = "DiscWithRangingTestCase";
    private static final boolean DBG = true;

    private static final int NUM_RTT_ITERATIONS = 10;
    private static final int MAX_RTT_RANGING_SUCCESS = 5; // high: but open environment
    private static final int MIN_RSSI = -100;

    private WifiRttManager mWifiRttManager;
    private boolean mIsPublish;

    public DiscoveryWithRangingTestCase(Context context, boolean isPublish) {
        super(context, true, true);

        mWifiRttManager = (WifiRttManager) mContext.getSystemService(
                Context.WIFI_RTT_RANGING_SERVICE);
        mIsPublish = isPublish;
    }

    @Override
    protected boolean executeTest() throws InterruptedException {
        if (DBG) Log.d(TAG, "executeTest: mIsPublish=" + mIsPublish);

        if (mIsPublish) {
            if (!executePublish()) {
                return false;
            }
        } else {
            if (!executeSubscribe()) {
                return false;
            }
        }

        // Directed range to peer with PeerHandler
        int numFailures = 0;
        RangingRequest rangeToPeerHandle = new RangingRequest.Builder().addWifiAwarePeer(
                mPeerHandle).build();
        for (int i = 0; i < NUM_RTT_ITERATIONS; ++i) {
            if (!executeRanging(rangeToPeerHandle)) {
                numFailures++;
            }
        }
        if (numFailures > MAX_RTT_RANGING_SUCCESS) {
            setFailureReason(
                    mContext.getString(R.string.aware_status_ranging_peer_failure, numFailures,
                            NUM_RTT_ITERATIONS));
            return false;
        }
        Log.d(TAG, "executeTest: Direct RTT to PeerHandle " + numFailures + " of "
                + NUM_RTT_ITERATIONS + " FAIL");

        // Directed range to peer with MAC address
        numFailures = 0;
        RangingRequest rangeToMAC = new RangingRequest.Builder().addWifiAwarePeer(
                mPeerMacAddress).build();
        for (int i = 0; i < NUM_RTT_ITERATIONS; ++i) {
            if (!executeRanging(rangeToMAC)) {
                numFailures++;
            }
        }
        if (numFailures > MAX_RTT_RANGING_SUCCESS) {
            setFailureReason(
                    mContext.getString(R.string.aware_status_ranging_mac_failure, numFailures,
                            NUM_RTT_ITERATIONS));
            return false;
        }
        Log.d(TAG, "executeTest: Direct RTT to MAC " + numFailures + " of " + NUM_RTT_ITERATIONS
                + " FAIL");

        // LAST. destroy session
        mWifiAwareDiscoverySession.close();
        mWifiAwareDiscoverySession = null;

        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_lifecycle_ok));
        return true;
    }

    private boolean executeRanging(RangingRequest request) throws InterruptedException {
        CallbackUtils.RangingCb rangingCb = new CallbackUtils.RangingCb();
        mWifiRttManager.startRanging(request, new HandlerExecutor(mHandler), rangingCb);
        Pair<Integer, List<RangingResult>> results = rangingCb.waitForRangingResults();
        switch (results.first) {
            case CallbackUtils.RangingCb.TIMEOUT:
                Log.e(TAG, "executeTest: ranging to peer TIMEOUT");
                return false;
            case CallbackUtils.RangingCb.ON_FAILURE:
                Log.e(TAG, "executeTest: ranging peer ON_FAILURE");
                return false;
        }
        if (results.second == null || results.second.size() != 1) {
            Log.e(TAG, "executeTest: ranging peer invalid results - null, empty, or wrong length");
            return false;
        }
        RangingResult result = results.second.get(0);
        if (result.getStatus() != RangingResult.STATUS_SUCCESS) {
            Log.e(TAG, "executeTest: ranging peer failed - individual result failure code");
            return false;
        }
        int distanceMm = result.getDistanceMm();
        int distanceStdDevMm = result.getDistanceStdDevMm();
        int rssi = result.getRssi();

        if (distanceMm > LARGE_ENOUGH_DISTANCE || rssi < MIN_RSSI) {
            Log.e(TAG, "executeTest: ranging peer failed - invalid results: distanceMm="
                    + distanceMm + ", distanceStdDevMm=" + distanceStdDevMm + ", rssi=" + rssi);
            return false;
        }

        return true;
    }
}
