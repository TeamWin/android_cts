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

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
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
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
     * Tests {@link UsbDeviceConnection#bulkTransfer}.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     * @throws Throwable
     */
    private void bulkTransferTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // Find bulk in and out endpoints
        assertEquals(2, iface.getEndpointCount());
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        boolean claimed = connection.claimInterface(iface, false);
        assertTrue(claimed);

        // Transmission tests
        nextTest(connection, in, out, "Echo 1 byte");
        echoBulkTransfer(connection, in, out, 1);

        nextTest(connection, in, out, "Echo 42 bytes (with offset 23)");
        echoBulkTransferOffset(connection, in, out, 23, 42);

        nextTest(connection, in, out, "Echo max bytes");
        echoBulkTransfer(connection, in, out, MAX_BUFFER_SIZE);

        nextTest(connection, in, out, "Echo oversized buffer");
        echoOversizedBulkTransfer(connection, in, out);

        nextTest(connection, in, out, "Receive oversized buffer");
        receiveOversizedBulkTransfer(connection, in);

        // Illegal arguments
        nextTest(connection, in, out, "Length more than buffer size (out)");
        assertException(() -> connection.bulkTransfer(out, new byte[1], 2, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Length more than buffer size (in)");
        assertException(() -> connection.bulkTransfer(in, new byte[1], 2, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Offset + length more than buffer size (out)");
        assertException(() -> connection.bulkTransfer(out, new byte[2], 1, 2, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Offset + length more than buffer size (in)");
        assertException(() -> connection.bulkTransfer(in, new byte[2], 1, 2, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Negative length (out)");
        assertException(() -> connection.bulkTransfer(out, new byte[1], -1, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Negative length (in)");
        assertException(() -> connection.bulkTransfer(in, new byte[1], -1, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Negative offset (out)");
        assertException(() -> connection.bulkTransfer(out, new byte[1], 1, -1, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Negative offset (in)");
        assertException(() -> connection.bulkTransfer(in, new byte[1], 1, -1, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Negative length and offset (out)");
        assertException(() -> connection.bulkTransfer(out, new byte[1], -1, -1, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Negative length and offset (in)");
        assertException(() -> connection.bulkTransfer(in, new byte[1], -1, -1, 0),
                IllegalArgumentException.class);

        nextTest(connection, in, out, "Null endpoint");
        assertException(() -> connection.bulkTransfer(null, new byte[1], 1, 0),
                NullPointerException.class);

        // Transmissions that do nothing
        nextTest(connection, in, out, "Null buffer (out)");
        int numSent = connection.bulkTransfer(out, null, 0, 0);
        assertEquals(0, numSent);

        nextTest(connection, in, out, "Null buffer (with offset, out)");
        numSent = connection.bulkTransfer(out, null, 0, 0, 0);
        assertEquals(0, numSent);

        nextTest(connection, in, out, "Empty buffer (out)");
        numSent = connection.bulkTransfer(out, new byte[0], 0, 0);
        assertEquals(0, numSent);

        nextTest(connection, in, out, "Empty buffer (with offset, out)");
        numSent = connection.bulkTransfer(out, new byte[0], 0, 0, 0);
        assertEquals(0, numSent);

        nextTest(connection, in, out, "Offset == buffer.size (out)");
        numSent = connection.bulkTransfer(out, new byte[2], 2, 0, 0);
        assertEquals(0, numSent);

        // Transmissions that do not transfer data:
        // - first transfer blocks until data is received, but does not return the data.
        // - The data is read in the second transfer
        nextTest(connection, in, out, "Null buffer (in)");
        receiveWithEmptyBuffer(connection, in, null, 0, 0);

        nextTest(connection, in, out, "Empty buffer (in)");
        receiveWithEmptyBuffer(connection, in, new byte[0], 0, 0);

        nextTest(connection, in, out, "Offset == buffer.size (in)");
        receiveWithEmptyBuffer(connection, in, new byte[2], 2, 0);

        // Timeouts
        nextTest(connection, in, out, "Receive timeout (small positive timeout)");
        int numReceived = connection.bulkTransfer(in, new byte[1], 1, 100);
        assertEquals(-1, numReceived);

        nextTest(connection, in, out, "Receive timeout (large positive timeout)");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, 10000);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive timeout (0 timeout)");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, 0);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive timeout (negative == very long timeout)");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, -1);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive timeout (positive timeout, offset)");
        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, 100);
        assertEquals(-1, numReceived);

        nextTest(connection, in, out, "Receive timeout (0 timeout, offset)");
        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, 0);
        assertEquals(1, numReceived);

        nextTest(connection, in, out,
                "Receive timeout (negative == very long timeout, offset)");
        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, -1);
        assertEquals(1, numReceived);

        endTesting(connection, iface);
        boolean released = connection.releaseInterface(iface);
        assertTrue(released);
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
            ctrlTransferTests(connection);
            bulkTransferTests(connection, iface);
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
