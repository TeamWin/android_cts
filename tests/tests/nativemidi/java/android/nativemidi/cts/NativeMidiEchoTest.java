/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.nativemidi.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDevice.MidiConnection;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiDeviceInfo.PortInfo;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiReceiver;
import android.media.midi.MidiSender;
import android.os.Bundle;
import android.test.AndroidTestCase;

import java.io.IOException;

import java.util.ArrayList;
import android.util.Log;
import java.util.Random;

/**
 * Test MIDI using a virtual MIDI device that echos input to output.
 */
public class NativeMidiEchoTest extends AndroidTestCase {
    // Load the JNI shared library.
    static {
        System.loadLibrary("nativemidi_jni");
    }

    private static final String TAG = "NativeMidiEchoTest";

    public static final String TEST_MANUFACTURER = "AndroidCTS";
    public static final String ECHO_PRODUCT = "NativeMidiEcho";

    private static final long NANOS_PER_MSEC = 1000L * 1000L;

    // On a fast device in 2016, the test fails if timeout is 3 but works if it is 4.
    // So this timeout value is very generous.
    private static final int TIMEOUT_OPEN_MSEC = 2000; // arbitrary

    private MidiManager mMidiManager;

    private MidiDevice mEchoDevice;
    private long mNativeDeviceHandle;
    
    // (Native code) attributes associated with a test/EchoServer instance.
    private long mTestContext;

    private Random mRandom = new Random(1972941337);

    // Listens for an asynchronous device open and notifies waiting foreground
    // test.
    class MyTestOpenCallback implements MidiManager.OnDeviceOpenedListener {
        MidiDevice mDevice;

        @Override
        public synchronized void onDeviceOpened(MidiDevice device) {
            mDevice = device;
            notifyAll();
        }

