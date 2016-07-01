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

package android.widget.cts.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.cts.MockURLSpanTestActivity;
import android.widget.cts.R;

import java.util.concurrent.CountDownLatch;

public final class MyAppWidgetProvider extends AppWidgetProvider {
    public static final String KEY_DISPLAYED_CHILD_INDEX =
            "MyAppWidgetProvider.displayedChildIndex";
    public static final String KEY_SHOW_NEXT = "MyAppWidgetProvider.showNext";
    public static final String KEY_SHOW_PREVIOUS = "MyAppWidgetProvider.showPrevious";
    private static CountDownLatch sCountDownLatch;

    private int mDisplayedChildIndex;
    private boolean mShowNext;
    private boolean mShowPrevious;

    public static void setCountDownLatch(CountDownLatch countDownLatch) {
        sCountDownLatch = countDownLatch;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mDisplayedChildIndex = intent.getIntExtra(KEY_DISPLAYED_CHILD_INDEX, -1);
        mShowNext = intent.getBooleanExtra(KEY_SHOW_NEXT, false);
        mShowPrevious = intent.getBooleanExtra(KEY_SHOW_PREVIOUS, false);

        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int appWidgetId = appWidgetIds[0];

        final Intent stackIntent = new Intent(context, MyAppWidgetService.class);
        stackIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        stackIntent.setData(Uri.parse(stackIntent.toUri(Intent.URI_INTENT_SCHEME)));

        final RemoteViews widgetAdapterView = new RemoteViews(context.getPackageName(),
                R.layout.remoteviews_adapter);
        widgetAdapterView.setRemoteAdapter(R.id.remoteViews_stack, stackIntent);
        widgetAdapterView.setEmptyView(R.id.remoteViews_stack, R.id.remoteViews_empty);

        if (mDisplayedChildIndex >= 0) {
            widgetAdapterView.setDisplayedChild(R.id.remoteViews_stack, mDisplayedChildIndex);
        }
        if (mShowNext) {
            widgetAdapterView.showNext(R.id.remoteViews_stack);
        }
        if (mShowPrevious) {
            widgetAdapterView.showPrevious(R.id.remoteViews_stack);
        }

        // Here we setup the a pending intent template. Individuals items of a collection
        // cannot setup their own pending intents, instead, the collection as a whole can
        // setup a pending intent template, and the individual items can set a fillInIntent
        // to create unique before on an item to item basis.
        Intent viewIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("ctstest://RemoteView/testWidget"));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        widgetAdapterView.setPendingIntentTemplate(R.id.remoteViews_stack, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, widgetAdapterView);

        sCountDownLatch.countDown();
    }

    @Override
    public void onEnabled(Context context) {
        sCountDownLatch.countDown();
    }
}
