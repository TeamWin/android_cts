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

package com.android.cts.input;

import static android.os.FileUtils.closeQuietly;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.hardware.input.InputManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Declares a virtual INPUT device registered through /dev/uinput or /dev/hid.
 */
public abstract class VirtualInputDevice implements InputManager.InputDeviceListener {
    private static final String TAG = "VirtualInputDevice";
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private Instrumentation mInstrumentation;
    private final Thread mThread;
    private volatile CountDownLatch mDeviceAddedSignal; // to wait for onInputDeviceAdded signal

    protected final int mId; // initialized from the json file
    protected JsonReader mReader;
    protected final Object mLock = new Object();

    /**
     * To be implemented with device specific shell command to execute.
     */
    abstract String getShellCommand();

    /**
     * To be implemented with device specific result reading function.
     */
    abstract void readResults();

    private final class ResultReader implements Runnable {
        @Override
        public void run() {
            try {
                while (mReader.peek() != JsonToken.END_DOCUMENT) {
                    readResults();
                }
            } catch (IOException ex) {
                Log.w(TAG, "Exiting JSON Result reader. " + ex);
            }
        }
    }

    public VirtualInputDevice(Instrumentation instrumentation, int deviceId,
            String registerCommand) {
        mInstrumentation = instrumentation;
        setupPipes();

        mInstrumentation.runOnMainSync(new Runnable(){
            @Override
            public void run() {
                InputManager inputManager =
                        mInstrumentation.getContext().getSystemService(InputManager.class);
                inputManager.registerInputDeviceListener(VirtualInputDevice.this, null);
            }
        });

        mId = deviceId;
        mThread = new Thread(new ResultReader());
        mThread.start();
        registerInputDevice(registerCommand);
    }

    protected byte[] readData() throws IOException {
        ArrayList<Integer> data = new ArrayList<Integer>();
        try {
            mReader.beginArray();
            while (mReader.hasNext()) {
                data.add(Integer.decode(mReader.nextString()));
            }
            mReader.endArray();
        } catch (IllegalStateException | NumberFormatException e) {
            mReader.endArray();
            throw new IllegalStateException("Encountered malformed data.", e);
        }
        byte[] rawData = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            int d = data.get(i);
            if ((d & 0xFF) != d) {
                throw new IllegalStateException("Invalid data, all values must be byte-sized");
            }
            rawData[i] = (byte) d;
        }
        return rawData;
    }

    /**
     * Register an input device. May cause a failure if the device added notification
     * is not received within the timeout period
     *
     * @param registerCommand The full json command that specifies how to register this device
     */
    private void registerInputDevice(String registerCommand) {
        mDeviceAddedSignal = new CountDownLatch(1);
        Log.i(TAG, "registerInputDevice: " + registerCommand);
        writeCommands(registerCommand.getBytes());
        try {
            // Wait for input device added callback.
            mDeviceAddedSignal.await(20L, TimeUnit.SECONDS);
            if (mDeviceAddedSignal.getCount() != 0) {
                throw new RuntimeException("Did not receive device added notification in time");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(
                    "Unexpectedly interrupted while waiting for device added notification.");
        }
        // Even though the device has been added, it still may not be ready to process the events
        // right away. This seems to be a kernel bug.
        // Add a small delay here to ensure device is "ready".
        SystemClock.sleep(1000);
    }

    /**
     * Add a delay between processing events.
     *
     * @param milliSeconds The delay in milliseconds.
     */
    public void delay(int milliSeconds) {
        JSONObject json = new JSONObject();
        try {
            json.put("command", "delay");
            json.put("id", mId);
            json.put("duration", milliSeconds);
        } catch (JSONException e) {
            throw new RuntimeException(
                    "Could not create JSON object to delay " + milliSeconds + " milliseconds");
        }
        writeCommands(json.toString().getBytes());
    }

    /**
     * Close the device, which would cause the associated input device to unregister.
     */
    public void close() {
        closeQuietly(mInputStream);
        closeQuietly(mOutputStream);
        // mThread should exit when stream is closed.
    }

    private void setupPipes() {
        UiAutomation ui = mInstrumentation.getUiAutomation();
        ParcelFileDescriptor[] pipes = ui.executeShellCommandRw(getShellCommand());

        mInputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipes[0]);
        mOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]);
        try {
            mReader = new JsonReader(new InputStreamReader(mInputStream, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        mReader.setLenient(true);
    }

    protected void writeCommands(byte[] bytes) {
        try {
            mOutputStream.write(bytes);
            mOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // InputManager.InputDeviceListener functions
    @Override
    public void onInputDeviceAdded(int deviceId) {
        mDeviceAddedSignal.countDown();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
    }
}
