/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.device.batterystats;

import static org.junit.Assert.assertTrue;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Used by BatteryStatsValidationTest.
 */
@RunWith(AndroidJUnit4.class)
public class BatteryStatsWifiTransferTests extends BatteryStatsDeviceTestBase {
    private static final String TAG = "BatteryStatsWifiTransferTests";

    /** String extra to instruct whether to download or upload. */
    private static final String EXTRA_ACTION = "action";
    private static final String ACTION_DOWNLOAD = "download";
    private static final String ACTION_UPLOAD = "upload";

    /** Action to notify the test that wifi transfer is complete. */
    private static final String ACTION_TRANSFER_COMPLETE = "transfer_complete";

    /** String extra of any error encountered during wifi transfer. */
    private static final String EXTRA_TRANSFER_ERROR = "transfer_error";

    private static final int READ_BUFFER_SIZE = 4096;

    /** Server to send requests to. */
    private static final String SERVER_URL = "https://developer.android.com/index.html";

    private Context mContext;
    private CountDownLatch mResultsReceivedSignal;
    private Intent mTransferService;
    private boolean mHasFeature;
    private volatile String mError;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mHasFeature = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
        if (!mHasFeature) {
            return;
        }
        mResultsReceivedSignal = new CountDownLatch(1);
        mTransferService = new Intent(mContext, TransferService.class);
        registerReceiver(mContext, mResultsReceivedSignal);
    }

    @Test
    public void testBackgroundDownload() throws Exception {
        doBackgroundTransfer(ACTION_DOWNLOAD);
    }

    @Test
    public void testForegroundDownload() throws Exception {
        doForegroundTransfer(ACTION_DOWNLOAD);
    }

    @Test
    public void testBackgroundUpload() throws Exception {
        doBackgroundTransfer(ACTION_UPLOAD);
    }

    @Test
    public void testForegroundUpload() throws Exception {
        doForegroundTransfer(ACTION_UPLOAD);
    }

    private void doBackgroundTransfer(String action) throws Exception {
        if (!mHasFeature) {
            return;
        }
        mTransferService.putExtra(EXTRA_ACTION, action);
        mContext.startService(mTransferService);
        mResultsReceivedSignal.await(10, TimeUnit.SECONDS);
        assertTrue("Got error: " + mError, mError == null);
    }

    private void doForegroundTransfer(String action) throws Exception {
        if (!mHasFeature) {
            return;
        }
        mTransferService.putExtra(EXTRA_ACTION, action);
        Notification notification =
            new Notification.Builder(mContext, "Wifi Transfer Foreground Service")
                    .setContentTitle("Wifi Transfer Foreground")
                    .setContentText("Wifi Transfer Foreground")
                    .setSmallIcon(android.R.drawable.ic_secure)
                    .build();
        mContext.getSystemService(NotificationManager.class).startServiceInForeground(mTransferService,
                1, notification);

        mResultsReceivedSignal.await(10, TimeUnit.SECONDS);
        assertTrue("Got error: " + mError, mError == null);
    }

    private void registerReceiver(Context ctx, CountDownLatch onReceiveLatch) {
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mError = intent.getStringExtra(EXTRA_TRANSFER_ERROR);
                onReceiveLatch.countDown();
            }
        }, new IntentFilter(ACTION_TRANSFER_COMPLETE));
    }

    public static class TransferService extends IntentService {
        public TransferService() {
            this(TransferService.class.getName());
        }

        public TransferService(String name) {
            super(name);
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            String error = null;
            switch (intent.getStringExtra(EXTRA_ACTION)) {
                case ACTION_DOWNLOAD:
                    error = download();
                    break;
                case ACTION_UPLOAD:
                    error = upload();
                    break;
                default:
                    error = "Unknown action " + intent.getStringExtra(EXTRA_ACTION);
            }
            Intent localIntent = new Intent(ACTION_TRANSFER_COMPLETE);
            localIntent.putExtra(EXTRA_TRANSFER_ERROR, error);
            sendBroadcast(localIntent);
       }

       private String download() {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(SERVER_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setUseCaches(false);
                conn.setRequestProperty("Accept-Encoding", "identity"); // Disable compression.

                InputStream in = new BufferedInputStream(conn.getInputStream());
                byte[] data = new byte[READ_BUFFER_SIZE];

                int total = 0;
                int count;
                while ((count = in.read(data)) != -1) {
                    total += count;
                }
                Log.i(TAG, Integer.toString(total));
            } catch (IOException e) {
                 return "Caught exception";
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            return null;
       }

       private String upload() {
            HttpURLConnection conn = null;
            try {
                // Append a long query string.
                char[] queryChars = new char[2*1024];
                Arrays.fill(queryChars, 'a');
                URL url = new URL(SERVER_URL + "?" + new String(queryChars));
                conn = (HttpURLConnection) url.openConnection();
                InputStream in = conn.getInputStream();
                in.close();
            } catch (IOException e) {
                return "IO exception";
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            return null;
       }
    }
}
