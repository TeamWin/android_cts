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

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.satellite.IRadioSatellite;
import android.hardware.radio.satellite.IRadioSatelliteIndication;
import android.hardware.radio.satellite.IRadioSatelliteResponse;
import android.hardware.radio.satellite.SatelliteCapabilities;
import android.hardware.radio.satellite.SatelliteFeature;
import android.hardware.radio.satellite.SatelliteMode;
import android.os.RemoteException;
import android.telephony.satellite.PointingInfo;
import android.util.Log;

import com.android.internal.telephony.RILUtils;

public class IRadioSatelliteImpl extends IRadioSatellite.Stub {
    private static final String TAG = "MRSATELLITE";

    private final MockModemService mService;
    private IRadioSatelliteResponse mRadioSatelliteResponse;
    private IRadioSatelliteIndication mRadioSatelliteIndication;
    private final MockModemConfigInterface mMockModemConfigInterface;
    private final int mSubId;
    private final String mTag;

    private SatelliteCapabilities mCapabilities;
    private boolean mPowerSate = false;
    private boolean mIsProvisioned = true;
    private int mMode = SatelliteMode.ACQUIRED;
    private int mNTRadioTechnology = android.hardware.radio.satellite.NTRadioTechnology.EMTC_NTN;
    private int mMaxCharactersPerTextMessage = 100;
    private int mNextSatelliteVisibility = 10;
    private String[] mPendingMessages = {"This is test 1.", "This is test 2"};

    public IRadioSatelliteImpl(
            MockModemService service, MockModemConfigInterface configInterface, int instanceId) {
        mTag = TAG + "-" + instanceId;
        Log.d(mTag, "Instantiated");

        this.mService = service;
        mMockModemConfigInterface = configInterface;
        mSubId = instanceId;

        mCapabilities = new SatelliteCapabilities();
        mCapabilities.supportedRadioTechnologies =
                new int[]{
                        android.hardware.radio.satellite.NTRadioTechnology.EMTC_NTN,
                        android.hardware.radio.satellite.NTRadioTechnology.NB_IOT_NTN};
        mCapabilities.isAlwaysOn = false;
        mCapabilities.needsPointingToSatellite = true;
        mCapabilities.supportedFeatures = new int[]{SatelliteFeature.EMERGENCY_SMS,
                SatelliteFeature.SOS_SMS};
    }

    /** Implementation of IRadioSatellite APIs */

    @Override
    public void setResponseFunctions(
            IRadioSatelliteResponse radioSatelliteResponse,
            IRadioSatelliteIndication radioSatelliteIndication) {
        Log.d(mTag, "setResponseFunctions");
        mRadioSatelliteResponse = radioSatelliteResponse;
        mRadioSatelliteIndication = radioSatelliteIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    /**
     * Send acknowledgement to a response from modem.
     */
    @Override
    public void responseAcknowledgement() {
        Log.d(mTag, "responseAcknowledgement");
    }

    /**
     * Get feature capabilities supported by satellite.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void getCapabilities(int serial) {
        Log.d(mTag, "getCapabilities");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.getCapabilitiesResponse(rsp, mCapabilities);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getCapabilities from AIDL. Exception" + ex);
        }
    }

    /**
     * Turn satellite modem on/off.
     *
     * @param serial Serial number of request.
     * @param on True for turning on.
     *           False for turning off.
     */
    @Override
    public void setPower(int serial, boolean on) {
        Log.d(mTag, "setPower");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.setPowerResponse(rsp);
            mPowerSate = on;
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setPower from AIDL. Exception" + ex);
        }
    }

