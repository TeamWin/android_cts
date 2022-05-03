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

package android.os.cts.companiontestapp;

import android.companion.CompanionDeviceService;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class PermissionsTransferCompanionService extends CompanionDeviceService {

    public static final String EXTRA_MESSAGE_BYTES = "message_bytes";
    public static final String EXTRA_ASSOCIATION_ID = "association_id";
    public static final String EXTRA_HAS_RECEIVED_MESSAGE = "has_received_message";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean hasReceivedMessage = intent.getBooleanExtra(EXTRA_HAS_RECEIVED_MESSAGE, false);
        if(hasReceivedMessage) {
            byte[] data = intent.getByteArrayExtra(EXTRA_MESSAGE_BYTES);
            int associationId = intent.getIntExtra(EXTRA_ASSOCIATION_ID, -1);

            if (data == null || associationId == -1) {
                throw new IllegalArgumentException("Data is null or association id is not valid");
            }
            Toast.makeText(this, "calling dispatchMessageToSystem", Toast.LENGTH_SHORT).show();
            dispatchMessageToSystem(0, associationId, data);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onMessageDispatchedFromSystem(int messageId, int associationId,
            @NonNull byte[] message) {
        Toast.makeText(this, "onMessageDispatchedFromSystem", Toast.LENGTH_LONG).show();

        CommunicationManager.getInstance().sendData(message);
    }
}
