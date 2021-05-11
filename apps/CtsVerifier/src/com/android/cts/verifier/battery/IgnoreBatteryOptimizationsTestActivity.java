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

package com.android.cts.verifier.battery;

import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;

import com.android.cts.verifier.OrderedTestActivity;
import com.android.cts.verifier.R;

/** Test activity to check fulfillment of the ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent. */
public class IgnoreBatteryOptimizationsTestActivity extends OrderedTestActivity {
    private PowerManager mPowerManager;
    private UsageStatsManager mUsageStatsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setInfoResources(R.string.ibo_test, R.string.ibo_test_info, -1);

        mPowerManager = getSystemService(PowerManager.class);
        mUsageStatsManager = getSystemService(UsageStatsManager.class);
    }

    @Override
    protected Test[] getTests() {
        return new Test[]{
                mConfirmNotExemptedAtStart,
                mRequestExemption,
                mIntermediate,
                mConfirmIsExempted,
                mRemoveExemption,
                mIntermediate,
                mConfirmIsNotExempted
        };
    }

    private boolean isExempted() {
        return mUsageStatsManager.getAppStandbyBucket() == UsageStatsManager.STANDBY_BUCKET_EXEMPTED
                && mPowerManager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private final Test mConfirmNotExemptedAtStart = new Test(R.string.ibo_test_start_unexempt_app) {
        @Override
        protected void run() {
            super.run();

            if (!isExempted()) {
                succeed();
            }
        }

        @Override
        protected void onNextClick() {
            if (isExempted()) {
                Intent appInfoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                appInfoIntent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(appInfoIntent);
            } else {
                succeed();
            }
        }
    };

    private final Test mRequestExemption = new Test(R.string.ibo_exempt_app) {
        @Override
        protected void onNextClick() {
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            request.setData(Uri.parse("package:" + getPackageName()));
            startActivity(request);
            succeed();
        }
    };

    private final Test mIntermediate = new Test(R.string.ibo_next_to_confirm) {
        @Override
        protected void onNextClick() {
            succeed();
        }
    };

    private final Test mConfirmIsExempted = new Test(R.string.ibo_app_not_exempted) {
        @Override
        protected void run() {
            super.run();

            if (isExempted()) {
                succeed();
            }
        }
    };

    private final Test mRemoveExemption = new Test(R.string.ibo_unexempt_app) {
        @Override
        protected void run() {
            super.run();

            if (!isExempted()) {
                succeed();
            }
        }

        @Override
        protected void onNextClick() {
            if (isExempted()) {
                Intent appInfoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                appInfoIntent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(appInfoIntent);
            } else {
                succeed();
            }
        }
    };

    private final Test mConfirmIsNotExempted = new Test(R.string.ibo_app_is_exempted) {
        @Override
        protected void run() {
            super.run();

            if (!isExempted()) {
                succeed();
            } else {
                findViewById(R.id.btn_next).setVisibility(View.GONE);
            }
        }
    };
}
