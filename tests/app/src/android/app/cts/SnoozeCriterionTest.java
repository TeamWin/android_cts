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
import android.os.Bundle;
import android.os.Parcel;
import android.service.notification.Adjustment;
import android.service.notification.SnoozeCriterion;
import android.test.AndroidTestCase;

import java.util.ArrayList;

public class SnoozeCriterionTest extends AndroidTestCase {

    final String mId = "id";
    final String mExplanation = "explanation";
    final String mConfirmation = "confirmation";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testDescribeContents() {
        final int expected = 0;

        SnoozeCriterion snooze = new SnoozeCriterion(mId, mExplanation, mConfirmation);
        assertEquals(expected, snooze.describeContents());
    }

    public void testConstructor() {
        SnoozeCriterion snooze = new SnoozeCriterion(mId, mExplanation, mConfirmation);
        assertEquals(mId, snooze.getId());
        assertEquals(mExplanation, snooze.getExplanation());
        assertEquals(mConfirmation, snooze.getConfirmation());
    }

    public void testWriteToParcel() {
        SnoozeCriterion snooze = new SnoozeCriterion(mId, mExplanation, mConfirmation);
        Parcel parcel = Parcel.obtain();
        snooze.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SnoozeCriterion snooze1 = SnoozeCriterion.CREATOR.createFromParcel(parcel);
        assertEquals(mId, snooze1.getId());
        assertEquals(mExplanation, snooze1.getExplanation());
        assertEquals(mConfirmation, snooze1.getConfirmation());
    }
}
