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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import org.junit.rules.ExternalResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Class that helps communicate with the cec-client */
public final class HdmiCecClientWrapper extends ExternalResource {

    private static final int MILLISECONDS_TO_READY = 10000;
    private static final int DEFAULT_TIMEOUT = 20000;
    private static final int BUFFER_SIZE = 1024;

    private Process mCecClient;
    private BufferedWriter mOutputConsole;
    private BufferedReader mInputConsole;
    private boolean mCecClientInitialised = false;

    private LogicalAddress selfDevice = LogicalAddress.RECORDER_1;
    private LogicalAddress targetDevice = LogicalAddress.UNKNOWN;
    private String clientParams[];
    private StringBuilder sendVendorCommand = new StringBuilder("cmd hdmi_control vendorcommand ");
    private int physicalAddress = 0xFFFF;

    public HdmiCecClientWrapper(String ...clientParams) {
        this.clientParams = clientParams;
    }

    @Override
    protected void after() {
        this.killCecProcess();
    }

    void setTargetLogicalAddress(LogicalAddress dutLogicalAddress) {
        targetDevice = dutLogicalAddress;
    }

    List<String> getValidCecClientPorts() throws Exception {

        List<String> listPortsCommand = new ArrayList();

        listPortsCommand.add("cec-client");
        listPortsCommand.add("-l");

        List<String> comPorts = new ArrayList();
        Process cecClient = RunUtil.getDefault().runCmdInBackground(listPortsCommand);
        BufferedReader inputConsole =
                new BufferedReader(new InputStreamReader(cecClient.getInputStream()));
        while (cecClient.isAlive()) {
            if (inputConsole.ready()) {
                String line = inputConsole.readLine();
                if (line.toLowerCase().contains("com port")) {
                    String port = line.split(":")[1].trim();
                    comPorts.add(port);
                }
            }
        }
        inputConsole.close();
        cecClient.waitFor();

        return comPorts;
    }

    boolean initValidCecClient(
            ITestDevice device, List<String> clientCommands, List<String> comPorts)
            throws Exception {
        String serialNo = device.getProperty("ro.serialno");
        String serialNoParam = CecMessage.convertStringToHexParams(serialNo);
        /* formatParams prefixes with a ':' that we do not want in the vendorcommand
         * command line utility.
         */
        serialNoParam = serialNoParam.substring(1);
        /* Logic below needs to be consistent with the app, see
         * hdmicec/app/src/android/hdmicec/app/HdmiControlManagerHelper.java
         */
        LogicalAddress toDevice =
                (targetDevice == LogicalAddress.TV) ? LogicalAddress.PLAYBACK_1 : LogicalAddress.TV;
        sendVendorCommand.append(" -t " + targetDevice.toString());
        sendVendorCommand.append(" -d " + toDevice.toString());
        sendVendorCommand.append(" -a " + serialNoParam);
        for (String port : comPorts) {
            List<String> launchCommand = new ArrayList(clientCommands);
            launchCommand.add(port);
            mCecClient = RunUtil.getDefault().runCmdInBackground(launchCommand);
            mInputConsole = new BufferedReader(new InputStreamReader(mCecClient.getInputStream()));

            /* Wait for the client to become ready */
            if (checkConsoleOutput(
                    CecClientMessage.CLIENT_CONSOLE_READY + "", MILLISECONDS_TO_READY)) {
                try {
                    device.executeShellCommand(sendVendorCommand.toString());
                    String message = checkExpectedOutput(toDevice, CecOperand.VENDOR_COMMAND);
                    if (CecMessage.getAsciiString(message).equalsIgnoreCase(serialNo)) {
                        /* If no Exception was thrown, then we have received the message we were
                         * looking for.
                         */
                        mOutputConsole =
                                new BufferedWriter(
                                        new OutputStreamWriter(mCecClient.getOutputStream()),
                                        BUFFER_SIZE);
                        return true;
                    }
                } catch (Exception e) {
                    /* Did not find the expected output, because we do not have a match with the
                     * port. Don't fail test, continue checking the other ports.
                     */
                    mInputConsole.close();
                }
            } else {
                CLog.e("Console did not get ready!");
            }
            /* Kill the unwanted cec-client process. */
            Process killProcess = mCecClient.destroyForcibly();
            killProcess.waitFor();
            launchCommand.remove(port);
        }
        return false;
    }

