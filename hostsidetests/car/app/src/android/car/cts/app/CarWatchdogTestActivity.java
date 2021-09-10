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

package android.car.cts.app;

import android.app.Activity;
import android.car.Car;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.ResourceOveruseStats;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CarWatchdogTestActivity extends Activity {
    private static final String TAG = CarWatchdogTestActivity.class.getSimpleName();
    private static final long TEN_MEGABYTES = 1024 * 1024 * 10;
    private static final long TWO_HUNDRED_MEGABYTES = 1024 * 1024 * 200;
    private static final int DISK_DELAY_MS = 4000;
    private static final double EXCEED_WARN_THRESHOLD_PERCENT = 0.9;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private String mDumpMessage = "";
    private Car mCar;
    private File mTestDir;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private CarWatchdogManager mCarWatchdogManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCarApi();

        try {
            mTestDir =
                    Files.createTempDirectory(getFilesDir().toPath(), "testDir").toFile();
        } catch (IOException e) {
            setDumpMessage("ERROR: " + e.getMessage());
            finish();
            return;
        }

        mExecutor.execute(
                () -> {
                    synchronized (mLock) {
                        IoOveruseListener listener = addResourceOveruseListenerLocked();
                        try {
                            if (!writeToDisk(TEN_MEGABYTES)) {
                                finish();
                                return;
                            }

                            long remainingBytes = fetchRemainingBytesLocked(TEN_MEGABYTES);
                            if (remainingBytes == 0) {
                                finish();
                                return;
                            }

                            long bytesToExceedWarnThreshold =
                                    (long) Math.ceil(remainingBytes
                                            * EXCEED_WARN_THRESHOLD_PERCENT);

                            listener.setExpectedMinWrittenBytes(
                                    TEN_MEGABYTES + bytesToExceedWarnThreshold);

                            if (!writeToDisk(bytesToExceedWarnThreshold)) {
                                finish();
                                return;
                            }

                            listener.checkIsNotified();
                        } finally {
                            mCarWatchdogManager.removeResourceOveruseListener(listener);
                        }
                    }
                });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setDumpMessage("");
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.w(TAG, "onNewIntent: empty extras");
            return;
        }
        long remainingBytes = extras.getLong("bytes_to_kill");
        Log.d(TAG, "Bytes to kill: " + remainingBytes);
        if (remainingBytes == 0) {
            Log.w(TAG, "onNewIntent: remaining bytes is 0");
            return;
        }
        mExecutor.execute(() -> {
            IoOveruseListener listener = addResourceOveruseListenerLocked();
            try {
                listener.setExpectedMinWrittenBytes(TWO_HUNDRED_MEGABYTES);

                writeToDisk(remainingBytes);

                listener.checkIsNotified();
            } finally {
                mCarWatchdogManager.removeResourceOveruseListener(listener);
            }
        });
    }

    @Override
    public void dump(@NonNull String prefix, @Nullable FileDescriptor fd,
            @NonNull PrintWriter writer, @Nullable String[] args) {
        writer.println(String.format("%s: %s\n", TAG, mDumpMessage));
    }

    @Override
    protected void onDestroy() {
        if (mCar != null) {
            mCar.disconnect();
        }
        super.onDestroy();
    }

    private void initCarApi() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        mCar = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                this::initManagers);
    }

    private void initManagers(Car car, boolean ready) {
        synchronized (mLock) {
            if (ready) {
                mCarWatchdogManager = (CarWatchdogManager) car.getCarManager(
                        Car.CAR_WATCHDOG_SERVICE);
                Log.d(TAG, "initManagers() completed");
            } else {
                mCarWatchdogManager = null;
                Log.wtf(TAG, "mCarWatchdogManager set to be null");
            }
        }
    }

    @GuardedBy("mLock")
    private IoOveruseListener addResourceOveruseListenerLocked() {
        IoOveruseListener listener = new IoOveruseListener();
        mCarWatchdogManager.addResourceOveruseListener(getMainExecutor(),
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, listener);
        return listener;
    }

    private boolean writeToDisk(long bytes) {
        long writtenBytes;
        File uniqueFile = new File(mTestDir, Long.toString(System.nanoTime()));
        try (FileOutputStream fos = new FileOutputStream(uniqueFile)) {
            Log.d(TAG, "Attempting to write " + bytes + " bytes");
            writtenBytes = writeToFos(fos, bytes);
            if (writtenBytes < bytes) {
                setDumpMessage("ERROR: Failed to write '" + bytes
                        + "' bytes to disk. '" + writtenBytes
                        + "' bytes were successfully written, while '" + (bytes - writtenBytes)
                        + "' bytes were pending at the moment the exception occurred.");
                return false;
            }
            fos.getFD().sync();
            // Wait for the IO event to propagate to the system
            Thread.sleep(DISK_DELAY_MS);
            return true;
        } catch (IOException | InterruptedException e) {
            String reason = e instanceof IOException ? "I/O exception" : "Thread interrupted";
            setDumpMessage("ERROR: " + reason
                    + " after successfully writing to disk.\n\n" + e.getMessage());
            return false;
        }
    }

    private long writeToFos(FileOutputStream fos, long maxSize) {
        long writtenSize = 0;
        while (maxSize != 0) {
            int writeSize =
                    (int) Math.min(Integer.MAX_VALUE,
                            Math.min(Runtime.getRuntime().freeMemory(), maxSize));
            try {
                fos.write(new byte[writeSize]);
            } catch (InterruptedIOException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
                return writtenSize;
            } catch (IOException e) {
                e.printStackTrace();
                return writtenSize;
            }
            writtenSize += writeSize;
            maxSize -= writeSize;
            if (writeSize > 0) {
                Log.d(TAG, "writeSize:" + writeSize);
            }
        }
        Log.d(TAG, "Write completed.");
        return writtenSize;
    }

    @GuardedBy("mLock")
    private long fetchRemainingBytesLocked(long minWrittenBytes) {
        ResourceOveruseStats stats =
                mCarWatchdogManager.getResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        IoOveruseStats ioOveruseStats = stats.getIoOveruseStats();
        if (ioOveruseStats == null) {
            setDumpMessage(
                    "ERROR: No I/O overuse stats available for the application after writing "
                    + minWrittenBytes + " bytes.");
            return 0;
        }
        if (ioOveruseStats.getTotalBytesWritten() < minWrittenBytes) {
            setDumpMessage("ERROR: Actual written bytes to disk '" + minWrittenBytes
                    + "' don't match written bytes '" + ioOveruseStats.getTotalBytesWritten()
                    + "' returned by get request");
            return 0;
        }
        Log.d(TAG, ioOveruseStats.toString());
        /*
         * Check for foreground mode bytes given CtsCarApp is running in the foreground
         * during testing.
         */
        return ioOveruseStats.getRemainingWriteBytes().getForegroundModeBytes();
    }

    private void setDumpMessage(String message) {
        if (mDumpMessage.startsWith("ERROR:")) {
            mDumpMessage += ", " + message;
        } else {
            mDumpMessage = message;
        }
    }

    private final class IoOveruseListener
            implements CarWatchdogManager.ResourceOveruseListener {
        private static final int NOTIFICATION_DELAY_MS = 5000;

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private boolean mNotificationReceived;

        private long mExpectedMinWrittenBytes;

        @Override
        public void onOveruse(ResourceOveruseStats resourceOveruseStats) {
            synchronized (mLock) {
                mNotificationReceived = true;
                mLock.notifyAll();
            }
            Log.d(TAG, resourceOveruseStats.toString());
            if (resourceOveruseStats.getIoOveruseStats() == null) {
                setDumpMessage(
                        "ERROR: No I/O overuse stats reported for the application in the overuse "
                        + "notification.");
                return;
            }
            long reportedWrittenBytes =
                    resourceOveruseStats.getIoOveruseStats().getTotalBytesWritten();
            if (reportedWrittenBytes < mExpectedMinWrittenBytes) {
                setDumpMessage("ERROR: Actual written bytes to disk '" + mExpectedMinWrittenBytes
                        + "' don't match written bytes '" + reportedWrittenBytes
                        + "' reported in overuse notification");
                return;
            }
            long foregroundModeBytes =
                    resourceOveruseStats.getIoOveruseStats().getRemainingWriteBytes()
                            .getForegroundModeBytes();
            // Dump the resource overuse stats
            setDumpMessage("INFO: --Notification-- foregroundModeBytes = " + foregroundModeBytes);
        }

        public void setExpectedMinWrittenBytes(long expectedMinWrittenBytes) {
            mExpectedMinWrittenBytes = expectedMinWrittenBytes;
        }

        public void checkIsNotified() {
            synchronized (mLock) {
                long now = SystemClock.uptimeMillis();
                long deadline = now + NOTIFICATION_DELAY_MS;
                while (!mNotificationReceived && now < deadline) {
                    try {
                        mLock.wait(deadline - now);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        continue;
                    } finally {
                        now = SystemClock.uptimeMillis();
                    }
                    break;
                }
                if (!mNotificationReceived) {
                    setDumpMessage("ERROR: I/O Overuse notification not received.");
                }
            }
        }
    }
}
