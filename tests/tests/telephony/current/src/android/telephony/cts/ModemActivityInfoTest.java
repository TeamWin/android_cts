/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.telephony.ModemActivityInfo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * CTS test for ModemActivityInfo APIs
 */
public class ModemActivityInfoTest {
    private static final String TAG = "ModemActivityInfoTest";

    private static final int VALID_SLEEP_TIME_MS = 1;
    private static final int VALID_IDLE_TIME_MS = 1;
    private static final int VALID_RX_TIME_MS = 1;
    private static final int[] VALID_TX_TIME_MS = {1, 1};

    private static final int INVALID_SLEEP_TIME_MS = -1;
    private static final int INVALID_IDLE_TIME_MS = -1;
    private static final int INVALID_RX_TIME_MS = -1;
    private static final int[] INVALID_TX_TIME_MS = {-1, 1};

    @Test
    public void testModemActivityInfoIsValid() {
        ModemActivityInfo modemActivityInfo = new ModemActivityInfo(0, VALID_SLEEP_TIME_MS,
                VALID_IDLE_TIME_MS, VALID_TX_TIME_MS, VALID_RX_TIME_MS);
        assertTrue("ModemActivityInfo should be valid", modemActivityInfo.isValid());

        modemActivityInfo = new ModemActivityInfo(0, INVALID_SLEEP_TIME_MS,
                VALID_IDLE_TIME_MS, VALID_TX_TIME_MS, VALID_RX_TIME_MS);
        assertFalse("ModemActivityInfo should be invalid because sleep time is invalid",
                modemActivityInfo.isValid());

        modemActivityInfo = new ModemActivityInfo(0, VALID_SLEEP_TIME_MS,
                INVALID_IDLE_TIME_MS, VALID_TX_TIME_MS, VALID_RX_TIME_MS);
        assertFalse("ModemActivityInfo should be invalid because idle time is invalid",
                modemActivityInfo.isValid());

        modemActivityInfo = new ModemActivityInfo(0, VALID_SLEEP_TIME_MS,
                VALID_IDLE_TIME_MS, VALID_TX_TIME_MS, INVALID_RX_TIME_MS);
        assertFalse("ModemActivityInfo should be invalid because receive time is invalid",
                modemActivityInfo.isValid());

        modemActivityInfo = new ModemActivityInfo(0, VALID_SLEEP_TIME_MS,
                VALID_IDLE_TIME_MS, INVALID_TX_TIME_MS, INVALID_RX_TIME_MS);
        assertFalse("ModemActivityInfo should be invalid because transmit time is invalid",
                modemActivityInfo.isValid());
    }
}
