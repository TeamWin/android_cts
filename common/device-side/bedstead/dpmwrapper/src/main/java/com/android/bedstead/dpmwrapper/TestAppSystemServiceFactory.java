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
package com.android.bedstead.dpmwrapper;

import static com.android.bedstead.dpmwrapper.DataFormatter.addArg;
import static com.android.bedstead.dpmwrapper.DataFormatter.getArg;
import static com.android.bedstead.dpmwrapper.Utils.ACTION_WRAPPED_MANAGER_CALL;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_CLASS;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_METHOD;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_NUMBER_ARGS;
import static com.android.bedstead.dpmwrapper.Utils.VERBOSE;

import android.annotation.Nullable;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.DebugUtils;
import android.util.Log;

import org.mockito.stubbing.Answer;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

//TODO(b/176993670): STOPSHIP - it currently uses ordered broadcasts and a Mockito spy to implement
//the IPC between users, but before S is shipped it should be changed to use the connected apps SDK
//or the new CTS infrastructure.
/**
 * Class used to create to provide a {@link DevicePolicyManager} implementation (and other managers
 * that must be run by the device owner user) that automatically funnels calls between the user
 * running the tests and the user that is the device owner.
 */
public final class TestAppSystemServiceFactory {

    private static final String TAG = TestAppSystemServiceFactory.class.getSimpleName();

    static final int RESULT_OK = 42;
    static final int RESULT_EXCEPTION = 666;

    // Must be high enough to outlast long tests like NetworkLoggingTest, which waits up to
    // 6 minutes for network monitoring events.
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

    private static final HandlerThread HANDLER_THREAD = new HandlerThread(TAG + "HandlerThread");

    private static Handler sHandler;

    /**
     * Gets the proper {@link DevicePolicyManager} instance to be used by the test.
     */
    public static DevicePolicyManager getDevicePolicyManager(Context context,
            Class<? extends DeviceAdminReceiver> receiverClass) {
        return getSystemService(context, DevicePolicyManager.class, receiverClass);
    }

    /**
     * Gets the proper {@link WifiManager} instance to be used by the test.
     */
    public static WifiManager getWifiManager(Context context,
            Class<? extends DeviceAdminReceiver> receiverClass) {
        return getSystemService(context, WifiManager.class, receiverClass);
    }

