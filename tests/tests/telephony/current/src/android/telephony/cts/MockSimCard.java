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

import android.util.Log;

public class MockSimCard {
    private static final String TAG = "MockSimCard";

    /* Support SIM card identify */
    public static final int MOCK_SIM_PROFILE_ID_DEFAULT = 0; // SIM Absent
    public static final int MOCK_SIM_PROFILE_ID_MAX = 1;

    /* Support SIM slot */
    private static final int MOCK_SIM_SLOT_1 = 0;
    private static final int MOCK_SIM_SLOT_2 = 1;
    private static final int MOCK_SIM_SLOT_3 = 2;
    private static final int MOCK_SIM_SLOT_MAX = 3;

    /* Default value definition */
    private static final int MOCK_SIM_DEFAULT_SLOTID = MOCK_SIM_SLOT_1;
    private static final int DEFAULT_NUM_OF_SIM_PORT_INfO = 1;
    private static final int DEFAULT_NUM_OF_SIM_APP = 0;
    private static final int DEFAULT_GSM_APP_IDX = -1;
    private static final int DEFAULT_CDMA_APP_IDX = -1;
    private static final int DEFAULT_IMS_APP_IDX = -1;
    // SIM1 slot status
    private static final int DEFAULT_SIM1_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM1_CARD_PRESENT = false;
    private static final String DEFAULT_SIM1_ATR = "";
    private static final String DEFAULT_SIM1_EID = "";
    private static final String DEFAULT_SIM1_ICCID = "";
    private static final boolean DEFAULT_SIM1_PORT_ACTIVE = true;
    private static final int DEFAULT_SIM1_PORT_ID = 0;
    private static final int DEFAULT_SIM1_LOGICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM1_PHYSICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM1_UNIVERSAL_PIN_STATE = 0;
    // SIM2 slot status
    private static final int DEFAULT_SIM2_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM2_CARD_PRESENT = false;
    private static final String DEFAULT_SIM2_ATR =
            "3B9F97C00A3FC6828031E073FE211F65D002341512810F51";
    private static final String DEFAULT_SIM2_EID = "89033023426200000000005430099507";
    private static final String DEFAULT_SIM2_ICCID = "";
    private static final boolean DEFAULT_SIM2_PORT_ACTIVE = false;
    private static final int DEFAULT_SIM2_PORT_ID = 0;
    private static final int DEFAULT_SIM2_LOGICAL_SLOT_ID = -1;
    private static final int DEFAULT_SIM2_PHYSICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM2_UNIVERSAL_PIN_STATE = 0;
    // SIM3 slot status
    private static final int DEFAULT_SIM3_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM3_CARD_PRESENT = false;
    private static final String DEFAULT_SIM3_ATR = "";
    private static final String DEFAULT_SIM3_EID = "";
    private static final String DEFAULT_SIM3_ICCID = "";
    private static final boolean DEFAULT_SIM3_PORT_ACTIVE = false;
    private static final int DEFAULT_SIM3_PORT_ID = 0;
    private static final int DEFAULT_SIM3_LOGICAL_SLOT_ID = -1;
    private static final int DEFAULT_SIM3_PHYSICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM3_UNIVERSAL_PIN_STATE = 0;

    // SIM Slot status
    private int mPhysicalSlotId;
    private int mLogicalSlotId;
    private int mSlotPortId;
    private boolean mIsSlotPortActive;
    private boolean mIsCardPresent;

    // SIM card data
    private int mSimProfileId;
    private String mICCID;
    private String mEID;
    private String mATR;
    private int mUniversalPinState;
    private int mGsmAppIndex;
    private int mCdmaAppIndex;
    private int mImsAppIndex;
    private int mNumOfSimApp;

