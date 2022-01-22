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

package android.telephony.cts;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.TimeUnit;

public class MockModemManager {
    private static final String TAG = "MockModemManager";

    private static Context sContext;
    private static MockModemServiceConnector sServiceConnector;
    private MockModemService mMockModemService;

    public MockModemManager() {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    private void waitForTelephonyFrameworkDone(int delayInSec) throws Exception {
        TimeUnit.SECONDS.sleep(delayInSec);
    }

    /* Public APIs */

    /**
     * Bring up Mock Modem Service and connect to it.
     *
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean connectMockModemService() throws Exception {
        boolean result = false;

        if (sServiceConnector == null) {
            sServiceConnector =
                    new MockModemServiceConnector(InstrumentationRegistry.getInstrumentation());
        }

        if (sServiceConnector != null) {
            result = sServiceConnector.connectMockModemService();

            if (result) {
                mMockModemService = sServiceConnector.getMockModemService();

                if (mMockModemService != null) {
                    /*
                     It needs to have a delay to wait for Telephony Framework to bind with
                     MockModemService and set radio power as a desired state for initial condition
                     even get SIM card state. Currently, 1 sec is enough for now.
                    */
                    waitForTelephonyFrameworkDone(1);
                } else {
                    Log.e(TAG, "MockModemService get failed!");
                    result = false;
                }
            }
        } else {
            Log.e(TAG, "Create MockModemServiceConnector failed!");
        }

        return result;
    }

    /**
     * Disconnect from Mock Modem Service.
     *
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean disconnectMockModemService() throws Exception {
        boolean result = false;

        if (sServiceConnector != null) {
            result = sServiceConnector.disconnectMockModemService();

            if (result) {
                mMockModemService = null;
            } else {
                Log.e(TAG, "MockModemService disconnected failed!");
            }
        } else {
            Log.e(TAG, "No MockModemServiceConnector exist!");
        }

        return result;
    }

    /**
     * Set SIM card status as present.
     *
     * @param subId which sub needs to be set.
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean setSimPresent(int subId) throws Exception {
        Log.d(TAG, "setSimPresent[" + subId + "]");
        boolean result = true;

        MockModemConfigInterface[] configInterfaces =
                mMockModemService.getMockModemConfigInterfaces();
        configInterfaces[subId].setSimPresent(true, TAG);
        waitForTelephonyFrameworkDone(1);
        return result;
    }

    /**
     * Force the response error return for a specific RIL request
     *
     * @param subId which sub needs to be set.
     * @param requestId the request/response message ID
     * @param error RIL_Errno and -1 means to disable the modified mechanism, back to original mock
     *     modem behavior
     * @return boolean true if the operation is successful, otherwise false.
     */
    public boolean forceErrorResponse(int subId, int requestId, int error) throws Exception {
        Log.d(
                TAG,
                "forceErrorResponse[" + subId + "] for request:" + requestId + " ,error:" + error);
        boolean result = true;

        // TODO: support DSDS
        switch (requestId) {
            case RIL_REQUEST_RADIO_POWER:
                mMockModemService.getIRadioModem().forceErrorResponse(requestId, error);
                break;
            default:
                Log.e(TAG, "request:" + requestId + " not support to change the response error");
                result = false;
                break;
        }
        return result;
    }
}
