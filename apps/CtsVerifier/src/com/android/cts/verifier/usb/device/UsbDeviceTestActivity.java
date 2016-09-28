/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.cts.verifier.usb.device;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import org.junit.AssumptionViolatedException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

public class UsbDeviceTestActivity extends PassFailButtons.Activity {
    private static final String ACTION_USB_PERMISSION =
            "com.android.cts.verifier.usb.device.USB_PERMISSION";
    private static final String LOG_TAG = UsbDeviceTestActivity.class.getSimpleName();
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int MAX_BUFFER_SIZE = 16384;

    private UsbManager mUsbManager;
    private final BroadcastReceiver mUsbDeviceConnectionReceiver;

    /**
     * Run a {@link Invokable} and expect a {@link Throwable} of a certain type.
     *
     * @param r             The {@link Invokable} to run
     * @param expectedClass The expected {@link Throwable} type
     */
    private static void assertException(@NonNull Invokable r,
            @NonNull Class<? extends Throwable> expectedClass) throws Throwable {
        try {
            r.run();
        } catch (Throwable e) {
            if (e.getClass().isAssignableFrom(expectedClass)) {
                return;
            } else {
                Log.e(LOG_TAG, "Expected: " + expectedClass.getName() + ", got: "
                        + e.getClass().getName());
                throw e;
            }
        }

        throw new AssertionError("No throwable thrown");
    }

