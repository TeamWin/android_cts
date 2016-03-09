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

package android.location.cts;

import android.location.GnssNavigationMessage;
import android.location.GnssNavigationMessageEvent;
import android.os.Parcel;
import android.test.AndroidTestCase;

public class GnssNavigationMessageEventTest extends AndroidTestCase {
    public void testDescribeContents() {
        GnssNavigationMessageEvent event = new GnssNavigationMessageEvent(null);
        event.describeContents();
    }

    public void testWriteToParcel() {
        GnssNavigationMessage message = new GnssNavigationMessage();
        message.setMessageId(2);
        GnssNavigationMessageEvent event = new GnssNavigationMessageEvent(message);
        Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssNavigationMessageEvent newEvent =
                GnssNavigationMessageEvent.CREATOR.createFromParcel(parcel);
        assertEquals(2, newEvent.getNavigationMessage().getMessageId());
    }
}
