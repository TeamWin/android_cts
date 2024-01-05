/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.nfc.cts;

import android.content.Intent;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.List;

public class CustomHostApduService extends CtsMyHostApduService {

    @Override
    public void processPollingFrames(List<Bundle> frames) {
        Intent intent = new Intent(CtsMyHostApduService.POLLING_LOOP_RECEIVED_ACTION);
        intent.putExtra(CtsMyHostApduService.SERVICE_NAME_EXTRA, this.getClass().getName());
        intent.putParcelableArrayListExtra(CtsMyHostApduService.POLLING_FRAMES_EXTRA,
                new ArrayList<Bundle>(frames));
        InstrumentationRegistry.getContext().sendOrderedBroadcast(intent, null);
    }
}
