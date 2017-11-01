/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.telephony.cts.embmstestapp;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.mbms.DownloadRequest;
import android.telephony.mbms.DownloadStateCallback;
import android.telephony.mbms.FileServiceInfo;
import android.telephony.mbms.MbmsDownloadSessionCallback;
import android.telephony.mbms.MbmsErrors;
import android.telephony.mbms.vendor.MbmsDownloadServiceBase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CtsDownloadService extends Service {
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<String>() {{
        add("android.telephony.cts");
    }};
    private static final String TAG = "EmbmsTestDownload";

    public static final String METHOD_NAME = "method_name";
    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_REQUEST_UPDATE_FILE_SERVICES =
            "requestUpdateFileServices";
    public static final String METHOD_SET_TEMP_FILE_ROOT = "setTempFileRootDirectory";
    public static final String METHOD_CLOSE = "close";

    public static final String ARGUMENT_SUBSCRIPTION_ID = "subscriptionId";
    public static final String ARGUMENT_SERVICE_CLASSES = "serviceClasses";
    public static final String ARGUMENT_ROOT_DIRECTORY_PATH = "rootDirectoryPath";

    public static final String CONTROL_INTERFACE_ACTION =
            "android.telephony.cts.embmstestapp.ACTION_CONTROL_MIDDLEWARE";
    public static final ComponentName CONTROL_INTERFACE_COMPONENT =
            ComponentName.unflattenFromString(
                    "android.telephony.cts.embmstestapp/.CtsDownloadService");

    public static final FileServiceInfo FILE_SERVICE_INFO;
    static {
        String id = "FileServiceId";
        Map<Locale, String> localeDict = new HashMap<Locale, String>() {{
            put(Locale.US, "Entertainment Source 1");
            put(Locale.CANADA, "Entertainment Source 1, eh?");
        }};
        List<Locale> locales = new ArrayList<Locale>() {{
            add(Locale.CANADA);
            add(Locale.US);
        }};
        FILE_SERVICE_INFO = new FileServiceInfo(localeDict, "class1", locales,
                id, new Date(2017, 8, 21, 18, 20, 29),
                new Date(2017, 8, 21, 18, 23, 9), Collections.emptyList());
    }

    private MbmsDownloadSessionCallback mAppCallback;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private List<Bundle> mReceivedCalls = new LinkedList<>();
    private int mErrorCodeOverride = MbmsErrors.SUCCESS;

    private final MbmsDownloadServiceBase mDownloadServiceImpl = new MbmsDownloadServiceBase() {
        @Override
        public int initialize(int subscriptionId, MbmsDownloadSessionCallback callback) {
            Bundle b = new Bundle();
            b.putString(METHOD_NAME, METHOD_INITIALIZE);
            b.putInt(ARGUMENT_SUBSCRIPTION_ID, subscriptionId);
            mReceivedCalls.add(b);

            if (mErrorCodeOverride != MbmsErrors.SUCCESS) {
                return mErrorCodeOverride;
            }

            int packageUid = Binder.getCallingUid();
            String[] packageNames = getPackageManager().getPackagesForUid(packageUid);
            if (packageNames == null) {
                return MbmsErrors.InitializationErrors.ERROR_APP_PERMISSIONS_NOT_GRANTED;
            }
            boolean isUidAllowed = Arrays.stream(packageNames).anyMatch(ALLOWED_PACKAGES::contains);
            if (!isUidAllowed) {
                return MbmsErrors.InitializationErrors.ERROR_APP_PERMISSIONS_NOT_GRANTED;
            }

            mHandler.post(() -> {
                if (mAppCallback == null) {
                    mAppCallback = callback;
                } else {
                    callback.onError(
                            MbmsErrors.InitializationErrors.ERROR_DUPLICATE_INITIALIZE, "");
                    return;
                }
                callback.onMiddlewareReady();
            });
            return MbmsErrors.SUCCESS;
        }

        @Override
        public int requestUpdateFileServices(int subscriptionId, List<String> serviceClasses) {
            Bundle b = new Bundle();
            b.putString(METHOD_NAME, METHOD_REQUEST_UPDATE_FILE_SERVICES);
            b.putInt(ARGUMENT_SUBSCRIPTION_ID, subscriptionId);
            b.putStringArrayList(ARGUMENT_SERVICE_CLASSES, new ArrayList<>(serviceClasses));
            mReceivedCalls.add(b);

            if (mErrorCodeOverride != MbmsErrors.SUCCESS) {
                return mErrorCodeOverride;
            }

            List<FileServiceInfo> serviceInfos = Collections.singletonList(FILE_SERVICE_INFO);

            mHandler.post(() -> {
                if (mAppCallback!= null) {
                    mAppCallback.onFileServicesUpdated(serviceInfos);
                }
            });

            return MbmsErrors.SUCCESS;
        }

        @Override
        public int setTempFileRootDirectory(int subscriptionId, String rootDirectoryPath) {
            Bundle b = new Bundle();
            b.putString(METHOD_NAME, METHOD_SET_TEMP_FILE_ROOT);
            b.putInt(ARGUMENT_SUBSCRIPTION_ID, subscriptionId);
            b.putString(ARGUMENT_ROOT_DIRECTORY_PATH, rootDirectoryPath);
            mReceivedCalls.add(b);

            return 0;
        }

        @Override
        public int registerStateCallback(DownloadRequest downloadRequest,
                DownloadStateCallback listener) throws RemoteException {
            // TODO
            return MbmsErrors.SUCCESS;
        }

        @Override
        public void dispose(int subscriptionId) {
            Bundle b = new Bundle();
            b.putString(METHOD_NAME, METHOD_CLOSE);
            b.putInt(ARGUMENT_SUBSCRIPTION_ID, subscriptionId);
            mReceivedCalls.add(b);

            // TODO
        }

        @Override
        public void onAppCallbackDied(int uid, int subscriptionId) {
            mAppCallback = null;
        }
    };

    private final IBinder mControlInterface = new ICtsDownloadMiddlewareControl.Stub() {
        @Override
        public void reset() {
            mReceivedCalls.clear();
            mHandler.removeCallbacksAndMessages(null);
            mAppCallback = null;
            mErrorCodeOverride = MbmsErrors.SUCCESS;
        }

        @Override
        public List<Bundle> getDownloadSessionCalls() {
            return mReceivedCalls;
        }

        @Override
        public void forceErrorCode(int error) {
            mErrorCodeOverride = error;
        }

        @Override
        public void fireErrorOnSession(int errorCode, String message) {
            mHandler.post(() -> mAppCallback.onError(errorCode, message));
        }
    };

    @Override
    public void onDestroy() {
        super.onCreate();
        mHandlerThread.quitSafely();
        logd("CtsDownloadService onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            logd("CtsDownloadService control interface bind");
            return mControlInterface;
        }

        logd("CtsDownloadService onBind");
        if (mHandlerThread != null && mHandlerThread.isAlive()) {
            return mDownloadServiceImpl;
        }

        mHandlerThread = new HandlerThread("CtsDownloadServiceWorker");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        return mDownloadServiceImpl;
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private void checkInitialized() {
        if (mAppCallback == null) {
            throw new IllegalStateException("Not yet initialized");
        }
    }
}
