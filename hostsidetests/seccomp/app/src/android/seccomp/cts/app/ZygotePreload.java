/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.seccomp.cts.app;

import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.util.Log;

public class ZygotePreload implements android.app.ZygotePreload {
    static final String TAG = "SeccompDeviceTest";

    static volatile boolean sResult = false;
    static volatile int sStartOfIsolatedRange = -1;

    static private boolean testSetResUidGidBlocked(int rid, int eid, int sid) {
        if (!SeccompDeviceTest.testSetresuidBlocked(rid, eid, sid)) {
            Log.e(TAG, "setresuid( " + Integer.toString(rid) + ","
                    + Integer.toString(eid) + "," + Integer.toString(sid) + ")"
                    + " is wrongly allowed.");
            return false;
        }
        if (!SeccompDeviceTest.testSetresgidBlocked(rid, eid, sid)) {
            Log.e(TAG, "setresguid( " + Integer.toString(rid) + ","
                    + Integer.toString(eid) + "," + Integer.toString(sid) + ")"
                    + " is wrongly allowed.");
            return false;
        }

        return true;
    }

    static private boolean testSetResUidGidAllowed(int rid, int eid, int sid) {
        if (SeccompDeviceTest.testSetresuidBlocked(rid, eid, sid)) {
            Log.e(TAG, "setresuid( " + Integer.toString(rid) + ","
                    + Integer.toString(eid) + "," + Integer.toString(sid) + ")"
                    + " is wrongly blocked.");
            return false;
        }
        if (SeccompDeviceTest.testSetresgidBlocked(rid, eid, sid)) {
            Log.e(TAG, "setresguid( " + Integer.toString(rid) + ","
                    + Integer.toString(eid) + "," + Integer.toString(sid) + ")"
                    + " is wrongly blocked.");
            return false;
        }

        return true;
    }

    static synchronized public boolean getSeccomptestResult() {
        return sResult;
    }

    static synchronized public int getStartOfIsolatedRange() {
        return sStartOfIsolatedRange;
    }

    /*
     * This is called from the app_zygote security context, which has two seccomp
     * filters in place:
     * 1) The regular app seccomp filter (which allows setresuid/setresgid)
     * 2) A setresuid/setresgid limiting filter, which restricts the calls to
     *    setresuid/setresgid to be in a particular range.
     *
     * This test enforces 2) is in place.
     */
    @Override
    synchronized public void doPreload(ApplicationInfo appInfo) {
        boolean result = true;

        // root uid
        result &= testSetResUidGidBlocked(0, 0, 0);
        // system uid
        result &= testSetResUidGidBlocked(Process.SYSTEM_UID, Process.SYSTEM_UID,
                Process.SYSTEM_UID);
        // mix of uids
        result &= testSetResUidGidBlocked(0, Process.SYSTEM_UID,
                Process.SYSTEM_UID);

        // an app uid
        result &= testSetResUidGidBlocked(Process.FIRST_APPLICATION_UID,
                Process.FIRST_APPLICATION_UID, Process.FIRST_APPLICATION_UID);
        result &= testSetResUidGidBlocked(Process.LAST_APPLICATION_UID,
                Process.LAST_APPLICATION_UID, Process.LAST_APPLICATION_UID);

        // an isolated process uid
        result &= testSetResUidGidBlocked(Process.FIRST_ISOLATED_UID,
                Process.FIRST_ISOLATED_UID, Process.FIRST_ISOLATED_UID);
        result &= testSetResUidGidBlocked(Process.LAST_ISOLATED_UID, Process.LAST_ISOLATED_UID,
                Process.LAST_ISOLATED_UID);

        // Test all ranges of app zygote UIDs; we don't know here which
        // isolated UID is assigned to our process, so we will test all ranges,
        // and verify only one is allowed; then have the caller verify that
        // this was indeed our allowed range.
        for (int i = Process.FIRST_APP_ZYGOTE_ISOLATED_UID;
                i < Process.LAST_APP_ZYGOTE_ISOLATED_UID; i += Process.NUM_UIDS_PER_APP_ZYGOTE) {
            boolean rangeAllowed = testSetResUidGidAllowed(i, i, i);
            if (rangeAllowed) {
                if (sStartOfIsolatedRange != -1) {
                    Log.e(TAG, "Found more than one allowed isolated UID range: "
                            + sStartOfIsolatedRange + ", " + i);
                    result = false;
                    break;
                }
                sStartOfIsolatedRange = i;

                // off-by-one and some variants
                result &= testSetResUidGidBlocked(i - 1, i - 1, i - 1);
                result &= testSetResUidGidBlocked(i, i - 1, i);
                result &= testSetResUidGidBlocked(i, i, i - 1);
                int overEndOfRange = i + Process.NUM_UIDS_PER_APP_ZYGOTE;
                result &= testSetResUidGidBlocked(overEndOfRange, overEndOfRange, overEndOfRange);
                result &= testSetResUidGidBlocked(overEndOfRange, i, i);
                result &= testSetResUidGidBlocked(i, overEndOfRange, overEndOfRange);

                // mixed allowed rgid with dis-allowed euid and suid (and variants)
                result &= testSetResUidGidBlocked(i, 0, 0);
                result &= testSetResUidGidBlocked(i, 0, i);
                result &= testSetResUidGidBlocked(i, i, 0);
                result &= testSetResUidGidBlocked(0, i, 0);
                result &= testSetResUidGidBlocked(0, i, i);
                result &= testSetResUidGidBlocked(0, 0, i);
            }
        }

        // one over the range
        result &= testSetResUidGidBlocked(Process.LAST_APP_ZYGOTE_ISOLATED_UID + 1,
                Process.LAST_APP_ZYGOTE_ISOLATED_UID + 1, Process.LAST_APP_ZYGOTE_ISOLATED_UID + 1);

        // Store result
        sResult = result;
    }
}
