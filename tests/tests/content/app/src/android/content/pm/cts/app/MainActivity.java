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

package android.content.pm.cts.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.os.RemoteCallback;

/**
 * Helper activity to listen for Incremental package state change broadcasts.
 */
public class MainActivity extends Activity {
    private IncrementalStatesBroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        final RemoteCallback remoteCallback = intent.getParcelableExtra("callback");
        final IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_STARTABLE);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_LOADED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(targetPackageName, PatternMatcher.PATTERN_LITERAL);
        mReceiver = new IncrementalStatesBroadcastReceiver(targetPackageName, remoteCallback);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private class IncrementalStatesBroadcastReceiver extends BroadcastReceiver {
        private final String mPackageName;
        private final RemoteCallback mCallback;
        IncrementalStatesBroadcastReceiver(String packageName, RemoteCallback callback) {
            mPackageName = packageName;
            mCallback = callback;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final Bundle extras = intent.getExtras();
            final String packageName = extras.getString(Intent.EXTRA_PACKAGE_NAME, "");
            if (!mPackageName.equals(packageName)) {
                return;
            }
            Bundle result = new Bundle();
            result.putString("intent", intent.getAction());
            result.putBundle("extras", intent.getExtras());
            mCallback.sendResult(result);
        }
    }
}
