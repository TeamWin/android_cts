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

public class AdjustmentTest extends AndroidTestCase {

    final String mPkg = "pkg";
    final String mKey = "key";
    final Bundle mSignals = new Bundle();
    final CharSequence mExplanation = "explanation";
    final int mUser = 0;
    ArrayList<String> mPeople = new ArrayList<>();
    ArrayList<SnoozeCriterion> mSnoozeCriteria = new ArrayList<>();
    NotificationChannel mNotificationChannel = new NotificationChannel("id", "name",
            NotificationManager.IMPORTANCE_DEFAULT);

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSnoozeCriteria.add(new SnoozeCriterion("id", "exp", "confirm"));
        mSnoozeCriteria.add(new SnoozeCriterion("id2", "hello", "goodbye"));
        mSignals.putParcelableArrayList(Adjustment.KEY_SNOOZE_CRITERIA, mSnoozeCriteria);

        mPeople.add(new Uri.Builder().scheme("person").build().toString());
        mSignals.putStringArrayList(Adjustment.KEY_PEOPLE, mPeople);

        mSignals.putParcelable(Adjustment.KEY_CHANNEL_ID, mNotificationChannel);
    }

    public void testDescribeContents() {
        final int expected = 0;

        Adjustment adjustment = new Adjustment(mPkg, mKey, mSignals, mExplanation, mUser);
        assertEquals(expected, adjustment.describeContents());
    }

    public void testConstructor() {
        Adjustment adjustment = new Adjustment(mPkg, mKey, mSignals, mExplanation, mUser);
        assertEquals(mPkg, adjustment.getPackage());
        assertEquals(mKey, adjustment.getKey());
        assertEquals(mSignals, adjustment.getSignals());
        assertEquals(mExplanation, adjustment.getExplanation());
        assertEquals(mUser, adjustment.getUser());
    }

    public void testWriteToParcel() {
        Adjustment adjustment = new Adjustment(mPkg, mKey, mSignals, mExplanation, mUser);
        Parcel parcel = Parcel.obtain();
        adjustment.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Adjustment adjustment1 = Adjustment.CREATOR.createFromParcel(parcel);
        assertEquals(mPkg, adjustment1.getPackage());
        assertEquals(mKey, adjustment1.getKey());
        for (String key : mSignals.keySet()) {
            assertTrue(adjustment1.getSignals().containsKey(key));
            assertEquals(mSignals.get(key), adjustment1.getSignals().get(key));
        }
        assertEquals(mExplanation, adjustment1.getExplanation());
        assertEquals(mUser, adjustment1.getUser());
    }
}
