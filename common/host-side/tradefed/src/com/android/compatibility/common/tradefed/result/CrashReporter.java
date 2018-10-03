/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.result;


import com.android.compatibility.common.util.Crash;
import com.android.compatibility.common.util.CrashUtils;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class CrashReporter extends Thread {

    private String mTestName;
    private String mDeviceSerial;

    private ArrayList<Crash> mCrashes;

    private Process mLogcatProcess;

    private boolean mClearChunk;

    private Pattern mUploadPattern;

    public CrashReporter(String serialNumber) {
        this(serialNumber, CrashUtils.sUploadRequestPattern);
    }

    public CrashReporter(String serialNumber, Pattern uploadSignal) {
        mDeviceSerial = serialNumber;
        mCrashes = new ArrayList<Crash>();
        mUploadPattern = uploadSignal;
    }

    /**
     * Sets up the directory on the device to upload to and starts the thread
     */
    @Override
    public void start() {
        try {
            CrashUtils.executeCommand(
                    10000, "adb -s %s shell rm -rf %s", mDeviceSerial, CrashUtils.DEVICE_PATH);
            CrashUtils.executeCommand(
                    10000, "adb -s %s shell mkdir %s", mDeviceSerial, CrashUtils.DEVICE_PATH);
        } catch (InterruptedException | IOException | TimeoutException e) {
            CLog.logAndDisplay(
                    LogLevel.ERROR, "CrashReporter failed to setup storage directory on device");
            CLog.logAndDisplay(LogLevel.ERROR, e.getMessage());
        }
        super.start();
    }

    public synchronized void testStarted(String testName) {
        mTestName = testName;
        mCrashes = new ArrayList<Crash>();
        mClearChunk = true;
    }

    /**
     * Spins up a logcat process and scans the output for crashes. When an upload signal is found in
     * logcat uploads the Crashes found to the device.
     */
    @Override
    public void run() {
        try {
            mLogcatProcess = Runtime.getRuntime().exec("adb -s " + mDeviceSerial + " logcat");
        } catch (IOException e) {
            CLog.logAndDisplay(LogLevel.ERROR, "CrashReporter failed to start logcat process");
            return;
        }
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(mLogcatProcess.getInputStream()))) {
            StringBuilder mLogcatChunk = new StringBuilder();
            while (!interrupted()) {
                String line = reader.readLine();
                synchronized (this) {
                    if (mClearChunk == true) {
                        mLogcatChunk.setLength(0);
                        mClearChunk = false;
                    }
                }
                if (line == null) {
                    break;
                }

                mLogcatChunk.append(line);

                if (CrashUtils.sEndofCrashPattern.matcher(line).matches()) {
                    addCrashes(CrashUtils.getAllCrashes(mLogcatChunk.toString()));
                    mLogcatChunk.setLength(0);
                } else if (mUploadPattern.matcher(line).matches()) {
                    upload();
                }
            }
        } catch (IOException | InterruptedException e) {
            mLogcatProcess.destroyForcibly();
        }
    }

    private synchronized boolean upload() throws InterruptedException {
        try {
            if (mTestName == null) {
                CLog.logAndDisplay(LogLevel.ERROR, "Attempted upload with no test name");
                return false;
            }
            CrashUtils.writeCrashReport(mTestName, mDeviceSerial, mCrashes);
        } catch (IOException | TimeoutException | SecurityException e) {
            CLog.logAndDisplay(LogLevel.ERROR, "Upload to device " + mDeviceSerial + " failed");
            CLog.logAndDisplay(LogLevel.ERROR, e.getMessage());
            return false;
        }
        return true;
    }

    private synchronized void addCrashes(List<Crash> crashes) {
        mCrashes.addAll(crashes);
    }

    @Override
    public void interrupt() {
        if (mLogcatProcess != null) {
            mLogcatProcess.destroyForcibly();
        }
        super.interrupt();
    }
}
