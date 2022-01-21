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

import android.content.Context;
import android.hardware.radio.config.PhoneCapability;
import android.hardware.radio.config.SimPortInfo;
import android.hardware.radio.config.SimSlotStatus;
import android.hardware.radio.sim.CardStatus;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.util.Log;

public class MockModemConfigBase implements MockModemConfigInterface {
    // ***** Instance Variables
    private static final int DEFAULT_SUB_ID = 0;
    private String mTAG = "MockModemConfigBase";
    private final Handler mHandler;
    private Context mContext;
    private int mSubId;
    private int mSimPhyicalId;
    private Object mConfigAccess;
    private int mNumOfSim = MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT;
    private int mNumOfPhone = MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM;

    // ***** Events
    static final int EVENT_SET_RADIO_POWER = 1;
    static final int EVENT_SET_SIM_PRESENT = 2;

    // ***** Modem config values
    private String mBasebandVersion = MockModemConfigInterface.DEFAULT_BASEBAND_VERSION;
    private String mImei = MockModemConfigInterface.DEFAULT_IMEI;
    private String mImeiSv = MockModemConfigInterface.DEFAULT_IMEISV;
    private String mEsn = MockModemConfigInterface.DEFAULT_ESN;
    private String mMeid = MockModemConfigInterface.DEFAULT_MEID;
    private int mRadioState = MockModemConfigInterface.DEFAULT_RADIO_STATE;
    private byte mNumOfLiveModem = MockModemConfigInterface.DEFAULT_NUM_OF_LIVE_MODEM;
    private SimSlotStatus[] mSimSlotStatus;
    private CardStatus mCardStatus;
    private MockSimCard[] mSIMCard;
    private PhoneCapability mPhoneCapability = new PhoneCapability();

    // ***** RegistrantLists
    // ***** IRadioConfig RegistrantLists
    private RegistrantList mNumOfLiveModemChangedRegistrants = new RegistrantList();
    private RegistrantList mPhoneCapabilityChangedRegistrants = new RegistrantList();
    private RegistrantList mSimSlotStatusChangedRegistrants = new RegistrantList();

    // ***** IRadioModem RegistrantLists
    private RegistrantList mBasebandVersionChangedRegistrants = new RegistrantList();
    private RegistrantList mDeviceIdentityChangedRegistrants = new RegistrantList();
    private RegistrantList mRadioStateChangedRegistrants = new RegistrantList();

    // ***** IRadioSim RegistrantLists
    private RegistrantList mCardStatusChangedRegistrants = new RegistrantList();

    public MockModemConfigBase(Context context, int instanceId, int numOfSim, int numOfPhone) {
        mContext = context;
        mSubId = instanceId;
        mNumOfSim =
                (numOfSim > MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT)
                        ? MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT
                        : numOfSim;
        mNumOfPhone =
                (numOfPhone > MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM)
                        ? MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM
                        : numOfPhone;
        mTAG = mTAG + "[" + mSubId + "]";
        mConfigAccess = new Object();
        mHandler = new MockModemConfigHandler();
        mSimSlotStatus = new SimSlotStatus[mNumOfSim];
        mCardStatus = new CardStatus();
        mSIMCard = new MockSimCard[mNumOfSim];
        mSimPhyicalId = mSubId; // for default mapping
        createSIMCards();
        setDefaultConfigValue();
    }

    public class MockModemConfigHandler extends Handler {
        // ***** Handler implementation
        @Override
        public void handleMessage(Message msg) {
            synchronized (mConfigAccess) {
                switch (msg.what) {
                    case EVENT_SET_RADIO_POWER:
                        int state = msg.arg1;
                        if (state >= RADIO_STATE_UNAVAILABLE && state <= RADIO_STATE_ON) {
                            Log.d(
                                    mTAG,
                                    "EVENT_SET_RADIO_POWER: old("
                                            + mRadioState
                                            + "), new("
                                            + state
                                            + ")");
                            if (mRadioState != state) {
                                mRadioState = state;
                                mRadioStateChangedRegistrants.notifyRegistrants(
                                        new AsyncResult(null, mRadioState, null));
                            }
                        } else {
                            Log.e(mTAG, "EVENT_SET_RADIO_POWER: invalid state(" + state + ")");
                            mRadioStateChangedRegistrants.notifyRegistrants(null);
                        }
                        break;
                    case EVENT_SET_SIM_PRESENT:
                        boolean isPresent = msg.getData().getBoolean("isPresent", false);
                        Log.d(mTAG, "EVENT_SET_SIM_PRESENT: " + (isPresent ? "Present" : "Absent"));
                        int newCardState =
                                isPresent ? CardStatus.STATE_PRESENT : CardStatus.STATE_ABSENT;
                        if (mSubId == DEFAULT_SUB_ID
                                && mSimSlotStatus[mSimPhyicalId].cardState != newCardState) {
                            mSimSlotStatus[mSimPhyicalId].cardState = newCardState;
                            mSimSlotStatusChangedRegistrants.notifyRegistrants(
                                    new AsyncResult(null, mSimSlotStatus, null));
                        }
                        if (mCardStatus.cardState != newCardState) {
                            mCardStatus.cardState = newCardState;
                            mCardStatusChangedRegistrants.notifyRegistrants(
                                    new AsyncResult(null, mCardStatus, null));
                        }
                        break;
                }
            }
        }
    }

