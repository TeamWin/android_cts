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

package com.android.cts.verifierusbcompanion;

import android.content.Context;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Companion code for com.android.cts.verifier.usb.device.UsbDeviceTestActivity
 */
class DeviceTestCompanion extends TestCompanion {
    private static final int MAX_BUFFER_SIZE = 16384;

    DeviceTestCompanion(@NonNull Context context, @NonNull TestObserver observer) {
        super(context, observer);
    }

    /**
     * Switches to next test:
     * <ol>
     *     <li>Send result of last test to device under test</li>
     *     <li>Receive name of next test</li>
     * </ol>
     *
     * @param is                The stream to read from
     * @param os                The stream to write to
     * @param lastTestSucceeded If the last test succeeded
     *
     * @return Name of next test or empty string if there is no next test
     *
     * @throws IOException if the communication with the device under test is disturbed
     */
    private String nextTest(@NonNull InputStream is, @NonNull OutputStream os,
            boolean lastTestSucceeded) throws IOException {
        // Read next test name
        byte[] sizeBuffer = new byte[1];
        int numRead = is.read(sizeBuffer);
        assertEquals(1, numRead);

        byte[] nextTestNameBytes = new byte[sizeBuffer[0]];
        numRead = is.read(nextTestNameBytes);
        assertEquals(sizeBuffer[0], numRead);

        // Write test result
        os.write(lastTestSucceeded ? (byte) 1 : (byte) 0);

        // Wait for ready signal
        numRead = is.read(sizeBuffer);
        assertEquals(42, sizeBuffer[0]);
        assertEquals(1, numRead);

        return Charset.forName("UTF-8").decode(ByteBuffer.wrap(nextTestNameBytes)).toString();
    }

    /**
     * Read some bytes and send them back to the sender.
     *
     * @param is   Stream to read from
     * @param os   Stream to write to
     * @param size The number of bytes to read
     *
     * @return {@code true} iff the bytes could be read and written
     *
     * @throws IOException
     */
    private boolean echoBytes(@NonNull InputStream is, @NonNull OutputStream os, int size)
            throws IOException {
        byte[] buffer = new byte[size];

        int numRead = is.read(buffer);
        if (numRead != size) {
            return false;
        }

        os.write(buffer);
        return true;
    }

    @Override
    protected void runTest() throws Throwable {
        UsbAccessory accessory;

        final UsbAccessory[] accessoryReceiver = new UsbAccessory[1];
        AccessoryAttachmentHandler.AccessoryAttachmentObserver accessoryAttachmentObserver =
                a -> {
                    synchronized (DeviceTestCompanion.this) {
                        accessoryReceiver[0] = a;
                        notifyAll();
                    }
                };
        AccessoryAttachmentHandler.addObserver(accessoryAttachmentObserver);

        updateStatus("Waiting for device under test to connect");
        synchronized (this) {
            do {
                wait();
            } while (accessoryReceiver[0] == null);

            accessory = accessoryReceiver[0];
        }

        updateStatus("Connecting to " + accessory.getDescription());
        UsbManager usbManager = getContext().getSystemService(UsbManager.class);
        ParcelFileDescriptor fd = usbManager.openAccessory(accessory);
        assertNotNull(fd);

        try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            try (OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
                String testName;
                boolean isSuccess = true;

                do {
                    testName = nextTest(is, os, isSuccess);

                    updateStatus("Running test \"" + testName + "\"");

                    switch (testName) {
                        case "Echo 1 byte":
                            isSuccess = echoBytes(is, os, 1);
                            break;
                        case "Echo 42 bytes (with offset 23)":
                            isSuccess = echoBytes(is, os, 42);
                            break;
                        case "Echo max bytes":
                            isSuccess = echoBytes(is, os, MAX_BUFFER_SIZE);
                            break;
                        case "Echo oversized buffer":
                            // The bytes beyond MAX_BUFFER_SIZE got ignored when sending
                            isSuccess = echoBytes(is, os, MAX_BUFFER_SIZE);
                            break;
                        case "Receive oversized buffer": {
                            byte[] buffer = new byte[MAX_BUFFER_SIZE * 3 / 2];
                            buffer[0] = 1;
                            buffer[MAX_BUFFER_SIZE - 1] = 2;
                            buffer[MAX_BUFFER_SIZE] = 3;
                            buffer[MAX_BUFFER_SIZE * 3 / 2 - 1] = 4;

                            os.write(buffer);

                            isSuccess = true;
                        }
                        break;
                        case "Receive timeout (large positive timeout)":
                        case "Receive timeout (0 timeout)":
                        case "Receive timeout (negative == very long timeout)":
                        case "Receive timeout (0 timeout, offset)":
                        case "Receive timeout (negative == very long timeout, offset)":
                        case "Null buffer (in)":
                        case "Empty buffer (in)":
                        case "Offset == buffer.size (in)": {
                            Thread.sleep(200);
                            os.write(new byte[1]);
                            isSuccess = true;
                        }
                        break;
                        case "done":
                        default:
                            isSuccess = true;
                            break;
                    }
                } while (!"done".equals(testName));
            }
        }

        AccessoryAttachmentHandler.removeObserver(accessoryAttachmentObserver);
    }
}
