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

package android.telephonyprovider.cts;

import static android.provider.Telephony.ServiceStateTable.VOICE_REG_STATE;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class ServiceStateTest {

    private ContentResolver mContentResolver;
    private TelephonyManager mTelephonyManager;

    @Before
    public void setupTestEnvironment() {
        mContentResolver = getInstrumentation().getContext().getContentResolver();
        mTelephonyManager =
                getInstrumentation().getContext().getSystemService(TelephonyManager.class);
    }

    /**
     * Asserts that the voice reg state is valid and matches TelephonyManager#getServiceState().
     */
    @Test
    public void testGetVoiceRegState() {
        Uri uri = Telephony.ServiceStateTable.CONTENT_URI;
        assertThat(uri).isEqualTo(Uri.parse("content://service-state/"));

        Cursor cursor = mContentResolver.query(uri, new String[] {VOICE_REG_STATE}, null, null);
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToNext();

        int voiceRegState = cursor.getInt(cursor.getColumnIndex(VOICE_REG_STATE));
        assertThat(voiceRegState).isEqualTo(mTelephonyManager.getServiceState().getState());
    }
}
