/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.backgroundactivity.common;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.storage.StorageManager;
import android.view.View;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;

public class TestService extends Service {
    static final String TAG = TestService.class.getName();
    private static final Icon EMPTY_ICON = Icon.createWithBitmap(
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888));
    private final ITestService mBinder = new MyBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    private class MyBinder extends ITestService.Stub {
        @Override
        public PendingIntent generatePendingIntent(ComponentName componentName) {
            Intent newIntent = new Intent();
            newIntent.setComponent(componentName);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return PendingIntent.getActivity(TestService.this, 0, newIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        @Override
        public PendingIntent generatePendingIntentBroadcast(ComponentName componentName) {
            Intent newIntent = new Intent();
            newIntent.setComponent(componentName);
            return PendingIntent.getBroadcast(TestService.this, 0, newIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        @Override
        public void startManageSpaceActivity() {
            final long token = Binder.clearCallingIdentity();
            try {
                StorageManager stm = getSystemService(StorageManager.class);
                PendingIntent pi = stm.getManageSpaceActivityIntent(getPackageName(), 0);
                pi.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
                throw new IllegalStateException("Unable to send PendingIntent");
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public TextClassification createTextClassification(PendingIntent pendingIntent) {
            TextClassification classification = new TextClassification.Builder()
                    .setText("my classified text")
                    .setEntityType(TextClassifier.TYPE_ADDRESS, 1.0f)
                    .addAction(new RemoteAction(EMPTY_ICON, "myAction",
                            "classifiedContentDescription", pendingIntent))
                    .setId("id")
                    .setExtras(new Bundle())
                    .build();
            return classification;
        }

        @Override
        public void sendByTextClassification(TextClassification classification) {
            View.OnClickListener onClickListener = classification.getOnClickListener();
            onClickListener.onClick(null);
        }

        @Override
        public void sendPendingIntent(PendingIntent pendingIntent, Bundle sendOptions) {
            try {
                pendingIntent.send(sendOptions);
            } catch (PendingIntent.CanceledException e) {
                throw new AssertionError(e);
            }
        }
    }
}
