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

package android.hdmicec.cts;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Class that helps communicate with the cec-client */
public final class HdmiCecUtils {

    private static final String CEC_CONSOLE_READY = "waiting for input";
    private static final int MILLISECONDS_TO_READY = 5000;
    private static final int DEFAULT_TIMEOUT = 20000;

    private Process mCecClient;
    private BufferedWriter mOutputConsole;
    private BufferedReader mInputConsole;
    private boolean mCecClientInitialised = false;

    private CecDevice targetDevice;
    private CecDevice lastTarget;
    private String physicalAddress;

    public HdmiCecUtils(CecDevice targetDevice, String physicalAddress) {
        this.targetDevice = targetDevice;
        this.lastTarget = targetDevice;
        this.physicalAddress = physicalAddress;
    }

    /** Initialise the client */
    public boolean init() throws Exception {
        boolean gotExpectedOut = false;
        List<String> commands = new ArrayList();
        int seconds = 0;

        commands.add("cec-client");
        mCecClient = RunUtil.getDefault().runCmdInBackground(commands);
        mInputConsole = new BufferedReader(new InputStreamReader(mCecClient.getInputStream()));

        /* Wait for the client to become ready */
        mCecClientInitialised = true;
        if (checkConsoleOutput(CecMessage.CLIENT_CONSOLE_READY + "", MILLISECONDS_TO_READY)) {
            mOutputConsole = new BufferedWriter(
                                new OutputStreamWriter(mCecClient.getOutputStream()));
            return mCecClientInitialised;
        }

        mCecClientInitialised = false;

        throw (new Exception("Could not initialise cec-client process"));
    }

    private void checkCecClient() throws Exception {
        if (!mCecClientInitialised) {
            throw new Exception("cec-client not initialised!");
        }
        if (!mCecClient.isAlive()) {
            throw new Exception("cec-client not running!");
        }
    }

    /**
     * Sends a CEC message with source marked as broadcast to the output console of the
     * cec-communication channel
     */
    public void sendCecMessage(CecMessage message) throws Exception {
        sendCecMessage(CecDevice.BROADCAST, targetDevice, message, "");
    }

    /**
     * Sends a CEC message from source device to the output console of the cec-communication
     * channel
     */
    public void sendCecMessage(CecDevice source, CecMessage message) throws Exception {
        sendCecMessage(source, targetDevice, message, "");
    }

    /**
     * Sends a CEC message from source device to the output console of the cec-communication
     * channel with the appended params.
     */
    public void sendCecMessage(CecDevice source, CecDevice destination,
        CecMessage message, String params) throws Exception {
        checkCecClient();
        mOutputConsole.write("tx " + source + destination + ":" + message + params);
        mOutputConsole.flush();
        lastTarget = destination;
    }

    /** Sends a message to the output console of the cec-client */
    public void sendConsoleMessage(String message) throws Exception {
        checkCecClient();
        CLog.e("Sending message:: " + message);
        mOutputConsole.write(message);
        mOutputConsole.flush();
    }

    /** Check for any string on the input console of the cec-client, uses default timeout */
    public boolean checkConsoleOutput(String expectedMessage) throws Exception {
        return checkConsoleOutput(expectedMessage, DEFAULT_TIMEOUT);
    }

    /** Check for any string on the input console of the cec-client */
    public boolean checkConsoleOutput(String expectedMessage,
                                       long timeoutMillis) throws Exception {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;

        while ((endTime - startTime <= timeoutMillis)) {
            if (mInputConsole.ready()) {
                String line = mInputConsole.readLine();
                if (line.contains(expectedMessage)) {
                    CLog.v("Found " + expectedMessage + " in " + line);
                    return true;
                }
            }
            endTime = System.currentTimeMillis();
        }
        return false;
    }

    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within default timeout. If the CEC message
     * is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecMessage expectedMessage) throws Exception {
        return checkExpectedOutput(CecDevice.BROADCAST, expectedMessage, DEFAULT_TIMEOUT);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client
     * communication channel and returns the first line that contains that message within
     * default timeout. If the CEC message is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecDevice toDevice,
                                      CecMessage expectedMessage) throws Exception {
        return checkExpectedOutput(toDevice, expectedMessage, DEFAULT_TIMEOUT);
    }

    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within timeoutMillis. If the CEC message
     * is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecMessage expectedMessage,
                                      long timeoutMillis) throws Exception {
        return checkExpectedOutput(CecDevice.BROADCAST, expectedMessage, timeoutMillis);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client
     * communication channel and returns the first line that contains that message within
     * timeoutMillis. If the CEC message is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecDevice toDevice, CecMessage expectedMessage,
                                       long timeoutMillis) throws Exception {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern = Pattern.compile("(.*>>)(.*?)" +
                                          "(" + lastTarget + toDevice + "):" +
                                          "(" + expectedMessage + ")(.*)",
                                          Pattern.CASE_INSENSITIVE);
        CLog.e("Expected out pattern:: " + pattern);

        while ((endTime - startTime <= timeoutMillis)) {
            if (mInputConsole.ready()) {
                String line = mInputConsole.readLine();
                if (pattern.matcher(line).matches()) {
                    CLog.v("Found " + expectedMessage.name() + " in " + line);
                    return line;
                }
            }
            endTime = System.currentTimeMillis();
        }
        throw new Exception("Could not find message " + expectedMessage.name());
    }

    /**
     * Kills the cec-client process that was created in init().
     */
    public void killCecProcess() throws Exception {
        checkCecClient();
        sendConsoleMessage(CecMessage.QUIT_CLIENT.toString());
        mOutputConsole.close();
        mInputConsole.close();
        mCecClientInitialised = false;
    }
}
