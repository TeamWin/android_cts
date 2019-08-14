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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.RemoteCallback;

public class TestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RemoteCallback remoteCallback =
                getIntent().getParcelableExtra("remoteCallback");
        Bundle result = new Bundle();
        try {

            final String action = getIntent().getAction();
            switch (action) {
                case "android.appenumeration.cts.action.GET_PACKAGE_INFO":
                    final String packageName = getIntent().getStringExtra(
                            Intent.EXTRA_PACKAGE_NAME);
                    final PackageInfo pi = getPackageManager().getPackageInfo(packageName, 0);
                    result.putParcelable(Intent.EXTRA_RETURN_RESULT, pi);
                    break;
                default:
                    result.putSerializable("error",
                            new Exception("unknown action " + action));
                    break;
            }
        } catch (Exception failure) {
            result.putSerializable("error", failure);
        } finally {
            remoteCallback.sendResult(result);
        }
        finish();
    }
}