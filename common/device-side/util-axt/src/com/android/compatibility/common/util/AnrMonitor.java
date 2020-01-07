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

package com.android.compatibility.common.util;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * A utility class interact with "am monitor"
 */
public final class AnrMonitor {
    private static final String TAG = "AnrMonitor";
    private static final String WAIT_FOR_ANR = "Waiting after early ANR...  available commands:";
    private static final String MONITOR_READY = "Monitoring activity manager...  available commands:";

    /**
     * Command for the {@link #sendCommand}: continue the process
     */
    public static final String CMD_CONTINUE = "k";

    /**
     * Command for the {@link #sendCommand}: kill the process
     */
    public static final String CMD_KILL = "k";

    /**
     * Command for the {@link #sendCommand}: quit the monitor
     */
    public static final String CMD_QUIT = "q";

    private final Instrumentation mInstrumentation;
    private final ParcelFileDescriptor mReadFd;
    private final FileInputStream mReadStream;
    private final BufferedReader mReadReader;
    private final ParcelFileDescriptor mWriteFd;
    private final FileOutputStream mWriteStream;
    private final PrintWriter mWritePrinter;
    private final Thread mReaderThread;

    private final ArrayList<String> mPendingLines = new ArrayList<>();

    /**
     * Construct an instance of this class.
     */
    public AnrMonitor(final Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        ParcelFileDescriptor[] pfds = instrumentation.getUiAutomation()
                .executeShellCommandRw("am monitor");
        mReadFd = pfds[0];
        mReadStream = new ParcelFileDescriptor.AutoCloseInputStream(mReadFd);
        mReadReader = new BufferedReader(new InputStreamReader(mReadStream));
        mWriteFd = pfds[1];
        mWriteStream = new ParcelFileDescriptor.AutoCloseOutputStream(mWriteFd);
        mWritePrinter = new PrintWriter(new BufferedOutputStream(mWriteStream));
        mReaderThread = new ReaderThread();
        mReaderThread.start();
        waitFor(3600000L, MONITOR_READY);
    }

    /**
     * Wait for the ANR.
     *
     * @return true if it was successful, false if it got a timeout.
     */
    public boolean waitFor(final long timeout) {
        return waitFor(timeout, WAIT_FOR_ANR);
    }

    /**
     * Wait for the given output.
     *
     * @return true if it was successful, false if it got a timeout.
     */
    private boolean waitFor(final long timeout, final String expected) {
        final long waitUntil = SystemClock.uptimeMillis() + timeout;
        synchronized (mPendingLines) {
            while (true) {
                while (mPendingLines.size() == 0) {
                    final long now = SystemClock.uptimeMillis();
                    if (now >= waitUntil) {
                        Log.d(TAG, "Timed out waiting for next line: expected=" + expected);
                        return false;
                    }
                    try {
                        mPendingLines.wait(waitUntil - now);
                    } catch (InterruptedException e) {
                    }
                }
                final String line = mPendingLines.remove(0);
                if (TextUtils.equals(line, expected)) {
                    return true;
                }
            }
        }
    }

    /**
     * Finish the monitor and close the streams.
     */
    public void finish() {
        sendCommand(CMD_QUIT);
        try {
            mWriteStream.close();
        } catch (IOException e) {
        }
        try {
            mReadStream.close();
        } catch (IOException e) {
        }
    }

    /**
     * Send the command to the interactive command.
     *
     * @param cmd could be {@link #CMD_KILL}, {@link #CMD_QUIT} or {@link #CMD_CONTINUE}.
     */
    public void sendCommand(final String cmd) {
        mWritePrinter.println(cmd);
        mWritePrinter.flush();
    }

    private final class ReaderThread extends Thread {
        @Override
        public void run() {
            try {
                String line;
                while ((line = mReadReader.readLine()) != null) {
                    Log.i(TAG, "debug: " + line);
                    synchronized (mPendingLines) {
                        mPendingLines.add(line);
                        mPendingLines.notifyAll();
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed reading", e);
            }
        }
    }
}
