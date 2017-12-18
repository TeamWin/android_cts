/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permission.cts;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_DEFAULT;
import static android.app.AppOpsManager.MODE_ERRORED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_READ_SMS;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.AttributeSet;

import com.android.compatibility.common.util.SystemUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AppOpsTest extends InstrumentationTestCase {
    static final Class<?>[] sSetModeSignature = new Class[] {
            Context.class, AttributeSet.class};

    private AppOpsManager mAppOps;
    private Context mContext;
    private String mOpPackageName;
    private int mMyUid;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mAppOps = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mOpPackageName = mContext.getOpPackageName();
        mMyUid = Process.myUid();
        assertNotNull(mAppOps);
    }

    public void testNoteOpAndCheckOp() throws Exception {
        setAppOpMode(OPSTR_READ_SMS, MODE_ALLOWED);
        assertEquals(MODE_ALLOWED, mAppOps.noteOp(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_ALLOWED, mAppOps.noteOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_ALLOWED, mAppOps.checkOp(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_ALLOWED, mAppOps.checkOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));

        setAppOpMode(OPSTR_READ_SMS, MODE_IGNORED);
        assertEquals(MODE_IGNORED, mAppOps.noteOp(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_IGNORED, mAppOps.noteOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_IGNORED, mAppOps.checkOp(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_IGNORED, mAppOps.checkOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));

        setAppOpMode(OPSTR_READ_SMS, MODE_DEFAULT);
        assertEquals(MODE_DEFAULT, mAppOps.noteOp(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_DEFAULT, mAppOps.noteOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_DEFAULT, mAppOps.checkOp(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_DEFAULT, mAppOps.checkOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));

        setAppOpMode(OPSTR_READ_SMS, MODE_ERRORED);
        assertEquals(MODE_ERRORED, mAppOps.noteOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        assertEquals(MODE_ERRORED, mAppOps.checkOpNoThrow(OPSTR_READ_SMS, mMyUid, mOpPackageName));
        try {
            mAppOps.noteOp(OPSTR_READ_SMS, mMyUid, mOpPackageName);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }
        try {
            mAppOps.checkOp(OPSTR_READ_SMS, mMyUid, mOpPackageName);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }
    }

    public void testCheckPackagePassesTest() throws Exception {
        mAppOps.checkPackage(mMyUid, mOpPackageName);
        mAppOps.checkPackage(Process.SYSTEM_UID, "android");
    }

    public void testCheckPackageDoesntPassTest() throws Exception {
        try {
            // Package name doesn't match UID.
            mAppOps.checkPackage(Process.SYSTEM_UID, mOpPackageName);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            // Package name doesn't match UID.
            mAppOps.checkPackage(mMyUid, "android");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            // Package name missing
            mAppOps.checkPackage(mMyUid, "");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Test that the app can not change the app op mode for itself.
     */
    @SmallTest
    public void testCantSetModeForSelf() {
        boolean gotToTest = false;
        try {
            Method setMode = mAppOps.getClass().getMethod("setMode", int.class, int.class,
                    String.class, int.class);
            int writeSmsOp = mAppOps.getClass().getField("OP_WRITE_SMS").getInt(mAppOps);
            gotToTest = true;
            setMode.invoke(mAppOps, writeSmsOp, mMyUid, mOpPackageName, AppOpsManager.MODE_ALLOWED);
            fail("Was able to set mode for self");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Unable to find OP_WRITE_SMS", e);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Unable to find setMode method", e);
        } catch (InvocationTargetException e) {
            if (!gotToTest) {
                throw new AssertionError("Whoops", e);
            }
            // If we got to the test, we want it to have thrown a security exception.
            // We need to look inside of the wrapper exception to see.
            Throwable t = e.getCause();
            if (!(t instanceof SecurityException)) {
                throw new AssertionError("Did not throw SecurityException", e);
            }
        } catch (IllegalAccessException e) {
            throw new AssertionError("Whoops", e);
        }
    }

    private void setAppOpMode(String opStr, int mode) throws Exception {
        String modeStr;
        switch (mode) {
            case MODE_ALLOWED:
                modeStr = "allow";
                break;
            case MODE_ERRORED:
                modeStr = "deny";
                break;
            case MODE_IGNORED:
                modeStr = "ignore";
                break;
            case MODE_DEFAULT:
                modeStr = "default";
                break;
            default:
                throw new IllegalArgumentException("Unexpected app op type");
        }
        String command = "appops set " + mOpPackageName + " " + opStr + " " + modeStr;
        SystemUtil.runShellCommand(getInstrumentation(), command);
    }
}
