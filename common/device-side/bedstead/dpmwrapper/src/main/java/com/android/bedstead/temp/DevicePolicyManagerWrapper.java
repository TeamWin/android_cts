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
package com.android.bedstead.temp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.DebugUtils;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.internal.annotations.GuardedBy;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    public static final String ACTION_WRAPPED_DPM_CALL =
            "com.android.bedstead.temp.action.WRAPPED_DPM_CALL";

    private static final String EXTRA_METHOD = "methodName";
    private static final String EXTRA_NUMBER_ARGS = "number_args";
    private static final String EXTRA_ARG_PREFIX = "arg_";

    // NOTE: Bundle has a putObject() method that would make it much easier to marshal the args,
    // but unfortunately there is no Intent.putObjectExtra() method (and intent.getBundle() returns
    // a copy, so we need to explicitly marshal any supported type).
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_BYTE_ARRAY = "byte_array";
    private static final String TYPE_STRING_OR_CHAR_SEQUENCE = "string";
    private static final String TYPE_PARCELABLE = "parcelable";
    private static final String TYPE_SERIALIZABLE = "serializable";
    private static final String TYPE_ARRAY_LIST_STRING = "array_list_string";
    private static final String TYPE_ARRAY_LIST_PARCELABLE = "array_list_parcelable";
    private static final String TYPE_SET_STRING = "set_string";
    // Used when a method is called passing a null argument - the proper method will have to be
    // infered using findMethod()
    private static final String TYPE_NULL = "null";

    public static final int RESULT_OK = 42;
    public static final int RESULT_EXCEPTION = 666;

    // Must be high enough to outlast long tests like NetworkLoggingTest, which waits up to
    // 6 minutes for network monitoring events.
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

    private static final HashMap<Context, DevicePolicyManager> sSpies = new HashMap<>();

    private static final int MY_USER_ID = UserHandle.myUserId();

    private static final HandlerThread HANDLER_THREAD =
            new HandlerThread("DpmWrapperOrderedBroadcastsHandlerThread");

    private static Handler sHandler;

    /***
     * Gets the {@link DevicePolicyManager} for the given context.
     *
     * <p>Tests should use this method to get a {@link DevicePolicyManager} instance.
     */
    public static DevicePolicyManager get(Context context,
            Class<? extends DeviceAdminReceiver> receiverClass) {

        if (sHandler == null) {
            Log.i(TAG, "Starting handler thread " + HANDLER_THREAD);
            HANDLER_THREAD.start();
            sHandler = new Handler(HANDLER_THREAD.getLooper());
        }
        int userId = context.getUserId();
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        String receiverClassName = receiverClass.getName();
        Log.d(TAG, "get():  receiverClassName: " + receiverClassName);

        if (userId == UserHandle.USER_SYSTEM || !UserManager.isHeadlessSystemUserMode()) {
            Log.i(TAG, "get(): returning 'pure' DevicePolicyManager for user " + userId);
            return dpm;
        }

        DevicePolicyManager spy = sSpies.get(context);
        if (spy != null) {
            Log.d(TAG, "get(): returning cached spy for user " + userId);
            return spy;
        }

        spy = Mockito.spy(dpm);

        Answer<?> answer = (inv) -> {
            Object[] args = inv.getArguments();
            Log.d(TAG, "spying " + inv + " method: " + inv.getMethod());
            String methodName = inv.getMethod().getName();
            Intent intent = new Intent(ACTION_WRAPPED_DPM_CALL)
                    .setClassName(context, receiverClassName)
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

        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy

        try {
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

            // Used by NetworkLoggingTest
            doAnswer(answer).when(spy).retrieveNetworkLogs(any(), anyLong());
            doAnswer(answer).when(spy).setNetworkLoggingEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).isNetworkLoggingEnabled(any());

            // Used by CtsVerifier
            doAnswer(answer).when(spy).addUserRestriction(any(), any());
            doAnswer(answer).when(spy).clearUserRestriction(any(), any());
            doAnswer(answer).when(spy).clearDeviceOwnerApp(any());
            doAnswer(answer).when(spy).setKeyguardDisabledFeatures(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordQuality(any(), anyInt());
            doAnswer(answer).when(spy).setMaximumTimeToLock(any(), anyInt());
            doAnswer(answer).when(spy).setPermittedAccessibilityServices(any(), any());
            doAnswer(answer).when(spy).setPermittedInputMethods(any(), any());
            doAnswer(answer).when(spy).setDeviceOwnerLockScreenInfo(any(), any());
            doAnswer(answer).when(spy).setKeyguardDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setAutoTimeRequired(any(), anyBoolean());
            doAnswer(answer).when(spy).setStatusBarDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setOrganizationName(any(), any());
            doAnswer(answer).when(spy).setSecurityLoggingEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setPermissionGrantState(any(), any(), any(), anyInt());
            doAnswer(answer).when(spy).clearPackagePersistentPreferredActivities(any(), any());
            doAnswer(answer).when(spy).setAlwaysOnVpnPackage(any(), any(), anyBoolean());
            doAnswer(answer).when(spy).setRecommendedGlobalProxy(any(), any());
            doAnswer(answer).when(spy).uninstallCaCert(any(), any());
            doAnswer(answer).when(spy).setMaximumFailedPasswordsForWipe(any(), anyInt());
            doAnswer(answer).when(spy).setSecureSetting(any(), any(), any());
            doAnswer(answer).when(spy).setAffiliationIds(any(), any());
            doAnswer(answer).when(spy).setStartUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).setEndUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).setLogoutEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).removeUser(any(), any());

            // Used by DevicePolicySafetyCheckerIntegrationTest
            doAnswer(answer).when(spy).createAndManageUser(any(), any(), any(), any(), anyInt());
            doAnswer(answer).when(spy).lockNow();
            doAnswer(answer).when(spy).lockNow(anyInt());
            doAnswer(answer).when(spy).logoutUser(any());
            doAnswer(answer).when(spy).reboot(any());
            doAnswer(answer).when(spy).removeActiveAdmin(any());
            doAnswer(answer).when(spy).removeKeyPair(any(), any());
            doAnswer(answer).when(spy).requestBugreport(any());
            doAnswer(answer).when(spy).setAlwaysOnVpnPackage(any(), any(), anyBoolean(), any());
            doAnswer(answer).when(spy).setApplicationHidden(any(), any(), anyBoolean());
            doAnswer(answer).when(spy).setApplicationRestrictions(any(), any(), any());
            doAnswer(answer).when(spy).setCameraDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setFactoryResetProtectionPolicy(any(), any());
            doAnswer(answer).when(spy).setGlobalPrivateDnsModeOpportunistic(any());
            doAnswer(answer).when(spy).setKeepUninstalledPackages(any(), any());
            doAnswer(answer).when(spy).setLockTaskFeatures(any(), anyInt());
            doAnswer(answer).when(spy).setLockTaskPackages(any(), any());
            doAnswer(answer).when(spy).setMasterVolumeMuted(any(), anyBoolean());
            doAnswer(answer).when(spy).setOverrideApnsEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setPermissionPolicy(any(), anyInt());
            doAnswer(answer).when(spy).setRestrictionsProvider(any(), any());
            doAnswer(answer).when(spy).setSystemUpdatePolicy(any(), any());
            doAnswer(answer).when(spy).setTrustAgentConfiguration(any(), any(), any());
            doAnswer(answer).when(spy).startUserInBackground(any(), any());
            doAnswer(answer).when(spy).stopUser(any(), any());
            doAnswer(answer).when(spy).switchUser(any(), any());
            doAnswer(answer).when(spy).wipeData(anyInt(), any());
            doAnswer(answer).when(spy).wipeData(anyInt());

            // TODO(b/176993670): add more methods below as tests are converted
        } catch (Exception e) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e);
        }

        sSpies.put(context, spy);
        Log.d(TAG, "get(): returning new spy for context " + context + " and user "
                + userId);

        return spy;
    }

    /**
     * Handles the wrapped DPM call received by the {@link DeviceAdminReceiver}.
     */
    public static void onReceive(DeviceAdminReceiver receiver, Context context, Intent intent) {
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
            Method method = findMethod(methodName, parameterTypes);
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

    @Nullable
    private static Method findMethod(String methodName, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        // Handle some special cases first...

        // Methods that use CharSequence instead of String
        if (methodName.equals("wipeData") && parameterTypes.length == 2) {
            // wipeData() takes a CharSequence, but it's wrapped as String
            return DevicePolicyManager.class.getDeclaredMethod(methodName,
                    new Class<?>[] { int.class, CharSequence.class });
        }

        // Calls with null parameters (and hence the type cannot be inferred)
        Method method = findMethodWithNullParameterCall(methodName, parameterTypes);
        if (method != null) return method;

        // ...otherwise return exactly what as asked
        return DevicePolicyManager.class.getDeclaredMethod(methodName, parameterTypes);
    }

    @Nullable
    private static Method findMethodWithNullParameterCall(String methodName,
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
        for (Method candidate : DevicePolicyManager.class.getDeclaredMethods()) {
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

    /**
     * Called by the DO {@link BasicAdminReceiver} to broadcasts an intent to the test case app.
     */
    public static void sendBroadcastToTestCaseReceiver(Context context, Intent intent) {
        if (isHeadlessSystemUser()) {
            BridgeReceiver.sendBroadcast(context, intent);
            return;
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Called by test case to register a {@link BrodcastReceiver} to receive intents sent by the
     * DO {@link BasicAdminReceiver}.
     */
    public static void registerTestCaseReceiver(Context context, BroadcastReceiver receiver,
            IntentFilter filter) {
        if (isCurrentUserOnHeadlessSystemUser()) {
            BridgeReceiver.registerReceiver(context, receiver, filter);
            return;
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    /**
     * Called by test case to unregister a {@link BrodcastReceiver} that receive intents sent by the
     * DO {@link BasicAdminReceiver}.
     */
    public static void unregisterTestCaseReceiver(Context context, BroadcastReceiver receiver) {
        if (isCurrentUserOnHeadlessSystemUser()) {
            BridgeReceiver.unregisterReceiver(context, receiver);
            return;
        }
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
    }

    private static boolean isHeadlessSystemUser() {
        return UserManager.isHeadlessSystemUserMode() && MY_USER_ID == UserHandle.USER_SYSTEM;
    }

    private static boolean isCurrentUserOnHeadlessSystemUser() {
        return UserManager.isHeadlessSystemUserMode()
                && MY_USER_ID == ActivityManager.getCurrentUser();
    }

    private static void assertCurrentUserOnHeadlessSystemMode() {
        if (isCurrentUserOnHeadlessSystemUser()) return;

        throw new IllegalStateException("Should only be called by current user ("
                + ActivityManager.getCurrentUser() + ") on headless system user device, but was "
                        + "called by process from user " + MY_USER_ID);
    }

    static void addArg(Intent intent, Object[] args, int index) {
        Object value = args[index];
        String extraTypeName = getArgExtraTypeName(index);
        String extraValueName = getArgExtraValueName(index);
        if (VERBOSE) {
            Log.v(TAG, "addArg(" + index + "): typeName= " + extraTypeName
                    + ", valueName= " + extraValueName);
        }
        if (value == null) {
            logMarshalling("Adding Null", index, extraTypeName, TYPE_NULL, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_NULL);
            return;

        }
        if ((value instanceof Boolean)) {
            logMarshalling("Adding Boolean", index, extraTypeName, TYPE_BOOLEAN, extraValueName,
                    value);
            intent.putExtra(extraTypeName, TYPE_BOOLEAN);
            intent.putExtra(extraValueName, ((Boolean) value).booleanValue());
            return;
        }
        if ((value instanceof Integer)) {
            logMarshalling("Adding Integer", index, extraTypeName, TYPE_INT, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_INT);
            intent.putExtra(extraValueName, ((Integer) value).intValue());
            return;
        }
        if ((value instanceof Long)) {
            logMarshalling("Adding Long", index, extraTypeName, TYPE_LONG, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_LONG);
            intent.putExtra(extraValueName, ((Long) value).longValue());
            return;
        }
        if ((value instanceof byte[])) {
            logMarshalling("Adding Byte[]", index, extraTypeName, TYPE_BYTE_ARRAY, extraValueName,
                    value);
            intent.putExtra(extraTypeName, TYPE_BYTE_ARRAY);
            intent.putExtra(extraValueName, (byte[]) value);
            return;
        }
        if ((value instanceof CharSequence)) {
            logMarshalling("Adding CharSequence", index, extraTypeName,
                    TYPE_STRING_OR_CHAR_SEQUENCE, extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_STRING_OR_CHAR_SEQUENCE);
            intent.putExtra(extraValueName, (CharSequence) value);
            return;
        }
        if ((value instanceof Parcelable)) {
            logMarshalling("Adding Parcelable", index, extraTypeName, TYPE_PARCELABLE,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_PARCELABLE);
            intent.putExtra(extraValueName, (Parcelable) value);
            return;
        }

        if ((value instanceof List<?>)) {
            List<?> list = (List<?>) value;

            String type = null;
            if (list.isEmpty()) {
                Log.w(TAG, "Empty list at index " + index + "; assuming it's List<String>");
                type = TYPE_ARRAY_LIST_STRING;
            } else {
                Object firstItem = list.get(0);
                if (firstItem instanceof String) {
                    type = TYPE_ARRAY_LIST_STRING;
                } else if (firstItem instanceof Parcelable) {
                    type = TYPE_ARRAY_LIST_PARCELABLE;
                } else {
                    throw new IllegalArgumentException("Unsupported List type at index " + index
                            + ": " + firstItem);
                }
            }

            logMarshalling("Adding " + type, index, extraTypeName, type, extraValueName, value);
            intent.putExtra(extraTypeName, type);
            switch (type) {
                case TYPE_ARRAY_LIST_STRING:
                    @SuppressWarnings("unchecked")
                    ArrayList<String> arrayListString = (value instanceof ArrayList)
                            ? (ArrayList<String>) list
                            : new ArrayList<>((List<String>) list);
                    intent.putStringArrayListExtra(extraValueName, arrayListString);
                    break;
                case TYPE_ARRAY_LIST_PARCELABLE:
                    @SuppressWarnings("unchecked")
                    ArrayList<Parcelable> arrayListParcelable = (ArrayList<Parcelable>) list;
                    intent.putParcelableArrayListExtra(extraValueName, arrayListParcelable);
                    break;
                default:
                    // should never happen because type is checked above
                    throw new AssertionError("invalid type conversion: " + type);
            }
            return;
        }

        // TODO(b/176993670): ArraySet<> is encapsulate as ArrayList<>, so most of the code below
        // could be reused (right now it was copy-and-paste from ArrayList<>, minus the Parcelable
        // part.
        if ((value instanceof Set<?>)) {
            Set<?> set = (Set<?>) value;

            String type = null;
            if (set.isEmpty()) {
                Log.w(TAG, "Empty set at index " + index + "; assuming it's Set<String>");
                type = TYPE_SET_STRING;
            } else {
                Object firstItem = set.iterator().next();
                if (firstItem instanceof String) {
                    type = TYPE_SET_STRING;
                } else {
                    throw new IllegalArgumentException("Unsupported Set type at index "
                            + index + ": " + firstItem);
                }
            }

            logMarshalling("Adding " + type, index, extraTypeName, type, extraValueName, value);
            intent.putExtra(extraTypeName, type);
            switch (type) {
                case TYPE_SET_STRING:
                    @SuppressWarnings("unchecked")
                    Set<String> stringSet = (Set<String>) value;
                    intent.putStringArrayListExtra(extraValueName, new ArrayList<>(stringSet));
                    break;
                default:
                    // should never happen because type is checked above
                    throw new AssertionError("invalid type conversion: " + type);
            }
            return;
        }

        if ((value instanceof Serializable)) {
            logMarshalling("Adding Serializable", index, extraTypeName, TYPE_SERIALIZABLE,
                    extraValueName, value);
            intent.putExtra(extraTypeName, TYPE_SERIALIZABLE);
            intent.putExtra(extraValueName, (Serializable) value);
            return;
        }

        throw new IllegalArgumentException("Unsupported value type at index " + index + ": "
                + (value == null ? "null" : value.getClass()));
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
            case TYPE_NULL:
                logMarshalling("Got null", index, extraTypeName, type, extraValueName, value);
                break;
            case TYPE_SET_STRING:
                @SuppressWarnings("unchecked")
                ArrayList<String> list = (ArrayList<String>) extras.get(extraValueName);
                value = new ArraySet<String>(list);
                logMarshalling("Got ArraySet<String>", index, extraTypeName, type, extraValueName,
                        value);
                break;

            case TYPE_ARRAY_LIST_STRING:
            case TYPE_ARRAY_LIST_PARCELABLE:
            case TYPE_BYTE_ARRAY:
            case TYPE_BOOLEAN:
            case TYPE_INT:
            case TYPE_LONG:
            case TYPE_STRING_OR_CHAR_SEQUENCE:
            case TYPE_PARCELABLE:
            case TYPE_SERIALIZABLE:
                value = extras.get(extraValueName);
                logMarshalling("Got generic", index, extraTypeName, type, extraValueName, value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported value type at index " + index + ": "
                        + extraTypeName);
        }
        if (parameterTypes != null) {
            Class<?> parameterType = null;
            switch (type) {
                case TYPE_NULL:
                    break;
                case TYPE_BOOLEAN:
                    parameterType = boolean.class;
                    break;
                case TYPE_INT:
                    parameterType = int.class;
                    break;
                case TYPE_LONG:
                    parameterType = long.class;
                    break;
                case TYPE_STRING_OR_CHAR_SEQUENCE:
                    // A String is a CharSequence, but most methods take String, so we're assuming
                    // a string and handle the exceptional cases on findMethod()
                    parameterType = String.class;
                    break;
                case TYPE_BYTE_ARRAY:
                    parameterType = byte[].class;
                    break;
                case TYPE_ARRAY_LIST_STRING:
                    parameterType = List.class;
                    break;
                case TYPE_SET_STRING:
                    parameterType = Set.class;
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

    private static String resultCodeToString(int code) {
        return DebugUtils.constantToString(DevicePolicyManagerWrapper.class, "RESULT_", code);
    }

    private static void fail(String template, Object... args) {
        throw new AssertionError(String.format(Locale.ENGLISH, template, args));
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

    /**
     * {@link BroacastReceiver} running on current user used to relay {@link Intent intents}
     * received by the device owner {@link BasicAdminReceiver} on system user.
     */
    public static final class BridgeReceiver extends BroadcastReceiver {

        private static final String TAG = BridgeReceiver.class.getSimpleName();
        private static final String EXTRA = "relayed_intent";

        private static final Object LOCK = new Object();
        private static HandlerThread sHandlerThread;
        private static Handler sHandler;

        /**
         * Map of receivers per intent action.
         */
        @GuardedBy("LOCK")
        private static final ArrayMap<String, ArrayList<BroadcastReceiver>> sRealReceivers =
                new ArrayMap<>();

        /**
         * Called by {@code ActivityManager} to deliver an intent.
         */
        public BridgeReceiver() {
            assertCurrentUserOnHeadlessSystemMode();
            if (sHandlerThread == null) {
                sHandlerThread = new HandlerThread("BridgeReceiverThread");
                Log.i(TAG, "Starting thread " + sHandlerThread + " on user " + MY_USER_ID);
                sHandlerThread.start();
                sHandler = new Handler(sHandlerThread.getLooper());
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "BridgeReceiver received intent on user " + context.getUserId() + ": "
                    + intent);
            Intent realIntent = intent.getParcelableExtra(EXTRA);
            if (realIntent == null) {
                Log.e(TAG, "No " + EXTRA + " on intent " + intent);
                return;
            }
            String action = realIntent.getAction();
            ArrayList<BroadcastReceiver> receivers;
            synchronized (LOCK) {
                receivers = sRealReceivers.get(action);
            }
            if (receivers == null || receivers.isEmpty()) {
                Log.e(TAG, "onReceive(): no receiver for " + action + ": " + sRealReceivers);
                return;
            }
            Log.d(TAG, "Will dispatch intent to " + receivers.size() + " on handler thread");
            receivers.forEach((r) -> sHandler.post(() ->
                    handleDispatchIntent(r, context, realIntent)));
        }

        private void handleDispatchIntent(BroadcastReceiver receiver, Context context,
                Intent intent) {
            Log.d(TAG, "Dispatching " + intent + " to " + receiver + " on thread "
                    + Thread.currentThread());
            receiver.onReceive(context, intent);
        }

        private static void registerReceiver(Context context, BroadcastReceiver receiver,
                IntentFilter filter) {
            if (VERBOSE) Log.v(TAG, "registerReceiver(): " + receiver);
            synchronized (LOCK) {
                filter.actionsIterator().forEachRemaining((action) -> {
                    Log.d(TAG, "Registering " + receiver + " for " + action);
                    ArrayList<BroadcastReceiver> receivers = sRealReceivers.get(action);
                    if (receivers == null) {
                        receivers = new ArrayList<>();
                        if (VERBOSE) Log.v(TAG, "Creating list of receivers for " + action);
                        sRealReceivers.put(action, receivers);
                    }
                    receivers.add(receiver);
                });
            }
        }

        private static void unregisterReceiver(Context context, BroadcastReceiver receiver) {
            if (VERBOSE) Log.v(TAG, "unregisterReceiver(): " + receiver);

            synchronized (LOCK) {
                for (int i = 0; i < sRealReceivers.size(); i++) {
                    String action = sRealReceivers.keyAt(i);
                    ArrayList<BroadcastReceiver> receivers = sRealReceivers.valueAt(i);
                    boolean removed = receivers.remove(receiver);
                    if (removed) {
                        Log.d(TAG, "Removed " + receiver + " for action " + action);
                    }
                }
            }
        }

        private static void sendBroadcast(Context context, Intent intent) {
            int currentUserId = ActivityManager.getCurrentUser();
            Intent bridgeIntent = new Intent(context, BridgeReceiver.class).putExtra(EXTRA, intent);
            Log.d(TAG, "Relaying " + intent + " from user " + MY_USER_ID + " to user "
                    + currentUserId + " using " + bridgeIntent);
            context.sendBroadcastAsUser(bridgeIntent, UserHandle.of(currentUserId));
        }
    }
}