    /**
     * Get satellite modem state.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void getPowerState(int serial) {
        Log.d(mTag, "getPowerState");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.getPowerStateResponse(rsp, mPowerSate);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getPowerState from AIDL. Exception" + ex);
        }
    }

    /**
     * Provision the subscription with a satellite provider. This is needed to register the
     * subscription if the provider allows dynamic registration.
     *
     * @param serial Serial number of request.
     * @param imei IMEI of the SIM associated with the satellite modem.
     * @param msisdn MSISDN of the SIM associated with the satellite modem.
     * @param imsi IMSI of the SIM associated with the satellite modem.
     * @param features List of features to be provisioned.
     */
    @Override
    public void provisionService(
            int serial, String imei, String msisdn, String imsi, int[] features) {
        Log.d(mTag, "provisionService");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.provisionServiceResponse(rsp, mIsProvisioned);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to provisionService from AIDL. Exception" + ex);
        }
    }

    /**
     * Add contacts that are allowed to be used for satellite communication. This is applicable for
     * incoming messages as well.
     *
     * @param serial Serial number of request.
     * @param contacts List of allowed contacts to be added.
     */
    @Override
    public void addAllowedSatelliteContacts(int serial, String[] contacts) {
        Log.d(mTag, "addAllowedSatelliteContacts");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.addAllowedSatelliteContactsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to addAllowedSatelliteContacts from AIDL. Exception" + ex);
        }
    }

    /**
     * Remove contacts that are allowed to be used for satellite communication. This is applicable
     * for incoming messages as well.
     *
     * @param serial Serial number of request.
     * @param contacts List of allowed contacts to be removed.
     */
    @Override
    public void removeAllowedSatelliteContacts(int serial, String[] contacts) {
        Log.d(mTag, "removeAllowedSatelliteContacts");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.removeAllowedSatelliteContactsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to removeAllowedSatelliteContacts from AIDL. Exception" + ex);
        }
    }

    /**
     * Send text messages.
     *
     * @param serial Serial number of request.
     * @param messages List of messages in text format to be sent.
     * @param destination The recipient of the message.
     * @param latitude The current latitude of the device.
     * @param longitude The current longitude of the device. The location (i.e., latitude and
     *        longitude) of the device will be filled for emergency messages.
     */
    @Override
    public void sendMessages(int serial, String[] messages, String destination,
            double latitude, double longitude) {
        Log.d(mTag, "sendMessages");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.sendMessagesResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to sendMessages from AIDL. Exception" + ex);
        }
    }

    /**
     * Get pending messages. After receiving the pending messages from the satellite, the modem
     * will send {@link #onNewMessages} indication to Telephony framework.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void getPendingMessages(int serial) {
        Log.d(mTag, "getPendingMessages");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.getPendingMessagesResponse(rsp, mPendingMessages);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getPendingMessages from AIDL. Exception" + ex);
        }
    }

    /**
     * Get current satellite registration mode, which is defined in {@link #SatelliteMode}.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void getSatelliteMode(int serial) {
        Log.d(mTag, "getSatelliteMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.getSatelliteModeResponse(
                    rsp, mPowerSate ? mMode : SatelliteMode.POWERED_OFF, mNTRadioTechnology);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getSatelliteMode from AIDL. Exception" + ex);
        }
    }

    /**
     * Set the filter for what type of indication framework want to receive from modem.
     *
     * @param serial Serial number of request.
     * @param filterBitmask The filter bitmask identifying what type of indication Telephony
     *                      framework wants to receive from modem. This bitmask is the 'or'
     *                      combination of the enum values defined in {@link #IndicationFilter}.
     */
    @Override
    public void setIndicationFilter(int serial, int filterBitmask) {
        Log.d(mTag, "setIndicationFilter");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.setIndicationFilterResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to setIndicationFilter from AIDL. Exception" + ex);
        }
    }

    /**
     * User started pointing to the satellite. Modem should continue to update the pointing input
     * as user device/satellite moves.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void startSendingSatellitePointingInfo(int serial) {
        Log.d(mTag, "startSendingSatellitePointingInfo");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.startSendingSatellitePointingInfoResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to startSendingSatellitePointingInfo from AIDL. Exception" + ex);
        }
    }

    /**
     * Stop sending satellite pointing info to the framework.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void stopSendingSatellitePointingInfo(int serial) {
        Log.d(mTag, "stopSendingSatellitePointingInfo");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.stopSendingSatellitePointingInfoResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to stopSendingSatellitePointingInfo from AIDL. Exception" + ex);
        }
    }

    /**
     * Get max number of characters per text message.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void getMaxCharactersPerTextMessage(int serial) {
        Log.d(mTag, "getMaxCharactersPerTextMessage");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.getMaxCharactersPerTextMessageResponse(
                    rsp, mMaxCharactersPerTextMessage);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getMaxCharactersPerTextMessage from AIDL. Exception" + ex);
        }
    }

    /**
     * Get time for next visibility of satellite.
     *
     * @param serial Serial number of request.
     */
    @Override
    public void getTimeForNextSatelliteVisibility(int serial) {
        Log.d(mTag, "getTimeForNextSatelliteVisibility");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSatelliteResponse.getTimeForNextSatelliteVisibilityResponse(
                    rsp, mNextSatelliteVisibility);
        } catch (RemoteException ex) {
            Log.e(mTag, "Failed to getTimeForNextSatelliteVisibility from AIDL. Exception" + ex);
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioSatellite.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioSatellite.VERSION;
    }

    /** Implementation of IRadioSatelliteIndication APIs */

    /**
     * Confirms that ongoing message transfer is complete.
     *
     * @param complete True mean the transfer is complete.
     *                 False means the transfer is not complete.
     */
    public void sendMessagesTransferComplete(boolean complete) {
        Log.d(mTag, "onMessagesTransferComplete");

        if (mRadioSatelliteIndication != null) {
            try {
                mRadioSatelliteIndication.onMessagesTransferComplete(
                        RadioIndicationType.UNSOLICITED, complete);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to send onMessagesTransferComplete indication from AIDL."
                        + " Exception=" + ex);
            }
        } else {
            Log.e(mTag, "mRadioSatelliteIndication is null");
        }
    }

    /**
     * Indicates new message received on device.
     *
     * @param messages List of new messages received.
     */
    public void sendNewMessages(String[] messages) {
        Log.d(mTag, "onNewMessages");

        if (mRadioSatelliteIndication != null) {
            try {
                mRadioSatelliteIndication.onNewMessages(RadioIndicationType.UNSOLICITED, messages);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to send onNewMessages indication from AIDL."
                        + " Exception=" + ex);
            }
        } else {
            Log.e(mTag, "mRadioSatelliteIndication is null");
        }
    }

    /**
     * Indicates that satellite has pending messages for the device to be pulled.
     *
     * @param count Number of pending messages.
     */
    public void sendPendingMessageCount(int count) {
        Log.d(mTag, "onPendingMessageCount");

        if (mRadioSatelliteIndication != null) {
            try {
                mRadioSatelliteIndication.onPendingMessageCount(
                        RadioIndicationType.UNSOLICITED, count);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to send onPendingMessageCount indication from AIDL."
                        + " Exception=" + ex);
            }
        } else {
            Log.e(mTag, "mRadioSatelliteIndication is null");
        }
    }

    /**
     * Indicate that satellite provision state has changed.
     *
     * @param provisioned True means the service is provisioned.
     *                    False means the service is not provisioned.
     * @param features List of Feature whose provision state has changed.
     */
    public void sendProvisionStateChanged(boolean provisioned, int[] features) {
        Log.d(mTag, "onProvisionStateChanged");

        if (mRadioSatelliteIndication != null) {
            try {
                mRadioSatelliteIndication.onProvisionStateChanged(
                        RadioIndicationType.UNSOLICITED, provisioned, features);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to send onProvisionStateChanged indication from AIDL."
                        + " Exception=" + ex);
            }
        } else {
            Log.e(mTag, "mRadioSatelliteIndication is null");
        }
    }

    /**
     * Indicate that satellite mode has changed.
     *
     * @param mode The current mode of the satellite modem.
     */
    void sendSatelliteModeChanged(int mode) {
        Log.d(mTag, "onSatelliteModeChanged");

        if (mRadioSatelliteIndication != null) {
            try {
                mRadioSatelliteIndication.onSatelliteModeChanged(
                        RadioIndicationType.UNSOLICITED, mode);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to send onSatelliteModeChanged indication from AIDL."
                        + " Exception=" + ex);
            }
        } else {
            Log.e(mTag, "mRadioSatelliteIndication is null");
        }
    }

    /**
     * Indicate that satellite Pointing input has changed.
     *
     * @param pointingInfo The current pointing info.
     */
    public void sendSatellitePointingInfoChanged(PointingInfo pointingInfo) {
        Log.d(mTag, "onSatellitePointingInfoChanged");

        if (mRadioSatelliteIndication != null) {
            try {
                mRadioSatelliteIndication.onSatellitePointingInfoChanged(
                        RadioIndicationType.UNSOLICITED,
                        RILUtils.convertToHalSatellitePointingInfo(pointingInfo));
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to send onSatellitePointingInfoChanged indication from"
                        + "AIDL. Exception=" + ex);
            }
        } else {
            Log.e(mTag, "mRadioSatelliteIndication is null");
        }
    }

    /**
     * Indicate that satellite radio technology has changed.
     *
     * @param technology The current technology of the satellite modem.
     */
    public void sendSatelliteRadioTechnologyChanged(int technology) {
        Log.d(mTag, "onSatelliteRadioTechnologyChanged");

        if (mRadioSatelliteIndication != null) {
            try {
                mRadioSatelliteIndication.onSatelliteRadioTechnologyChanged(
                        RadioIndicationType.UNSOLICITED, technology);
            } catch (RemoteException ex) {
                Log.e(mTag, "Failed to send onSatelliteRadioTechnologyChanged indication from"
                        + "AIDL. Exception=" + ex);
            }
        } else {
            Log.e(mTag, "mRadioSatelliteIndication is null");
        }
    }
}
