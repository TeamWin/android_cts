/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.cts.deviceowner;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import android.annotation.Nullable;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.DebugUtils;
import android.util.Log;
import android.util.Slog;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class used to create to provide a {@link DevicePolicyManager} implementation that automatically
 * funnels calls between the user running the tests and the user that is the device owner.
 */
// TODO(b/176993670): STOPSHIP - it currently uses ordered broadcasts and a Mockito spy to implement
// the IPC between users, but before S is shipped it should be changed to use the connected apps SDK
// or the new CTS infrastructure.
public final class DevicePolicyManagerWrapper {

    private static final String TAG = DevicePolicyManagerWrapper.class.getSimpleName();
    private static final boolean VERBOSE = false;

    static final String ACTION_WRAPPED_DPM_CALL =
            "com.android.cts.deviceowner.action.WRAPPED_DPM_CALL";

    private static final String EXTRA_METHOD = "methodName";
    private static final String EXTRA_NUMBER_ARGS = "number_args";
    private static final String EXTRA_ARG_PREFIX = "arg_";

    // NOTE: Bundle has a putObject() method that would make it much easier to marshal the args,
    // but unfortunately there is no Intent.putObjectExtra() method (and intent.getBundle() returns
    // a copy, so we need to explicitly marshal any supported type).
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_PARCELABLE = "parcelable";
    private static final String TYPE_SERIALIZABLE = "serializable";
    private static final String TYPE_ARRAY_LIST_STRING = "array_list_string";

    public static final int RESULT_OK = 42;
    public static final int RESULT_EXCEPTION = 666;

    private static final int TIMEOUT_MS = 15_000;

    private static final HashMap<Context, DevicePolicyManager> sSpies = new HashMap<>();

    /***
     * Gets the {@link DevicePolicyManager} for the given context.
     *
     * <p>Tests should use this method to get a {@link DevicePolicyManager} instance.
     */
    public static DevicePolicyManager get(Context context) {
        int userId = context.getUserId();
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);

        if (!UserManager.isHeadlessSystemUserMode()) {
            if (VERBOSE) Log.v(TAG, "get(): returning 'pure' DevicePolicyManager: " + dpm);
            return dpm;
        }

        DevicePolicyManager spy = sSpies.get(context);
        if (spy != null) {
            Log.d(TAG, "get(): returning cached spy for context " + context + " and user "
                    + userId);
            return spy;
        }

        spy = Mockito.spy(dpm);

        Answer<?> answer = (inv) -> {
            Slog.d(TAG, "spying " + inv);
            Object[] args = inv.getArguments();
            String methodName = inv.getMethod().getName();
            Intent intent = new Intent(ACTION_WRAPPED_DPM_CALL)
                    .setClassName(context, BasicAdminReceiver.class.getName())
                    .putExtra(EXTRA_METHOD, methodName)
                    .putExtra(EXTRA_NUMBER_ARGS, args.length);
            for (int i = 0; i < args.length; i++) {
                addArg(intent, args, i);
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Result> resultRef = new AtomicReference<>();
            BroadcastReceiver myReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    if (VERBOSE) {
                        Log.v(TAG, "spy received intent " + intent.getAction() + " for user "
                                + context.getUserId());
                    }
                    Result result = new Result(this);
                    if (VERBOSE) Log.v(TAG, "result:" + result);
                    resultRef.set(result);
                    latch.countDown();
                };

            };
            if (VERBOSE) {
                Log.v(TAG, "Broadcasting intent from user "  + userId + " to user "
                        + UserHandle.SYSTEM);
            }
            runWithShellPermissionIdentity(() -> context.sendOrderedBroadcastAsUser(intent,
                    UserHandle.SYSTEM, /* permission= */ null, myReceiver, /* scheduler= */ null,
                    /* initialCode= */ 0, /* initialData= */ null, /* initialExtras= */ null));

