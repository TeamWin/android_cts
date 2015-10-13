/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.JsonWriter;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Collect device information on target device and write to a JSON file.
 */
public abstract class DeviceInfo extends InstrumentationTestCase {

    private enum ResultCode {
        // Collection started.
        STARTED,
        // Collection completed.
        COMPLETED,
        // Collection completed with error.
        ERROR,
        // Collection failed to complete.
        FAILED
    }

    private static final int MAX_STRING_VALUE_LENGTH = 1000;
    private static final int MAX_ARRAY_LENGTH = 1000;

    private static final String LOG_TAG = "ExtendedDeviceInfo";

    private JsonWriter mJsonWriter = null;
    private String mResultFilePath = null;
    private String mErrorMessage = null;
    private ResultCode mResultCode = ResultCode.STARTED;

    Set<String> mActivityList = new HashSet<String>();

    public void testCollectDeviceInfo() {
        if (!mActivityList.contains(getClass().getName())) {
            return;
        }

        if (createFilePath()) {
            createJsonWriter();
            startJsonWriter();
            collectDeviceInfo();
            closeJsonWriter();

            if (mResultCode == ResultCode.STARTED) {
                mResultCode = ResultCode.COMPLETED;
            }
        }

        sendStatus();

        String message = getClass().getSimpleName() + " collection completed.";
        assertEquals(message, ResultCode.COMPLETED, mResultCode);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Build the list of supported activities that can run collection.
        ActivityInfo[] activities = null;
        try {
            activities = getContext().getPackageManager().getPackageInfo(
                getContext().getPackageName(), PackageManager.GET_ACTIVITIES).activities;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception occurred while getting activities.", e);
            return;
        }

        for (ActivityInfo activityInfo : activities) {
            mActivityList.add(activityInfo.name);
        }
    }

    /**
     * Method to collect device information.
     */
    protected abstract void collectDeviceInfo();

    protected Context getContext() {
        return getInstrumentation().getContext();
    }

    /**
     * Sends status to instrumentation.
     */
    void sendStatus() {
        Bundle bundle = new Bundle();
        String className = getClass().getSimpleName();
        if (this instanceof GenericDeviceInfo) {
            ((GenericDeviceInfo) this).putDeviceInfo(bundle);
        }
        if (!TextUtils.isEmpty(mErrorMessage)) {
            bundle.putString("DEVICE_INFO_ERROR_" + className, mErrorMessage);
        }
        if (mResultCode == ResultCode.COMPLETED) {
            bundle.putString("DEVICE_INFO_FILE_" + className, mResultFilePath);
        }
        getInstrumentation().sendStatus(Activity.RESULT_OK, bundle);
    }

    /**
     * Returns the path to the json file if collector completed successfully.
     */
    String getResultFilePath() {
        return mResultFilePath;
    }

    private void error(String message) {
        mResultCode = ResultCode.ERROR;
        mErrorMessage = message;
        Log.e(LOG_TAG, message);
    }

    private void failed(String message) {
        mResultCode = ResultCode.FAILED;
        mErrorMessage = message;
        Log.e(LOG_TAG, message);
    }