        public synchronized MidiDevice waitForOpen(int msec)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + msec;
            long timeRemaining = msec;
            while (mDevice == null && timeRemaining > 0) {
                wait(timeRemaining);
                timeRemaining = deadline - System.currentTimeMillis();
            }
            return mDevice;
        }
    }

    public NativeMidiEchoTest() {
        super();
        Log.i(TAG, "NativeMidiEchoTest() [JAVA ctor]");
        mTestContext = 0;
    }

    //
    // Helpers
    //
    private boolean hasMidiSupport() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_MIDI);
    }

    private void compareMessages(byte[] buffer, long timestamp, NativeMidiMessage nativeMsg) {
        assertEquals("byte count of message", buffer.length, nativeMsg.len);
        assertEquals("timestamp in message", timestamp, nativeMsg.timestamp);

         for (int index = 0; index < buffer.length; index++) {
            assertEquals("message byte[" + index + "]", buffer[index] & 0x0FF,
                    nativeMsg.buffer[index] & 0x0FF);
        }
    }

    private byte[] generateRandomMessage(int len) {
        byte[] buffer = new byte[len];
        for(int index = 0; index < len; index++) {
            buffer[index] = (byte)(mRandom.nextInt() & 0xFF);
        }
        return buffer;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Log.i(TAG, "++++ setUp() mContext:" + mContext);
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            assertTrue("FEATURE_MIDI Not Supported.", false);
            return; // Not supported so don't test it.
        }
        mMidiManager = (MidiManager)mContext.getSystemService(Context.MIDI_SERVICE);
        assertTrue("Could not get the MidiManger.", mMidiManager != null);

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        Log.i(TAG, "++++ tearDown()");
        mMidiManager = null;
    }

    // Search through the available devices for the ECHO loop-back device.
    protected MidiDeviceInfo findEchoDevice() {
        MidiDeviceInfo[] infos = mMidiManager.getDevices();
        MidiDeviceInfo echoInfo = null;
        for (MidiDeviceInfo info : infos) {
            Bundle properties = info.getProperties();
            String manufacturer = (String) properties.get(
                    MidiDeviceInfo.PROPERTY_MANUFACTURER);

            if (TEST_MANUFACTURER.equals(manufacturer)) {
                String product = (String) properties.get(
                        MidiDeviceInfo.PROPERTY_PRODUCT);
                if (ECHO_PRODUCT.equals(product)) {
                    echoInfo = info;
                    break;
                }
            }
        }
        assertTrue("could not find " + ECHO_PRODUCT, echoInfo != null);
        return echoInfo;
    }

    protected long setUpEchoServer() throws Exception {
        // Log.i(TAG, "++++ setUpEchoServer()");
        MidiDeviceInfo echoInfo = findEchoDevice();

        // Open device.
        MyTestOpenCallback callback = new MyTestOpenCallback();
        mMidiManager.openDevice(echoInfo, callback, null);
        mEchoDevice = callback.waitForOpen(TIMEOUT_OPEN_MSEC);
        assertTrue("could not open " + ECHO_PRODUCT, mEchoDevice != null);

        // Query echo service directly to see if it is getting status updates.
        NativeMidiEchoTestService echoService = NativeMidiEchoTestService.getInstance();

        try {
            mNativeDeviceHandle = mEchoDevice.mirrorToNative();
        } catch (IOException ex) {
            Log.i(TAG, "! Failed to mirror to native !" + ex.getMessage() + "\n");
            assertTrue("! Failed to mirror to native !", false);
        }

        long testContext = allocTestContext();
        assertTrue("couldn't allocate test context.", testContext != 0);

        // Open Input
        int result =
                startWritingMidi(testContext, mNativeDeviceHandle, 0/*mPortNumber*/);
        assertEquals("Bad start writing (native) MIDI", 0, result);

        // Open Output
        result = startReadingMidi(testContext, mNativeDeviceHandle, 0/*mPortNumber*/);
        assertEquals("Bad start Reading (native) MIDI", 0, result);

        return testContext;
    }

    protected void tearDownEchoServer(long testContext) throws IOException {
        // Log.i(TAG, "++++ tearDownEchoServer()");
        // Query echo service directly to see if it is getting status updates.
        NativeMidiEchoTestService echoService = NativeMidiEchoTestService.getInstance();

        int result;

        // Stop inputs
        result = stopReadingMidi(testContext);
        assertEquals("Bad stop reading (native) MIDI", 0, result);
        result = stopReadingMidi(testContext); // should be safe to close twice
        assertTrue("Didn't get error code on second stop reading MIDI.", result != 0);

        // Stop outputs
        result = stopWritingMidi(testContext);
        assertEquals("Bad stop writing (native) MIDI", 0, result);
        result = stopWritingMidi(testContext); // should be safe to close twice, but get an error
        assertTrue("Didn't get error code on second stop writing MIDI.", result != 0);

        freeTestContext(testContext);

        mEchoDevice.close();
    }

    // Is the MidiManager supported?
    public void testMidiManager() throws Exception {
        Log.i(TAG, "++++ testMidiManager() this:" + System.identityHashCode(this));

        if (!hasMidiSupport()) {
            return; // Nothing to test
        }

        assertNotNull("MidiManager not supported.", mMidiManager);

        // There should be at least one device for the Echo server.
        MidiDeviceInfo[] infos = mMidiManager.getDevices();
        assertNotNull("device list was null", infos);
        assertTrue("device list was empty", infos.length >= 1);

        // Log.i(TAG, "++++ Num MIDI Devices:" + infos.length);
    }

    public void testSetupTeardownEchoServer() throws Exception {
        Log.i(TAG, "++++ testSetupTeardownEchoServer() this:" + System.identityHashCode(this));

        long testContext = setUpEchoServer();
        tearDownEchoServer(testContext);
    }

    public void testSendData() throws Exception {
        Log.i(TAG, "++++  testSendData() this:" + System.identityHashCode(this));
        long testContext = setUpEchoServer();

        clearCounters(testContext);

        assertEquals("Didn't start with 0 sends", 0, getNumSends(testContext));
        assertEquals("Didn't start with 0 bytes sent", 0, getNumBytesSent(testContext));

        final byte[] buffer = {
                (byte) 0x93, 0x47, 0x52
        };
        long timestamp = 0x0123765489ABFEDCL;
        writeMidi(testContext, buffer, 0, buffer.length);

        assertTrue("Didn't get 1 send", getNumBytesSent(testContext) == buffer.length);
        assertEquals("Didn't get right number of bytes sent",
                buffer.length, getNumBytesSent(testContext));

        tearDownEchoServer(testContext);
    }

    public void testWriteGetMaxMessageSize() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        assertTrue("Invalid write buffer size", getMaxWriteBufferSize(testContext) > 0);
        // this is based on some "inside baseball" and may well change
        // assertEquals("write buffer size != 1015", getMaxWriteBufferSize(testContext), 1015);

        tearDownEchoServer(testContext);
    }

    public void testEchoSmallMessage() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        final byte[] buffer = {
                (byte) 0x93, 0x47, 0x52
        };
        long timestamp = 0x0123765489ABFEDCL;

        writeMidiWithTimestamp(testContext, buffer, 0, 0, timestamp); // should be a NOOP
        writeMidiWithTimestamp(testContext, buffer, 0, buffer.length, timestamp);
        writeMidiWithTimestamp(testContext, buffer, 0, 0, timestamp); // should be a NOOP

        // Wait for message to pass quickly through echo service.
        final int numMessages = 1;
        final int timeoutMs = 20;
        Thread.sleep(timeoutMs);
        assertEquals("number of messages.", numMessages, getNumReceivedMessages(testContext));

        NativeMidiMessage message = getReceivedMessageAt(testContext, 0);
        compareMessages(buffer, timestamp, message);

        tearDownEchoServer(testContext);
    }

    public void testEchoNMessages() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        int N = 100;
        int messageLen;
        int maxMessageLen = getMaxWriteBufferSize(testContext);
        byte[][] buffers = new byte[N][];
        long timestamps[] = new long[N];
        for(int buffIndex = 0; buffIndex < N; buffIndex++) {
            messageLen = (int)(mRandom.nextFloat() * (maxMessageLen-1)) + 1;
            buffers[buffIndex] = generateRandomMessage(messageLen);
            timestamps[buffIndex] = mRandom.nextLong();
        }

        for(int msgIndex = 0; msgIndex < N; msgIndex++) {
            writeMidiWithTimestamp(testContext, buffers[msgIndex], 0, buffers[msgIndex].length,
                    timestamps[msgIndex]);
        }

        // Wait for message to pass quickly through echo service.
        final int timeoutMs = 20;
        Thread.sleep(timeoutMs);

        // correct number of messages
        assertEquals("number of messages.", N, getNumReceivedMessages(testContext));

        // correct data & order?
        for(int msgIndex = 0; msgIndex < N; msgIndex++) {
            NativeMidiMessage message = getReceivedMessageAt(testContext, msgIndex);
            compareMessages(buffers[msgIndex], timestamps[msgIndex], message);
        }

        tearDownEchoServer(testContext);
    }

    public void testFlushMessages() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        int N = 7;
        int messageLen;
        int maxMessageLen = getMaxWriteBufferSize(testContext);
        byte[][] buffers = new byte[N][];
        long timestamps[] = new long[N];
        for(int buffIndex = 0; buffIndex < N; buffIndex++) {
            messageLen = (int)(mRandom.nextFloat() * (maxMessageLen-1)) + 1;
            buffers[buffIndex] = generateRandomMessage(messageLen);
            timestamps[buffIndex] = mRandom.nextLong();
        }

        for(int msgIndex = 0; msgIndex < N; msgIndex++) {
            writeMidiWithTimestamp(testContext, buffers[msgIndex], 0, buffers[msgIndex].length,
                    timestamps[msgIndex]);
        }

        // Wait for message to pass quickly through echo service.
        final int timeoutMs = 20;
        Thread.sleep(timeoutMs);

        int result = flushSentMessages(testContext);
        assertEquals("flush messages failed", 0, result);

        // correct number of messages
        assertEquals("number of messages.", N, getNumReceivedMessages(testContext));

        // correct data & order?
        for(int msgIndex = 0; msgIndex < N; msgIndex++) {
            NativeMidiMessage message = getReceivedMessageAt(testContext, msgIndex);
            compareMessages(buffers[msgIndex], timestamps[msgIndex], message);
        }

        tearDownEchoServer(testContext);
    }

    public void testFailHugeMessage() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        int maxMessageLen = getMaxWriteBufferSize(testContext);
        byte[] buffer = generateRandomMessage(maxMessageLen * 2);
        int result = writeMidi(testContext, buffer, 0, buffer.length);
        assertTrue("Huge write didn't fail?", result < 0);

        buffer = generateRandomMessage(maxMessageLen + 1);
        result = writeMidi(testContext, buffer, 0, buffer.length);
        assertTrue("Kinda-big write didn't fail?", result < 0);

        tearDownEchoServer(testContext);
    }

    public void testNativeEchoLatency() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        final int numMessages = 10;
        final long maxLatencyNanos = 15 * NANOS_PER_MSEC; // generally < 3 msec on N6
        byte[] buffer = { (byte) 0x93, 0, 64 };

        // Send multiple messages in a burst.
        for (int index = 0; index < numMessages; index++) {
            buffer[1] = (byte) (60 + index);
            writeMidiWithTimestamp(testContext, buffer, 0, buffer.length, System.nanoTime());
        }

        // Wait for messages to pass quickly through echo service.
        final int timeoutMs = 20;
        Thread.sleep(timeoutMs);
        assertEquals("number of messages.", numMessages, getNumReceivedMessages(testContext));

        for (int msgIndex = 0; msgIndex < numMessages; msgIndex++) {
            NativeMidiMessage message = getReceivedMessageAt(testContext, msgIndex);
            assertEquals("message index", (byte) (60 + msgIndex), message.buffer[1]);
            long elapsedNanos = message.timeReceived - message.timestamp;
            Log.i(TAG, "---- elapsed:" + elapsedNanos);
            // If this test fails then there may be a problem with the thread scheduler
            // or there may be kernel activity that is blocking execution at the user level.
            assertTrue("MIDI round trip latency[" + msgIndex + "] too large, " + elapsedNanos
                    + " nanoseconds",
                    (elapsedNanos < maxLatencyNanos));
        }

        tearDownEchoServer(testContext);
    }

    public void testEchoNMessages_PureNative() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        int N = 2;
        int messageLen;
        int maxMessageLen = getMaxWriteBufferSize(testContext);
        byte[][] buffers = new byte[N][];
        long timestamps[] = new long[N];
        for(int buffIndex = 0; buffIndex < N; buffIndex++) {
            messageLen = (int)(mRandom.nextFloat() * (maxMessageLen-1)) + 1;
            buffers[buffIndex] = generateRandomMessage(messageLen);
            timestamps[buffIndex] = mRandom.nextLong();
        }

        for(int msgIndex = 0; msgIndex < N; msgIndex++) {
            writeMidiWithTimestamp(testContext, buffers[msgIndex], 0, buffers[msgIndex].length,
                    timestamps[msgIndex]);
        }

        // Wait for message to pass quickly through echo service.
        final int numMessages = N;
        final int timeoutMs = 20;
        Thread.sleep(timeoutMs);

        int result = matchNativeMessages(testContext);
        assertEquals("Native Compare Test Failed", result, 0);

        tearDownEchoServer(testContext);
    }

    public void testNativeEchoLatency_PureNative() throws Exception {
        if (!hasMidiSupport()) {
            return; // nothing to test
        }

        long testContext = setUpEchoServer();

        final int numMessages = 10;
        final long maxLatencyNanos = 15 * NANOS_PER_MSEC; // generally < 3 msec on N6
        byte[] buffer = { (byte) 0x93, 0, 64 };

        // Send multiple messages in a burst.
        for (int index = 0; index < numMessages; index++) {
            buffer[1] = (byte) (60 + index);
            writeMidiWithTimestamp(testContext, buffer, 0, buffer.length, System.nanoTime());
        }

        // Wait for messages to pass quickly through echo service.
        final int timeoutMs = 20;
        Thread.sleep(timeoutMs);
        assertEquals("number of messages.", numMessages, getNumReceivedMessages(testContext));

        int result = checkNativeLatency(testContext, maxLatencyNanos);
        assertEquals("failed pure native latency test.", 0, result);

        tearDownEchoServer(testContext);
    }

    // Native Routines
    public static native void initN();

    public static native long allocTestContext();
    public static native void freeTestContext(long context);

    public native int startReadingMidi(long ctx, long deviceHandle, int portNumber);
    public native int stopReadingMidi(long ctx);

    public native int startWritingMidi(long ctx, long deviceHandle, int portNumber);
    public native int stopWritingMidi(long ctx);
    public native int getMaxWriteBufferSize(long ctx);

    public native int writeMidi(long ctx, byte[] data, int offset, int length);
    public native int writeMidiWithTimestamp(long ctx, byte[] data, int offset, int length,
            long timestamp);
    public native int flushSentMessages(long ctx);

    // Status - Counters
    public native void clearCounters(long ctx);
    public native int getNumSends(long ctx);
    public native int getNumBytesSent(long ctx);
    public native int getNumReceives(long ctx);
    public native int getNumBytesReceived(long ctx);

    // Status - Received Messages
    public native int getNumReceivedMessages(long ctx);
    public native NativeMidiMessage getReceivedMessageAt(long ctx, int index);

    // Pure Native Checks
    public native int matchNativeMessages(long ctx);
    public native int checkNativeLatency(long ctx, long maxLatencyNanos);
}
