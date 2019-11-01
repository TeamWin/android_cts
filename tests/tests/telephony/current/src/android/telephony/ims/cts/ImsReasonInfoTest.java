/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims.cts;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.ims.ImsReasonInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImsReasonInfoTest {

    @Test
    public void testParcelUnparcel() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        int code = ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN;
        int extraCode = ImsReasonInfo.EXTRA_CODE_CALL_RETRY_NORMAL;
        String extraMessage = "oops";
        ImsReasonInfo info = new ImsReasonInfo(code, extraCode, extraMessage);

        Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ImsReasonInfo unparceledInfo = ImsReasonInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(code, unparceledInfo.getCode());
        assertEquals(extraCode, unparceledInfo.getExtraCode());
        assertEquals(extraMessage, unparceledInfo.getExtraMessage());
    }
}
