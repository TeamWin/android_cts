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
 * limitations under the License
 */
package com.android.server.cts.device.batterystats;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Used by BatteryStatsValidationTest.
 */
@RunWith(AndroidJUnit4.class)
public class BatteryStatsWifiScanTests {
    private CountDownLatch mResultsReceivedSignal;
    private WifiManager mWifiManager;
    private Context mContext;
    private boolean mHasFeature;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mHasFeature = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
        if (!mHasFeature) {
            return;
        }
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mResultsReceivedSignal = new CountDownLatch(1);
        registerReceiver(mContext, mResultsReceivedSignal);
    }

    @Test
    public void testBackgroundScan() throws Exception {
        if (!mHasFeature) {
            return;
        }
        mWifiManager.startScan();
        mResultsReceivedSignal.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void testForegroundScan() throws Exception {
        if (!mHasFeature) {
            return;
        }
        WiFiScanActivity.mResultsReceivedSignal = mResultsReceivedSignal;
        Intent intent = new Intent(mContext, WiFiScanActivity.class);
        mContext.startActivity(intent);
        mResultsReceivedSignal.await(10, TimeUnit.SECONDS);
        WiFiScanActivity.instance.finish();
    }

    static void registerReceiver(Context ctx, CountDownLatch onReceiveLatch) {
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveLatch.countDown();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    public static class WiFiScanActivity extends Activity {
        private static CountDownLatch mResultsReceivedSignal;
        private static Activity instance;

        @Override
        public void onCreate(Bundle bundle) {
            instance = this;
            super.onCreate(bundle);
            BatteryStatsWifiScanTests.registerReceiver(this, mResultsReceivedSignal);
            getSystemService(WifiManager.class).startScan();
        }
    }
}
