/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.carrierapi.cts;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.NetworkScan;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.AccessNetworkConstants;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyScanManager;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Build, install and run the tests by running the commands below:
 *  make cts -j64
 *  cts-tradefed run cts -m CtsCarrierApiTestCases --test android.carrierapi.cts.NetworkScanApiTest
 */
@RunWith(AndroidJUnit4.class)
public class NetworkScanApiTest {
    private TelephonyManager mTelephonyManager;
    private static final String TAG = "NetworkScanApiTest";
    private int mNetworkScanStatus;
    private static final int EVENT_NETWORK_SCAN_START = 100;
    private static final int EVENT_NETWORK_SCAN_RESULTS = 200;
    private static final int EVENT_NETWORK_SCAN_ERROR = 300;
    private static final int EVENT_NETWORK_SCAN_COMPLETED = 400;
    private List<CellInfo> mScanResults = null;
    private NetworkScanHandlerThread mTestHandlerThread;
    private Handler mHandler;
    private NetworkScanRequest mNetworkScanRequest;
    private NetworkScanCallbackImpl mNetworkScanCallback;
    private static final int MAX_INIT_WAIT_MS = 60000; // 60 seconds
    private Object mLock = new Object();
    private boolean mReady;
    private int mErrorCode;

    @Before
    public void setUp() throws Exception {
        mTelephonyManager = (TelephonyManager)
                InstrumentationRegistry.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mTestHandlerThread = new NetworkScanHandlerThread(TAG);
        mTestHandlerThread.start();
    }

    @After
    public void tearDown() throws Exception {
        mTestHandlerThread.quit();
    }

    private void waitUntilReady() {
        synchronized (mLock) {
            try {
                mLock.wait(MAX_INIT_WAIT_MS);
            } catch (InterruptedException ie) {
            }

            if (!mReady) {
                fail("NetworkScanApiTest failed to initialize");
            }
        }
    }