    public MockSimCard(int slotId) {
        int simprofile = DEFAULT_SIM1_PROFILE_ID;

        if (slotId >= MOCK_SIM_SLOT_MAX) {
            Log.e(
                    TAG,
                    "Invalid slot id("
                            + slotId
                            + "). Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            slotId = MOCK_SIM_DEFAULT_SLOTID;
        }

        // Init default SIM profile id
        switch (slotId) {
            case MOCK_SIM_SLOT_1:
                simprofile = DEFAULT_SIM1_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_2:
                simprofile = DEFAULT_SIM2_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_3:
                simprofile = DEFAULT_SIM3_PROFILE_ID;
                break;
        }

        // Initiate SIM card with default profile
        initMockSimCard(slotId, simprofile);
    }

    private void initMockSimCard(int slotId, int simProfileId) {
        if (slotId > MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT) {
            Log.e(
                    TAG,
                    "Physical slot id("
                            + slotId
                            + ") is invalid. Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            mPhysicalSlotId = MOCK_SIM_DEFAULT_SLOTID;
        } else {
            mPhysicalSlotId = slotId;
        }
        if (simProfileId >= 0 && simProfileId < MOCK_SIM_PROFILE_ID_MAX) {
            mSimProfileId = simProfileId;
            Log.i(
                    TAG,
                    "Load SIM profile ID: "
                            + mSimProfileId
                            + " into physical slot["
                            + mPhysicalSlotId
                            + "]");
        } else {
            mSimProfileId = MOCK_SIM_PROFILE_ID_DEFAULT;
            Log.e(
                    TAG,
                    "SIM Absent on physical slot["
                            + mPhysicalSlotId
                            + "]. Not support SIM card ID: "
                            + mSimProfileId);
        }

        // Initiate slot status
        initMockSimSlot();

        // Load SIM profile data
        loadMockSimCard();
    }

    private void initMockSimSlot() {
        switch (mPhysicalSlotId) {
            case MOCK_SIM_SLOT_1:
                mLogicalSlotId = DEFAULT_SIM1_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM1_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM1_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM1_CARD_PRESENT;
                break;
            case MOCK_SIM_SLOT_2:
                mLogicalSlotId = DEFAULT_SIM2_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM2_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM2_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
                break;
            case MOCK_SIM_SLOT_3:
                mLogicalSlotId = DEFAULT_SIM3_LOGICAL_SLOT_ID;
                mSlotPortId = DEFAULT_SIM3_PORT_ID;
                mIsSlotPortActive = DEFAULT_SIM3_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM3_CARD_PRESENT;
                break;
        }
    }

    private void loadMockSimCard() {
        // TODO: Read SIM card data from file
        switch (mSimProfileId) {
            default: // SIM absent
                switch (mPhysicalSlotId) {
                    case MOCK_SIM_SLOT_1:
                        mICCID = DEFAULT_SIM1_ICCID;
                        mATR = DEFAULT_SIM1_ATR;
                        mEID = DEFAULT_SIM1_EID;
                        mUniversalPinState = DEFAULT_SIM1_UNIVERSAL_PIN_STATE;
                        break;
                    case MOCK_SIM_SLOT_2:
                        mICCID = DEFAULT_SIM2_ICCID;
                        mATR = DEFAULT_SIM2_ATR;
                        mEID = DEFAULT_SIM2_EID;
                        mUniversalPinState = DEFAULT_SIM2_UNIVERSAL_PIN_STATE;
                        break;
                    case MOCK_SIM_SLOT_3:
                        mICCID = DEFAULT_SIM3_ICCID;
                        mATR = DEFAULT_SIM3_ATR;
                        mEID = DEFAULT_SIM3_EID;
                        mUniversalPinState = DEFAULT_SIM3_UNIVERSAL_PIN_STATE;
                        break;
                }
                mGsmAppIndex = DEFAULT_GSM_APP_IDX;
                mCdmaAppIndex = DEFAULT_CDMA_APP_IDX;
                mImsAppIndex = DEFAULT_IMS_APP_IDX;
                mNumOfSimApp = DEFAULT_NUM_OF_SIM_APP;
                break;
        }
    }

    public boolean isSlotPortActive() {
        return mIsSlotPortActive;
    }

    public boolean isCardPresent() {
        return mIsCardPresent;
    }

    public int getNumOfSimPortInfo() {
        return DEFAULT_NUM_OF_SIM_PORT_INfO;
    }

    public int getPhysicalSlotId() {
        return mPhysicalSlotId;
    }

    public int getLogicalSlotId() {
        return mLogicalSlotId;
    }

    public int getSlotPortId() {
        return mSlotPortId;
    }

    public String getEID() {
        return mEID;
    }

    public String getATR() {
        return mATR;
    }

    public String getICCID() {
        return mICCID;
    }

    public int getUniversalPinState() {
        return mUniversalPinState;
    }

    public int getGsmAppIndex() {
        return mGsmAppIndex;
    }

    public int getCdmaAppIndex() {
        return mCdmaAppIndex;
    }

    public int getImsAppIndex() {
        return mImsAppIndex;
    }

    public int getNumOfSimApp() {
        return mNumOfSimApp;
    }
}