    private void setDefaultConfigValue() {
        synchronized (mConfigAccess) {
            mBasebandVersion = MockModemConfigInterface.DEFAULT_BASEBAND_VERSION;
            mImei = MockModemConfigInterface.DEFAULT_IMEI;
            mImeiSv = MockModemConfigInterface.DEFAULT_IMEISV;
            mEsn = MockModemConfigInterface.DEFAULT_ESN;
            mMeid = MockModemConfigInterface.DEFAULT_MEID;
            mRadioState = MockModemConfigInterface.DEFAULT_RADIO_STATE;
            mNumOfLiveModem = MockModemConfigInterface.DEFAULT_NUM_OF_LIVE_MODEM;
            setDefaultPhoneCapability(mPhoneCapability);
            if (mSubId == DEFAULT_SUB_ID) {
                setDefaultSimSlotStatus();
            }
            setDefaultCardStatus();
        }
    }

    private void setDefaultPhoneCapability(PhoneCapability phoneCapability) {
        phoneCapability.logicalModemIds =
                new byte[MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM];
        phoneCapability.maxActiveData = MockModemConfigInterface.DEFAULT_MAX_ACTIVE_DATA;
        phoneCapability.maxActiveInternetData =
                MockModemConfigInterface.DEFAULT_MAX_ACTIVE_INTERNAL_DATA;
        phoneCapability.isInternetLingeringSupported =
                MockModemConfigInterface.DEFAULT_IS_INTERNAL_LINGERING_SUPPORTED;
        phoneCapability.logicalModemIds[0] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM1_ID;
        phoneCapability.logicalModemIds[1] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM2_ID;
    }

    private void createSIMCards() {
        for (int i = 0; i < mNumOfSim; i++) {
            mSIMCard[i] = new MockSimCard(i);
        }
    }

    private void setDefaultSimSlotStatus() {
        if (mSubId != DEFAULT_SUB_ID) {
            // Only sub 0 needs to response SimSlotStatus
            return;
        }

        for (int i = 0; i < mNumOfSim; i++) {
            int portInfoListLen = mSIMCard[i].getNumOfSimPortInfo();
            mSimSlotStatus[i] = new SimSlotStatus();
            mSimSlotStatus[i].cardState =
                    mSIMCard[i].isCardPresent()
                            ? CardStatus.STATE_PRESENT
                            : CardStatus.STATE_ABSENT;
            mSimSlotStatus[i].atr = mSIMCard[i].getATR();
            mSimSlotStatus[i].eid = mSIMCard[i].getEID();
            // Current only support one Sim port in MockSimCard
            SimPortInfo[] portInfoList0 = new SimPortInfo[portInfoListLen];
            portInfoList0[0] = new SimPortInfo();
            portInfoList0[0].portActive = mSIMCard[i].isSlotPortActive();
            portInfoList0[0].logicalSlotId = mSIMCard[i].getLogicalSlotId();
            portInfoList0[0].iccId = mSIMCard[i].getICCID();
            mSimSlotStatus[i].portInfo = portInfoList0;
        }
    }

    private void setDefaultCardStatus() {
        if (mSimPhyicalId != -1) {
            int numbOfSimApp = mSIMCard[mSimPhyicalId].getNumOfSimApp();
            mCardStatus = new CardStatus();
            mCardStatus.cardState =
                    mSIMCard[mSimPhyicalId].isCardPresent()
                            ? CardStatus.STATE_PRESENT
                            : CardStatus.STATE_ABSENT;
            mCardStatus.universalPinState = mSIMCard[mSimPhyicalId].getUniversalPinState();
            mCardStatus.gsmUmtsSubscriptionAppIndex = mSIMCard[mSimPhyicalId].getGsmAppIndex();
            mCardStatus.cdmaSubscriptionAppIndex = mSIMCard[mSimPhyicalId].getCdmaAppIndex();
            mCardStatus.imsSubscriptionAppIndex = mSIMCard[mSimPhyicalId].getImsAppIndex();
            mCardStatus.applications = new android.hardware.radio.sim.AppStatus[numbOfSimApp];
            mCardStatus.atr = mSIMCard[mSimPhyicalId].getATR();
            mCardStatus.iccid = mSIMCard[mSimPhyicalId].getICCID();
            mCardStatus.eid = mSIMCard[mSimPhyicalId].getEID();
            mCardStatus.slotMap = new android.hardware.radio.config.SlotPortMapping();
            mCardStatus.slotMap.physicalSlotId = mSIMCard[mSimPhyicalId].getPhysicalSlotId();
            mCardStatus.slotMap.portId = mSIMCard[mSimPhyicalId].getSlotPortId();
        } else {
            Log.e(mTAG, "Invalid Sim physical id");
        }
    }

