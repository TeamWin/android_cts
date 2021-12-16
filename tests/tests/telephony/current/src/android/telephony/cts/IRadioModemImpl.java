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

package android.telephony.cts;

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.modem.IRadioModem;
import android.hardware.radio.modem.IRadioModemIndication;
import android.hardware.radio.modem.IRadioModemResponse;
import android.os.RemoteException;
import android.util.Log;

public class IRadioModemImpl extends IRadioModem.Stub {
    private static final String TAG = "MRMDM";

    private static final String BASEBAND_VERSION = "mock-modem-service-1.0";
    private static final String DEFAULT_IMEI = "123456789012345";
    private static final String DEFAULT_IMEISV = "01";
    private static final String DEFAULT_ESN = "123456789";
    private static final String DEFAULT_MEID = "123456789012345";

    private final TestMockModemService mService;
    private IRadioModemResponse mRadioModemResponse;
    private IRadioModemIndication mRadioModemIndication;

    public IRadioModemImpl(TestMockModemService service) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
    }

    // Implementation of IRadioModem functions
    @Override
    public void setResponseFunctions(
            IRadioModemResponse radioModemResponse, IRadioModemIndication radioModemIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioModemResponse = radioModemResponse;
        mRadioModemIndication = radioModemIndication;
        mService.countDownLatch(TestMockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void enableModem(int serial, boolean on) {
        Log.d(TAG, "getNumOfLiveModems " + on);

        // TODO: cache value
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.enableModemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to enableModem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getBasebandVersion(int serial) {
        Log.d(TAG, "getBasebandVersion");

        String baseband = BASEBAND_VERSION;

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.getBasebandVersionResponse(rsp, baseband);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getBasebandVersion from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getDeviceIdentity(int serial) {
        Log.d(TAG, "getDeviceIdentity");

        String imei = DEFAULT_IMEI;
        String imeisv = DEFAULT_IMEISV;
        String esn = DEFAULT_ESN;
        String meid = DEFAULT_MEID;

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.getDeviceIdentityResponse(rsp, imei, imeisv, esn, meid);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getDeviceIdentity from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getHardwareConfig(int serial) {
        Log.d(TAG, "getHardwareConfig");

        android.hardware.radio.modem.HardwareConfig[] config =
                new android.hardware.radio.modem.HardwareConfig[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getHardwareConfigResponse(rsp, config);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getHardwareConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getModemActivityInfo(int serial) {
        Log.d(TAG, "getModemActivityInfo");

        android.hardware.radio.modem.ActivityStatsInfo activityInfo =
                new android.hardware.radio.modem.ActivityStatsInfo();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getModemActivityInfoResponse(rsp, activityInfo);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getModemActivityInfo from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getModemStackStatus(int serial) {
        Log.d(TAG, "getModemStackStatus");

        boolean isEnabled = false;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getModemStackStatusResponse(rsp, isEnabled);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getModemStackStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getRadioCapability(int serial) {
        Log.d(TAG, "getRadioCapability");

        android.hardware.radio.modem.RadioCapability rc =
                new android.hardware.radio.modem.RadioCapability();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getRadioCapabilityResponse(rsp, rc);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvReadItem(int serial, int itemId) {
        Log.d(TAG, "nvReadItem");

        // TODO: cache value
        String result = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvReadItemResponse(rsp, result);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvReadItem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvResetConfig(int serial, int resetType) {
        Log.d(TAG, "nvResetConfig");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvResetConfigResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvResetConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvWriteCdmaPrl(int serial, byte[] prl) {
        Log.d(TAG, "nvWriteCdmaPrl");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvWriteCdmaPrlResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvWriteCdmaPrl from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvWriteItem(int serial, android.hardware.radio.modem.NvWriteItem item) {
        Log.d(TAG, "nvWriteItem");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvWriteItemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvWriteItem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void requestShutdown(int serial) {
        Log.d(TAG, "requestShutdown");

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.requestShutdownResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to requestShutdown from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendDeviceState(int serial, int deviceStateType, boolean state) {
        Log.d(TAG, "sendDeviceState");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.sendDeviceStateResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendDeviceState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(TAG, "responseAcknowledgement");
    }

    @Override
    public void setRadioCapability(int serial, android.hardware.radio.modem.RadioCapability rc) {
        Log.d(TAG, "setRadioCapability");

        // TODO: cache value
        android.hardware.radio.modem.RadioCapability respRc =
                new android.hardware.radio.modem.RadioCapability();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.setRadioCapabilityResponse(rsp, respRc);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setRadioPower(
            int serial,
            boolean powerOn,
            boolean forEmergencyCall,
            boolean preferredForEmergencyCall) {
        Log.d(TAG, "setRadioPower");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.setRadioPowerResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setRadioPower from AIDL. Exception" + ex);
        }
    }

    /**
     * Sent when setRadioCapability() completes. Returns the same RadioCapability as
     * getRadioCapability() and is the same as the one sent by setRadioCapability().
     *
     * @param radioCapability Current radio capability
     */
    public void radioCapabilityIndication(
            android.hardware.radio.modem.RadioCapability radioCapability) {
        Log.d(TAG, "radioCapabilityIndication");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.radioCapabilityIndication(
                        RadioIndicationType.UNSOLICITED, radioCapability);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to radioCapabilityIndication from AIDL. Exception" + ex);
            }
        } else {

            Log.e(TAG, "null mRadioModemIndication");
        }
    }

    /**
     * Indicates when radio state changes.
     *
     * @param radioState Current radio state
     */
    public void radioStateChanged(int radioState) {
        Log.d(TAG, "radioStateChanged");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.radioStateChanged(
                        RadioIndicationType.UNSOLICITED, radioState);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to radioStateChanged from AIDL. Exception" + ex);
            }
        } else {

            Log.e(TAG, "null mRadioModemIndication");
        }
    }

    /** Indicates the ril connects and returns the version. */
    public void rilConnected() {
        Log.d(TAG, "rilConnected");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.rilConnected(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to rilConnected from AIDL. Exception" + ex);
            }
        } else {

            Log.e(TAG, "null mRadioModemIndication");
        }
    }
}
