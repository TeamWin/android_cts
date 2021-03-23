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

package android.car.cts.app;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

public final class PowerPolicyTestClient {
    private static final String TAG = PowerPolicyTestClient.class.getSimpleName();

    private static final String POWERPOLICY_TEST_CMD_IDENTIFIER = "powerpolicy";
    private static final String TEST_CMD_START = "start";
    private static final String TEST_CMD_END = "end";
    private static final String TEST_CMD_DUMP_STATE = "dumpstate";
    private static final String TEST_CMD_DUMP_POLICY = "dumppolicy";
    private static final String TEST_CMD_APPLY_POLICY = "applypolicy";
    private static final String TEST_CMD_CLOSE_DATAFILE = "closefile";

    private static PowerPolicyTestClient sPowerPolicyTestClient = new PowerPolicyTestClient();

    private long mClientStartTime;

    // This method is not intended for multi-threaded calls.
    public static void handleCommand(PowerPolicyTestCommand cmd) {
        switch (cmd.getType()) {
            case START:
                if (sPowerPolicyTestClient != null) {
                    Log.w(TAG, "can not restart the test without ending the previous test first");
                    return;
                }
                break;
            default:
                if (sPowerPolicyTestClient == null) {
                    Log.w(TAG, "execute test start command first");
                    return;
                }
                break;
        }
        cmd.execute(sPowerPolicyTestClient);

        if (cmd.getType() == PowerPolicyTestCommand.TestCommandType.END) {
            sPowerPolicyTestClient = null;
        }
    }

    public static PowerPolicyTestCommand parseCommand(Bundle intentExtras) {
        String testcase;
        String action;
        String data;
        PowerPolicyTestCommand cmd = null;
        String powertest = intentExtras.getString(POWERPOLICY_TEST_CMD_IDENTIFIER);
        if (powertest == null) {
            Log.d(TAG, "empty power test command");
            return cmd;
        }

        String[] tokens = powertest.split(",");
        int paramCount = tokens.length;
        if (paramCount != 2 && paramCount != 3) {
            throw new IllegalArgumentException("invalid command syntax");
        }

        testcase = tokens[0];
        action = tokens[1];
        if (paramCount == 3) {
            data = tokens[2];
        } else {
            data = null;
        }

        switch (testcase) {
            case TEST_CMD_START:
                cmd = new PowerPolicyTestCommand.StartTestcaseCommand(testcase);
                break;
            case TEST_CMD_END:
                cmd = new PowerPolicyTestCommand.EndTestcaseCommand(testcase);
                break;
            case TEST_CMD_DUMP_STATE:
                cmd = new PowerPolicyTestCommand.DumpStateCommand(testcase);
                break;
            case TEST_CMD_DUMP_POLICY:
                cmd = new PowerPolicyTestCommand.DumpPolicyCommand(testcase);
                break;
            case TEST_CMD_APPLY_POLICY:
                if (paramCount != 3) {
                    throw new IllegalArgumentException("invalid command syntax");
                }
                cmd = new PowerPolicyTestCommand.ApplyPolicyCommand(testcase);
                cmd.mPolicyId = data;
                break;
            case TEST_CMD_CLOSE_DATAFILE:
                cmd = new PowerPolicyTestCommand.CloseDataFileCommand(testcase);
                break;
            default:
                throw new IllegalArgumentException("invalid power policy test command: "
                    + action);
        }

        Log.i(TAG, "testcase=" + testcase + ", command=" + action);
        return cmd;
    }

    public void cleanup() {
        //TODO(b/183134882): add any necessary cleanup activities here
    }

    public void registerAndGo() {
        mClientStartTime = SystemClock.uptimeMillis();
        //TODO(b/183134882): here is the place to add listeners
    }
}
