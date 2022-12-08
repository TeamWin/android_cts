/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.wearable.cts;

import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.wearable.WearableSensingService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** An implementation of {@link WearableSensingService} for CTS testing. */
public class CtsWearableSensingService extends WearableSensingService {
    public static final int INITIAL_STATUS_TO_CONSUME = -1;

    private static final String TAG = "CtsWearableSensingService";
    private static final String FAKE_APP_PACKAGE = "foo.bar.baz";

    private static CountDownLatch sRespondLatch = new CountDownLatch(1);
    private static Consumer<Integer> sStatusConsumer;
    private static int sStatusToConsume;
    private static ParcelFileDescriptor sParcelFileDescriptor;
    private static SharedMemory sSharedMemory;
    private static PersistableBundle sData;

    @Override
    public void onDataStreamProvided(ParcelFileDescriptor parcelFileDescriptor,
            Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onDataStreamProvided");
        sParcelFileDescriptor = parcelFileDescriptor;
        sStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onDataProvided(PersistableBundle data, SharedMemory sharedMemory,
            Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onDataProvided");
        sData = data;
        sSharedMemory = sharedMemory;
        sStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    public static void whenCallbackTriggeredRespondWithStatus(int status) {
        Log.w(TAG, "whenCallbackTriggeredRespondWithStatus");
        sStatusToConsume = status;
    }

    public static void awaitResult() {
        try {
            if (!sRespondLatch.await(3000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("CtsWearableSensingService"
                        + " timed out while expecting a call.");
            }
            sStatusConsumer.accept(sStatusToConsume);
            // reset for next
            sRespondLatch = new CountDownLatch(1);
            sStatusToConsume = INITIAL_STATUS_TO_CONSUME;
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for serviceStatus.");
        }
    }


    public static PersistableBundle getData() {
        return sData;
    }

    public static ParcelFileDescriptor getParcelFileDescriptor() {
        return sParcelFileDescriptor;
    }
}