    private void notifyDeviceIdentityChangedRegistrants() {
        String[] deviceIdentity = new String[4];
        synchronized (mConfigAccess) {
            deviceIdentity[0] = mImei;
            deviceIdentity[1] = mImeiSv;
            deviceIdentity[2] = mEsn;
            deviceIdentity[3] = mMeid;
        }
        AsyncResult ar = new AsyncResult(null, deviceIdentity, null);
        mDeviceIdentityChangedRegistrants.notifyRegistrants(ar);
    }

    // ***** MockModemConfigInterface implementation
    @Override
    public void notifyAllRegistrantNotifications() {
        Log.d(mTAG, "notifyAllRegistrantNotifications");
        synchronized (mConfigAccess) {
            // IRadioConfig
            mNumOfLiveModemChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, mNumOfLiveModem, null));
            mPhoneCapabilityChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, mPhoneCapability, null));
            mSimSlotStatusChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, mSimSlotStatus, null));

            // IRadioModem
            mBasebandVersionChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, mBasebandVersion, null));
            notifyDeviceIdentityChangedRegistrants();
            mRadioStateChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, mRadioState, null));

            // IRadioSim
            mCardStatusChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, mCardStatus, null));
        }
    }

    // ***** IRadioConfig notification implementation
    @Override
    public void registerForNumOfLiveModemChanged(Handler h, int what, Object obj) {
        mNumOfLiveModemChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForNumOfLiveModemChanged(Handler h) {
        mNumOfLiveModemChangedRegistrants.remove(h);
    }

    @Override
    public void registerForPhoneCapabilityChanged(Handler h, int what, Object obj) {
        mPhoneCapabilityChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForPhoneCapabilityChanged(Handler h) {
        mPhoneCapabilityChangedRegistrants.remove(h);
    }

    @Override
    public void registerForSimSlotStatusChanged(Handler h, int what, Object obj) {
        mSimSlotStatusChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimSlotStatusChanged(Handler h) {
        mSimSlotStatusChangedRegistrants.remove(h);
    }

    // ***** IRadioModem notification implementation
    @Override
    public void registerForBasebandVersionChanged(Handler h, int what, Object obj) {
        mBasebandVersionChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForBasebandVersionChanged(Handler h) {
        mBasebandVersionChangedRegistrants.remove(h);
    }

    @Override
    public void registerForDeviceIdentityChanged(Handler h, int what, Object obj) {
        mDeviceIdentityChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForDeviceIdentityChanged(Handler h) {
        mDeviceIdentityChangedRegistrants.remove(h);
    }

    @Override
    public void registerForRadioStateChanged(Handler h, int what, Object obj) {
        mRadioStateChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForRadioStateChanged(Handler h) {
        mRadioStateChangedRegistrants.remove(h);
    }

    // ***** IRadioSim notification implementation
    @Override
    public void registerForCardStatusChanged(Handler h, int what, Object obj) {
        mCardStatusChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCardStatusChanged(Handler h) {
        mCardStatusChangedRegistrants.remove(h);
    }

    // ***** IRadioConfig set APIs implementation

    // ***** IRadioModem set APIs implementation
    @Override
    public void setRadioState(int state, String client) {
        Log.d(mTAG, "setRadioState (" + state + ") from " + client);

        Message msg = mHandler.obtainMessage(EVENT_SET_RADIO_POWER);
        msg.arg1 = state;
        mHandler.sendMessage(msg);
    }

    // ***** IRadioSim set APIs implementation

    // ***** IRadioNetwork set APIs implementation

    // ***** IRadioVoice set APIs implementation

    // ***** IRadioData set APIs implementation

    // ***** IRadioMessaging set APIs implementation

    // ***** Helper APIs implementation
    @Override
    public void setSimPresent(boolean isPresent, String client) {
        Log.d(mTAG, "setSimPresent (" + (isPresent ? "Present" : "Absent") + ") from: " + client);

        Message msg = mHandler.obtainMessage(EVENT_SET_SIM_PRESENT);
        msg.getData().putBoolean("isPresent", isPresent);
        mHandler.sendMessage(msg);
    }
}