    private void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    private class NetworkScanHandlerThread extends HandlerThread {

        public NetworkScanHandlerThread(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            /* create a custom handler for the Handler Thread */
            mHandler = new Handler(mTestHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case EVENT_NETWORK_SCAN_START:
                            Log.d(TAG, "request network scan");
                            mTelephonyManager.requestNetworkScan(
                                    mNetworkScanRequest, mNetworkScanCallback);
                            break;
                        default:
                            Log.d(TAG, "Unknown Event " + msg.what);
                    }
                }
            };
        }
    }

    private class NetworkScanCallbackImpl extends TelephonyScanManager.NetworkScanCallback {
        @Override
        public void onResults(List<CellInfo> results) {
            Log.d(TAG, "onResults: " + results.toString());
            mNetworkScanStatus = EVENT_NETWORK_SCAN_RESULTS;
            mScanResults = results;
        }

        @Override
        public void onComplete() {
            Log.d(TAG, "onComplete");
            mNetworkScanStatus = EVENT_NETWORK_SCAN_COMPLETED;
            setReady(true);
        }

        @Override
        public void onError(int error) {
            Log.d(TAG, "onError: " + String.valueOf(error));
            mNetworkScanStatus = EVENT_NETWORK_SCAN_ERROR;
            mErrorCode = error;
            setReady(true);
        }
    }

    private RadioAccessSpecifier getRadioAccessSpecifier(CellInfo cellInfo) {
        RadioAccessSpecifier ras;
        if (cellInfo instanceof CellInfoLte) {
            int ranLte = AccessNetworkConstants.AccessNetworkType.EUTRAN;
            int[] lteChannels = {((CellInfoLte) cellInfo).getCellIdentity().getEarfcn()};
            ras = new RadioAccessSpecifier(ranLte, null /* bands */, lteChannels);
            Log.d(TAG, "CellInfoLte channel: " + lteChannels[0]);
        } else if (cellInfo instanceof CellInfoWcdma) {
            int ranLte = AccessNetworkConstants.AccessNetworkType.UTRAN;
            int[] wcdmaChannels = {((CellInfoWcdma) cellInfo).getCellIdentity().getUarfcn()};
            ras = new RadioAccessSpecifier(ranLte, null /* bands */, wcdmaChannels);
            Log.d(TAG, "CellInfoWcdma channel: " + wcdmaChannels[0]);
        } else if (cellInfo instanceof CellInfoGsm) {
            int ranGsm = AccessNetworkConstants.AccessNetworkType.GERAN;
            int[] gsmChannels = {((CellInfoGsm) cellInfo).getCellIdentity().getArfcn()};
            ras = new RadioAccessSpecifier(ranGsm, null /* bands */, gsmChannels);
            Log.d(TAG, "CellInfoGsm channel: " + gsmChannels[0]);
        } else {
            ras = null;
        }
        return ras;
    }

    /**
     * Tests that the device properly requests a network scan.
     */
    @Test
    public void testRequestNetworkScan() throws InterruptedException {
        if (!mTelephonyManager.hasCarrierPrivileges()) {
            fail("This test requires a SIM card with carrier privilege rule on it.");
        }

        // Make sure that there should be at least one entry.
        List<CellInfo> allCellInfo = mTelephonyManager.getAllCellInfo();
        Log.d(TAG, "allCellInfo: " + allCellInfo.toString());
        if (allCellInfo == null) {
            fail("TelephonyManager.getAllCellInfo() returned NULL!");
        }
        if (allCellInfo.size() == 0) {
            fail("TelephonyManager.getAllCellInfo() returned zero-length list!");
        }

        // Construct a NetworkScanRequest
        List<RadioAccessSpecifier> radioAccessSpecifier = new ArrayList<>();
        for (int i = 0; i < allCellInfo.size(); i++) {
            RadioAccessSpecifier ras = getRadioAccessSpecifier(allCellInfo.get(i));
            if (ras != null) {
                radioAccessSpecifier.add(ras);
            }
        }
        if (radioAccessSpecifier.size() == 0) {
            RadioAccessSpecifier gsm = new RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.GERAN,
                    null /* bands */,
                    null /* channels */);
            RadioAccessSpecifier lte = new RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.EUTRAN,
                    null /* bands */,
                    null /* channels */);
            RadioAccessSpecifier wcdma = new RadioAccessSpecifier(
                    AccessNetworkConstants.AccessNetworkType.UTRAN,
                    null /* bands */,
                    null /* channels */);
            radioAccessSpecifier.add(gsm);
            radioAccessSpecifier.add(lte);
            radioAccessSpecifier.add(wcdma);
        }
        RadioAccessSpecifier[] radioAccessSpecifierArray =
                new RadioAccessSpecifier[radioAccessSpecifier.size()];
        mNetworkScanRequest = new NetworkScanRequest(
                NetworkScanRequest.SCAN_TYPE_ONE_SHOT /* scan type */,
                radioAccessSpecifier.toArray(radioAccessSpecifierArray),
                5 /* search periodicity */,
                60 /* max search time */,
                true /*enable incremental results*/,
                5 /* incremental results periodicity */,
                null /* List of PLMN ids (MCC-MNC) */);

        mNetworkScanCallback = new NetworkScanCallbackImpl();
        Message startNetworkScan = mHandler.obtainMessage(EVENT_NETWORK_SCAN_START);
        setReady(false);
        startNetworkScan.sendToTarget();
        waitUntilReady();

        Log.d(TAG, "mNetworkScanStatus: " + mNetworkScanStatus);
        assertTrue("The final scan status is not ScanCompleted or ScanError with an error "
                        + "code ERROR_MODEM_UNAVAILABLE or ERROR_UNSUPPORTED",
                isScanStatusValid());
    }

    private boolean isScanStatusValid() {
        // TODO(b/72162885): test the size of ScanResults is not zero after the blocking bug fixed.
        if ((mNetworkScanStatus == EVENT_NETWORK_SCAN_COMPLETED) && (mScanResults != null)) {
            // Scan complete.
            return true;
        }
        if ((mNetworkScanStatus == EVENT_NETWORK_SCAN_ERROR)
                && ((mErrorCode == NetworkScan.ERROR_MODEM_UNAVAILABLE)
                || (mErrorCode == NetworkScan.ERROR_UNSUPPORTED))) {
            // Scan error but the error type is allowed.
            return true;
        }
        return false;
    }
}
