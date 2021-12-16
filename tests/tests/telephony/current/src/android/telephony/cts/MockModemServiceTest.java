/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/** Test MockModemService interfaces. */
public class MockModemServiceTest {
    private static final String TAG = "MockModemServiceTest";
    private static MockModemServiceConnector sServiceConnector;

    @BeforeClass
    public static void beforeAllTests() throws Exception {

        Log.d(TAG, "MockModemServiceTest#beforeAllTests()");

        // Override all interfaces to TestMockModemService
        sServiceConnector =
                new MockModemServiceConnector(InstrumentationRegistry.getInstrumentation());

        assertNotNull(sServiceConnector);
        assertTrue(sServiceConnector.connectMockModemService());
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        Log.d(TAG, "MockModemServiceTest#afterAllTests()");

        // Rebind all interfaces which is binding to MockModemService to default.
        assertNotNull(sServiceConnector);
        assertTrue(sServiceConnector.disconnectMockModemService());
        sServiceConnector = null;
    }
}