    private static <T> T getSystemService(Context context, Class<T> serviceClass,
            Class<? extends DeviceAdminReceiver> receiverClass) {

        ServiceManagerWrapper<T> wrapper = null;
        Class<?> wrappedClass;

        if (serviceClass.equals(DevicePolicyManager.class)) {
            wrappedClass = DevicePolicyManager.class;
            @SuppressWarnings("unchecked")
            ServiceManagerWrapper<T> safeCastWrapper =
                    (ServiceManagerWrapper<T>) new DevicePolicyManagerWrapper();
            wrapper = safeCastWrapper;
        } else if (serviceClass.equals(WifiManager.class)) {
            @SuppressWarnings("unchecked")
            ServiceManagerWrapper<T> safeCastWrapper =
                    (ServiceManagerWrapper<T>) new WifiManagerWrapper();
            wrapper = safeCastWrapper;
            wrappedClass = WifiManager.class;
        } else {
            throw new IllegalArgumentException("invalid service class: " + serviceClass);
        }

        @SuppressWarnings("unchecked")
        T manager = (T) context.getSystemService(wrappedClass);

        int userId = context.getUserId();
        if (userId == UserHandle.USER_SYSTEM || !UserManager.isHeadlessSystemUserMode()) {
            Log.i(TAG, "get(): returning 'pure' DevicePolicyManager for user " + userId);
            return manager;
        }

        if (sHandler == null) {
            Log.i(TAG, "Starting handler thread " + HANDLER_THREAD);
            HANDLER_THREAD.start();
            sHandler = new Handler(HANDLER_THREAD.getLooper());
        }

        String receiverClassName = receiverClass.getName();
        final String wrappedClassName = wrappedClass.getName();
        if (VERBOSE) {
            Log.v(TAG, "get(): receiverClassName: " + receiverClassName
                    + ", wrappedClassName: " + wrappedClassName);
        }

        Answer<?> answer = (inv) -> {
            Object[] args = inv.getArguments();
            Log.d(TAG, "spying " + inv + " method: " + inv.getMethod());
            String methodName = inv.getMethod().getName();
            Intent intent = new Intent(ACTION_WRAPPED_MANAGER_CALL)
                    .setClassName(context, receiverClassName)
                    .putExtra(EXTRA_CLASS, wrappedClassName)
                    .putExtra(EXTRA_METHOD, methodName)
                    .putExtra(EXTRA_NUMBER_ARGS, args.length);
            for (int i = 0; i < args.length; i++) {
                addArg(intent, args, i);
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Result> resultRef = new AtomicReference<>();
            BroadcastReceiver myReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (VERBOSE) {
                        Log.v(TAG, "spy received intent " + action + " for user "
                                + context.getUserId());
                    }
                    Result result = new Result(this);
                    if (VERBOSE) Log.v(TAG, "result:" + result);
                    resultRef.set(result);
                    latch.countDown();
                };

            };
            if (VERBOSE) {
                Log.v(TAG, "Sending ordered broadcast from user " + userId + " to user "
                        + UserHandle.SYSTEM);
            }

            // NOTE: method below used to be wrapped under runWithShellPermissionIdentity() to get
            // INTERACT_ACROSS_USER permission, but that's not needed anymore (as the permission
            // is granted by the test. Besides, this class is now also used by DO apps that are not
            // instrumented, so it was removed
            context.sendOrderedBroadcastAsUser(intent,
                    UserHandle.SYSTEM, /* permission= */ null, myReceiver, sHandler,
                    /* initialCode= */ 0, /* initialData= */ null, /* initialExtras= */ null);

            if (VERBOSE) {
                Log.d(TAG, "Waiting up to " + TIMEOUT_MS + "ms for response on "
                        + Thread.currentThread());
            }
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Ordered broadcast for %s() not received in %dms", methodName, TIMEOUT_MS);
            }
            if (VERBOSE) Log.d(TAG, "Got response");

            Result result = resultRef.get();
            Log.d(TAG, "Received result on user " + userId + ": " + result);

            switch (result.code) {
                case RESULT_OK:
                    return result.value;
                case RESULT_EXCEPTION:
                    Exception e = (Exception) result.value;
                    throw (e instanceof InvocationTargetException) ? e.getCause() : e;
                default:
                    fail("Received invalid result: %s", result);
                    return null;
            }
        };

        T spy = wrapper.getWrapper(context, manager, answer);

        return spy;

    }

    private static String resultCodeToString(int code) {
        return DebugUtils.constantToString(DevicePolicyManagerWrapper.class, "RESULT_", code);
    }

    private static void fail(String template, Object... args) {
        throw new AssertionError(String.format(Locale.ENGLISH, template, args));
    }

    private static final class Result {
        public final int code;
        @Nullable public final String error;
        @Nullable public final Bundle extras;
        @Nullable public final Object value;

        Result(BroadcastReceiver receiver) {
            int resultCode = receiver.getResultCode();
            String data = receiver.getResultData();
            extras = receiver.getResultExtras(/* makeMap= */ true);
            Object parsedValue = null;
            try {
                if (extras != null && !extras.isEmpty()) {
                    Object[] result = new Object[1];
                    int index = 0;
                    getArg(extras, result, /* parameterTypes= */ null, index);
                    parsedValue = result[index];
                }
            } catch (Exception e) {
                Log.e(TAG, "error parsing extras (code=" + resultCode + ", data=" + data, e);
                data = "error parsing extras";
                resultCode = RESULT_EXCEPTION;
            }
            code = resultCode;
            error = data;
            value = parsedValue;
        }

        @Override
        public String toString() {
            return "Result[code=" + resultCodeToString(code) + ", error=" + error
                    + ", extras=" + extras + ", value=" + value + "]";
        }
    }

    abstract static class ServiceManagerWrapper<T> {
        abstract T getWrapper(Context context, T manager, Answer<?> answer);
    }

    private TestAppSystemServiceFactory() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
