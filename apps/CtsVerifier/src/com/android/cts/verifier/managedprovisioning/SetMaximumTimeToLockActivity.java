/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.cts.verifier.managedprovisioning;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.concurrent.TimeUnit;

/**
 * Test class to verify policy transparency for maximum time to lock.
 */
public class SetMaximumTimeToLockActivity extends PassFailButtons.Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.time_to_lock);
        setPassFailButtonClickListeners();

        View setMaxTimeToLockButton = findViewById(R.id.set_max_time_to_lock);
        setMaxTimeToLockButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText timeEditText = (EditText) findViewById(R.id.time_to_lock);
                try {
                    // Convert time to milliseconds.
                    final int timeInSeconds = Integer.parseInt(timeEditText.getText().toString());
                    final int timeInMilliseconds =
                            (int) TimeUnit.MILLISECONDS.convert(timeInSeconds, TimeUnit.SECONDS);
                    startActivity(new Intent(SetMaximumTimeToLockActivity.this,
                            DeviceOwnerPositiveTestActivity.CommandReceiver.class)
                                    .putExtra(DeviceOwnerPositiveTestActivity.EXTRA_COMMAND,
                                            DeviceOwnerPositiveTestActivity
                                                    .COMMAND_SET_MAXIMUM_TIME_TO_LOCK)
                                    .putExtra(DeviceOwnerPositiveTestActivity.EXTRA_PARAMETER_1,
                                            timeInMilliseconds));
                } catch (Exception e) {
                    Toast.makeText(SetMaximumTimeToLockActivity.this, "Please enter a valid number",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        View goButton = findViewById(R.id.go_button);
        goButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            }
        });
    }
}
