/*
 * Copyright (C) 2015 Google Inc.
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

package android.location.cts;

import android.location.GnssNavigationMessage;
import android.location.GnssNavigationMessageEvent;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.List;

/**
 * Test the {@link GnssNavigationMessage} values.
 *
 * Test steps:
 * 1. Register for {@link GnssNavigationMessageEvent}s.
 * 2. Wait for {@link #EVENTS_COUNT} events to arrive.
 * 3. Check {@link GnssNavigationMessageEvent} status: if the status is not
 *    {@link GnssNavigationMessageEvent#STATUS_READY}, the test will be skipped because one of the
 *    following reasons:
 *          3.1 the device does not support the feature,
 *          3.2 GPS is disabled in the device,
 *          3.3 Location is disabled in the device.
 * 4. Verify {@link GnssNavigationMessage}s (all mandatory fields), the test will fail if any of the
 *    mandatory fields is not populated or in the expected range.
 */
public class GnssNavigationMessageTest extends AndroidTestCase {

    private static final String TAG = "GpsNavMsgTest";
    private TestLocationManager mTestLocationManager;
    private static final int EVENTS_COUNT = 5;
    private TestGnssNavigationMessageListener mTestGnssNavigationMessageListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestLocationManager = new TestLocationManager(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        // Unregister GnssNavigationMessageListener
        if (mTestGnssNavigationMessageListener != null) {
            mTestLocationManager
                    .unregisterGnssNavigationMessageCallback(mTestGnssNavigationMessageListener);
            mTestGnssNavigationMessageListener = null;
        }
        super.tearDown();
    }

    /**
     * Tests that one can listen for {@link GnssNavigationMessageEvent}s for collection purposes.
     * It only performs sanity checks for the Navigation messages received.
     * This tests uses actual data retrieved from GPS HAL.
     */
    public void testGnssNavigationMessageMandatoryFieldRanges() throws Exception {
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager)) {
            return;
        }

        // Register Gps Navigation Message Listener.
        mTestGnssNavigationMessageListener =
                new TestGnssNavigationMessageListener(TAG, EVENTS_COUNT);
        mTestLocationManager.registerGnssNavigationMessageCallback(mTestGnssNavigationMessageListener);

        mTestGnssNavigationMessageListener.await();
        if (!mTestGnssNavigationMessageListener.verifyState()) {
            return;
        }

        List<GnssNavigationMessageEvent> events = mTestGnssNavigationMessageListener.getEvents();
        assertTrue("No Gps Navigation Message received.", !events.isEmpty());

        // Verify mandatory GnssNavigationMessage field values.
        TestMeasurementUtil.verifyGnssNavMessageMandatoryField(events);
    }
}
