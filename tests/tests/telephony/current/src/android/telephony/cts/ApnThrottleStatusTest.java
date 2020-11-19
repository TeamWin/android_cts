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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.ApnSetting;
import android.telephony.data.ApnThrottleStatus;

import org.junit.Test;

public class ApnThrottleStatusTest {

    private static final int SLOT_INDEX = 10;
    private static final int TRANSPORT_TYPE = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    private static final int APN_TYPE = ApnSetting.TYPE_IMS;
    private static final int THROTTLE_TYPE = ApnThrottleStatus.THROTTLE_TYPE_ELAPSED_TIME;
    private static final long THROTTLE_EXPIRY_TIME_MILLIS = 5005;
    private static final int RETRY_TYPE = ApnThrottleStatus.RETRY_TYPE_NEW_CONNECTION;

    @Test
    public void testBuilderAndGetters() {

        ApnThrottleStatus status = new ApnThrottleStatus.Builder()
                .setSlotIndex(SLOT_INDEX)
                .setTransportType(TRANSPORT_TYPE)
                .setApnType(APN_TYPE)
                .setThrottleExpiryTimeMillis(THROTTLE_EXPIRY_TIME_MILLIS)
                .setRetryType(RETRY_TYPE)
                .build();

        assertEquals(SLOT_INDEX, status.getSlotIndex());
        assertEquals(TRANSPORT_TYPE, status.getTransportType());
        assertEquals(APN_TYPE, status.getApnType());
        assertEquals(THROTTLE_TYPE, status.getThrottleType());
        assertEquals(THROTTLE_EXPIRY_TIME_MILLIS, status.getThrottleExpiryTimeMillis());
        assertEquals(RETRY_TYPE, status.getRetryType());
    }

    @Test
    public void testEquals() {

        ApnThrottleStatus status1 = new ApnThrottleStatus.Builder()
                .setSlotIndex(SLOT_INDEX)
                .setTransportType(TRANSPORT_TYPE)
                .setApnType(APN_TYPE)
                .setThrottleExpiryTimeMillis(THROTTLE_EXPIRY_TIME_MILLIS)
                .setRetryType(RETRY_TYPE)
                .build();
        ApnThrottleStatus status2 = new ApnThrottleStatus.Builder()
                .setSlotIndex(SLOT_INDEX)
                .setTransportType(TRANSPORT_TYPE)
                .setApnType(APN_TYPE)
                .setThrottleExpiryTimeMillis(THROTTLE_EXPIRY_TIME_MILLIS)
                .setRetryType(RETRY_TYPE)
                .build();

        assertEquals(status1, status2);
    }

    @Test
    public void testNotEquals() {
        ApnThrottleStatus status1 = new ApnThrottleStatus.Builder()
                .setSlotIndex(SLOT_INDEX)
                .setTransportType(TRANSPORT_TYPE)
                .setApnType(APN_TYPE)
                .setThrottleExpiryTimeMillis(THROTTLE_EXPIRY_TIME_MILLIS)
                .setRetryType(RETRY_TYPE)
                .build();
        ApnThrottleStatus status2 = new ApnThrottleStatus.Builder()
                .setSlotIndex(SLOT_INDEX)
                .setTransportType(TRANSPORT_TYPE)
                .setApnType(APN_TYPE)
                .setNoThrottle()
                .setRetryType(RETRY_TYPE)
                .build();
        assertNotEquals(status1, status2);
    }

    @Test
    public void testParcel() {
        ApnThrottleStatus status = new ApnThrottleStatus.Builder()
                .setSlotIndex(SLOT_INDEX)
                .setTransportType(TRANSPORT_TYPE)
                .setApnType(APN_TYPE)
                .setThrottleExpiryTimeMillis(THROTTLE_EXPIRY_TIME_MILLIS)
                .setRetryType(RETRY_TYPE)
                .build();

        Parcel stateParcel = Parcel.obtain();
        status.writeToParcel(stateParcel, 0);
        stateParcel.setDataPosition(0);

        ApnThrottleStatus postParcel = ApnThrottleStatus.CREATOR.createFromParcel(stateParcel);
        assertEquals(status, postParcel);
    }
}
