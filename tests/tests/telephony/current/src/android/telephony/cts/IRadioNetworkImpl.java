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
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.network.IRadioNetwork;
import android.hardware.radio.network.IRadioNetworkIndication;
import android.hardware.radio.network.IRadioNetworkResponse;
import android.hardware.radio.network.NetworkScanRequest;
import android.hardware.radio.network.RadioAccessSpecifier;
import android.hardware.radio.network.SignalThresholdInfo;
import android.os.RemoteException;
import android.util.Log;

public class IRadioNetworkImpl extends IRadioNetwork.Stub {
    private static final String TAG = "MRNW";

    private final MockModemService mService;
    private IRadioNetworkResponse mRadioNetworkResponse;
    private IRadioNetworkIndication mRadioNetworkIndication;

    public IRadioNetworkImpl(MockModemService service) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
    }

    // Implementation of IRadioNetwork functions
    @Override
    public void getAllowedNetworkTypesBitmap(int serial) {
        Log.d(TAG, "getAllowedNetworkTypesBitmap");
        int networkTypeBitmap = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);

        try {
            mRadioNetworkResponse.getAllowedNetworkTypesBitmapResponse(rsp, networkTypeBitmap);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getAllowedNetworkTypesBitmap from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getAvailableBandModes(int serial) {
        Log.d(TAG, "getAvailableBandModes");

        int[] bandModes = new int[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getAvailableBandModesResponse(rsp, bandModes);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getAvailableBandModes from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getAvailableNetworks(int serial) {
        Log.d(TAG, "getAvailableNetworks");

        android.hardware.radio.network.OperatorInfo[] networkInfos =
                new android.hardware.radio.network.OperatorInfo[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getAvailableNetworksResponse(rsp, networkInfos);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getAvailableNetworks from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getBarringInfo(int serial) {
        Log.d(TAG, "getBarringInfo");

        android.hardware.radio.network.CellIdentity cellIdentity =
                new android.hardware.radio.network.CellIdentity();
        android.hardware.radio.network.BarringInfo[] barringInfos =
                new android.hardware.radio.network.BarringInfo[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getBarringInfoResponse(rsp, cellIdentity, barringInfos);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getBarringInfo from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCdmaRoamingPreference(int serial) {
        Log.d(TAG, "getCdmaRoamingPreference");
        int type = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getCdmaRoamingPreferenceResponse(rsp, type);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCdmaRoamingPreference from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCellInfoList(int serial) {
        Log.d(TAG, "getCellInfoList");

        android.hardware.radio.network.CellInfo[] cellInfo =
                new android.hardware.radio.network.CellInfo[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getCellInfoListResponse(rsp, cellInfo);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCellInfoList from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getDataRegistrationState(int serial) {
        Log.d(TAG, "getDataRegistrationState");

        android.hardware.radio.network.RegStateResult dataRegResponse =
                new android.hardware.radio.network.RegStateResult();
        dataRegResponse.accessTechnologySpecificInfo =
                android.hardware.radio.network.AccessTechnologySpecificInfo.noinit(true);
        dataRegResponse.cellIdentity = android.hardware.radio.network.CellIdentity.noinit(true);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getDataRegistrationStateResponse(rsp, dataRegResponse);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getImsRegistrationState(int serial) {
        Log.d(TAG, "getImsRegistrationState");
        boolean isRegistered = false;
        int ratFamily = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getImsRegistrationStateResponse(rsp, isRegistered, ratFamily);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getImsRegistrationState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getNetworkSelectionMode(int serial) {
        Log.d(TAG, "getNetworkSelectionMode");
        boolean manual = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getNetworkSelectionModeResponse(rsp, manual);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getNetworkSelectionMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getOperator(int serial) {
        Log.d(TAG, "getOperator");

        String longName = "";
        String shortName = "";
        String numeric = "";
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getOperatorResponse(rsp, longName, shortName, numeric);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getOperator from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSignalStrength(int serial) {
        Log.d(TAG, "getSignalStrength");

        android.hardware.radio.network.SignalStrength signalStrength =
                new android.hardware.radio.network.SignalStrength();
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getSignalStrengthResponse(rsp, signalStrength);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getSignalStrength from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSystemSelectionChannels(int serial) {
        Log.d(TAG, "getSystemSelectionChannels");

        android.hardware.radio.network.RadioAccessSpecifier[] specifiers =
                new android.hardware.radio.network.RadioAccessSpecifier[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getSystemSelectionChannelsResponse(rsp, specifiers);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getSystemSelectionChannels from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getVoiceRadioTechnology(int serial) {
        Log.d(TAG, "getVoiceRadioTechnology");
        int rat = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getVoiceRadioTechnologyResponse(rsp, rat);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getVoiceRadioTechnology from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getVoiceRegistrationState(int serial) {
        Log.d(TAG, "getVoiceRegistrationState");

        android.hardware.radio.network.RegStateResult voiceRegResponse =
                new android.hardware.radio.network.RegStateResult();
        voiceRegResponse.accessTechnologySpecificInfo =
                android.hardware.radio.network.AccessTechnologySpecificInfo.noinit(true);
        voiceRegResponse.cellIdentity = android.hardware.radio.network.CellIdentity.noinit(true);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioNetworkResponse.getVoiceRegistrationStateResponse(rsp, voiceRegResponse);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getVoiceRegistrationState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void isNrDualConnectivityEnabled(int serial) {
        Log.d(TAG, "isNrDualConnectivityEnabled");
        boolean isEnabled = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.isNrDualConnectivityEnabledResponse(rsp, isEnabled);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to isNrDualConnectivityEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(TAG, "responseAcknowledgement");
    }

    @Override
    public void setAllowedNetworkTypesBitmap(int serial, int networkTypeBitmap) {
        Log.d(TAG, "setAllowedNetworkTypesBitmap");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setAllowedNetworkTypesBitmapResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setAllowedNetworkTypesBitmap from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setBandMode(int serial, int mode) {
        Log.d(TAG, "setBandMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setBandModeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setBandMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setBarringPassword(
            int serial, String facility, String oldPassword, String newPassword) {
        Log.d(TAG, "setBarringPassword");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setBarringPasswordResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setBarringPassword from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCdmaRoamingPreference(int serial, int type) {
        Log.d(TAG, "setCdmaRoamingPreference");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setCdmaRoamingPreferenceResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCdmaRoamingPreference from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCellInfoListRate(int serial, int rate) {
        Log.d(TAG, "setCellInfoListRate");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setCellInfoListRateResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCellInfoListRate from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setIndicationFilter(int serial, int indicationFilter) {
        Log.d(TAG, "setIndicationFilter");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setIndicationFilterResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setIndicationFilter from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setLinkCapacityReportingCriteria(
            int serial,
            int hysteresisMs,
            int hysteresisDlKbps,
            int hysteresisUlKbps,
            int[] thresholdsDownlinkKbps,
            int[] thresholdsUplinkKbps,
            int accessNetwork) {
        Log.d(TAG, "setLinkCapacityReportingCriteria");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setLinkCapacityReportingCriteriaResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setLinkCapacityReportingCriteria from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setLocationUpdates(int serial, boolean enable) {
        Log.d(TAG, "setLocationUpdates");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setLocationUpdatesResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setLocationUpdates from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNetworkSelectionModeAutomatic(int serial) {
        Log.d(TAG, "setNetworkSelectionModeAutomatic");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setNetworkSelectionModeAutomaticResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setNetworkSelectionModeAutomatic from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNetworkSelectionModeManual(int serial, String operatorNumeric, int ran) {
        Log.d(TAG, "setNetworkSelectionModeManual");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setNetworkSelectionModeManualResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setNetworkSelectionModeManual from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNrDualConnectivityState(int serial, byte nrDualConnectivityState) {
        Log.d(TAG, "setNrDualConnectivityState");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setNrDualConnectivityStateResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setNrDualConnectivityState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setResponseFunctions(
            IRadioNetworkResponse radioNetworkResponse,
            IRadioNetworkIndication radioNetworkIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioNetworkResponse = radioNetworkResponse;
        mRadioNetworkIndication = radioNetworkIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void setSignalStrengthReportingCriteria(
            int serial, SignalThresholdInfo[] signalThresholdInfos) {
        Log.d(TAG, "setSignalStrengthReportingCriteria");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setSignalStrengthReportingCriteriaResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setSignalStrengthReportingCriteria from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSuppServiceNotifications(int serial, boolean enable) {
        Log.d(TAG, "setSuppServiceNotifications");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setSuppServiceNotificationsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setSuppServiceNotifications from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSystemSelectionChannels(
            int serial, boolean specifyChannels, RadioAccessSpecifier[] specifiers) {
        Log.d(TAG, "setSystemSelectionChannels");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setSystemSelectionChannelsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setSystemSelectionChannels from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startNetworkScan(int serial, NetworkScanRequest request) {
        Log.d(TAG, "startNetworkScan");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.startNetworkScanResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to startNetworkScan from AIDL. Exception" + ex);
        }
    }

    @Override
    public void stopNetworkScan(int serial) {
        Log.d(TAG, "stopNetworkScan");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.stopNetworkScanResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to stopNetworkScan from AIDL. Exception" + ex);
        }
    }

    @Override
    public void supplyNetworkDepersonalization(int serial, String netPin) {
        Log.d(TAG, "supplyNetworkDepersonalization");
        int remainingRetries = 0;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.supplyNetworkDepersonalizationResponse(rsp, remainingRetries);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to supplyNetworkDepersonalization from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setUsageSetting(int serial, int usageSetting) {
        Log.d(TAG, "setUsageSetting");
        int remainingRetries = 0;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.setUsageSettingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setUsageSetting from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getUsageSetting(int serial) {
        Log.d(TAG, "getUsageSetting");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioNetworkResponse.getUsageSettingResponse(rsp, -1 /* Invalid value */);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getUsageSetting from AIDL. Exception" + ex);
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioNetwork.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioNetwork.VERSION;
    }
}
