/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telephony.satellite.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.OutcomeReceiver;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteDatagramCallback;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteProvisionStateCallback;
import android.telephony.satellite.SatelliteStateCallback;
import android.telephony.satellite.SatelliteTransmissionUpdateCallback;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SatelliteManagerTestBase {
    protected static String TAG = "SatelliteManagerTestBase";

    protected static final String TOKEN = "TEST_TOKEN";
    protected static final long TIMEOUT = 1000;
    protected static SatelliteManager sSatelliteManager;

    protected static void beforeAllTestsBase() {
        sSatelliteManager = getContext().getSystemService(SatelliteManager.class);
    }

    protected static void afterAllTestsBase() {
        sSatelliteManager = null;
    }

    protected static boolean shouldTestSatellite() {
        if (!getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE)) {
            logd("Skipping tests because FEATURE_TELEPHONY_SATELLITE is not available");
            return false;
        }
        try {
            getContext().getSystemService(TelephonyManager.class)
                    .getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            logd("Skipping tests because Telephony service is null, exception=" + e);
            return false;
        }
        return true;
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    protected static void grantSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION);
    }

    protected static void revokeSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected static class SatelliteTransmissionUpdateCallbackTest implements
            SatelliteTransmissionUpdateCallback {

        protected static final class DatagramStateChangeArgument {
            protected int state;
            protected int pendingCount;
            protected int errorCode;

            DatagramStateChangeArgument(int state, int pendingCount, int errorCode) {
                this.state = state;
                this.pendingCount = pendingCount;
                this.errorCode = errorCode;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (other == null || getClass() != other.getClass()) return false;
                DatagramStateChangeArgument that = (DatagramStateChangeArgument) other;
                return state == that.state
                        && pendingCount  == that.pendingCount
                        && errorCode == that.errorCode;
            }

            @Override
            public String toString() {
                return ("state: " + state + " pendingCount: " + pendingCount
                        + " errorCode: " + errorCode);
            }
        }

        public PointingInfo mPointingInfo;
        private final Semaphore mPositionChangeSemaphore = new Semaphore(0);
        private List<DatagramStateChangeArgument> mSendDatagramStateChanges = new ArrayList<>();
        private final Object mSendDatagramStateChangesLock = new Object();
        private final Semaphore mSendSemaphore = new Semaphore(0);
        private List<DatagramStateChangeArgument> mReceiveDatagramStateChanges = new ArrayList<>();
        private final Object mReceiveDatagramStateChangesLock = new Object();
        private final Semaphore mReceiveSemaphore = new Semaphore(0);

        @Override
        public void onSatellitePositionChanged(PointingInfo pointingInfo) {
            logd("onSatellitePositionChanged: pointingInfo=" + pointingInfo);
            mPointingInfo = pointingInfo;

            try {
                mPositionChangeSemaphore.release();
            } catch (Exception e) {
                loge("onSatellitePositionChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onSendDatagramStateChanged(int state, int sendPendingCount, int errorCode) {
            logd("onSendDatagramStateChanged: state=" + state + ", sendPendingCount="
                    + sendPendingCount + ", errorCode=" + errorCode);
            synchronized (mSendDatagramStateChangesLock) {
                mSendDatagramStateChanges.add(new DatagramStateChangeArgument(state,
                        sendPendingCount, errorCode));
            }

            try {
                mSendSemaphore.release();
            } catch (Exception e) {
                loge("onSendDatagramStateChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onReceiveDatagramStateChanged(
                int state, int receivePendingCount, int errorCode) {
            logd("onReceiveDatagramStateChanged: state=" + state + ", "
                    + "receivePendingCount=" + receivePendingCount + ", errorCode=" + errorCode);
            synchronized (mReceiveDatagramStateChangesLock) {
                mReceiveDatagramStateChanges.add(new DatagramStateChangeArgument(state,
                        receivePendingCount, errorCode));
            }

            try {
                mReceiveSemaphore.release();
            } catch (Exception e) {
                loge("onReceiveDatagramStateChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilOnSatellitePositionChanged(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mPositionChangeSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatellitePositionChanged() callback");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnSatellitePositionChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilOnSendDatagramStateChanged(int expectedNumberOfEvents) {
            logd("waitUntilOnSendDatagramStateChanged expectedNumberOfEvents:" + expectedNumberOfEvents);
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSendSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSendDatagramStateChanged() callback");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnSendDatagramStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilOnReceiveDatagramStateChanged(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mReceiveSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onReceiveDatagramStateChanged()");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnReceiveDatagramStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearSendDatagramStateChanges() {
            synchronized (mSendDatagramStateChangesLock) {
                logd("clearSendDatagramStateChanges");
                mSendDatagramStateChanges.clear();
            }
        }

        public void clearReceiveDatagramStateChanges() {
            synchronized (mReceiveDatagramStateChangesLock) {
                logd("clearReceiveDatagramStateChanges");
                mReceiveDatagramStateChanges.clear();
            }
        }

        @Nullable
        public DatagramStateChangeArgument getSendDatagramStateChange(int index) {
            synchronized (mSendDatagramStateChangesLock) {
                if (index < mSendDatagramStateChanges.size()) {
                    return mSendDatagramStateChanges.get(index);
                } else {
                    Log.e(TAG, "getSendDatagramStateChange: invalid index= " + index
                            + " mSendDatagramStateChanges.size= "
                            + mSendDatagramStateChanges.size());
                    return null;
                }
            }
        }

        @Nullable
        public DatagramStateChangeArgument getReceiveDatagramStateChange(int index) {
            synchronized (mReceiveDatagramStateChangesLock) {
                if (index < mReceiveDatagramStateChanges.size()) {
                    return mReceiveDatagramStateChanges.get(index);
                } else {
                    Log.e(TAG, "getReceiveDatagramStateChange: invalid index= " + index
                            + " mReceiveDatagramStateChanges.size= "
                            + mReceiveDatagramStateChanges.size());
                    return null;
                }
            }
        }

        public int getNumOfSendDatagramStateChanges() {
            synchronized (mSendDatagramStateChangesLock) {
                logd("getNumOfSendDatagramStateChanges size:" + mSendDatagramStateChanges.size());
                return mSendDatagramStateChanges.size();
            }
        }

        public int getNumOfReceiveDatagramStateChanges() {
            synchronized (mReceiveDatagramStateChangesLock) {
                return mReceiveDatagramStateChanges.size();
            }
        }
    }

    protected static class SatelliteProvisionStateCallbackTest implements
            SatelliteProvisionStateCallback {
        public boolean isProvisioned = false;
        private List<Boolean> mProvisionedStates = new ArrayList<>();
        private final Object mProvisionedStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            logd("onSatelliteProvisionStateChanged: provisioned=" + provisioned);
            isProvisioned = provisioned;
            synchronized (mProvisionedStatesLock) {
                mProvisionedStates.add(provisioned);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                loge("onSatelliteProvisionStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteProvisionStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteProvisionStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearProvisionedStates() {
            synchronized (mProvisionedStatesLock) {
                mProvisionedStates.clear();
            }
        }

        public int getTotalCountOfProvisionedStates() {
            synchronized (mProvisionedStatesLock) {
                return mProvisionedStates.size();
            }
        }

        public boolean getProvisionedState(int index) {
            synchronized (mProvisionedStatesLock) {
                if (index < mProvisionedStates.size()) {
                    return mProvisionedStates.get(index);
                }
            }
            loge("getProvisionedState: invalid index=" + index);
            return false;
        }
    }

    protected static class SatelliteStateCallbackTest implements SatelliteStateCallback {
        public int modemState = SatelliteManager.SATELLITE_MODEM_STATE_OFF;
        private List<Integer> mModemStates = new ArrayList<>();
        private final Object mModemStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteModemStateChanged(int state) {
            Log.d(TAG, "onSatelliteModemStateChanged: state=" + state);
            modemState = state;
            synchronized (mModemStatesLock) {
                mModemStates.add(state);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                Log.e(TAG, "onSatelliteModemStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        Log.e(TAG, "Timeout to receive onSatelliteModemStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "onSatelliteModemStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearModemStates() {
            synchronized (mModemStatesLock) {
                Log.d(TAG, "onSatelliteModemStateChanged: clearModemStates");
                mModemStates.clear();
            }
        }

        public int getModemState(int index) {
            synchronized (mModemStatesLock) {
                if (index < mModemStates.size()) {
                    return mModemStates.get(index);
                } else {
                    Log.e(TAG, "getModemState: invalid index=" + index
                            + ", mModemStates.size=" + mModemStates.size());
                    return -1;
                }
            }
        }

        public int getTotalCountOfModemStates() {
            synchronized (mModemStatesLock) {
                return mModemStates.size();
            }
        }
    }

    protected static class SatelliteDatagramCallbackTest implements SatelliteDatagramCallback {
        public SatelliteDatagram mDatagram;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteDatagramReceived(long datagramId, SatelliteDatagram datagram,
                int pendingCount, Consumer<Void> callback) {
            logd("onSatelliteDatagramReceived: datagramId=" + datagramId + ", datagram="
                    + datagram + ", pendingCount=" + pendingCount);
            mDatagram = datagram;
            if (callback != null) {
                logd("onSatelliteDatagramReceived: callback.accept() datagramId=" + datagramId);
                callback.accept(null);
            } else {
                logd("onSatelliteDatagramReceived: callback is null datagramId=" + datagramId);
            }

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onSatelliteDatagramReceived: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteDatagramReceived");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteDatagramReceived: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    protected static boolean provisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();

        sSatelliteManager.provisionSatelliteService(
                TOKEN, testProvisionData, null, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            loge("provisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_ERROR_NONE) {
            loge("provisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    protected static boolean deprovisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        sSatelliteManager.deprovisionSatelliteService(
                TOKEN, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            loge("deprovisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_ERROR_NONE) {
            loge("deprovisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    protected static boolean isSatelliteProvisioned() {
        final AtomicReference<Boolean> provisioned = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        provisioned.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsSatelliteProvisioned(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteProvisioned ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isProvisioned = provisioned.get();
        if (error == null) {
            assertNotNull(isProvisioned);
            return isProvisioned;
        } else {
            assertNull(isProvisioned);
            logd("isSatelliteProvisioned error=" + error);
            return false;
        }
    }

    protected static boolean isSatelliteEnabled() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };


        sSatelliteManager.requestIsSatelliteEnabled(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteEnabled ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isEnabled = enabled.get();
        if (error == null) {
            assertNotNull(isEnabled);
            return isEnabled;
        } else {
            assertNull(isEnabled);
            logd("isSatelliteEnabled error=" + error);
            return false;
        }
    }

    protected static void requestSatelliteEnabled(boolean enabled) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestSatelliteEnabled(
                enabled, true, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, (long) errorCode);
    }

    protected static boolean isSatelliteSupported() {
        final AtomicReference<Boolean> supported = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        supported.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsSatelliteSupported(getContext().getMainExecutor(),
                receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteSupported ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isSupported = supported.get();
        if (error == null) {
            assertNotNull(isSupported);
            logd("isSatelliteSupported isSupported=" + isSupported);
            return isSupported;
        } else {
            assertNull(isSupported);
            logd("isSatelliteSupported error=" + error);
            return false;
        }
    }

    protected static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    protected static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}
