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

import static org.junit.Assert.assertEquals;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.cts.batterysaving.common.BatterySavingCtsCommon.Payload;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Base class for "CommReceiver"s that live in the app side and responds to request from the
 * test process.
 */
public abstract class BaseCommunicationReceiver extends BroadcastReceiver {
    private static final String TAG = "BaseCommunicationReceiver";

    @Override
    public final void onReceive(Context context, Intent intent) {
        assertEquals(CommUtils.ACTION_REQUEST, intent.getAction());

        // Parse the request.
        final Payload request;
        try {
            request = Payload.parseFrom(
                    intent.getByteArrayExtra(CommUtils.EXTRA_PAYLOAD));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Received invalid request", e);
        }
        Log.i(TAG, "Request received: " + request.toString());

        // Handle it and generate a response.
        final Payload.Builder responseBuilder = Payload.newBuilder();
        handleRequest(context, request, responseBuilder);

        final Payload response = responseBuilder.build();
        Log.i(TAG, "Response generated: " + response.toString());

        // Send back.
        final Bundle extras = new Bundle();
        extras.putByteArray(CommUtils.EXTRA_PAYLOAD, response.toByteArray());
        setResultExtras(extras);
    }

    protected abstract void handleRequest(Context context,
            Payload request, Payload.Builder responseBuilder);
}
