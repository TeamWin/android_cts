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

package android.cts.tagging.sdk30memtag;

import android.app.Service;
import android.content.Intent;
import android.cts.tagging.Utils;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class CrashAppZygoteService extends Service {
    private static String TAG = CrashAppZygoteService.class.getName();

    private Messenger mClient;

    class IncomingHandler extends Handler {
        private CrashAppZygoteService mService;

        IncomingHandler(CrashAppZygoteService service) {
            mService = service;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != ServiceRunnerActivity.MSG_START_TEST) {
                Log.e(TAG, "CrashAppZygoteService received bad message: " + msg.what);
                super.handleMessage(msg);
                return;
            }
            mService.mClient = msg.replyTo;
            mService.startTests();
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    public CrashAppZygoteService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private void notifyClientOfResult(int result) {
        try {
          mClient.send(
              Message.obtain(null, ServiceRunnerActivity.MSG_NOTIFY_TEST_RESULT, result, 0, null));
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send message back to client.");
        }
    }

    private void notifyClientOfSuccess() {
      notifyClientOfResult(ServiceRunnerActivity.RESULT_TEST_SUCCESS);
    }

    private void startTests() {
      Utils.accessMistaggedPointer();
      notifyClientOfSuccess();
    }
}
