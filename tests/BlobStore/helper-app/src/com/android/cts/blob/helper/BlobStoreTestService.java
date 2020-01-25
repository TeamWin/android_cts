/*
 * Copyright 2020 The Android Open Source Project
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
package com.android.cts.blob.helper;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import android.app.Service;
import android.app.blob.BlobHandle;
import android.app.blob.BlobStoreManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.os.Process;

import com.android.cts.blob.ICommandReceiver;

import java.io.IOException;

public class BlobStoreTestService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return new CommandReceiver();
    }

    private class CommandReceiver extends ICommandReceiver.Stub {
        public void acquireLease(BlobHandle blobHandle) {
            final BlobStoreManager blobStoreManager = getSystemService(
                    BlobStoreManager.class);
            try {
                blobStoreManager.acquireLease(blobHandle, "Test description");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public void releaseLease(BlobHandle blobHandle) {
            final BlobStoreManager blobStoreManager = getSystemService(
                    BlobStoreManager.class);
            try {
                blobStoreManager.releaseLease(blobHandle);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public StorageStats queryStatsForPackage() {
            final StorageStatsManager storageStatsManager = getSystemService(
                    StorageStatsManager.class);
            try {
                return storageStatsManager
                        .queryStatsForPackage(UUID_DEFAULT, getPackageName(), getUser());
            } catch (IOException | NameNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        public StorageStats queryStatsForUid() {
            final StorageStatsManager storageStatsManager = getSystemService(
                    StorageStatsManager.class);
            try {
                return storageStatsManager
                        .queryStatsForUid(UUID_DEFAULT, Process.myUid());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