            if (VERBOSE) Log.d(TAG, "Waiting for response");
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("Ordered broadcast for " + methodName + "() not received in " + TIMEOUT_MS
                        + "ms");
            }

            Result result = resultRef.get();
            Log.d(TAG, "Received result on user " + userId + ": " + result);

            switch (result.code) {
                case RESULT_OK:
                    return result.value;
                case RESULT_EXCEPTION:
                    throw (Exception) result.value;
                default:
                    fail("Received invalid result" + result);
                    return null;
            }
        };

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy

        // Basic methods used by most tests
        doAnswer(answer).when(spy).isAdminActive(any());
        doAnswer(answer).when(spy).isDeviceOwnerApp(any());
        doAnswer(answer).when(spy).isManagedProfile(any());
        doAnswer(answer).when(spy).isProfileOwnerApp(any());
        doAnswer(answer).when(spy).isAffiliatedUser();

        // Used by SetTimeTest
        doAnswer(answer).when(spy).setTime(any(), anyLong());
        doAnswer(answer).when(spy).setTimeZone(any(), any());
        doAnswer(answer).when(spy).setGlobalSetting(any(), any(), any());

        // Used by UserControlDisabledPackagesTest
        doAnswer(answer).when(spy).setUserControlDisabledPackages(any(), any());
        doAnswer(answer).when(spy).getUserControlDisabledPackages(any());

        // Used by DeviceOwnerProvisioningTest
        doAnswer(answer).when(spy).enableSystemApp(any(), any(String.class));
        doAnswer(answer).when(spy).enableSystemApp(any(), any(Intent.class));

        // Used by HeadlessSystemUserTest
        doAnswer(answer).when(spy).getProfileOwnerAsUser(anyInt());
        doAnswer(answer).when(spy).getProfileOwnerAsUser(any());

        // TODO(b/176993670): add more methods below as tests are converted

        sSpies.put(context, spy);
        Log.d(TAG, "get(): returning new spy for context " + context + " and user "
                + userId);

        return spy;
    }

    /**
     * Handles the wrapped DPM call received by the {@link DeviceAdminReceiver}.
     */
    static void onReceive(DeviceAdminReceiver receiver, Context context, Intent intent) {
        try {
            String methodName = intent.getStringExtra(EXTRA_METHOD);
            int numberArgs = intent.getIntExtra(EXTRA_NUMBER_ARGS, 0);
            Log.d(TAG, "onReceive(): userId=" + context.getUserId()
                    + ", intent=" + intent.getAction() + ", methodName=" + methodName
                    + ", numberArgs=" + numberArgs);
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
            Method method = DevicePolicyManager.class.getDeclaredMethod(methodName, parameterTypes);
            if (method == null) {
                sendError(receiver, new IllegalArgumentException(
                        "Could not find method " + methodName + " using reflection"));
                return;
            }
            Object result = method.invoke(receiver.getManager(context), args);
            Log.d(TAG, "onReceive(): result=" + result);
            sendResult(receiver, result);
        } catch (Exception e) {
            sendError(receiver, e);
        }
        return;
    }


    static void addArg(Intent intent, Object[] args, int index) {
        Object value = args[index];
        String extraTypeName = getArgExtraTypeName(index);
        String extraValueName = getArgExtraValueName(index);
        if (VERBOSE) {
            Log.v(TAG, "addArg(" + index + "): typeName= " + extraTypeName
                    + ", valueName= " + extraValueName);
        }
        if ((value instanceof Boolean)) {
            logMarshalling("Adding Boolean", index, extraTypeName, TYPE_BOOLEAN,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_BOOLEAN);
            intent.putExtra(extraValueName, ((Boolean) value).booleanValue());
            return;
        }
        if ((value instanceof Integer)) {
            logMarshalling("Adding Integer", index, extraTypeName, TYPE_INT,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_INT);
            intent.putExtra(extraValueName, ((Integer) value).intValue());
            return;
        }
        if ((value instanceof Long)) {
            logMarshalling("Adding Long", index, extraTypeName, TYPE_LONG,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_LONG);
            intent.putExtra(extraValueName, ((Long) value).longValue());
            return;
        }
        if ((value instanceof String)) {
            logMarshalling("Adding String", index, extraTypeName, TYPE_STRING,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_STRING);
            intent.putExtra(extraValueName, (String) value);
            return;
        }
        if ((value instanceof Parcelable)) {
            logMarshalling("Adding Parcelable", index, extraTypeName, TYPE_PARCELABLE,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_PARCELABLE);
            intent.putExtra(extraValueName, (Parcelable) value);
            return;
        }
        if ((value instanceof Serializable)) {
            logMarshalling("Adding Serializable", index, extraTypeName, TYPE_SERIALIZABLE,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_SERIALIZABLE);
            intent.putExtra(extraValueName, (Serializable) value);
            return;
        }
        if ((value instanceof ArrayList<?>)) {
            ArrayList<?> arrayList = (ArrayList<?>) value;
            String type = null;
            if (arrayList.isEmpty()) {
                Log.w(TAG, "Empty list at index " + index + "; assuming it's ArrayList<String>");
            } else {
                Object firstItem = arrayList.get(0);
                if (!(firstItem instanceof String)) {
                    throw new IllegalArgumentException("Unsupported ArrayList type at index "
                            + index + ": " + firstItem);
                }
                logMarshalling("Adding ArrayList<String>", index, extraTypeName,
                        TYPE_ARRAY_LIST_STRING, extraValueName, value);
                intent.putExtra(extraTypeName, TYPE_ARRAY_LIST_STRING);
                intent.putExtra(extraValueName, (ArrayList<String>) arrayList);
            }
            return;
        }

        throw new IllegalArgumentException("Unsupported value type at index " + index + ": "
                + value.getClass());
    }

    static void getArg(Bundle extras, Object[] args, @Nullable Class<?>[] parameterTypes,
            int index) {
        String extraTypeName = getArgExtraTypeName(index);
        String extraValueName = getArgExtraValueName(index);
        String type = extras.getString(extraTypeName);
        if (VERBOSE) {
            Log.v(TAG, "getArg(" + index + "): typeName= " + extraTypeName + ", type=" + type
                    + ", valueName= " + extraValueName);
        }
        Object value = null;
        switch (type) {
            case TYPE_BOOLEAN:
            case TYPE_INT:
            case TYPE_LONG:
            case TYPE_STRING:
            case TYPE_PARCELABLE:
            case TYPE_SERIALIZABLE:
            case TYPE_ARRAY_LIST_STRING:
                value = extras.get(extraValueName);
                logMarshalling("Got generic", index, extraTypeName, type, extraValueName,
                        value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported value type at index " + index + ": "
                        + extraTypeName);
        }
        if (parameterTypes != null) {
            Class<?> parameterType = null;
            switch (type) {
                case TYPE_BOOLEAN:
                    parameterType = boolean.class;
                    break;
                case TYPE_INT:
                    parameterType = int.class;
                    break;
                case TYPE_LONG:
                    parameterType = long.class;
                    break;
                case TYPE_ARRAY_LIST_STRING:
                    parameterType = List.class;
                    break;
                default:
                    parameterType = value.getClass();
            }
            parameterTypes[index] = parameterType;
        }
        args[index] = value;
    }

    static String getArgExtraTypeName(int index) {
        return EXTRA_ARG_PREFIX + index + "_type";
    }

    static String getArgExtraValueName(int index) {
        return EXTRA_ARG_PREFIX + index + "_value";
    }

    private static void logMarshalling(String operation, int index, String typeName,
            String type, String valueName, Object value) {
        if (VERBOSE) {
            Log.v(TAG, operation + " on " + index + ": typeName=" + typeName + ", type=" + type
                    + ", valueName=" + valueName + ", value=" + value);
        }
    }

    private static void sendError(DeviceAdminReceiver receiver, Exception e) {
        Log.e(TAG, "Exception handling wrapped DPC call" , e);
        sendNoLog(receiver, RESULT_EXCEPTION, e);
    }

    private static void sendResult(DeviceAdminReceiver receiver, Object result) {
        if (VERBOSE) Log.v(TAG, "Returning " + result);
        sendNoLog(receiver, RESULT_OK, result);
    }

    private static void sendNoLog(DeviceAdminReceiver receiver, int code, Object result) {
        receiver.setResultCode(code);
        if (result != null) {
            Intent intent = new Intent();
            addArg(intent, new Object[] { result }, /* index= */ 0);
            receiver.setResultExtras(intent.getExtras());
        }
    }

    private static String resultCodeToString(int code) {
        return DebugUtils.constantToString(DevicePolicyManagerWrapper.class, "RESULT_", code);
    }

    private DevicePolicyManagerWrapper() {
        throw new UnsupportedOperationException("contains only static methods");
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
}
