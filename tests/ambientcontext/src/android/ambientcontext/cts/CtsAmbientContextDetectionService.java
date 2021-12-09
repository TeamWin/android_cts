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
package android.ambientcontext.cts;

import android.app.ambientcontext.AmbientContextEvent;
import android.app.ambientcontext.AmbientContextEventRequest;
import android.app.ambientcontext.AmbientContextEventResponse;
import android.service.ambientcontext.AmbientContextDetectionService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public class CtsAmbientContextDetectionService
        extends AmbientContextDetectionService {
    private static final String TAG = "CtsTestAmbientContextEventProviderService";

    private static Consumer<AmbientContextEventResponse> sConsumer;
    private static CountDownLatch sRespondLatch = new CountDownLatch(1);

    @Override
    public void onStartDetection(AmbientContextEventRequest request, String packageName,
            Consumer<AmbientContextEventResponse> consumer) {
        sConsumer = consumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onStopDetection(String packageName) {
    }

    public static void reset() {
        sConsumer = null;
    }

    public static void respondSuccess(AmbientContextEvent event) {
        if (sConsumer != null) {
            AmbientContextEventResponse response = new AmbientContextEventResponse.Builder()
                    .setStatusCode(AmbientContextEventResponse.STATUS_SUCCESS)
                    .addEvent(event)
                    .build();
            sConsumer.accept(response);
        }
        reset();
    }

    public static void respondFailure(int status) {
        if (sConsumer != null) {
            AmbientContextEventResponse response = new AmbientContextEventResponse.Builder()
                    .setStatusCode(status)
                    .build();
            sConsumer.accept(response);
        }
        reset();
    }

    public static boolean hasPendingRequest() {
        return sConsumer != null;
    }

    public static void onReceivedResponse() {
        try {
            if (!sRespondLatch.await(3000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("CtsTestAmbientContextEventProviderService"
                        + " timed out while expecting a call.");
            }
            //reset for next
            sRespondLatch = new CountDownLatch(1);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for response.");
        }
    }
}
