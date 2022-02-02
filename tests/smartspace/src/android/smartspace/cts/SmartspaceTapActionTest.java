/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.smartspace.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.smartspace.uitemplatedata.SmartspaceTapAction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SmartspaceTapAction}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceTapActionTest {

    private static final String TAG = "SmartspaceTapActionTest";

    @Test
    public void testCreateSmartspaceTapAction() {
        Bundle extras = new Bundle();
        extras.putString("key", "value");

        Intent intent = new Intent();
        PendingIntent pendingIntent = TaskStackBuilder.create(getContext())
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);

        SmartspaceTapAction smartspaceTapAction = new SmartspaceTapAction.Builder("id")
                .setIntent(intent)
                .setPendingIntent(pendingIntent)
                .setUserHandle(Process.myUserHandle())
                .setExtras(extras).build();

        assertThat(smartspaceTapAction.getId()).isEqualTo("id");
        assertThat(smartspaceTapAction.getIntent()).isEqualTo(intent);
        assertThat(smartspaceTapAction.getPendingIntent()).isEqualTo(pendingIntent);
        assertThat(smartspaceTapAction.getUserHandle()).isEqualTo(Process.myUserHandle());
        assertThat(smartspaceTapAction.getExtras()).isEqualTo(extras);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        smartspaceTapAction.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceTapAction copyTapAction = SmartspaceTapAction.CREATOR.createFromParcel(parcel);
        assertThat(smartspaceTapAction).isEqualTo(copyTapAction);
        parcel.recycle();
    }
}