    /** Initialise the client */
    void init(boolean startAsTv, ITestDevice device) throws Exception {
        if (targetDevice == LogicalAddress.UNKNOWN) {
            throw new IllegalStateException("Missing logical address of the target device.");
        }

        List<String> commands = new ArrayList();

        commands.add("cec-client");

        /* "-p 2" starts the client as if it is connected to HDMI port 2, taking the physical
         * address 2.0.0.0 */
        commands.add("-p");
        commands.add("2");
        physicalAddress = 0x2000;
        if (startAsTv) {
            commands.add("-t");
            commands.add("x");
            selfDevice = LogicalAddress.TV;
        }
        commands.addAll(Arrays.asList(clientParams));
        if (Arrays.asList(clientParams).contains("a")) {
            selfDevice = LogicalAddress.AUDIO_SYSTEM;
        }

        List<String> comPorts = getValidCecClientPorts();

        mCecClientInitialised = true;
        if (!initValidCecClient(device, commands, comPorts)) {
            mCecClientInitialised = false;

            throw (new Exception("Could not initialise cec-client process"));
        }
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
     * Sends a CEC message with source marked as broadcast to the device passed in the constructor
     * through the output console of the cec-communication channel.
     */
    public void sendCecMessage(CecOperand message) throws Exception {
        sendCecMessage(LogicalAddress.BROADCAST, targetDevice, message, "");
    }

    /**
     * Sends a CEC message from source device to the device passed in the constructor through the
     * output console of the cec-communication channel.
     */
    public void sendCecMessage(LogicalAddress source, CecOperand message) throws Exception {
        sendCecMessage(source, targetDevice, message, "");
    }

    /**
     * Sends a CEC message from source device to a destination device through the output console of
     * the cec-communication channel.
     */
    public void sendCecMessage(LogicalAddress source, LogicalAddress destination,
        CecOperand message) throws Exception {
        sendCecMessage(source, destination, message, "");
    }

    /**
     * Broadcasts a CEC ACTIVE_SOURCE message from client device source through the output console
     * of the cec-communication channel.
     */
    public void broadcastActiveSource(LogicalAddress source) throws Exception {
        int sourcePa = (source == selfDevice) ? physicalAddress : 0xFFFF;
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE,
                CecMessage.formatParams(sourcePa, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
    }

    /**
     * Broadcasts a CEC ACTIVE_SOURCE message with physicalAddressOfActiveDevice from client device
     * source through the output console of the cec-communication channel.
     */
    public void broadcastActiveSource(LogicalAddress source, int physicalAddressOfActiveDevice)
            throws Exception {
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE,
                CecMessage.formatParams(
                        physicalAddressOfActiveDevice, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
    }

    /**
     * Broadcasts a CEC REPORT_PHYSICAL_ADDRESS message from client device source through the output
     * console of the cec-communication channel.
     */
    public void broadcastReportPhysicalAddress(LogicalAddress source) throws Exception {
        String deviceType = CecMessage.formatParams(source.getDeviceType());
        int sourcePa = (source == selfDevice) ? physicalAddress : 0xFFFF;
        String physicalAddress =
                CecMessage.formatParams(sourcePa, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                physicalAddress + deviceType);
    }

    /**
     * Broadcasts a CEC REPORT_PHYSICAL_ADDRESS message with physicalAddressToReport from client
     * device source through the output console of the cec-communication channel.
     */
    public void broadcastReportPhysicalAddress(LogicalAddress source, int physicalAddressToReport)
            throws Exception {
        String deviceType = CecMessage.formatParams(source.getDeviceType());
        String physicalAddress =
                CecMessage.formatParams(
                        physicalAddressToReport, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                physicalAddress + deviceType);
    }

    /**
     * Sends a CEC message from source device to a destination device through the output console of
     * the cec-communication channel with the appended params.
     */
    public void sendCecMessage(LogicalAddress source, LogicalAddress destination,
            CecOperand message, String params) throws Exception {
        checkCecClient();
        String sendMessageString = "tx " + source + destination + ":" + message + params;
        mOutputConsole.write(sendMessageString);
        mOutputConsole.newLine();
        mOutputConsole.flush();
    }

    /**
     * Sends a <USER_CONTROL_PRESSED> and <USER_CONTROL_RELEASED> from source to destination
     * through the output console of the cec-communication channel with the mentioned keycode.
     */
    public void sendUserControlPressAndRelease(LogicalAddress source, LogicalAddress destination,
            int keycode, boolean holdKey) throws Exception {
        sendUserControlPress(source, destination, keycode, holdKey);
        /* Sleep less than 200ms between press and release */
        TimeUnit.MILLISECONDS.sleep(100);
        mOutputConsole.write("tx " + source + destination + ":" +
                              CecOperand.USER_CONTROL_RELEASED);
        mOutputConsole.flush();
    }

    /**
     * Sends a <UCP> message from source to destination through the output console of the
     * cec-communication channel with the mentioned keycode. If holdKey is true, the method will
     * send multiple <UCP> messages to simulate a long press. No <UCR> will be sent.
     */
    public void sendUserControlPress(LogicalAddress source, LogicalAddress destination,
            int keycode, boolean holdKey) throws Exception {
        String key = String.format("%02x", keycode);
        String command = "tx " + source + destination + ":" +
                CecOperand.USER_CONTROL_PRESSED + ":" + key;

        if (holdKey) {
            /* Repeat once between 200ms and 450ms for at least 5 seconds. Since message will be
             * sent once later, send 16 times in loop every 300ms. */
            int repeat = 16;
            for (int i = 0; i < repeat; i++) {
                mOutputConsole.write(command);
                mOutputConsole.newLine();
                mOutputConsole.flush();
                TimeUnit.MILLISECONDS.sleep(300);
            }
        }

        mOutputConsole.write(command);
        mOutputConsole.newLine();
        mOutputConsole.flush();
    }

    /**
     * Sends a series of <UCP> [firstKeycode] from source to destination through the output console
     * of the cec-communication channel immediately followed by <UCP> [secondKeycode]. No <UCR>
     *  message is sent.
     */
    public void sendUserControlInterruptedPressAndHold(
        LogicalAddress source, LogicalAddress destination,
            int firstKeycode, int secondKeycode, boolean holdKey) throws Exception {
        sendUserControlPress(source, destination, firstKeycode, holdKey);
        /* Sleep less than 200ms between press and release */
        TimeUnit.MILLISECONDS.sleep(100);
        sendUserControlPress(source, destination, secondKeycode, false);
    }

    /** Sends a message to the output console of the cec-client */
    public void sendConsoleMessage(String message) throws Exception {
        checkCecClient();
        CLog.v("Sending message:: " + message);
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

    /** Gets all the messages received from the given source device during a period of duration
     * seconds.
     */
    public List<CecOperand> getAllMessages(LogicalAddress source, int duration) throws Exception {
        List<CecOperand> receivedOperands = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern = Pattern.compile("(.*>>)(.*?)" +
                "(" + source + "\\p{XDigit}):(.*)",
            Pattern.CASE_INSENSITIVE);

        while ((endTime - startTime <= (duration * 1000))) {
            if (mInputConsole.ready()) {
                String line = mInputConsole.readLine();
                if (pattern.matcher(line).matches()) {
                    CecOperand operand = CecMessage.getOperand(line);
                    if (!receivedOperands.contains(operand)) {
                        receivedOperands.add(operand);
                    }
                }
            }
            endTime = System.currentTimeMillis();
        }
        return receivedOperands;
    }


    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within default timeout. If the CEC message
     * is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecOperand expectedMessage) throws Exception {
        return checkExpectedOutput(
                targetDevice, LogicalAddress.BROADCAST, expectedMessage, DEFAULT_TIMEOUT, false);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client
     * communication channel and returns the first line that contains that message within
     * default timeout. If the CEC message is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(LogicalAddress toDevice,
                                      CecOperand expectedMessage) throws Exception {
        return checkExpectedOutput(targetDevice, toDevice, expectedMessage, DEFAULT_TIMEOUT, false);
    }

    /**
     * Looks for the broadcasted CEC expectedMessage sent from cec-client device fromDevice on the
     * cec-client communication channel and returns the first line that contains that message within
     * default timeout. If the CEC message is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedMessageFromClient(
            LogicalAddress fromDevice, CecOperand expectedMessage) throws Exception {
        return checkExpectedMessageFromClient(
                fromDevice, LogicalAddress.BROADCAST, expectedMessage);
    }

    /**
     * Looks for the CEC expectedMessage sent from cec-client device fromDevice to CEC device
     * toDevice on the cec-client communication channel and returns the first line that contains
     * that message within default timeout. If the CEC message is not found within the timeout, an
     * exception is thrown.
     */
    public String checkExpectedMessageFromClient(
            LogicalAddress fromDevice, LogicalAddress toDevice, CecOperand expectedMessage)
            throws Exception {
        return checkExpectedOutput(fromDevice, toDevice, expectedMessage, DEFAULT_TIMEOUT, true);
    }

    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within timeoutMillis. If the CEC message
     * is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecOperand expectedMessage,
                                      long timeoutMillis) throws Exception {
        return checkExpectedOutput(
                targetDevice, LogicalAddress.BROADCAST, expectedMessage, timeoutMillis, false);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client
     * communication channel and returns the first line that contains that message within
     * timeoutMillis. If the CEC message is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(LogicalAddress toDevice, CecOperand expectedMessage,
                                       long timeoutMillis) throws Exception {
        return checkExpectedOutput(targetDevice, toDevice, expectedMessage, timeoutMillis, false);
    }

    /**
     * Looks for the CEC expectedMessage sent from CEC device fromDevice to CEC device toDevice on
     * the cec-client communication channel and returns the first line that contains that message
     * within timeoutMillis. If the CEC message is not found within the timeout, an exception is
     * thrown. This method looks for the CEC messages coming from Cec-client if fromCecClient is
     * true.
     */
    public String checkExpectedOutput(
            LogicalAddress fromDevice,
            LogicalAddress toDevice,
            CecOperand expectedMessage,
            long timeoutMillis,
            boolean fromCecClient)
            throws Exception {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        String direction = fromCecClient ? "<<" : ">>";
        Pattern pattern =
                Pattern.compile(
                        "(.*"
                                + direction
                                + ")(.*?)"
                                + "("
                                + fromDevice
                                + toDevice
                                + "):"
                                + "("
                                + expectedMessage
                                + ")(.*)",
                        Pattern.CASE_INSENSITIVE);

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
     * Looks for the CEC message incorrectMessage sent to CEC device toDevice on the cec-client
     * communication channel and throws an exception if it finds the line that contains the message
     * within the default timeout. If the CEC message is not found within the timeout, function
     * returns without error.
     */
    public void checkOutputDoesNotContainMessage(LogicalAddress toDevice,
            CecOperand incorrectMessage) throws Exception {
        checkOutputDoesNotContainMessage(toDevice, incorrectMessage, "", DEFAULT_TIMEOUT);
     }

    /**
     * Looks for the CEC message incorrectMessage along with the params sent to CEC device toDevice
     * on the cec-client communication channel and throws an exception if it finds the line that
     * contains the message with its params within the default timeout. If the CEC message is not
     * found within the timeout, function returns without error.
     */
    public void checkOutputDoesNotContainMessage(
            LogicalAddress toDevice, CecOperand incorrectMessage, String params) throws Exception {
        checkOutputDoesNotContainMessage(toDevice, incorrectMessage, params, DEFAULT_TIMEOUT);
    }

    /**
     * Looks for the CEC message incorrectMessage sent to CEC device toDevice on the cec-client
     * communication channel and throws an exception if it finds the line that contains the message
     * within timeoutMillis. If the CEC message is not found within the timeout, function returns
     * without error.
     */
    public void checkOutputDoesNotContainMessage(
            LogicalAddress toDevice, CecOperand incorrectMessage, long timeoutMillis)
            throws Exception {
        checkOutputDoesNotContainMessage(toDevice, incorrectMessage, "", timeoutMillis);
    }

    /**
     * Looks for the CEC message incorrectMessage along with the params sent to CEC device toDevice
     * on the cec-client communication channel and throws an exception if it finds the line that
     * contains the message and params within timeoutMillis. If the CEC message is not found within
     * the timeout, function returns without error.
     */
    public void checkOutputDoesNotContainMessage(
            LogicalAddress toDevice, CecOperand incorrectMessage, String params, long timeoutMillis)
            throws Exception {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern =
                Pattern.compile(
                        "(.*>>)(.*?)"
                                + "("
                                + targetDevice
                                + toDevice
                                + "):"
                                + "("
                                + incorrectMessage
                                + params
                                + ")(.*)",
                        Pattern.CASE_INSENSITIVE);

        while ((endTime - startTime <= timeoutMillis)) {
            if (mInputConsole.ready()) {
                String line = mInputConsole.readLine();
                if (pattern.matcher(line).matches()) {
                    CLog.v("Found " + incorrectMessage.name() + " in " + line);
                    throw new Exception("Found " + incorrectMessage.name() + " to " + toDevice +
                            " with params " + CecMessage.getParamsAsString(line));
                }
            }
            endTime = System.currentTimeMillis();
        }
     }

    /** Returns the device type that the cec-client has started as. */
    public LogicalAddress getSelfDevice() {
        return selfDevice;
    }

    /** Set the physical address of the cec-client instance */
    public void setPhysicalAddress(int newPhysicalAddress) throws Exception {
        String command =
                String.format(
                        "pa %02d %02d",
                        (newPhysicalAddress & 0xFF00) >> 8, newPhysicalAddress & 0xFF);
        sendConsoleMessage(command);
        physicalAddress = newPhysicalAddress;
    }

    /** Get the physical address of the cec-client instance, will return 0xFFFF if uninitialised */
    public int getPhysicalAddress() {
        return physicalAddress;
    }

    /**
     * Kills the cec-client process that was created in init().
     */
    private void killCecProcess() {
        try {
            checkCecClient();
            sendConsoleMessage(CecClientMessage.QUIT_CLIENT.toString());
            mOutputConsole.close();
            mInputConsole.close();
            mCecClientInitialised = false;
            if (!mCecClient.waitFor(MILLISECONDS_TO_READY, TimeUnit.MILLISECONDS)) {
                /* Use a pkill cec-client if the cec-client process is not dead in spite of the
                 * quit above.
                 */
                List<String> commands = new ArrayList<>();
                Process killProcess;
                commands.add("pkill");
                commands.add("cec-client");
                killProcess = RunUtil.getDefault().runCmdInBackground(commands);
                killProcess.waitFor();
            }
        } catch (Exception e) {
            /* If cec-client is not running, do not throw an exception, just return. */
            CLog.w(new Exception("Unable to close cec-client", e));
        }
    }
}
