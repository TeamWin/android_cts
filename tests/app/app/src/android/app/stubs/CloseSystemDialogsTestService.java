/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.stubs;

import android.app.Service;
import android.app.stubs.shared.ICloseSystemDialogsTestsService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * This is a bound service used in conjunction with CloseSystemDialogsTest.
 */
public class CloseSystemDialogsTestService extends Service {
    private final ICloseSystemDialogsTestsService mBinder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    private class Binder extends ICloseSystemDialogsTestsService.Stub {
        private final Context mContext = CloseSystemDialogsTestService.this;

        @Override
        public void sendCloseSystemDialogsBroadcast() {
            mContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
    }
}