    public UsbDeviceTestActivity() {
        mUsbDeviceConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (UsbDeviceTestActivity.this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    switch (intent.getAction()) {
                        case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                            mUsbManager.requestPermission(device,
                                    PendingIntent.getBroadcast(UsbDeviceTestActivity.this, 0,
                                            new Intent(ACTION_USB_PERMISSION), 0));
                            break;
                        case ACTION_USB_PERMISSION:
                            boolean granted = intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED, false);

                            if (granted) {
                                if (!AoapInterface.isDeviceInAoapMode(device)) {
                                    UsbDeviceConnection connection = mUsbManager.openDevice(device);
                                    try {
                                        makeThisDeviceAnAccessory(connection);
                                    } finally {
                                        connection.close();
                                    }
                                } else {
                                    runTests(device);
                                }
                            } else {
                                fail("Permission to connect to " + device.getProductName()
                                        + " not granted", null);
                            }
                            break;
                    }
                }
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.usb_main);
        setInfoResources(R.string.usb_device_test, R.string.usb_device_test_info, -1);

        mUsbManager = getSystemService(UsbManager.class);

        getPassButton().setEnabled(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        registerReceiver(mUsbDeviceConnectionReceiver, filter);
    }

    /**
     * Indicate that the test failed.
     */
    private void fail(@Nullable String s, @Nullable Throwable e) {
        Log.e(LOG_TAG, s, e);
        setTestResultAndFinish(false);
    }

    /**
     * Converts the device under test into an Android accessory. Accessories are USB hosts that are
     * detected on the device side via {@link UsbManager#getAccessoryList()}.
     *
     * @param connection The connection to the USB device
     */
    private void makeThisDeviceAnAccessory(@NonNull UsbDeviceConnection connection) {
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_MANUFACTURER,
                "Android");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_MODEL,
                "Android device");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_DESCRIPTION,
                "Android device running CTS verifier");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_VERSION, "1");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_URI,
                "https://source.android.com/compatibility/cts/verifier.html");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_SERIAL, "0");
        AoapInterface.sendAoapStart(connection);
    }

    /**
     * Switch to next test.
     *
     * @param connection   Connection to the USB device
     * @param in           The in endpoint
     * @param out          The out endpoint
     * @param nextTestName The name of the new test
     */
    private void nextTest(@NonNull UsbDeviceConnection connection, @NonNull UsbEndpoint in,
            @NonNull UsbEndpoint out, @NonNull CharSequence nextTestName) {
        Log.v(LOG_TAG, "Finishing previous test");

        // Send name of next test
        assertTrue(nextTestName.length() <= Byte.MAX_VALUE);
        ByteBuffer nextTestNameBuffer = Charset.forName("UTF-8")
                .encode(CharBuffer.wrap(nextTestName));
        byte[] sizeBuffer = { (byte) nextTestNameBuffer.limit() };
        int numSent = connection.bulkTransfer(out, sizeBuffer, 1, 0);
        assertEquals(1, numSent);

        numSent = connection.bulkTransfer(out, nextTestNameBuffer.array(),
                nextTestNameBuffer.limit(), 0);
        assertEquals(nextTestNameBuffer.limit(), numSent);

        // Receive result of last test
        byte[] lastTestResultBytes = new byte[1];
        int numReceived = connection.bulkTransfer(in, lastTestResultBytes,
                lastTestResultBytes.length, TIMEOUT_MILLIS);
        assertEquals(1, numReceived);
        assertEquals(1, lastTestResultBytes[0]);

        // Send ready signal
        sizeBuffer[0] = 42;
        numSent = connection.bulkTransfer(out, sizeBuffer, 1, 0);
        assertEquals(1, numSent);

        Log.i(LOG_TAG, "Running test \"" + nextTestName + "\"");
    }

    /**
     * Send some data and expect it to be echoed back.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param out        The out endpoint
     * @param size       The number of bytes to send
     */
    private void echoBulkTransfer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out, int size) {
        byte[] sentBuffer = new byte[size];
        Random r = new Random();
        r.nextBytes(sentBuffer);

        int numSent = connection.bulkTransfer(out, sentBuffer, sentBuffer.length, 0);
        assertEquals(size, numSent);

        byte[] receivedBuffer = new byte[size];
        int numReceived = connection.bulkTransfer(in, receivedBuffer, receivedBuffer.length,
                TIMEOUT_MILLIS);
        assertEquals(size, numReceived);

        assertArrayEquals(sentBuffer, receivedBuffer);
    }

    /**
     * Send some data and expect it to be echoed back (but have an offset in the send buffer).
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param out        The out endpoint
     * @param size       The number of bytes to send
     */
    private void echoBulkTransferOffset(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out, int offset, int size) {
        byte[] sentBuffer = new byte[offset + size];
        Random r = new Random();
        r.nextBytes(sentBuffer);

        int numSent = connection.bulkTransfer(out, sentBuffer, offset, size, 0);
        assertEquals(size, numSent);

        byte[] receivedBuffer = new byte[offset + size];
        int numReceived = connection.bulkTransfer(in, receivedBuffer, offset, size, TIMEOUT_MILLIS);
        assertEquals(size, numReceived);

        for (int i = 0; i < offset + size; i++) {
            if (i < offset) {
                assertEquals(0, receivedBuffer[i]);
            } else {
                assertEquals(sentBuffer[i], receivedBuffer[i]);
            }
        }
    }

    /**
     * Send a transfer that is larger than MAX_BUFFER_SIZE.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param out        The out endpoint
     */
    private void echoOversizedBulkTransfer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out) {
        int totalSize = MAX_BUFFER_SIZE * 3 / 2;
        byte[] sentBuffer = new byte[totalSize];
        Random r = new Random();
        r.nextBytes(sentBuffer);

        int numSent = connection.bulkTransfer(out, sentBuffer, sentBuffer.length, 0);

        // Buffer will only be partially transferred
        assertEquals(MAX_BUFFER_SIZE, numSent);

        byte[] receivedBuffer = new byte[totalSize];
        int numReceived = connection.bulkTransfer(in, receivedBuffer, receivedBuffer.length,
                TIMEOUT_MILLIS);

        // All beyond MAX_BUFFER_SIZE was not send, hence it will not be echoed back
        assertEquals(MAX_BUFFER_SIZE, numReceived);

        for (int i = 0; i < totalSize; i++) {
            if (i < MAX_BUFFER_SIZE) {
                assertEquals(sentBuffer[i], receivedBuffer[i]);
            } else {
                assertEquals(0, receivedBuffer[i]);
            }
        }
    }

    /**
     * Receive a transfer that is larger than MAX_BUFFER_SIZE
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     */
    private void receiveOversizedBulkTransfer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in) {
        // Buffer will be received as two transfers
        byte[] receivedBuffer1 = new byte[MAX_BUFFER_SIZE * 3 / 2];
        int numReceived = connection.bulkTransfer(in, receivedBuffer1, receivedBuffer1.length,
                TIMEOUT_MILLIS);
        assertEquals(MAX_BUFFER_SIZE, numReceived);

        byte[] receivedBuffer2 = new byte[MAX_BUFFER_SIZE / 2];
        numReceived = connection.bulkTransfer(in, receivedBuffer2, receivedBuffer2.length,
                TIMEOUT_MILLIS);
        assertEquals(MAX_BUFFER_SIZE / 2, numReceived);

        assertEquals(1, receivedBuffer1[0]);
        assertEquals(2, receivedBuffer1[MAX_BUFFER_SIZE - 1]);
        assertEquals(3, receivedBuffer2[0]);
        assertEquals(4, receivedBuffer2[MAX_BUFFER_SIZE / 2 - 1]);
    }

    /**
     * Receive data but supply an empty buffer. This causes the thread to block until any data is
     * sent. The zero-sized receive-transfer just returns without data and the next transfer can
     * actually read the data.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param buffer     The buffer to use
     * @param offset     The offset into the buffer
     * @param length     The lenght of data to receive
     */
    private void receiveWithEmptyBuffer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @Nullable byte[] buffer, int offset, int length) {
        long startTime = System.currentTimeMillis();
        int numReceived;
        if (offset == 0) {
            numReceived = connection.bulkTransfer(in, buffer, length, 0);
        } else {
            numReceived = connection.bulkTransfer(in, buffer, offset, length, 0);
        }
        long endTime = System.currentTimeMillis();
        assertEquals(-1, numReceived);

        // The transfer should block
        assertTrue(endTime - startTime > 100);

        numReceived = connection.bulkTransfer(in, new byte[1], 1, 0);
        assertEquals(1, numReceived);
    }

    /**
     * Tests {@link UsbDeviceConnection#controlTransfer}.
     *
     * <p>Note: We cannot send ctrl data to the device as it thinks it talks to an accessory, hence
     * the testing is currently limited.</p>
     *
     * @param connection The connection to use for testing
     *
     * @throws Throwable
     */
    private void ctrlTransferTests(@NonNull UsbDeviceConnection connection) throws Throwable {
        assertException(() -> connection.controlTransfer(0, 0, 0, 0, null, 1, 0),
                IllegalArgumentException.class);

        assertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], -1, 0),
                IllegalArgumentException.class);

        assertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], 2, 0),
                IllegalArgumentException.class);

        assertException(() -> connection.controlTransfer(0, 0, 0, 0, null, 0, 1, 0),
                IllegalArgumentException.class);

        assertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], 0, -1, 0),
                IllegalArgumentException.class);

        assertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], 1, 1, 0),
                IllegalArgumentException.class);
    }

    /**
     * Search an {@link UsbInterface} for an {@link UsbEndpoint endpoint} of a certain direction.
     *
     * @param iface     The interface to search
     * @param direction The direction the endpoint is for.
     *
     * @return The first endpoint found or {@link null}.
     */
    private @NonNull UsbEndpoint getEndpoint(@NonNull UsbInterface iface, int direction) {
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint ep = iface.getEndpoint(i);
            if (ep.getDirection() == direction) {
                return ep;
            }
        }

        throw new IllegalStateException("Could not find " + direction + " endpoint in "
                + iface.getName());
    }

    /**
     * Send a USB request and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     * @param size            The size of the request to send
     * @param originalSize    The size of the original buffer
     * @param sliceStart      The start of the final buffer in the original buffer
     * @param sliceEnd        The end of the final buffer in the original buffer
     * @param positionInSlice The position parameter in the final buffer
     * @param limitInSlice    The limited parameter in the final buffer
     * @param useDirectBuffer If the buffer to be used should be a direct buffer
     */
    private void echoUsbRequest(@NonNull UsbDeviceConnection connection, @NonNull UsbEndpoint in,
            @NonNull UsbEndpoint out, int size, int originalSize, int sliceStart, int sliceEnd,
            int positionInSlice, int limitInSlice, boolean useDirectBuffer) {
        Random random = new Random();

        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);
        Object sentClientData = new Object();
        sent.setClientData(sentClientData);

        UsbRequest receive = new UsbRequest();
        isInited = receive.initialize(connection, in);
        assertTrue(isInited);
        Object receiveClientData = new Object();
        receive.setClientData(receiveClientData);

        ByteBuffer bufferSent;
        if (useDirectBuffer) {
            bufferSent = ByteBuffer.allocateDirect(originalSize);
        } else {
            bufferSent = ByteBuffer.allocate(originalSize);
        }
        for (int i = 0; i < originalSize; i++) {
            bufferSent.put((byte) random.nextInt());
        }
        bufferSent.position(sliceStart);
        bufferSent.limit(sliceEnd);
        ByteBuffer bufferSentSliced = bufferSent.slice();
        bufferSentSliced.position(positionInSlice);
        bufferSentSliced.limit(limitInSlice);

        bufferSent.position(0);
        bufferSent.limit(originalSize);

        ByteBuffer bufferReceived;
        if (useDirectBuffer) {
            bufferReceived = ByteBuffer.allocateDirect(originalSize);
        } else {
            bufferReceived = ByteBuffer.allocate(originalSize);
        }
        bufferReceived.position(sliceStart);
        bufferReceived.limit(sliceEnd);
        ByteBuffer bufferReceivedSliced = bufferReceived.slice();
        bufferReceivedSliced.position(positionInSlice);
        bufferReceivedSliced.limit(limitInSlice);

        bufferReceived.position(0);
        bufferReceived.limit(originalSize);

        boolean wasQueued = receive.queue(bufferReceivedSliced, size);
        assertTrue(wasQueued);
        wasQueued = sent.queue(bufferSentSliced, size);
        assertTrue(wasQueued);

        for (int reqRun = 0; reqRun < 2; reqRun++) {
            UsbRequest finished;

            try {
                finished = connection.requestWait();
            } catch (IllegalArgumentException e) {
                if (size > bufferSentSliced.limit() || size > bufferReceivedSliced.limit()) {
                    Log.e(LOG_TAG, "Expected failure", e);
                    continue;
                } else {
                    throw e;
                }
            }

            // Should we have gotten a failure?
            if (finished == receive) {
                // We should have gotten an exception if size > limit
                assumeTrue(bufferReceivedSliced.limit() >= size);

                assertEquals(size, bufferReceivedSliced.position());

                for (int i = 0; i < size; i++) {
                    if (i < size) {
                        assertEquals(bufferSent.get(i), bufferReceived.get(i));
                    } else {
                        assertEquals(0, bufferReceived.get(i));
                    }
                }

                assertSame(receiveClientData, finished.getClientData());
                assertSame(in, finished.getEndpoint());
            } else {
                assertEquals(size, bufferSentSliced.position());

                // We should have gotten an exception if size > limit
                assumeTrue(bufferSentSliced.limit() >= size);
                assertSame(sent, finished);
                assertSame(sentClientData, finished.getClientData());
                assertSame(out, finished.getEndpoint());
            }
            finished.close();
        }
    }

    /**
     * Send a USB request and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     * @param size            The size of the request to send
     * @param useDirectBuffer If the buffer to be used should be a direct buffer
     */
    private void echoUsbRequest(@NonNull UsbDeviceConnection connection, @NonNull UsbEndpoint in,
            @NonNull UsbEndpoint out, int size, boolean useDirectBuffer) {
        echoUsbRequest(connection, in, out, size, size, 0, size, 0, size, useDirectBuffer);
    }

    /**
     * Send a USB request which more than the allowed size and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     */
    private void echoOversizedUsbRequest(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out) {
        Random random = new Random();
        int totalSize = MAX_BUFFER_SIZE * 3 / 2;

        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);

        UsbRequest receive = new UsbRequest();
        isInited = receive.initialize(connection, in);
        assertTrue(isInited);

        byte[] sentBytes = new byte[totalSize];
        random.nextBytes(sentBytes);
        ByteBuffer bufferSent = ByteBuffer.wrap(sentBytes);

        byte[] receivedBytes = new byte[totalSize];
        ByteBuffer bufferReceived = ByteBuffer.wrap(receivedBytes);

        boolean wasQueued = receive.queue(bufferReceived, totalSize);
        assertTrue(wasQueued);
        wasQueued = sent.queue(bufferSent, totalSize);
        assertTrue(wasQueued);

        for (int requestNum = 0; requestNum < 2; requestNum++) {
            UsbRequest finished = connection.requestWait();
            if (finished == receive) {
                // size beyond MAX_BUFFER_SIZE is ignored
                for (int i = 0; i < totalSize; i++) {
                    if (i < MAX_BUFFER_SIZE) {
                        assertEquals(sentBytes[i], receivedBytes[i]);
                    } else {
                        assertEquals(0, receivedBytes[i]);
                    }
                }
            } else {
                assertSame(sent, finished);
            }
            finished.close();
        }
    }

    /**
     * Send a USB request with size 0.
     *
     * @param connection      The connection to use
     * @param out             The endpoint to send requests to
     */
    private void sendZeroLengthRequest(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint out, boolean useDirectBuffer) {
        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);

        ByteBuffer buffer;
        if (useDirectBuffer) {
            buffer = ByteBuffer.allocateDirect(0);
        } else {
            buffer = ByteBuffer.allocate(0);
        }

        boolean isQueued = sent.queue(buffer, 0);
        assumeTrue(isQueued);
        UsbRequest finished = connection.requestWait();
        assertSame(finished, sent);
        finished.close();
    }

    /**
     * Receive a USB request with size 0.
     *
     * @param connection      The connection to use
     * @param in             The endpoint to recevie requests from
     */
    private void receiveZeroLengthRequest(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, boolean useDirectBuffer) {
        UsbRequest zeroReceived = new UsbRequest();
        boolean isInited = zeroReceived.initialize(connection, in);
        assertTrue(isInited);

        UsbRequest oneReceived = new UsbRequest();
        isInited = oneReceived.initialize(connection, in);
        assertTrue(isInited);

        ByteBuffer buffer;
        if (useDirectBuffer) {
            buffer = ByteBuffer.allocateDirect(0);
        } else {
            buffer = ByteBuffer.allocate(0);
        }

        ByteBuffer buffer1;
        if (useDirectBuffer) {
            buffer1 = ByteBuffer.allocateDirect(1);
        } else {
            buffer1 = ByteBuffer.allocate(1);
        }

        boolean isQueued = zeroReceived.queue(buffer, 0);
        assumeTrue(isQueued);
        isQueued = oneReceived.queue(buffer1, 0);
        assumeTrue(isQueued);

        // We expect both to be returned after some time
        ArrayList<UsbRequest> finished = new ArrayList<>(2);

        // We expect both request to come back after the delay, but then quickly
        long startTime = System.currentTimeMillis();
        finished.add(connection.requestWait());
        long firstReturned = System.currentTimeMillis();
        finished.add(connection.requestWait());
        long secondReturned = System.currentTimeMillis();

        assumeTrue(firstReturned - startTime > 100);
        assumeTrue(secondReturned - firstReturned < 100);

        assertTrue(finished.contains(zeroReceived));
        assertTrue(finished.contains(oneReceived));
    }

    /**
     * Tests {@link UsbRequest} and {@link UsbDeviceConnection#requestWait()}.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     * @throws Throwable
     */
    private void usbRequestTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // Find bulk in and out endpoints
        assumeTrue(iface.getEndpointCount() == 2);
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        // Single threaded send and receive
        nextTest(connection, in, out, "Echo 1 byte");
        echoUsbRequest(connection, in, out, 1, true);

        nextTest(connection, in, out, "Echo 1 byte");
        echoUsbRequest(connection, in, out, 1, false);

        nextTest(connection, in, out, "Echo max bytes");
        echoUsbRequest(connection, in, out, MAX_BUFFER_SIZE, true);

        nextTest(connection, in, out, "Echo max bytes");
        echoUsbRequest(connection, in, out, MAX_BUFFER_SIZE, false);

        nextTest(connection, in, out, "Echo oversized buffer");
        echoOversizedUsbRequest(connection, in, out);

        // Send empty requests
        sendZeroLengthRequest(connection, out, true);
        sendZeroLengthRequest(connection, out, false);

        /* TODO: Unreliable

        // Zero length means waiting for the next data and then return
        nextTest(connection, in, out, "Receive byte after some time");
        receiveZeroLengthRequest(connection, in, true);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveZeroLengthRequest(connection, in, true);

        */

        // UsbRequest.queue ignores position, limit, arrayOffset, and capacity
        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 42, 0, 42, 5, 42, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 42, 0, 42, 0, 36, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 42, 5, 42, 0, 36, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 42, 0, 36, 0, 31, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 47, 0, 47, 0, 47, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 47, 5, 47, 0, 42, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 47, 0, 42, 0, 42, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 47, 0, 47, 5, 47, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequest(connection, in, out, 42, 47, 5, 47, 5, 36, false);

        // Illegal arguments
        final UsbRequest req1 = new UsbRequest();
        assertException(() -> req1.initialize(null, in), NullPointerException.class);
        assertException(() -> req1.initialize(connection, null), NullPointerException.class);
        boolean isInited = req1.initialize(connection, in);
        assertTrue(isInited);
        assertException(() -> req1.queue(null, 0), NullPointerException.class);
        assertException(() -> req1.queue(ByteBuffer.allocate(1).asReadOnlyBuffer(), 1),
                IllegalArgumentException.class);
        req1.close();

        // Cannot queue closed request
        assertException(() -> req1.queue(ByteBuffer.allocate(1), 1), NullPointerException.class);
        assertException(() -> req1.queue(ByteBuffer.allocateDirect(1), 1),
                NullPointerException.class);

        // Initialize
        UsbRequest req2 = new UsbRequest();
        isInited = req2.initialize(connection, in);
        assertTrue(isInited);
        isInited = req2.initialize(connection, out);
        assertTrue(isInited);
        req2.close();

        // Close
        req2 = new UsbRequest();
        req2.close();

        req2.initialize(connection, in);
        req2.close();
        req2.close();
    }

    /**
     * Tests {@link UsbDeviceConnection#bulkTransfer}.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     * @throws Throwable
     */
    private void bulkTransferTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // Find bulk in and out endpoints
        assumeTrue(iface.getEndpointCount() == 2);
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        // Transmission tests
        nextTest(connection, in, out, "Echo 1 byte");
        echoBulkTransfer(connection, in, out, 1);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoBulkTransferOffset(connection, in, out, 23, 42);

        nextTest(connection, in, out, "Echo max bytes");
        echoBulkTransfer(connection, in, out, MAX_BUFFER_SIZE);

        nextTest(connection, in, out, "Echo oversized buffer");
        echoOversizedBulkTransfer(connection, in, out);

        nextTest(connection, in, out, "Receive oversized buffer");
        receiveOversizedBulkTransfer(connection, in);

        // Illegal arguments
        assertException(() -> connection.bulkTransfer(out, new byte[1], 2, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(in, new byte[1], 2, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(out, new byte[2], 1, 2, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(in, new byte[2], 1, 2, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(out, new byte[1], -1, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(in, new byte[1], -1, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(out, new byte[1], 1, -1, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(in, new byte[1], 1, -1, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(out, new byte[1], -1, -1, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(in, new byte[1], -1, -1, 0),
                IllegalArgumentException.class);
        assertException(() -> connection.bulkTransfer(null, new byte[1], 1, 0),
                NullPointerException.class);

        // Transmissions that do nothing
        int numSent = connection.bulkTransfer(out, null, 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, null, 0, 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, new byte[0], 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, new byte[0], 0, 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, new byte[2], 2, 0, 0);
        assertEquals(0, numSent);

        /* TODO: These tests are flaky as they appear to be affected by previous tests

        // Transmissions that do not transfer data:
        // - first transfer blocks until data is received, but does not return the data.
        // - The data is read in the second transfer
        nextTest(connection, in, out, "Receive byte after some time");
        receiveWithEmptyBuffer(connection, in, null, 0, 0);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveWithEmptyBuffer(connection, in, new byte[0], 0, 0);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveWithEmptyBuffer(connection, in, new byte[2], 2, 0);

        */

        // Timeouts
        int numReceived = connection.bulkTransfer(in, new byte[1], 1, 100);
        assertEquals(-1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, 10000);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, 0);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, -1);
        assertEquals(1, numReceived);

        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, 100);
        assertEquals(-1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, 0);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, -1);
        assertEquals(1, numReceived);
    }

    /**
     * Send signal to the remove device that testing is finished.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     */
    private void endTesting(@NonNull UsbDeviceConnection connection, @NonNull UsbInterface iface) {
        // "done" signals that testing is over
        nextTest(connection, getEndpoint(iface, UsbConstants.USB_DIR_IN),
                getEndpoint(iface, UsbConstants.USB_DIR_OUT), "done");
    }

    /**
     * Test the behavior of {@link UsbDeviceConnection#claimInterface} and
     * {@link UsbDeviceConnection#releaseInterface}.
     *
     * <p>Note: The interface under test is <u>not</u> claimed by a kernel driver, hence there is
     * no difference in behavior between force and non-force versions of
     * {@link UsbDeviceConnection#claimInterface}</p>
     *
     * @param connection The connection to use
     * @param iface The interface to claim and release
     *
     * @throws Throwable
     */
    private void claimInterfaceTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // The interface is not claimed by the kernel driver, so not forcing it should work
        boolean claimed = connection.claimInterface(iface, false);
        assertTrue(claimed);
        boolean released = connection.releaseInterface(iface);
        assertTrue(released);

        // Forcing if it is not necessary does no harm
        claimed = connection.claimInterface(iface, true);
        assertTrue(claimed);

        // Re-claiming does nothing
        claimed = connection.claimInterface(iface, true);
        assertTrue(claimed);

        released = connection.releaseInterface(iface);
        assertTrue(released);

        // Re-releasing is not allowed
        released = connection.releaseInterface(iface);
        assertFalse(released);

        // Using an unclaimed interface claims it automatically
        int numSent = connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_OUT), null, 0,
                0);
        assertEquals(0, numSent);

        released = connection.releaseInterface(iface);
        assertTrue(released);

        assertException(() -> connection.claimInterface(null, true), NullPointerException.class);
        assertException(() -> connection.claimInterface(null, false), NullPointerException.class);
        assertException(() -> connection.releaseInterface(null), NullPointerException.class);
    }

    /**
     * Test all input parameters to {@link UsbDeviceConnection#setConfiguration} .
     *
     * <p>Note:
     * <ul>
     *     <li>The device under test only supports one configuration, hence changing configuration
     * is not tested.</li>
     *     <li>This test sets the current configuration again. This resets the device.</li>
     * </ul></p>
     *
     * @param device the device under test
     * @param connection The connection to use
     * @param iface An interface of the device
     *
     * @throws Throwable
     */
    private void setConfigurationTests(@NonNull UsbDevice device,
            @NonNull UsbDeviceConnection connection, @NonNull UsbInterface iface) throws Throwable {
        assumeTrue(device.getConfigurationCount() == 1);
        boolean wasSet = connection.setConfiguration(device.getConfiguration(0));
        assertTrue(wasSet);

        // Cannot set configuration for a device with a claimed interface
        boolean claimed = connection.claimInterface(iface, false);
        assertTrue(claimed);
        wasSet = connection.setConfiguration(device.getConfiguration(0));
        assertFalse(wasSet);
        boolean released = connection.releaseInterface(iface);
        assertTrue(released);

        assertException(() -> connection.setConfiguration(null), NullPointerException.class);
    }

    /**
     * Test all input parameters to {@link UsbDeviceConnection#setConfiguration} .
     *
     * <p>Note: The interface under test only supports one settings, hence changing the setting can
     * not be tested.</p>
     *
     * @param connection The connection to use
     * @param iface The interface to test
     *
     * @throws Throwable
     */
    private void setInterfaceTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        boolean claimed = connection.claimInterface(iface, false);
        assertTrue(claimed);
        boolean wasSet = connection.setInterface(iface);
        assertTrue(wasSet);
        boolean released = connection.releaseInterface(iface);
        assertTrue(released);

        // Setting the interface for an unclaimed interface automatically claims it
        wasSet = connection.setInterface(iface);
        assertTrue(wasSet);
        released = connection.releaseInterface(iface);
        assertTrue(released);

        assertException(() -> connection.setInterface(null), NullPointerException.class);
    }

    /**
     * Run tests.
     *
     * @param device The device to run the test against. This device is running
     *               com.android.cts.verifierusbcompanion.DeviceTestCompanion
     */
    private void runTests(@NonNull UsbDevice device) {
        try {
            // Find the AOAP interface
            UsbInterface iface = null;
            for (int i = 0; i < device.getConfigurationCount(); i++) {
                if (device.getInterface(i).getName().equals("Android Accessory Interface")) {
                    iface = device.getInterface(i);
                    break;
                }
            }
            assumeNotNull(iface);

            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            assertNotNull(connection);

            claimInterfaceTests(connection, iface);

            boolean claimed = connection.claimInterface(iface, false);
            assertTrue(claimed);

            usbRequestTests(connection, iface);
            ctrlTransferTests(connection);
            bulkTransferTests(connection, iface);

            // Signal to the DeviceTestCompanion that there are no more transfer test
            endTesting(connection, iface);
            boolean released = connection.releaseInterface(iface);
            assertTrue(released);

            setInterfaceTests(connection, iface);
            setConfigurationTests(device, connection, iface);

            assertFalse(connection.getFileDescriptor() == -1);
            assertNotNull(connection.getRawDescriptors());
            assertFalse(connection.getRawDescriptors().length == 0);
            assertEquals(device.getSerialNumber(), connection.getSerial());

            connection.close();

            // We should not be able to communicate with the device anymore
            assertFalse(connection.claimInterface(iface, true));
            assertFalse(connection.releaseInterface(iface));
            assertFalse(connection.setConfiguration(device.getConfiguration(0)));
            assertFalse(connection.setInterface(iface));
            assertTrue(connection.getFileDescriptor() == -1);
            assertNull(connection.getRawDescriptors());
            assertNull(connection.getSerial());
            assertEquals(-1, connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_OUT),
                    new byte[1], 1, 0));
            assertEquals(-1, connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_OUT),
                    null, 0, 0));
            assertEquals(-1, connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_IN),
                    null, 0, 0));
            assertFalse((new UsbRequest()).initialize(connection, getEndpoint(iface,
                    UsbConstants.USB_DIR_IN)));

            // Double close should do no harm
            connection.close();

            setTestResultAndFinish(true);
        } catch (AssumptionViolatedException e) {
            // Assumptions failing means that somehow the device/connection is set up incorrectly
            Toast.makeText(this, getString(R.string.usb_device_unexpected, e.getLocalizedMessage()),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            fail(null, e);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUsbDeviceConnectionReceiver);
        super.onDestroy();
    }

    /**
     * A {@link Runnable} that can throw an {@link Throwable}.
     */
    private interface Invokable {
        void run() throws Throwable;
    }
}
