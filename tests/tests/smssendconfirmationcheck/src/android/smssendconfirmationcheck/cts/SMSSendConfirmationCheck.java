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

package android.smssendconfirmationcheck.cts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;

public class SMSSendConfirmationCheck extends AndroidTestCase {

    /*
     * The following method tries to send a short code SMS via default SMS API (silently). If
     * the message is sent (no confirmation displayed to user), the SMS db size will not
     * match the previous db size.  If SMS is not capable on the device (i.e. a tablet or tv)
     * this test will always pass.
     */

    public void testSMSSendingExploit() throws Exception {
        TelephonyManager telephonyManager =
            (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if(!telephonyManager.isSmsCapable()) {
            return;
        }
        int inboxSizeBeforeExploit = getSMSInboxSize();
        SmsManager manager = SmsManager.getDefault();
        manager.sendTextMessage("25578", null, "STOP", null, null);
        SystemClock.sleep(250);
        int inboxSizeAfterExploit = getSMSInboxSize();
        assertTrue("Device is vulnerable to bug #22314646!! For more information " +
                "please refer - https://android.googlesource.com/platform/frameworks/" +
                "opt/telephony/+/ce058be%5E!/", inboxSizeBeforeExploit == inboxSizeAfterExploit);
    }

    public int getSMSInboxSize() {
        ContentResolver resolver = mContext.getContentResolver();
        Cursor cur = resolver.query(Uri.parse("content://sms/"), null, null, null, null);
        int count = cur.getCount();
        cur.close();
        return count;
    }
}
