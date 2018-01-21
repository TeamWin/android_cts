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
package android.os.cts.batterysaving.app;

import static android.os.cts.batterysaving.common.Values.KEY_REQUEST_FOREGROUND;
import static android.os.cts.batterysaving.common.Values.getTestService;

import android.content.Context;
import android.content.Intent;
import android.os.cts.batterysaving.common.BaseCommunicationReceiver;
import android.os.cts.batterysaving.common.BatterySavingCtsCommon.Payload;
import android.os.cts.batterysaving.common.BatterySavingCtsCommon.Payload.TestServiceResponse;
import android.util.Log;

public class CommReceiver extends BaseCommunicationReceiver {
    private static final String TAG = "CommReceiver";

    @Override
    protected void handleRequest(
            Context context, Payload request, Payload.Builder responseBuilder) {
        if (request.hasTestServiceRequest()) {
            handleBatterySaverBgServiceRequest(context, request, responseBuilder);
        }
        return;
    }

    private void handleBatterySaverBgServiceRequest(Context context,
            Payload request, Payload.Builder responseBuilder) {
        final TestServiceResponse.Builder rb = TestServiceResponse.newBuilder();

        if (request.getTestServiceRequest().getClearLastIntent()) {
            // Request to clear the last intent to TestService.

            TestService.LastStartIntent.set(null);
            rb.setClearLastIntentAck(true);

        } else if (request.getTestServiceRequest().getGetLastIntent()) {
            // Request to return the last intent action that started TestService.

            final Intent intent = TestService.LastStartIntent.get();
            if (intent != null) {
                rb.setGetLastIntentAction(intent.getAction());
            }

        } else if (request.getTestServiceRequest().hasStartService()) {
            // Request to start TestService with a given action.

            final String action = request.getTestServiceRequest().getStartService().getAction();
            final boolean fg = request.getTestServiceRequest().getStartService().getForeground();

            final Intent intent = new Intent(action)
                    .setComponent(getTestService(context.getPackageName()))
                    .putExtra(KEY_REQUEST_FOREGROUND, fg);

            Log.d(TAG, "Starting service " + intent);

            if (fg) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            rb.setStartServiceAck(true);
        }

        responseBuilder.setTestServiceResponse(rb);
    }
}
