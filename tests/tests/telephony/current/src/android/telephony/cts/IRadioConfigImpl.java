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
import android.hardware.radio.config.IRadioConfig;
import android.hardware.radio.config.IRadioConfigIndication;
import android.hardware.radio.config.IRadioConfigResponse;
import android.hardware.radio.config.PhoneCapability;
import android.hardware.radio.config.SimPortInfo;
import android.hardware.radio.config.SimSlotStatus;
import android.hardware.radio.config.SlotPortMapping;
import android.hardware.radio.sim.CardStatus;
import android.os.RemoteException;
import android.util.Log;

public class IRadioConfigImpl extends IRadioConfig.Stub {
    private static final String TAG = "MRCFG";
    private final TestMockModemService mService;
    private byte mNumOfLiveModems = 1;
    private IRadioConfigResponse mRadioConfigResponse;
    private IRadioConfigIndication mRadioConfigIndication;

    private int mSlotNum = 1;
    private boolean mSimStatePresent = false;

    public IRadioConfigImpl(TestMockModemService service) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
        mSlotNum = mService.getNumPhysicalSlots();
    }

    // Implementation of IRadioConfig functions
    @Override
    public void getHalDeviceCapabilities(int serial) {
        Log.d(TAG, "getHalDeviceCapabilities");

        boolean modemReducedFeatureSet1 = false;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.getHalDeviceCapabilitiesResponse(rsp, modemReducedFeatureSet1);
        } catch (RemoteException ex) {
            Log.e(
                    TAG,
                    "Failed to invoke getHalDeviceCapabilitiesResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getNumOfLiveModems(int serial) {
        Log.d(TAG, "getNumOfLiveModems");

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioConfigResponse.getNumOfLiveModemsResponse(rsp, mNumOfLiveModems);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke getNumOfLiveModemsResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getPhoneCapability(int serial) {
        Log.d(TAG, "getPhoneCapability");

        PhoneCapability phoneCapability = new PhoneCapability();
        phoneCapability.logicalModemIds = new byte[2];
        convertMockPhoneCapToHal(phoneCapability);

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioConfigResponse.getPhoneCapabilityResponse(rsp, phoneCapability);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke getPhoneCapabilityResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSimSlotsStatus(int serial) {
        Log.d(TAG, "getSimSlotsStatus");
        SimSlotStatus[] slotStatus;

        if (mSlotNum < 1) {
            Log.d(TAG, "No slot information is retured.");
            slotStatus = null;
        } else {
            slotStatus = new SimSlotStatus[mSlotNum];

            for (int i = 0; i < mSlotNum; i++) slotStatus[i] = new SimSlotStatus();
            convertMockSimSlotStatusToHal(slotStatus);
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioConfigResponse.getSimSlotsStatusResponse(rsp, slotStatus);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke getSimSlotsStatusResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNumOfLiveModems(int serial, byte numOfLiveModems) {
        Log.d(TAG, "setNumOfLiveModems");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.setNumOfLiveModemsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke setNumOfLiveModemsResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setPreferredDataModem(int serial, byte modemId) {
        Log.d(TAG, "setPreferredDataModem");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.setPreferredDataModemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke setPreferredDataModemResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setResponseFunctions(
            IRadioConfigResponse radioConfigResponse,
            IRadioConfigIndication radioConfigIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioConfigResponse = radioConfigResponse;
        mRadioConfigIndication = radioConfigIndication;
        mService.countDownLatch(TestMockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void setSimSlotsMapping(int serial, SlotPortMapping[] slotMap) {
        Log.d(TAG, "setSimSlotsMapping");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.setSimSlotsMappingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke setSimSlotsMappingResponse from AIDL. Exception" + ex);
        }
    }

    public void unsolSimSlotsStatusChanged() {
        Log.d(TAG, "unsolSimSlotsStatusChanged");
        SimSlotStatus[] slotStatus;

        if (mRadioConfigIndication != null) {

            if (mSlotNum < 1) {
                Log.d(TAG, "No slot information is retured.");
                slotStatus = null;
            } else {
                slotStatus = new SimSlotStatus[mSlotNum];

                for (int i = 0; i < mSlotNum; i++) slotStatus[i] = new SimSlotStatus();
                convertMockSimSlotStatusToHal(slotStatus);
            }

            try {
                mRadioConfigIndication.simSlotsStatusChanged(
                        RadioIndicationType.UNSOLICITED, slotStatus);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to invoke simSlotsStatusChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioConfigIndication");
        }
    }

    private void convertMockSimSlotStatusToHal(SimSlotStatus[] slotStatus) {

        int portInfoListLen = 1;

        if (mSlotNum >= 1) {
            if (mSimStatePresent) {
                slotStatus[0].cardState = CardStatus.STATE_PRESENT;
            } else {
                slotStatus[0].cardState = CardStatus.STATE_ABSENT;
            }
            slotStatus[0].atr = "";
            slotStatus[0].eid = "";
            SimPortInfo[] portInfoList0 = new SimPortInfo[portInfoListLen];
            for (int i = 0; i < portInfoListLen; i++) portInfoList0[i] = new SimPortInfo();
            portInfoList0[0].portActive = true;
            portInfoList0[0].logicalSlotId = 0;
            portInfoList0[0].iccId = "";
            slotStatus[0].portInfo = portInfoList0;
        }

        if (mSlotNum >= 2) {
            slotStatus[1].cardState = CardStatus.STATE_PRESENT;
            slotStatus[1].atr = "3B9F97C00A3FC6828031E073FE211F65D002341512810F51";
            slotStatus[1].eid = "89033023426200000000005430099507";
            SimPortInfo[] portInfoList1 = new SimPortInfo[portInfoListLen];
            for (int i = 0; i < portInfoListLen; i++) portInfoList1[i] = new SimPortInfo();
            portInfoList1[0].portActive = false;
            portInfoList1[0].logicalSlotId = -1;
            portInfoList1[0].iccId = "";
            slotStatus[1].portInfo = portInfoList1;
        }

        if (mSlotNum >= 3) {
            slotStatus[2].cardState = CardStatus.STATE_ABSENT;
            slotStatus[2].atr = "";
            slotStatus[2].eid = "";
            SimPortInfo[] portInfoList2 = new SimPortInfo[portInfoListLen];
            for (int i = 0; i < portInfoListLen; i++) portInfoList2[i] = new SimPortInfo();
            portInfoList2[0].portActive = false;
            portInfoList2[0].logicalSlotId = -1;
            portInfoList2[0].iccId = "";
            slotStatus[2].portInfo = portInfoList2;
        }
    }

    private void convertMockPhoneCapToHal(PhoneCapability phoneCapability) {

        phoneCapability.maxActiveData = 2;
        phoneCapability.maxActiveInternetData = 1;
        phoneCapability.isInternetLingeringSupported = false;
        phoneCapability.logicalModemIds[0] = 0;
        phoneCapability.logicalModemIds[1] = 1;
    }

    // TODO: use helper function to handle
    public void setSimPresent(int slotId) {
        // TODO: check  slotId and Phone ID
        mSimStatePresent = true;
    }
}
