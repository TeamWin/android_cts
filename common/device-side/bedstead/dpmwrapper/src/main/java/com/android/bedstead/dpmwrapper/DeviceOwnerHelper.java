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
import static com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.RESULT_EXCEPTION;
import static com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory.RESULT_OK;
import static com.android.bedstead.dpmwrapper.Utils.ACTION_WRAPPED_MANAGER_CALL;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_CLASS;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_METHOD;
import static com.android.bedstead.dpmwrapper.Utils.EXTRA_NUMBER_ARGS;
import static com.android.bedstead.dpmwrapper.Utils.VERBOSE;
import static com.android.bedstead.dpmwrapper.Utils.isHeadlessSystemUser;

import android.annotation.Nullable;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Helper class used by the device owner apps.
 */
public final class DeviceOwnerHelper {

    private static final String TAG = DeviceOwnerHelper.class.getSimpleName();

    /**
     * Executes a method requested by the test app.
     *
     * <p>Typical usage:
     *
     * <pre><code>
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DeviceOwnerAdminReceiverHelper.runManagerMethod(this, context, intent)) return;
            super.onReceive(context, intent);
        }
</code></pre>
     *
     * @return whether the {@code intent} represented a method that was executed.
     */
    public static boolean runManagerMethod(DeviceAdminReceiver receiver, Context context,
            Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive(): user=" + context.getUserId() + ", action=" + action);

        if (!action.equals(ACTION_WRAPPED_MANAGER_CALL)) return false;

        try {
            String className = intent.getStringExtra(EXTRA_CLASS);
            String methodName = intent.getStringExtra(EXTRA_METHOD);
            int numberArgs = intent.getIntExtra(EXTRA_NUMBER_ARGS, 0);
            Log.d(TAG, "onReceive(): userId=" + context.getUserId()
                    + ", intent=" + intent.getAction() + ", class=" + className
                    + ", methodName=" + methodName + ", numberArgs=" + numberArgs);
            Object[] args = null;
            Class<?>[] parameterTypes = null;
            if (numberArgs > 0) {
                args = new Object[numberArgs];
                parameterTypes = new Class<?>[numberArgs];
                Bundle extras = intent.getExtras();
                for (int i = 0; i < numberArgs; i++) {
                    getArg(extras, args, parameterTypes, i);
                }
                Log.d(TAG, "onReceive(): args=" + Arrays.toString(args) + ", types="
                        + Arrays.toString(parameterTypes));

            }
            Class<?> managerClass = Class.forName(className);
            Method method = findMethod(managerClass, methodName, parameterTypes);
            if (method == null) {
                sendError(receiver, new IllegalArgumentException(
                        "Could not find method " + methodName + " using reflection"));
                return true;
            }
            Object manager = managerClass.equals(DevicePolicyManager.class)
                    ? receiver.getManager(context)
                    : context.getSystemService(managerClass);

            Object result = method.invoke(manager, args);
            Log.d(TAG, "onReceive(): result=" + result);
            sendResult(receiver, result);
        } catch (Exception e) {
            sendError(receiver, e);
        }

        return true;
    }

    /**
     * Called by the device owner  {@link DeviceAdminReceiver} to broadcasts an intent back to the
     * test case app.
     */
    public static void sendBroadcastToTestCaseReceiver(Context context, Intent intent) {
        if (isHeadlessSystemUser()) {
            TestAppCallbacksReceiver.sendBroadcast(context, intent);
            return;
        }
        Log.d(TAG, "Broadcasting " + intent.getAction() + " locally on user "
                + context.getUserId());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Nullable
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        // Handle some special cases first...

        // Methods that use CharSequence instead of String
        if (methodName.equals("wipeData") && parameterTypes.length == 2) {
            return clazz.getDeclaredMethod(methodName,
                    new Class<?>[] { int.class, CharSequence.class });
        }
        if ((methodName.equals("setStartUserSessionMessage")
                || methodName.equals("setEndUserSessionMessage"))) {
            return clazz.getDeclaredMethod(methodName,
                    new Class<?>[] { ComponentName.class, CharSequence.class });
        }

        // Calls with null parameters (and hence the type cannot be inferred)
        Method method = findMethodWithNullParameterCall(clazz, methodName, parameterTypes);
        if (method != null) return method;

        // ...otherwise return exactly what as asked
        return clazz.getDeclaredMethod(methodName, parameterTypes);
    }

    @Nullable
    private static Method findMethodWithNullParameterCall(Class<?> clazz, String methodName,
            Class<?>[] parameterTypes) {
        if (parameterTypes == null) return null;

        boolean hasNullParameter = false;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == null) {
                if (VERBOSE) {
                    Log.v(TAG, "Found null parameter at index " + i + " of " + methodName);
                }
                hasNullParameter = true;
                break;
            }
        }
        if (!hasNullParameter) return null;

        Method method = null;
        for (Method candidate : clazz.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                if (method != null) {
                    // TODO: figure out how to solve this scenario if it happen (most likely it will
                    // need to use the non-null types and/or length of types to infer the right one
                    Log.e(TAG, "found another method (" + candidate + ") for " + methodName
                            + ", but will use " + method);
                } else {
                    method = candidate;
                    Log.d(TAG, "using method " + method + " for " + methodName
                            + " with null arguments");
                }
            }
        }
        return method;
    }

    private static void sendError(DeviceAdminReceiver receiver, Exception e) {
        Log.e(TAG, "Exception handling wrapped DPC call" , e);
        sendNoLog(receiver, RESULT_EXCEPTION, e);
    }

    private static void sendResult(DeviceAdminReceiver receiver, Object result) {
        if (VERBOSE) {
            Log.v(TAG, "Sending '" + result + "' to " + receiver + " on " + Thread.currentThread());
        }
        sendNoLog(receiver, RESULT_OK, result);
        if (VERBOSE) Log.v(TAG, "Sent");
    }

    private static void sendNoLog(DeviceAdminReceiver receiver, int code, Object result) {
        receiver.setResultCode(code);
        if (result != null) {
            Intent intent = new Intent();
            addArg(intent, new Object[] { result }, /* index= */ 0);
            receiver.setResultExtras(intent.getExtras());
        }
    }

    private DeviceOwnerHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
