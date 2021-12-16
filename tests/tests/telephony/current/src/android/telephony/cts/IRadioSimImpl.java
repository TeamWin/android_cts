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
import android.hardware.radio.sim.CardStatus;
import android.hardware.radio.sim.IRadioSim;
import android.hardware.radio.sim.IRadioSimIndication;
import android.hardware.radio.sim.IRadioSimResponse;
import android.os.RemoteException;
import android.util.Log;

public class IRadioSimImpl extends IRadioSim.Stub {
    private static final String TAG = "MRSIM";
    private final TestMockModemService mService;
    private IRadioSimResponse mRadioSimResponse;
    private IRadioSimIndication mRadioSimIndication;
    private CardStatus mCardStatus = null;

    public IRadioSimImpl(TestMockModemService service) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
        mCardStatus = setCardAbsent();
    }

    // Implementation of IRadioSim functions
    @Override
    public void setResponseFunctions(
            IRadioSimResponse radioSimResponse, IRadioSimIndication radioSimIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioSimResponse = radioSimResponse;
        mRadioSimIndication = radioSimIndication;
        mService.countDownLatch(TestMockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(TAG, "responseAcknowledgement");
        // TODO
        // acknowledgeRequest(in int serial);
    }

    @Override
    public void areUiccApplicationsEnabled(int serial) {
        Log.d(TAG, "areUiccApplicationsEnabled");

        boolean enabled = true;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.areUiccApplicationsEnabledResponse(rsp, enabled);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to areUiccApplicationsEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void changeIccPin2ForApp(int serial, String oldPin2, String newPin2, String aid) {
        Log.d(TAG, "changeIccPin2ForApp");
        // TODO: cache value

        int remainingRetries = 3;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.changeIccPin2ForAppResponse(rsp, remainingRetries);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to changeIccPin2ForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void changeIccPinForApp(int serial, String oldPin, String newPin, String aid) {
        Log.d(TAG, "changeIccPinForApp");
        // TODO: cache value

        int remainingRetries = 3;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.changeIccPinForAppResponse(rsp, remainingRetries);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to changeIccPinForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void enableUiccApplications(int serial, boolean enable) {
        Log.d(TAG, "enableUiccApplications");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.enableUiccApplicationsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to enableUiccApplications from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getAllowedCarriers(int serial) {
        Log.d(TAG, "getAllowedCarriers");

        android.hardware.radio.sim.CarrierRestrictions carriers =
                new android.hardware.radio.sim.CarrierRestrictions();
        int multiSimPolicy = android.hardware.radio.sim.SimLockMultiSimPolicy.NO_MULTISIM_POLICY;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getAllowedCarriersResponse(rsp, carriers, multiSimPolicy);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getAllowedCarriers from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCdmaSubscription(int serial) {
        Log.d(TAG, "getCdmaSubscription");

        String mdn = "";
        String hSid = "";
        String hNid = "";
        String min = "";
        String prl = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getCdmaSubscriptionResponse(rsp, mdn, hSid, hNid, min, prl);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCdmaSubscription from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCdmaSubscriptionSource(int serial) {
        Log.d(TAG, "getCdmaSubscriptionSource");

        int source = 0; // CdmaSubscriptionSource.RUIM_SIM

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getCdmaSubscriptionSourceResponse(rsp, source);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCdmaSubscriptionSource from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getFacilityLockForApp(
            int serial, String facility, String password, int serviceClass, String appId) {
        Log.d(TAG, "getFacilityLockForApp");

        // TODO: cache value
        int response = 0;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getFacilityLockForAppResponse(rsp, response);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getFacilityLockForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getIccCardStatus(int serial) {
        Log.d(TAG, "getIccCardStatus");

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioSimResponse.getIccCardStatusResponse(rsp, mCardStatus);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getIccCardStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getImsiForApp(int serial, String aid) {
        Log.d(TAG, "getImsiForApp");
        // TODO: cache value
        String imsi = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getImsiForAppResponse(rsp, imsi);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getImsiForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSimPhonebookCapacity(int serial) {
        Log.d(TAG, "getSimPhonebookCapacity");

        android.hardware.radio.sim.PhonebookCapacity capacity =
                new android.hardware.radio.sim.PhonebookCapacity();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getSimPhonebookCapacityResponse(rsp, capacity);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getSimPhonebookCapacity from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSimPhonebookRecords(int serial) {
        Log.d(TAG, "getSimPhonebookRecords");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.getSimPhonebookRecordsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getSimPhonebookRecords from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccCloseLogicalChannel(int serial, int channelId) {
        Log.d(TAG, "iccCloseLogicalChannel");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccCloseLogicalChannelResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to iccCloseLogicalChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccIoForApp(int serial, android.hardware.radio.sim.IccIo iccIo) {
        Log.d(TAG, "iccIoForApp");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccIoForAppResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to iccIoForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccOpenLogicalChannel(int serial, String aid, int p2) {
        Log.d(TAG, "iccOpenLogicalChannel");
        // TODO: cache value
        int channelId = 0;
        byte[] selectResponse = new byte[0];

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccOpenLogicalChannelResponse(rsp, channelId, selectResponse);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to iccOpenLogicalChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccTransmitApduBasicChannel(
            int serial, android.hardware.radio.sim.SimApdu message) {
        Log.d(TAG, "iccTransmitApduBasicChannel");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccTransmitApduBasicChannelResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to iccTransmitApduBasicChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void iccTransmitApduLogicalChannel(
            int serial, android.hardware.radio.sim.SimApdu message) {
        Log.d(TAG, "iccTransmitApduLogicalChannel");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.iccTransmitApduBasicChannelResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to iccTransmitApduLogicalChannel from AIDL. Exception" + ex);
        }
    }

    @Override
    public void reportStkServiceIsRunning(int serial) {
        Log.d(TAG, "reportStkServiceIsRunning");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.reportStkServiceIsRunningResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to reportStkServiceIsRunning from AIDL. Exception" + ex);
        }
    }

    @Override
    public void requestIccSimAuthentication(
            int serial, int authContext, String authData, String aid) {
        Log.d(TAG, "requestIccSimAuthentication");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.requestIccSimAuthenticationResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to requestIccSimAuthentication from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendEnvelope(int serial, String contents) {
        Log.d(TAG, "sendEnvelope");
        // TODO: cache value
        String commandResponse = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.sendEnvelopeResponse(rsp, commandResponse);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendEnvelope from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendEnvelopeWithStatus(int serial, String contents) {
        Log.d(TAG, "sendEnvelopeWithStatus");
        // TODO: cache value
        android.hardware.radio.sim.IccIoResult iccIoResult =
                new android.hardware.radio.sim.IccIoResult();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.sendEnvelopeWithStatusResponse(rsp, iccIoResult);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendEnvelopeWithStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendTerminalResponseToSim(int serial, String contents) {
        Log.d(TAG, "sendTerminalResponseToSim");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.sendTerminalResponseToSimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendTerminalResponseToSim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setAllowedCarriers(
            int serial,
            android.hardware.radio.sim.CarrierRestrictions carriers,
            int multiSimPolicy) {
        Log.d(TAG, "sendTerminalResponseToSim");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setAllowedCarriersResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setAllowedCarriers from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCarrierInfoForImsiEncryption(
            int serial, android.hardware.radio.sim.ImsiEncryptionInfo imsiEncryptionInfo) {
        Log.d(TAG, "setCarrierInfoForImsiEncryption");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setCarrierInfoForImsiEncryptionResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCarrierInfoForImsiEncryption from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCdmaSubscriptionSource(int serial, int cdmaSub) {
        Log.d(TAG, "setCdmaSubscriptionSource");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setCdmaSubscriptionSourceResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCdmaSubscriptionSource from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setFacilityLockForApp(
            int serial,
            String facility,
            boolean lockState,
            String password,
            int serviceClass,
            String appId) {
        Log.d(TAG, "setFacilityLockForApp");
        // TODO: cache value
        int retry = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setFacilityLockForAppResponse(rsp, retry);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setFacilityLockForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSimCardPower(int serial, int powerUp) {
        Log.d(TAG, "setSimCardPower");
        // TODO: cache value
        int retry = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setSimCardPowerResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setSimCardPower from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setUiccSubscription(int serial, android.hardware.radio.sim.SelectUiccSub uiccSub) {
        Log.d(TAG, "setUiccSubscription");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.setUiccSubscriptionResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setUiccSubscription from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPin2ForApp(int serial, String pin2, String aid) {
        Log.d(TAG, "supplyIccPin2ForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPin2ForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to supplyIccPin2ForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPinForApp(int serial, String pin, String aid) {
        Log.d(TAG, "supplyIccPinForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPinForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to supplyIccPinForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPuk2ForApp(int serial, String puk2, String pin2, String aid) {
        Log.d(TAG, "supplyIccPuk2ForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPuk2ForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to supplyIccPuk2ForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyIccPukForApp(int serial, String puk, String pin, String aid) {
        Log.d(TAG, "supplyIccPukForApp");
        // TODO: cache value
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplyIccPukForAppResponse(rsp, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to supplyIccPukForApp from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplySimDepersonalization(int serial, int persoType, String controlKey) {
        Log.d(TAG, "supplySimDepersonalization");
        // TODO: cache value
        int retPersoType = persoType;
        int setFacilityLockForApp = 10;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.supplySimDepersonalizationResponse(
                    rsp, retPersoType, setFacilityLockForApp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to supplySimDepersonalization from AIDL. Exception" + ex);
        }
    }

    @Override
    public void updateSimPhonebookRecords(
            int serial, android.hardware.radio.sim.PhonebookRecordInfo recordInfo) {
        Log.d(TAG, "updateSimPhonebookRecords");
        // TODO: cache value
        int updatedRecordIndex = 0;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioSimResponse.updateSimPhonebookRecordsResponse(rsp, updatedRecordIndex);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to updateSimPhonebookRecords from AIDL. Exception" + ex);
        }
    }

    public void carrierInfoForImsiEncryption() {
        Log.d(TAG, "carrierInfoForImsiEncryption");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.carrierInfoForImsiEncryption(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to carrierInfoForImsiEncryption from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void cdmaSubscriptionSourceChanged(int cdmaSource) {
        Log.d(TAG, "carrierInfoForImsiEncryption");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.cdmaSubscriptionSourceChanged(
                        RadioIndicationType.UNSOLICITED, cdmaSource);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to cdmaSubscriptionSourceChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void simPhonebookChanged() {
        Log.d(TAG, "simPhonebookChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simPhonebookChanged(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to simPhonebookChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void simPhonebookRecordsReceived(
            byte status, android.hardware.radio.sim.PhonebookRecordInfo[] records) {
        Log.d(TAG, "simPhonebookRecordsReceived");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simPhonebookRecordsReceived(
                        RadioIndicationType.UNSOLICITED, status, records);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to simPhonebookRecordsReceived from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void simRefresh(android.hardware.radio.sim.SimRefreshResult refreshResult) {
        Log.d(TAG, "simRefresh");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simRefresh(RadioIndicationType.UNSOLICITED, refreshResult);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to simRefresh from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void simStatusChanged() {
        Log.d(TAG, "simStatusChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.simStatusChanged(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to simStatusChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void stkEventNotify(String cmd) {
        Log.d(TAG, "stkEventNotify");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.stkEventNotify(RadioIndicationType.UNSOLICITED, cmd);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to stkEventNotify from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void stkProactiveCommand(String cmd) {
        Log.d(TAG, "stkProactiveCommand");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.stkProactiveCommand(RadioIndicationType.UNSOLICITED, cmd);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to stkProactiveCommand from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void stkSessionEnd() {
        Log.d(TAG, "stkSessionEnd");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.stkSessionEnd(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to stkSessionEnd from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void subscriptionStatusChanged(boolean activate) {
        Log.d(TAG, "subscriptionStatusChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.subscriptionStatusChanged(
                        RadioIndicationType.UNSOLICITED, activate);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to subscriptionStatusChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    public void uiccApplicationsEnablementChanged(boolean enabled) {
        Log.d(TAG, "uiccApplicationsEnablementChanged");

        if (mRadioSimIndication != null) {
            try {
                mRadioSimIndication.uiccApplicationsEnablementChanged(
                        RadioIndicationType.UNSOLICITED, enabled);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to uiccApplicationsEnablementChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioSimIndication");
        }
    }

    private CardStatus setCardAbsent() {
        CardStatus cardStatus = new CardStatus();
        cardStatus.cardState = CardStatus.STATE_ABSENT;
        cardStatus.universalPinState = 0;
        cardStatus.gsmUmtsSubscriptionAppIndex = -1;
        cardStatus.cdmaSubscriptionAppIndex = -1;
        cardStatus.imsSubscriptionAppIndex = -1;
        cardStatus.applications = new android.hardware.radio.sim.AppStatus[0];
        cardStatus.atr = "";
        cardStatus.iccid = "";
        cardStatus.eid = "";
        cardStatus.slotMap = new android.hardware.radio.config.SlotPortMapping();
        cardStatus.slotMap.physicalSlotId = 0;
        cardStatus.slotMap.portId = 0;

        return cardStatus;
    }
}
