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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.radio.RadioError;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.RadioResponseType;
import android.os.Binder;
import android.os.IBinder;
import android.sysprop.TelephonyProperties;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockModemService extends Service {
    private static final String TAG = "MockModemService";

    public static final int TEST_TIMEOUT_MS = 30000;
    public static final String IRADIOCONFIG_MOCKMODEM_SERVICE_INTERFACE =
            "android.telephony.cts.iradioconfig.mockmodem.service";
    public static final String IRADIOMODEM_MOCKMODEM_SERVICE_INTERFACE =
            "android.telephony.cts.iradiomodem.mockmodem.service";
    public static final String IRADIOSIM_MOCKMODEM_SERVICE_INTERFACE =
            "android.telephony.cts.iradiosim.mockmodem.service";
    public static final String IRADIONETWORK_MOCKMODEM_SERVICE_INTERFACE =
            "android.telephony.cts.iradionetwork.mockmodem.service";
    public static final String IRADIODATA_MOCKMODEM_SERVICE_INTERFACE =
            "android.telephony.cts.iradiodata.mockmodem.service";
    public static final String IRADIOMESSAGING_MOCKMODEM_SERVICE_INTERFACE =
            "android.telephony.cts.iradiomessaging.mockmodem.service";
    public static final String IRADIOVOICE_MOCKMODEM_SERVICE_INTERFACE =
            "android.telephony.cts.iradiovoice.mockmodem.service";

    private static Context sContext;
    private static IRadioConfigImpl sIRadioConfigImpl;
    private static IRadioModemImpl sIRadioModemImpl;
    private static IRadioSimImpl sIRadioSimImpl;
    private static IRadioNetworkImpl sIRadioNetworkImpl;
    private static IRadioDataImpl sIRadioDataImpl;
    private static IRadioMessagingImpl sIRadioMessagingImpl;
    private static IRadioVoiceImpl sIRadioVoiceImpl;

    public static final int LATCH_MOCK_MODEM_SERVICE_READY = 0;
    public static final int LATCH_MOCK_MODEM_RADIO_POWR_ON = 1;
    public static final int LATCH_MOCK_MODEM_RADIO_POWR_OFF = 2;
    public static final int LATCH_MOCK_MODEM_SIM_READY = 3;
    public static final int LATCH_MAX = 4;

    private static final int IRADIO_CONFIG_INTERFACE_NUMBER = 1;
    private static final int IRADIO_INTERFACE_NUMBER = 6;
    public static final int LATCH_RADIO_INTERFACES_READY = LATCH_MAX;
    public static final int LATCH_MOCK_MODEM_INITIALIZATION_READY =
            LATCH_RADIO_INTERFACES_READY + 1;
    public static final int TOTAL_LATCH_NUMBER = LATCH_MAX + 2;

    private int mSimNumber;

    private Object mLock;
    protected static CountDownLatch[] sLatches;
    private LocalBinder mBinder;

    // For local access of this Service.
    class LocalBinder extends Binder {
        MockModemService getService() {
            return MockModemService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Mock Modem Service Created");

        mSimNumber = 1; // TODO: Read property to know the device is single SIM or DSDS

        mLock = new Object();

        sLatches = new CountDownLatch[TOTAL_LATCH_NUMBER];
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }

        int radioInterfaceNumber =
                IRADIO_CONFIG_INTERFACE_NUMBER + mSimNumber * IRADIO_INTERFACE_NUMBER;
        sLatches[LATCH_RADIO_INTERFACES_READY] = new CountDownLatch(radioInterfaceNumber);
        sLatches[LATCH_MOCK_MODEM_INITIALIZATION_READY] = new CountDownLatch(1);

        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        sIRadioConfigImpl = new IRadioConfigImpl(this);
        sIRadioModemImpl = new IRadioModemImpl(this);
        sIRadioSimImpl = new IRadioSimImpl(this);
        sIRadioNetworkImpl = new IRadioNetworkImpl(this);
        sIRadioDataImpl = new IRadioDataImpl(this);
        sIRadioMessagingImpl = new IRadioMessagingImpl(this);
        sIRadioVoiceImpl = new IRadioVoiceImpl(this);

        mBinder = new LocalBinder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (IRADIOCONFIG_MOCKMODEM_SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioConfig");
            return sIRadioConfigImpl;
        } else if (IRADIOMODEM_MOCKMODEM_SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioModem");
            return sIRadioModemImpl;
        } else if (IRADIOSIM_MOCKMODEM_SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioSim");
            return sIRadioSimImpl;
        } else if (IRADIONETWORK_MOCKMODEM_SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioNetwork");
            return sIRadioNetworkImpl;
        } else if (IRADIODATA_MOCKMODEM_SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioData");
            return sIRadioDataImpl;
        } else if (IRADIOMESSAGING_MOCKMODEM_SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioMessaging");
            return sIRadioMessagingImpl;
        } else if (IRADIOVOICE_MOCKMODEM_SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "onBind-IRadioVoice");
            return sIRadioVoiceImpl;
        }

        countDownLatch(LATCH_MOCK_MODEM_SERVICE_READY);
        Log.i(TAG, "onBind-Local");
        return mBinder;
    }

    public void resetState() {
        synchronized (mLock) {
            for (int i = 0; i < LATCH_MAX; i++) {
                sLatches[i] = new CountDownLatch(1);
            }
        }
    }

    public boolean waitForLatchCountdown(int latchIndex) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public int getNumPhysicalSlots() {
        int numPhysicalSlots =
                sContext.getResources()
                        .getInteger(com.android.internal.R.integer.config_num_physical_slots);
        Log.d(TAG, "numPhysicalSlots: " + numPhysicalSlots);
        return numPhysicalSlots;
    }

    public RadioResponseInfo makeSolRsp(int serial) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = RadioError.NONE;

        return rspInfo;
    }

    public RadioResponseInfo makeSolRsp(int serial, int error) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = error;

        return rspInfo;
    }

    private boolean initRadioState() {
        int waitLatch;

        boolean apm = TelephonyProperties.airplane_mode_on().orElse(false);
        Log.d(TAG, "APM setting: " + apm);

        if (!apm) {
            waitLatch = LATCH_MOCK_MODEM_RADIO_POWR_ON;
        } else {
            waitLatch = LATCH_MOCK_MODEM_RADIO_POWR_OFF;
        }

        sIRadioModemImpl.radioStateChanged(android.hardware.radio.modem.RadioState.OFF);

        // Radio Power command is expected.
        return waitForLatchCountdown(waitLatch);
    }

    private boolean initSimSlotState() {

        // SIM/Slot commands are expected.
        return waitForLatchCountdown(LATCH_MOCK_MODEM_SIM_READY);
    }

    public void initialization() {
        Log.d(TAG, "initialization");

        boolean status = false;

        sIRadioModemImpl.rilConnected();

        status = initRadioState();
        if (!status) {
            Log.e(TAG, "radio state initialization fail");
            return;
        }

        status = initSimSlotState();
        if (!status) {
            Log.e(TAG, "sim/slot state initialization fail");
            return;
        }

        countDownLatch(LATCH_MOCK_MODEM_INITIALIZATION_READY);
    }

    public void unsolSimSlotsStatusChanged() {
        sIRadioConfigImpl.unsolSimSlotsStatusChanged();
        sIRadioSimImpl.simStatusChanged();
    }

    public void setSimPresent(int slotId) {
        Log.d(TAG, "setSimPresent");

        sIRadioSimImpl.setSimPresent(slotId);
        sIRadioConfigImpl.setSimPresent(slotId);
    }
}
