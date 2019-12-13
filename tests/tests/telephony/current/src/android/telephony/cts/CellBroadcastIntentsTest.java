/*
 * Copyright (C) 2019 The Android Open Source Project
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


package android.telephony.cts;

import static junit.framework.Assert.fail;

import android.content.Intent;
import android.os.UserHandle;
import android.telephony.CellBroadcastIntents;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

public class CellBroadcastIntentsTest {

    private static final String TEST_ACTION = "test_action";

    @Test
    public void testGetIntentForBackgroundReceivers() {
        try {
            CellBroadcastIntents.sendOrderedBroadcastForBackgroundReceivers(
                    InstrumentationRegistry.getContext(), UserHandle.ALL, new Intent(TEST_ACTION),
                    null, null, null, null, 0, null, null);
        } catch (SecurityException e) {
            // expected
            return;
        }
        fail();
    }
}
