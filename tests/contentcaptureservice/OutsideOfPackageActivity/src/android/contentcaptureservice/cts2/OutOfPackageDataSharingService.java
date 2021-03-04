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

package android.contentcaptureservice.cts2;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.contentcapture.ContentCaptureManager;
import android.contentcaptureservice.cts.IOutOfPackageDataSharingService;

import androidx.annotation.Nullable;

public class OutOfPackageDataSharingService extends Service {

    private final IBinder mBinder = new IOutOfPackageDataSharingService.Stub() {
        @Override
        public boolean isContentCaptureManagerAvailable() {
            ContentCaptureManager manager =
                    getApplicationContext().getSystemService(ContentCaptureManager.class);
            return manager != null && manager.isContentCaptureEnabled();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
