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
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.storage.StorageManager;
import android.util.Log;
import android.view.View;
import android.view.textclassifier.TextClassification;

import java.util.UUID;

public class TestService extends Service {
    static final String TAG = TestService.class.getName();
    private final ITestService mBinder = new MyBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    private class MyBinder extends ITestService.Stub {
        @Override
        public PendingIntent generatePendingIntent(ComponentName componentName,
                Bundle createOptions) {
            Intent newIntent = new Intent();
            newIntent.setComponent(componentName);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            newIntent.setIdentifier(UUID.randomUUID().toString());
            return PendingIntent.getActivity(TestService.this, 0, newIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                    createOptions);
        }

        @Override
        public PendingIntent generatePendingIntentBroadcast(ComponentName componentName) {
            Intent newIntent = new Intent();
            newIntent.setComponent(componentName);
            newIntent.setIdentifier(UUID.randomUUID().toString());
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
            } catch (Exception e) {
                Log.e(TAG, "startManageSpaceActivity failed", e);
                throw new IllegalStateException("Unable to send PendingIntent");
            } finally {
                Binder.restoreCallingIdentity(token);
            }
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
            } catch (Exception e) {
                Log.e(TAG, "sendPendingIntent failed", e);
                throw new AssertionError(e);
            }
        }

        @Override
        public void startActivityIntent(Intent intent) {
            try {
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "startActivityIntent failed", e);
                throw new AssertionError(e);
            }
        }
    }
}
