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

package android.appenumeration.cts.query;

import static android.content.Intent.EXTRA_RETURN_RESULT;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import java.util.Random;

public class TestActivity extends Activity {

    SparseArray<RemoteCallback> callbacks = new SparseArray<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        RemoteCallback remoteCallback = intent.getParcelableExtra("remoteCallback");
        Bundle result = new Bundle();
        final String action = intent.getAction();
        final String packageName = intent.getStringExtra(
                Intent.EXTRA_PACKAGE_NAME);
        if ("android.appenumeration.cts.action.GET_PACKAGE_INFO".equals(action)) {
            sendPackageInfo(remoteCallback, packageName);
        } else if ("android.appenumeration.cts.action.START_FOR_RESULT".equals(action)) {
            int requestCode = RESULT_FIRST_USER + callbacks.size();
            callbacks.put(requestCode, remoteCallback);
            startActivityForResult(
                    new Intent("android.appenumeration.cts.action.SEND_RESULT").setComponent(
                            new ComponentName(packageName, getClass().getCanonicalName())),
                    requestCode);
            // don't send anything... await result callback
        } else if ("android.appenumeration.cts.action.SEND_RESULT".equals(action)) {
            try {
                setResult(RESULT_OK,
                        getIntent().putExtra(
                                Intent.EXTRA_RETURN_RESULT,
                                getPackageManager().getPackageInfo(getCallingPackage(), 0)));
            } catch (PackageManager.NameNotFoundException e) {
                setResult(RESULT_FIRST_USER, new Intent().putExtra("error", e));
            }
            finish();
        } else {
            sendError(remoteCallback, new Exception("unknown action " + action));
        }
    }

    private void sendError(RemoteCallback remoteCallback, Exception failure) {
        Bundle result = new Bundle();
        result.putSerializable("error", failure);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendPackageInfo(RemoteCallback remoteCallback, String packageName) {
        final PackageInfo pi;
        try {
            pi = getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            sendError(remoteCallback, e);
            return;
        }
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, pi);
        remoteCallback.sendResult(result);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final RemoteCallback remoteCallback = callbacks.get(requestCode);
        if (resultCode != RESULT_OK) {
            Exception e = (Exception) data.getSerializableExtra("error");
            sendError(remoteCallback, e == null ? new Exception("Result was " + resultCode) : e);
            return;
        }
        final Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, data.getParcelableExtra(EXTRA_RETURN_RESULT));
        remoteCallback.sendResult(result);
        finish();
    }
}