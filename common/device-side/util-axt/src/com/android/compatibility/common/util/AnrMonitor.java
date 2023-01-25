/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.util.Log;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Queue;

import javax.annotation.concurrent.GuardedBy;

/**
 * Detects an ANR using `am monitor`.
 *
 * Use {@link #start} to create an instance and start monitoring.
 * Use {@link #waitForAnrAndReturnUptime} to wait for an "early ANR", and get the uptime of it.
 */
public class AnrMonitor implements AutoCloseable {
    private static final String TAG = "AnrMonitor";

    private final Instrumentation mInstrumentation;
    private final String mTargetProcess;
    private final Thread mThread;
    private volatile boolean mStop = false;

    /** Queue of detected "early ANR" uptime */
    @GuardedBy("mEventQueue")
    private final Queue<Long> mEventQueue = new ArrayDeque<>(0);

    private AnrMonitor(Instrumentation instrumentation, String targetProcess) {
        mInstrumentation = instrumentation;
        mTargetProcess = targetProcess;
        mThread = new Thread(this::threadMain, "AnrMonitor");
        mThread.setDaemon(true);
    }

    /**
     * Start monitoring a process for an "early ANR".
     */
    public static AnrMonitor start(Instrumentation instrumentation, String targetProcess) {
        final AnrMonitor instance =  new AnrMonitor(instrumentation, targetProcess);

        instance.run();

        return instance;
    }

    private void run() {
        mThread.start();
    }

    @Override
    public void close() {
        mStop = true;
        synchronized (mEventQueue) {
            mEventQueue.notifyAll();
        }
        if (mThread.isAlive()) {
            mThread.interrupt();
        }
    }

    /**
     * Return for an early-ANR event from the target process, and return the uptime of it.
     * Fails if an ANR doesn't happen before the timeout.
     */
    public long waitForAnrAndReturnUptime(long timeoutMillis) {
        try {
            final long timeoutUptime = SystemClock.uptimeMillis() + timeoutMillis;
            synchronized (mEventQueue) {
                for (;;) {
                    final long waitTime = timeoutUptime - SystemClock.uptimeMillis();
                    if (waitTime <= 0) {
                        // Timed out
                        Assert.fail("Timeout waiting for an ANR event from `am monitor`");
                    }
                    try {
                        mEventQueue.wait(waitTime);
                    } catch (InterruptedException e) {
                        continue;
                    }
                    final Long uptime = mEventQueue.poll();
                    if (uptime == null) {
                        // Empty
                        continue;
                    }
                    return uptime;
                }
            }
        } finally {
            close();
        }
    }

    private void threadMain() {
        ParcelFileDescriptor[] pfds = null;
        try {
            pfds = mInstrumentation.getUiAutomation()
                    .executeShellCommandRw("am monitor -s -c -p " + mTargetProcess);
            final ParcelFileDescriptor rfd = pfds[0];
            final FileInputStream rfs = new ParcelFileDescriptor.AutoCloseInputStream(rfd);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(rfs));

            Log.i(TAG, "am monitor started");

            // We don't use the writer.
            // We don't need to send "q" for finish. Closing the FD will finish the command.

            // Read from the `am monitor` command output...
            for (;;) {
                if (mStop) {
                    return;
                }
                final String line = reader.readLine();
                if (line == null || mStop) {
                    // command finished, or stopping
                    return;
                }
                if (!line.startsWith("** EARLY PROCESS NOT RESPONDING:")) {
                    Log.i(TAG, "Ignoring unrelated am monitor output: " + line);
                    // ignore
                    continue;
                }

                Log.i(TAG, "Early ANR detected: " + line);
                synchronized (mEventQueue) {
                    mEventQueue.add(SystemClock.uptimeMillis());
                    mEventQueue.notifyAll();
                }
            }
        } catch (Throwable th) {
            if (!mStop) {
                Log.e(TAG, "BG thread dying unexpectedly", th);
                Assert.fail("Unexpected exception detected: " + th.getMessage() + "\n"
                        + Log.getStackTraceString(th));
            }
        } finally {
            if (pfds != null) {
                closeQuietly(pfds[0]);
                closeQuietly(pfds[1]);
            }
            Log.i(TAG, "am monitor finished");
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
            }
        }
    }
}
