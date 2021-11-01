/*
 * Copyright 2021 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.Tuner.Result;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterConfiguration;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.SectionSettingsWithTableInfo;
import android.media.tv.tuner.filter.Settings;
import android.media.tv.tuner.filter.SharedFilter;
import android.media.tv.tuner.filter.SharedFilterCallback;
import android.media.tv.tuner.filter.TsFilterConfiguration;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.os.IBinder;
import android.util.Log;
import java.util.List;
import java.util.concurrent.Executor;

public class SharedFilterTestService extends Service {
    private static final String TAG = "SharedFilterTestService";
    private Context mContext;
    private Tuner mTuner;
    private Filter mFilter;
    private String mSharedFilterToken;
    private boolean mTuning;

    @Override
    public void onCreate() {
        mContext = this;
        mTuner = new Tuner(mContext, null, 100);
    }

    @Override
    public void onDestroy() {
        mTuner.close();
        mTuner = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String command = intent.getStringExtra("CMD");
        if (command.equals("start")) {
            mFilter = TunerTest.createFilterForSharedFilterTest(
                    mTuner, getExecutor(), getFilterCallback());

            // Tune a frontend before start the filter
            List<FrontendInfo> infos = mTuner.getAvailableFrontendInfos();
            mTuner.tune(TunerTest.createFrontendSettings(infos.get(0)));
            mTuning = true;

            mSharedFilterToken = mFilter.createSharedFilter();

            Intent tokenIntent = new Intent();
            tokenIntent.setAction("android.media.tv.tuner.cts.SHARED_FILTER_TOKEN");
            tokenIntent.putExtra("TOKEN", mSharedFilterToken);
            sendBroadcast(tokenIntent);
        } else if (command.equals("close")) {
            if (mTuning) {
                mTuner.cancelTuning();
                mTuning = false;
            }
            if (mFilter != null) {
                mFilter.close();
            }
            mFilter = null;
        } else if (command.equals("release")) {
            if (mTuning) {
                mTuner.cancelTuning();
                mTuning = false;
            }
            if (mFilter != null && mSharedFilterToken != null) {
                mFilter.releaseSharedFilter(mSharedFilterToken);
            }
        } else if (command.equals("share")) {
            final String token = intent.getStringExtra("TOKEN");
            SharedFilter filter = Tuner.openSharedFilter(
                    mContext, token, getExecutor(), getSharedFilterCallback());
            filter.start();
            filter.flush();
            filter.read(new byte[3], 0, 3);
            filter.stop();
            filter.close();
            filter = null;
            Intent closedIntent = new Intent();
            closedIntent.setAction("android.media.tv.tuner.cts.SHARED_FILTER_CLOSED");
            sendBroadcast(closedIntent);
        }
        return Service.START_NOT_STICKY;
    }

    private FilterCallback getFilterCallback() {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(Filter filter, int status) {}
        };
    }

    private SharedFilterCallback getSharedFilterCallback() {
        return new SharedFilterCallback() {
            @Override
            public void onFilterEvent(SharedFilter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(SharedFilter filter, int status) {}
        };
    }

    private Executor getExecutor() { return Runnable::run; }
}
