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


import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.telephony.cts.externalpointingui.ExternalMockPointingUi;
import android.telephony.cts.externalsatellitegatewayservice.ExternalMockSatelliteGatewayService;
import android.telephony.cts.externalsatellitegatewayservice.IExternalMockSatelliteGatewayService;
import android.telephony.cts.externalsatellitegatewayservice.IExternalSatelliteGatewayListener;
import android.telephony.cts.externalsatelliteservice.ExternalMockSatelliteService;
import android.telephony.cts.externalsatelliteservice.IExternalMockSatelliteService;
import android.telephony.cts.externalsatelliteservice.IExternalSatelliteListener;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.satellite.stub.PointingInfo;
import android.telephony.satellite.stub.SatelliteDatagram;
import android.telephony.satellite.stub.SatelliteError;
import android.text.TextUtils;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for connecting Telephony framework and CTS to the MockSatelliteService.
 */
class MockSatelliteServiceManager {
    private static final String TAG = "MockSatelliteServiceManager";
    private static final String PACKAGE = "android.telephony.cts";
    private static final String EXTERNAL_SATELLITE_GATEWAY_PACKAGE =
            ExternalMockSatelliteGatewayService.class.getPackage().getName();
    private static final String EXTERNAL_SATELLITE_PACKAGE =
            ExternalMockSatelliteService.class.getPackage().getName();
    private static final String EXTERNAL_POINTING_UI_PACKAGE =
            ExternalMockPointingUi.class.getPackage().getName();
    private static final String SET_SATELLITE_SERVICE_PACKAGE_NAME_CMD =
            "cmd phone set-satellite-service-package-name -s ";
    private static final String SET_SATELLITE_GATEWAY_SERVICE_PACKAGE_NAME_CMD =
            "cmd phone set-satellite-gateway-service-package-name -s ";
    private static final String SET_SATELLITE_LISTENING_TIMEOUT_DURATION_CMD =
            "cmd phone set-satellite-listening-timeout-duration -t ";
    private static final String SET_SATELLITE_POINTING_UI_CLASS_NAME_CMD =
            "cmd phone set-satellite-pointing-ui-class-name";
    private static final String SET_SATELLITE_DEVICE_ALIGN_TIMEOUT_DURATION_CMD =
            "cmd phone set-satellite-device-aligned-timeout-duration -t ";
    private static final long TIMEOUT = 5000;
    @NonNull private ActivityManager mActivityManager;
    @NonNull private UidImportanceListener mUidImportanceListener = new UidImportanceListener();

    private MockSatelliteService mSatelliteService;
    private TestSatelliteServiceConnection mSatelliteServiceConn;
    private IExternalMockSatelliteService mExternalSatelliteService;
    private ExternalSatelliteServiceConnection mExternalSatelliteServiceConn;
    private final Semaphore mRemoteServiceConnectedSemaphore = new Semaphore(0);
    private final Semaphore mExternalServiceDisconnectedSemaphore = new Semaphore(0);
    private MockSatelliteGatewayService mSatelliteGatewayService;
    private TestSatelliteGatewayServiceConnection mSatelliteGatewayServiceConn;
    private IExternalMockSatelliteGatewayService mExternalSatelliteGatewayService;
    private ExternalSatelliteGatewayServiceConnection mExternalSatelliteGatewayServiceConn;
    private final Semaphore mRemoteGatewayServiceConnectedSemaphore = new Semaphore(0);
    private final Semaphore mRemoteGatewayServiceDisconnectedSemaphore = new Semaphore(0);
    private final Semaphore mExternalGatewayServiceDisconnectedSemaphore = new Semaphore(0);
    private Instrumentation mInstrumentation;
    private final Semaphore mStartSendingPointingInfoSemaphore = new Semaphore(0);
    private final Semaphore mStopSendingPointingInfoSemaphore = new Semaphore(0);
    private final Semaphore mPollPendingDatagramsSemaphore = new Semaphore(0);
    private final Semaphore mStopPointingUiSemaphore = new Semaphore(0);
    private final Semaphore mSendDatagramsSemaphore = new Semaphore(0);
    private List<SatelliteDatagram> mSentSatelliteDatagrams = new ArrayList<>();
    private List<Boolean> mSentIsEmergencyList = new ArrayList<>();
    private final Object mSendDatagramLock = new Object();
    private final Semaphore mListeningEnabledSemaphore = new Semaphore(0);
    private List<Boolean> mListeningEnabledList = new ArrayList<>();
    private final Object mListeningEnabledLock = new Object();
    private final Semaphore mMockPointingUiActivitySemaphore = new Semaphore(0);
    private final MockPointingUiActivityStatusReceiver mMockPointingUiActivityStatusReceiver =
            new MockPointingUiActivityStatusReceiver(mMockPointingUiActivitySemaphore);
    private final boolean mIsSatelliteServicePackageConfigured;
    boolean mIsPointingUiOverridden = false;