    private boolean createFilePath() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            failed("External storage is not mounted");
            return false;
        }
        final File dir = new File(Environment.getExternalStorageDirectory(), "device-info-files");
        if (!dir.mkdirs() && !dir.isDirectory()) {
            failed("Cannot create directory for device info files");
            return false;
        }

        // Create file at /sdcard/device-info-files/<class_name>.deviceinfo.json
        final File jsonFile = new File(dir, getClass().getSimpleName() + ".deviceinfo.json");
        try {
            jsonFile.createNewFile();
        } catch (Exception e) {
            failed("Cannot create file to collect device info");
            return false;
        }
        mResultFilePath = jsonFile.getAbsolutePath();
        return true;
    }

    private void createJsonWriter() {
        try {
            FileOutputStream out = new FileOutputStream(mResultFilePath);
            mJsonWriter = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            // TODO(agathaman): remove to make json output less pretty
            mJsonWriter.setIndent("  ");
        } catch (Exception e) {
            failed("Failed to create JSON writer: " + e.getMessage());
        }
    }

    private void startJsonWriter() {
        try {
            mJsonWriter.beginObject();
        } catch (Exception e) {
            failed("Failed to begin JSON object: " + e.getMessage());
        }
    }

    private void closeJsonWriter() {
        try {
            mJsonWriter.endObject();
            mJsonWriter.close();
        } catch (Exception e) {
            failed("Failed to close JSON object: " + e.getMessage());
        }
    }

    /**
     * Start a new group of result.
     */
    public void startGroup() {
        try {
            mJsonWriter.beginObject();
        } catch (Exception e) {
            error("Failed to begin JSON group: " + e.getMessage());
        }
    }

    /**
     * Start a new group of result with specified name.
     */
    public void startGroup(String name) {
        try {
            mJsonWriter.name(name);
            mJsonWriter.beginObject();
        } catch (Exception e) {
            error("Failed to begin JSON group: " + e.getMessage());
        }
    }

    /**
     * Complete adding result to the last started group.
     */
    public void endGroup() {
        try {
            mJsonWriter.endObject();
        } catch (Exception e) {
            error("Failed to end JSON group: " + e.getMessage());
        }
    }

    /**
     * Start a new array of result.
     */
    public void startArray() {
        try {
            mJsonWriter.beginArray();
        } catch (Exception e) {
            error("Failed to begin JSON array: " + e.getMessage());
        }
    }

    /**
     * Start a new array of result with specified name.
     */
    public void startArray(String name) {
        checkName(name);
        try {
            mJsonWriter.name(name);
            mJsonWriter.beginArray();
        } catch (Exception e) {
            error("Failed to begin JSON array: " + e.getMessage());
        }
    }

    /**
     * Complete adding result to the last started array.
     */
    public void endArray() {
        try {
            mJsonWriter.endArray();
        } catch (Exception e) {
            error("Failed to end JSON group: " + e.getMessage());
        }
    }

    /**
     * Add a double value result.
     */
    public void addResult(String name, double value) {
        checkName(name);
        try {
            mJsonWriter.name(name).value(value);
        } catch (Exception e) {
            error("Failed to add result for type double: " + e.getMessage());
        }
    }

    /**
    * Add a long value result.
    */
    public void addResult(String name, long value) {
        checkName(name);
        try {
            mJsonWriter.name(name).value(value);
        } catch (Exception e) {
            error("Failed to add result for type long: " + e.getMessage());
        }
    }

    /**
     * Add an int value result.
     */
    public void addResult(String name, int value) {
        checkName(name);
        try {
            mJsonWriter.name(name).value((Number) value);
        } catch (Exception e) {
            error("Failed to add result for type int: " + e.getMessage());
        }
    }

    /**
     * Add a boolean value result.
     */
    public void addResult(String name, boolean value) {
        checkName(name);
        try {
            mJsonWriter.name(name).value(value);
        } catch (Exception e) {
            error("Failed to add result for type boolean: " + e.getMessage());
        }
    }

    /**
     * Add a String value result.
     */
    public void addResult(String name, String value) {
        checkName(name);
        try {
            mJsonWriter.name(name).value(checkString(value));
        } catch (Exception e) {
            error("Failed to add result for type String: " + e.getMessage());
        }
    }

    /**
     * Add a double array result.
     */
    public void addArray(String name, double[] list) {
        checkName(name);
        try {
            mJsonWriter.name(name);
            mJsonWriter.beginArray();
            for (double value : checkArray(list)) {
                mJsonWriter.value(value);
            }
            mJsonWriter.endArray();
        } catch (Exception e) {
            error("Failed to add result array for type double: " + e.getMessage());
        }
    }

    /**
     * Add a long array result.
     */
    public void addArray(String name, long[] list) {
        checkName(name);
        try {
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
        for (long value : checkArray(list)) {
            mJsonWriter.value(value);
        }
            mJsonWriter.endArray();
        } catch (Exception e) {
            error("Failed to add result array for type long: " + e.getMessage());
        }
    }

    /**
     * Add an int array result.
     */
    public void addArray(String name, int[] list) {
        checkName(name);
        try {
            mJsonWriter.name(name);
            mJsonWriter.beginArray();
            for (int value : checkArray(list)) {
                mJsonWriter.value((Number) value);
            }
            mJsonWriter.endArray();
        } catch (Exception e) {
            error("Failed to add result array for type int: " + e.getMessage());
        }
    }

    /**
     * Add a boolean array result.
     */
    public void addArray(String name, boolean[] list) {
        checkName(name);
        try {
            mJsonWriter.name(name);
            mJsonWriter.beginArray();
            for (boolean value : checkArray(list)) {
                mJsonWriter.value(value);
            }
            mJsonWriter.endArray();
        } catch (Exception e) {
            error("Failed to add result array for type boolean: " + e.getMessage());
        }
    }

    /**
     * Add a String array result.
     */
    public void addArray(String name, String[] list) {
        checkName(name);
        try {
            mJsonWriter.name(name);
            mJsonWriter.beginArray();
            for (String value : checkArray(list)) {
                mJsonWriter.value(checkString(value));
            }
            mJsonWriter.endArray();
        } catch (Exception e) {
            error("Failed to add result array for type Sting: " + e.getMessage());
        }
    }

    private static boolean[] checkArray(boolean[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    private static double[] checkArray(double[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    private static int[] checkArray(int[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    private static long[] checkArray(long[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    private static String[] checkArray(String[] values) {
        if (values.length > MAX_ARRAY_LENGTH) {
            return Arrays.copyOf(values, MAX_ARRAY_LENGTH);
        } else {
            return values;
        }
    }

    private static String checkString(String value) {
        if (value.length() > MAX_STRING_VALUE_LENGTH) {
            return value.substring(0, MAX_STRING_VALUE_LENGTH);
        }
        return value;
    }

    private static String checkName(String value) {
        if (TextUtils.isEmpty(value)) {
            throw new NullPointerException();
        }
        return value;
    }
}
