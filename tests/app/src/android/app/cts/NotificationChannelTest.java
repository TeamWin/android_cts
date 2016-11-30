/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.app.cts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.net.Uri;
import android.os.Parcel;
import android.test.AndroidTestCase;

public class NotificationChannelTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testDescribeContents() {
        final int expected = 0;
        NotificationChannel channel =
                new NotificationChannel("1", "1", NotificationManager.IMPORTANCE_DEFAULT);
        assertEquals(expected, channel.describeContents());
    }

    public void testConstructor() {
        NotificationChannel channel =
                new NotificationChannel("1", "one", NotificationManager.IMPORTANCE_DEFAULT);
        assertEquals("1", channel.getId());
        assertEquals("one", channel.getName());
        assertEquals(false, channel.canBypassDnd());
        assertEquals(false, channel.shouldShowLights());
        assertEquals(false, channel.shouldVibrate());
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.getImportance());
        assertEquals(null, channel.getSound());
    }

    public void testWriteToParcel() {
        NotificationChannel channel =
                new NotificationChannel("1", "one", NotificationManager.IMPORTANCE_DEFAULT);
        Parcel parcel = Parcel.obtain();
        channel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NotificationChannel channel1 = NotificationChannel.CREATOR.createFromParcel(parcel);
        assertEquals(channel, channel1);
    }

    public void testLights() {
        NotificationChannel channel =
                new NotificationChannel("1", "one", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLights(true);
        assertTrue(channel.shouldShowLights());
        channel.setLights(false);
        assertFalse(channel.shouldShowLights());
    }

    public void testVibration() {
        NotificationChannel channel =
                new NotificationChannel("1", "one", NotificationManager.IMPORTANCE_DEFAULT);
        channel.enableVibration(true);
        assertTrue(channel.shouldVibrate());
        channel.enableVibration(false);
        assertFalse(channel.shouldVibrate());
    }

    public void testVibrationPattern() {
        final long[] pattern = new long[] {1, 7, 1, 7, 3};
        NotificationChannel channel =
                new NotificationChannel("1", "one", NotificationManager.IMPORTANCE_DEFAULT);
        assertNull(channel.getVibrationPattern());
        channel.setVibrationPattern(pattern);
        assertEquals(pattern, channel.getVibrationPattern());
    }

    public void testRingtone() {
        Uri expected = new Uri.Builder().scheme("fruit").appendQueryParameter("favorite", "bananas")
                .build();
        NotificationChannel channel =
                new NotificationChannel("1", "one", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(expected);
        assertEquals(expected, channel.getSound());
    }
}
