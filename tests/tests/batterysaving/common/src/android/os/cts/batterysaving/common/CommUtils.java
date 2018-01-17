/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.os.cts.batterysaving.common;

import static android.os.cts.batterysaving.common.Values.getCommReceiver;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CommUtils {
    private static final String TAG = "CommUtils";

    static final String ACTION_REQUEST = "ACTION_REQUEST";
    static final String EXTRA_PAYLOAD = "EXTRA_PAYLOAD";

    static Handler sMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Sends a request to the "CommReceiver" in a given package, and return the response.
     */
    public static BatterySavingCtsCommon.Payload sendRequest(
            String targetPackage, BatterySavingCtsCommon.Payload request)
            throws Exception {

        // Create a request intent.
        Log.i(TAG, "Request generated: " + request.toString());

        final Intent requestIntent = new Intent(ACTION_REQUEST)
                .setComponent(getCommReceiver(targetPackage))
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(EXTRA_PAYLOAD, request.toByteArray());

        // Send it.
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Bundle> responseBundle = new AtomicReference<>();

        InstrumentationRegistry.getContext().sendOrderedBroadcast(
                requestIntent, null, new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        responseBundle.set(getResultExtras(false));
                        latch.countDown();
                    }
                }, sMainHandler, 0, null, null);

        // Wait for a reply and check it.
        assertTrue("Didn't receive broadcast result.",
                latch.await(60, TimeUnit.SECONDS));

        assertNotNull("Didn't receive result extras", responseBundle.get());

        final byte[] resultPayload = responseBundle.get().getByteArray(EXTRA_PAYLOAD);
        assertNotNull("Didn't receive result payload", resultPayload);

        Log.i(TAG, "Response received: " + resultPayload.toString());

        return BatterySavingCtsCommon.Payload.parseFrom(resultPayload);
    }
}
