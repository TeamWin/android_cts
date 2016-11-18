/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Date;

public class AlarmIntentTest extends PassFailButtons.Activity {

    // Just for cancelling alarm
    private PendingIntent mAlarmIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarmintent);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.alarmintenttest_title, R.string.alarmintenttest_desc, -1);
        mAlarmIntent = PendingIntent.getBroadcast(this, 0, new Intent("alarm_going_off"), 0);

        Button setAlarm = (Button) findViewById(R.id.set_alarm);
        setAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImageView view1 = (ImageView) findViewById(R.id.img1);
                setAlarm();
                view1.setImageResource(R.drawable.fs_good);
            }
        });

        Button cancelAlarm = (Button) findViewById(R.id.cancel_alarm);
        cancelAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImageView view2 = (ImageView) findViewById(R.id.img2);
                ImageView view3 = (ImageView) findViewById(R.id.img3);
                cancelAlarm();
                view2.setImageResource(R.drawable.fs_good);
                view3.setImageResource(R.drawable.fs_good);
            }
        });
    }

    public void setAlarm() {
        PendingIntent editAlarmIntent = PendingIntent.getActivity(this, 0, new Intent().
                setClassName("android", "com.android.internal.app.PlatLogoActivity"), 0);

        // Set the alarm
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                new Date().getTime() + 1000 * 60 * 5, editAlarmIntent), mAlarmIntent);
        // Alarm intent(mAlarmIntent) is irrelevant here, just for cancelling
    }

    public void cancelAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(mAlarmIntent);
    }
}