    @NonNull
    private final ILocalSatelliteListener mSatelliteListener =
            new ILocalSatelliteListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("onRemoteServiceConnected");
                    mRemoteServiceConnectedSemaphore.release();
                }

                @Override
                public void onStartSendingSatellitePointingInfo() {
                    logd("onStartSendingSatellitePointingInfo");
                    try {
                        mStartSendingPointingInfoSemaphore.release();
                    } catch (Exception ex) {
                        logd("onStartSendingSatellitePointingInfo: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onStopSendingSatellitePointingInfo() {
                    logd("onStopSendingSatellitePointingInfo");
                    try {
                        mStopSendingPointingInfoSemaphore.release();
                    } catch (Exception ex) {
                        logd("onStopSendingSatellitePointingInfo: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onPollPendingSatelliteDatagrams() {
                    logd("onPollPendingSatelliteDatagrams");
                    try {
                        mPollPendingDatagramsSemaphore.release();
                    } catch (Exception ex) {
                        logd("onPollPendingSatelliteDatagrams: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onSendSatelliteDatagram(
                        SatelliteDatagram datagram, boolean isEmergency) {
                    logd("onSendSatelliteDatagram");
                    synchronized (mSendDatagramLock) {
                        mSentSatelliteDatagrams.add(datagram);
                        mSentIsEmergencyList.add(isEmergency);
                    }
                    try {
                        mSendDatagramsSemaphore.release();
                    } catch (Exception ex) {
                        logd("onSendSatelliteDatagram: Got exception, ex=" + ex);
                    }
                }

                @Override
                public void onSatelliteListeningEnabled(boolean enabled) {
                    logd("onSatelliteListeningEnabled: enabled=" + enabled);
                    synchronized (mListeningEnabledLock) {
                        mListeningEnabledList.add(enabled);
                    }
                    try {
                        mListeningEnabledSemaphore.release();
                    } catch (Exception ex) {
                        logd("onSatelliteListeningEnabled: Got exception, ex=" + ex);
                    }
                }
            };

    @NonNull
    private final ILocalSatelliteGatewayListener mSatelliteGatewayListener =
            new ILocalSatelliteGatewayListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("ILocalSatelliteGatewayListener: onRemoteServiceConnected");
                    mRemoteGatewayServiceConnectedSemaphore.release();
                }

                @Override
                public void onRemoteServiceDisconnected() {
                    logd("ILocalSatelliteGatewayListener: onRemoteServiceDisconnected");
                    mRemoteGatewayServiceDisconnectedSemaphore.release();
                }
            };

    private class TestSatelliteServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        TestSatelliteServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("onServiceConnected");
            mSatelliteService = ((MockSatelliteService.LocalBinder) service).getService();
            mSatelliteService.setLocalSatelliteListener(mSatelliteListener);
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("onServiceDisconnected");
            mSatelliteService = null;
        }
    }

    private class ExternalSatelliteServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        ExternalSatelliteServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("ExternalSatelliteService: onServiceConnected");
            mExternalSatelliteService =
                    IExternalMockSatelliteService.Stub.asInterface(service);
            try {
                mExternalSatelliteService.setExternalSatelliteListener(
                        mExternalSatelliteListener);
                mLatch.countDown();
            } catch (RemoteException ex) {
                loge("ExternalSatelliteService: setExternalSatelliteListener ex=" + ex);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("ExternalSatelliteService: onServiceDisconnected");
            mExternalSatelliteService = null;
            try {
                mExternalServiceDisconnectedSemaphore.release();
            } catch (Exception ex) {
                loge("ExternalSatelliteService: ex=" + ex);
            }
        }
    }

    private class UidImportanceListener implements ActivityManager.OnUidImportanceListener {
        @Override
        public void onUidImportance(int uid, int importance) {
            if (importance != IMPORTANCE_GONE) return;
            final PackageManager pm = mInstrumentation.getContext().getPackageManager();
            final String[] callerPackages = pm.getPackagesForUid(uid);
            if (callerPackages != null) {
                if (Arrays.stream(callerPackages)
                        .anyMatch(EXTERNAL_POINTING_UI_PACKAGE::contains)) {
                    mStopPointingUiSemaphore.release();
                }
            }
        }
    }

    @NonNull
    private final IExternalSatelliteListener mExternalSatelliteListener =
            new IExternalSatelliteListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("IExternalSatelliteListener: onRemoteServiceConnected");
                    mRemoteServiceConnectedSemaphore.release();
                }
            };

    private class TestSatelliteGatewayServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        TestSatelliteGatewayServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("GatewayService: onServiceConnected");
            mSatelliteGatewayService =
                    ((MockSatelliteGatewayService.LocalBinder) service).getService();
            mSatelliteGatewayService.setLocalSatelliteListener(mSatelliteGatewayListener);
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("GatewayService: onServiceDisconnected");
            mSatelliteGatewayService = null;
        }
    }

    @NonNull
    private final IExternalSatelliteGatewayListener mExternalSatelliteGatewayListener =
            new IExternalSatelliteGatewayListener.Stub() {
                @Override
                public void onRemoteServiceConnected() {
                    logd("IExternalSatelliteGatewayListener: onRemoteServiceConnected");
                    mRemoteGatewayServiceConnectedSemaphore.release();
                }
            };

    private class ExternalSatelliteGatewayServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;

        ExternalSatelliteGatewayServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("ExternalGatewayService: onServiceConnected");
            mExternalSatelliteGatewayService =
                    IExternalMockSatelliteGatewayService.Stub.asInterface(service);
            try {
                mExternalSatelliteGatewayService.setExternalSatelliteGatewayListener(
                        mExternalSatelliteGatewayListener);
                mLatch.countDown();
            } catch (RemoteException ex) {
                loge("ExternalGatewayService: setExternalSatelliteGatewayListener ex=" + ex);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logd("ExternalGatewayService: onServiceDisconnected");
            mExternalSatelliteGatewayService = null;
            try {
                mExternalGatewayServiceDisconnectedSemaphore.release();
            } catch (Exception ex) {
                loge("ExternalGatewayService: ex=" + ex);
            }
        }
    }

    MockSatelliteServiceManager(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        mIsSatelliteServicePackageConfigured = !TextUtils.isEmpty(getSatelliteServicePackageName());
    }

    boolean connectSatelliteService() {
        logd("connectSatelliteService starting ...");
        if (!setupLocalSatelliteService()) {
            loge("Failed to set up local satellite service");
            return false;
        }

        try {
            if (!setSatelliteServicePackageName(PACKAGE)) {
                loge("Failed to set satellite service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectSatelliteService: Got exception with setSatelliteServicePackageName "
                    + "ex=" + ex);
            return false;
        }

        // Wait for SatelliteModemInterface connecting to MockSatelliteService.
        return waitForRemoteSatelliteServiceConnected(1);
    }

    boolean connectExternalSatelliteService() {
        logd("connectExternalSatelliteService starting ...");
        if (!setupExternalSatelliteService()) {
            loge("Failed to set up external satellite service");
            return false;
        }

        try {
            if (!setSatelliteServicePackageName(EXTERNAL_SATELLITE_PACKAGE)) {
                loge("Failed to set satellite service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectExternalSatelliteService: Got exception with "
                    + "setSatelliteServicePackageName, ex=" + ex);
            return false;
        }
        return true;
    }

    boolean setupExternalSatelliteService() {
        logd("setupExternalSatelliteService start");
        if (mExternalSatelliteService != null) {
            logd("setupExternalSatelliteService: external service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mExternalSatelliteServiceConn = new ExternalSatelliteServiceConnection(latch);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(EXTERNAL_SATELLITE_PACKAGE,
                ExternalMockSatelliteService.class.getName()));
        mInstrumentation.getContext().bindService(intent, mExternalSatelliteServiceConn,
                Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupExternalSatelliteService: Got InterruptedException e=" + e);
            return false;
        }
    }

    boolean connectSatelliteGatewayService() {
        logd("connectSatelliteGatewayService starting ...");
        if (!setupLocalSatelliteGatewayService()) {
            loge("Failed to set up local satellite gateway service");
            return false;
        }

        try {
            if (!setSatelliteGatewayServicePackageName(PACKAGE)) {
                loge("Failed to set satellite gateway service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectSatelliteGatewayService: Got exception with "
                    + "setSatelliteGatewayServicePackageName, ex=" + ex);
            return false;
        }
        return true;
    }

    boolean connectExternalSatelliteGatewayService() {
        logd("connectExternalSatelliteGatewayService starting ...");
        if (!setupExternalSatelliteGatewayService()) {
            loge("Failed to set up external satellite gateway service");
            return false;
        }

        try {
            if (!setSatelliteGatewayServicePackageName(EXTERNAL_SATELLITE_GATEWAY_PACKAGE)) {
                loge("Failed to set satellite gateway service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("connectExternalSatelliteGatewayService: Got exception with "
                    + "setSatelliteGatewayServicePackageName, ex=" + ex);
            return false;
        }
        return true;
    }

    boolean setupExternalSatelliteGatewayService() {
        logd("setupExternalSatelliteGatewayService start");
        if (mExternalSatelliteGatewayService != null) {
            logd("setupExternalSatelliteGatewayService: external service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mExternalSatelliteGatewayServiceConn = new ExternalSatelliteGatewayServiceConnection(latch);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(EXTERNAL_SATELLITE_GATEWAY_PACKAGE,
                ExternalMockSatelliteGatewayService.class.getName()));
        mInstrumentation.getContext().bindService(intent, mExternalSatelliteGatewayServiceConn,
                Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupExternalSatelliteGatewayService: Got InterruptedException e=" + e);
            return false;
        }
    }

    boolean restoreSatelliteServicePackageName() {
        logd("restoreSatelliteServicePackageName");
        try {
            if (!setSatelliteServicePackageName(null)) {
                loge("Failed to restore satellite service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("restoreSatelliteServicePackageName: Got exception with "
                    + "setSatelliteServicePackageName ex=" + ex);
            return false;
        }
        return true;
    }

    boolean restoreSatelliteGatewayServicePackageName() {
        logd("restoreSatelliteGatewayServicePackageName");
        try {
            if (!setSatelliteGatewayServicePackageName(null)) {
                loge("Failed to restore satellite gateway service package name");
                return false;
            }
        } catch (Exception ex) {
            loge("restoreSatelliteGatewayServicePackageName: Got exception with "
                    + "setSatelliteGatewayServicePackageName ex=" + ex);
            return false;
        }
        return true;
    }

    boolean overrideSatellitePointingUiClassName() {
        logd("overrideSatellitePointingUiClassName");
        try {
            if (!setSatellitePointingUiClassName(
                    PACKAGE, MockPointingUiActivity.class.getName())) {
                loge("Failed to override satellite pointing UI package and class names");
                return false;
            }
        } catch (Exception ex) {
            loge("overrideSatellitePointingUiClassName: Got exception with "
                    + "setSatellitePointingUiClassName ex=" + ex);
            return false;
        }
        registerForMockPointingUiActivityStatus();
        mIsPointingUiOverridden = true;
        return true;
    }

    boolean overrideExternalSatellitePointingUiClassName() {
        logd("overrideExternalSatellitePointingUiClassName");
        try {
            mActivityManager = mInstrumentation.getContext()
                    .getSystemService(ActivityManager.class);
            mActivityManager.addOnUidImportanceListener(mUidImportanceListener, IMPORTANCE_GONE);
            if (!setSatellitePointingUiClassName(
                    EXTERNAL_POINTING_UI_PACKAGE, ExternalMockPointingUi.class.getName())) {
                loge("Failed to override external satellite pointing UI package and class names");
                return false;
            }
        } catch (Exception ex) {
            loge("overrideExternalSatellitePointingUiClassName: Got exception with "
                    + "setSatellitePointingUiClassName ex=" + ex);
            return false;
        }
        registerForExternalMockPointingUiActivityStatus();
        mIsPointingUiOverridden = true;
        return true;
    }
    boolean restoreSatellitePointingUiClassName() {
        logd("restoreSatellitePointingUiClassName");
        if (!mIsPointingUiOverridden) {
            return true;
        }
        try {
            if (!setSatellitePointingUiClassName(null, null)) {
                loge("Failed to restore satellite pointing UI package and class names");
                return false;
            }
        } catch (Exception ex) {
            loge("restoreSatellitePointingUiClassName: Got exception with "
                    + "setSatellitePointingUiClassName ex=" + ex);
            return false;
        }
        unregisterForMockPointingUiActivityStatus();
        mIsPointingUiOverridden = false;
        return true;
    }

    boolean isSatelliteServicePackageConfigured() {
        return mIsSatelliteServicePackageConfigured;
    }

    Boolean getSentIsEmergency(int index) {
        synchronized (mSendDatagramLock) {
            if (index >= mSentIsEmergencyList.size()) return null;
            return mSentIsEmergencyList.get(index);
        }
    }

    SatelliteDatagram getSentSatelliteDatagram(int index) {
        synchronized (mSendDatagramLock) {
            if (index >= mSentSatelliteDatagrams.size()) return null;
            return mSentSatelliteDatagrams.get(index);
        }
    }

    void clearSentSatelliteDatagramInfo() {
        synchronized (mSendDatagramLock) {
            mSentSatelliteDatagrams.clear();
            mSentIsEmergencyList.clear();
            mSendDatagramsSemaphore.drainPermits();
        }
    }

    int getTotalCountOfSentSatelliteDatagrams() {
        synchronized (mSendDatagramLock) {
            return mSentSatelliteDatagrams.size();
        }
    }

    Boolean getListeningEnabled(int index) {
        synchronized (mListeningEnabledLock) {
            if (index >= mListeningEnabledList.size()) return null;
            return mListeningEnabledList.get(index);
        }
    }

    int getTotalCountOfListeningEnabledList() {
        synchronized (mListeningEnabledLock) {
            return mListeningEnabledList.size();
        }
    }

    void clearListeningEnabledList() {
        synchronized (mListeningEnabledLock) {
            mListeningEnabledList.clear();
            mListeningEnabledSemaphore.drainPermits();
        }
    }

    void clearMockPointingUiActivityStatusChanges() {
        mMockPointingUiActivitySemaphore.drainPermits();
    }

    void clearRemoteGatewayServiceConnectedStatusChanges() {
        mRemoteGatewayServiceConnectedSemaphore.drainPermits();
    }

    void clearRemoteGatewayServiceDisconnectedStatusChanges() {
        mRemoteGatewayServiceDisconnectedSemaphore.drainPermits();
    }

    void clearStopPointingUiActivity() {
        mStopPointingUiSemaphore.drainPermits();
    }

    boolean waitForEventOnStartSendingSatellitePointingInfo(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mStartSendingPointingInfoSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onStartSendingSatellitePointingInfo");
                    return false;
                }
            } catch (Exception ex) {
                loge("onStartSendingSatellitePointingInfo: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnStopSendingSatellitePointingInfo(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mStopSendingPointingInfoSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onStopSendingSatellitePointingInfo");
                    return false;
                }
            } catch (Exception ex) {
                loge("onStopSendingSatellitePointingInfo: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnPollPendingSatelliteDatagrams(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mPollPendingDatagramsSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onPollPendingSatelliteDatagrams");
                    return false;
                }
            } catch (Exception ex) {
                loge("onPollPendingSatelliteDatagrams: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnSendSatelliteDatagram(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mSendDatagramsSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onSendSatelliteDatagram");
                    return false;
                }
            } catch (Exception ex) {
                loge("onSendSatelliteDatagram: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventOnSatelliteListeningEnabled(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mListeningEnabledSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive onSatelliteListeningEnabled");
                    return false;
                }
            } catch (Exception ex) {
                loge("onSatelliteListeningEnabled: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForRemoteSatelliteServiceConnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRemoteServiceConnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive RemoteSatelliteServiceConnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("RemoteSatelliteServiceConnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForRemoteSatelliteGatewayServiceConnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRemoteGatewayServiceConnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive RemoteSatelliteGatewayServiceConnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("RemoteSatelliteGatewayServiceConnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForRemoteSatelliteGatewayServiceDisconnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mRemoteGatewayServiceDisconnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive RemoteGatewayServiceDisconnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("RemoteGatewayServiceDisconnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForExternalSatelliteServiceDisconnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mExternalServiceDisconnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive ExternalServiceDisconnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("ExternalServiceDisconnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForExternalSatelliteGatewayServiceDisconnected(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mExternalGatewayServiceDisconnectedSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive ExternalGatewayServiceDisconnected");
                    return false;
                }
            } catch (Exception ex) {
                loge("ExternalGatewayServiceDisconnected: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventMockPointingUiActivityStarted(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mMockPointingUiActivitySemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive MockPointingUiActivityStarted");
                    return false;
                }
            } catch (Exception ex) {
                loge("MockPointingUiActivityStarted: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    boolean waitForEventMockPointingUiActivityStopped(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mStopPointingUiSemaphore.tryAcquire(
                        TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("Timeout to receive StopPointingUiSemaphore");
                    return false;
                }
            } catch (Exception ex) {
                loge("StopPointingUiSemaphore: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    void setErrorCode(@SatelliteError int errorCode) {
        logd("setErrorCode: errorCode=" + errorCode);
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.setErrorCode(errorCode);
    }

    void setSatelliteSupport(boolean supported) {
        logd("setSatelliteSupport: supported=" + supported);
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.setSatelliteSupport(supported);
    }

    public void setShouldRespondTelephony(boolean shouldRespondTelephony) {
        logd("setShouldRespondTelephony: shouldRespondTelephony=" + shouldRespondTelephony);
        if (mSatelliteService == null) {
            loge("setShouldRespondTelephony: mSatelliteService is null");
            return;
        }
        mSatelliteService.setShouldRespondTelephony(shouldRespondTelephony);
    }

    void sendOnSatelliteDatagramReceived(SatelliteDatagram datagram, int pendingCount) {
        logd("sendOnSatelliteDatagramReceived");
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.sendOnSatelliteDatagramReceived(datagram, pendingCount);
    }

    void sendOnPendingDatagrams() {
        logd("sendOnPendingDatagrams");
        if (mSatelliteService == null) {
            loge("setErrorCode: mSatelliteService is null");
            return;
        }
        mSatelliteService.sendOnPendingDatagrams();
    }

    void sendOnSatellitePositionChanged(PointingInfo pointingInfo) {
        logd("sendOnSatellitePositionChanged");
        mSatelliteService.sendOnSatellitePositionChanged(pointingInfo);
    }

    boolean setSatelliteListeningTimeoutDuration(long timeoutMillis) {
        try {
            String result =
                    TelephonyUtils.executeShellCommand(mInstrumentation,
                            SET_SATELLITE_LISTENING_TIMEOUT_DURATION_CMD + timeoutMillis);
            logd("setSatelliteListeningTimeoutDuration: result = " + result);
            return "true".equals(result);
        } catch (Exception e) {
            loge("setSatelliteListeningTimeoutDuration: e=" + e);
            return false;
        }
    }

    boolean setSatelliteDeviceAlignedTimeoutDuration(long timeoutMillis) {
        try {
            String result =
                    TelephonyUtils.executeShellCommand(mInstrumentation,
                            SET_SATELLITE_DEVICE_ALIGN_TIMEOUT_DURATION_CMD + timeoutMillis);
            logd("setDeviceAlignedTimeoutDuration: result = " + result);
            return "true".equals(result);
        } catch (Exception e) {
            loge("setDeviceAlignedTimeoutDuration: e=" + e);
            return false;
        }
    }

    void setWaitToSend(boolean wait) {
        logd("setWaitToSend: wait= " + wait);
        if (mSatelliteService == null) {
            loge("setWaitToSend: mSatelliteService is null");
            return;
        }
        mSatelliteService.setWaitToSend(wait);
    }

    boolean sendSavedDatagram() {
        logd("sendSavedDatagram");
        if (mSatelliteService == null) {
            loge("sendSavedDatagram: mSatelliteService is null");
            return false;
        }
        return mSatelliteService.sendSavedDatagram();
    }

    boolean stopExternalSatelliteService() {
        logd("stopExternalSatelliteService");
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    "am force-stop " + EXTERNAL_SATELLITE_PACKAGE);
            return true;
        } catch (Exception ex) {
            loge("stopExternalSatelliteService: ex=" + ex);
            return false;
        }
    }

    boolean stopExternalSatelliteGatewayService() {
        logd("stopExternalSatelliteGatewayService");
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    "am force-stop " + EXTERNAL_SATELLITE_GATEWAY_PACKAGE);
            return true;
        } catch (Exception ex) {
            loge("stopExternalSatelliteGatewayService: ex=" + ex);
            return false;
        }
    }

    void resetSatelliteService() {
        mSatelliteService = null;
    }

    boolean stopExternalMockPointingUi() {
        logd("stopExternalMockPointingUi");
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    "am force-stop " + EXTERNAL_POINTING_UI_PACKAGE);
            return true;
        } catch (Exception ex) {
            loge("stopExternalMockPointingUi: ex=" + ex);
            return false;
        }
    }

    private boolean setupLocalSatelliteService() {
        if (mSatelliteService != null) {
            logd("setupLocalSatelliteService: local service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mSatelliteServiceConn = new TestSatelliteServiceConnection(latch);
        mInstrumentation.getContext().bindService(new Intent(mInstrumentation.getContext(),
                MockSatelliteService.class), mSatelliteServiceConn, Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupLocalSatelliteService: Got InterruptedException e=" + e);
            return false;
        }
    }

    private boolean setupLocalSatelliteGatewayService() {
        if (mSatelliteGatewayService != null) {
            logd("setupLocalSatelliteGatewayService: local service is already set up");
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mSatelliteGatewayServiceConn = new TestSatelliteGatewayServiceConnection(latch);
        mInstrumentation.getContext().bindService(new Intent(mInstrumentation.getContext(),
                MockSatelliteGatewayService.class), mSatelliteGatewayServiceConn,
                Context.BIND_AUTO_CREATE);
        try {
            return latch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            loge("setupLocalSatelliteGatewayService: Got InterruptedException e=" + e);
            return false;
        }
    }

    private boolean setSatelliteServicePackageName(@Nullable String packageName) {
        try {
            TelephonyUtils.executeShellCommand(
                    mInstrumentation, SET_SATELLITE_SERVICE_PACKAGE_NAME_CMD + packageName);
            return true;
        } catch (Exception ex) {
            loge("setSatelliteServicePackageName: ex= " + ex);
            return false;
        }
    }

    private boolean setSatelliteGatewayServicePackageName(
            @Nullable String packageName) throws Exception {
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    SET_SATELLITE_GATEWAY_SERVICE_PACKAGE_NAME_CMD + packageName);
            return true;
        } catch (Exception ex) {
            loge("setSatelliteGatewayServicePackageName: ex=" + ex);
            return false;
        }
    }

    private boolean setSatellitePointingUiClassName(
            @Nullable String packageName, @Nullable String className)  {
        try {
            TelephonyUtils.executeShellCommand(mInstrumentation,
                    SET_SATELLITE_POINTING_UI_CLASS_NAME_CMD + " -p " + packageName
                            + " -c " + className);
            return true;
        } catch (Exception ex) {
            loge("setSatellitePointingUiClassName: ex = " + ex);
            return false;
        }
    }

    private void registerForMockPointingUiActivityStatus() {
        IntentFilter intentFilter = new IntentFilter(
                MockPointingUiActivity.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED);
        mInstrumentation.getContext().registerReceiver(
                mMockPointingUiActivityStatusReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    private void registerForExternalMockPointingUiActivityStatus() {
        logd("registerForExternalMockPointingUiActivityStatus");
        IntentFilter intentFilter = new IntentFilter(
                ExternalMockPointingUi.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED);
        mInstrumentation.getContext().registerReceiver(
                mMockPointingUiActivityStatusReceiver, intentFilter, Context.RECEIVER_EXPORTED);
    }

    private void unregisterForMockPointingUiActivityStatus() {
        mInstrumentation.getContext().unregisterReceiver(mMockPointingUiActivityStatusReceiver);
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }

    private static class MockPointingUiActivityStatusReceiver extends BroadcastReceiver {
        private Semaphore mSemaphore;

        MockPointingUiActivityStatusReceiver(Semaphore semaphore) {
            mSemaphore = semaphore;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            logd("MockPointingUiActivityStatusReceiver: onReceive " + intent.getAction());
            if ((MockPointingUiActivity.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED.equals(
                    intent.getAction()))
                    || (ExternalMockPointingUi.ACTION_MOCK_POINTING_UI_ACTIVITY_STARTED.equals(
                    intent.getAction()))) {
                logd("MockPointingUiActivityStatusReceiver: onReceive");
                try {
                    mSemaphore.release();
                } catch (Exception ex) {
                    loge("MockPointingUiActivityStatusReceiver: Got exception, ex=" + ex);
                }
            }
        }
    }

    private String getSatelliteServicePackageName() {
        return TextUtils.emptyIfNull(mInstrumentation.getContext().getResources().getString(
                R.string.config_satellite_service_package));
    }
}
