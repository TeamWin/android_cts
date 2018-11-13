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

package android.binder.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ParcelFileDescriptor;

import android.support.test.InstrumentationRegistry;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import test_package.IEmpty;
import test_package.ITest;
import test_package.RegularPolygon;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@RunWith(Parameterized.class)
public class JavaClientTest {
    private final String TAG = "JavaClientTest";

    private Class mServiceClass;
    private ITest mInterface;
    private String mExpectedName;

    public JavaClientTest(Class serviceClass, String expectedName) {
        mServiceClass = serviceClass;
        mExpectedName = expectedName;
    }

    @Parameterized.Parameters( name = "{0}" )
    public static Collection<Object[]> data() {
        // For local interfaces, this test will parcel the data locally.
        // Whenever possible, the desired service should be accessed directly
        // in order to avoid this additional overhead.
        return Arrays.asList(new Object[][] {
                {NativeService.Local.class, "CPP"},
                {JavaService.Local.class, "JAVA"},
                {NativeService.Remote.class, "CPP"},
                {JavaService.Remote.class, "JAVA"},
            });
    }

    @Before
    public void setUp() {
        Log.e(TAG, "Setting up");

        SyncTestServiceConnection connection = new SyncTestServiceConnection(
            InstrumentationRegistry.getTargetContext(), mServiceClass);

        mInterface = connection.get();
        assertNotEquals(null, mInterface);
    }

    @Test
    public void testSanityCheckSource() throws RemoteException {
        String name = mInterface.GetName();

        Log.i(TAG, "Service GetName: " + name);
        assertEquals(mExpectedName, name);
    }

    @Test
    public void testTrivial() throws RemoteException {
        mInterface.TestVoidReturn();
        mInterface.TestOneway();
    }

    @Test
    public void testRepeatPrimitives() throws RemoteException {
        assertEquals(1, mInterface.RepeatInt(1));
        assertEquals(2, mInterface.RepeatLong(2));
        assertEquals(1.0f, mInterface.RepeatFloat(1.0f), 0.0f);
        assertEquals(2.0, mInterface.RepeatDouble(2.0), 0.0);
        assertEquals(true, mInterface.RepeatBoolean(true));
        assertEquals('a', mInterface.RepeatChar('a'));
        assertEquals((byte)3, mInterface.RepeatByte((byte)3));
    }

    @Test
    public void testRepeatBinder() throws RemoteException {
        IBinder binder = mInterface.asBinder();

        assertEquals(binder, mInterface.RepeatBinder(binder));

        assertEquals(null, mInterface.RepeatBinder(null));
    }

    private static class Empty extends IEmpty.Stub {}

    @Test
    public void testRepeatInterface() throws RemoteException {
        IEmpty empty = new Empty();

        assertEquals(empty, mInterface.RepeatInterface(empty));

        assertEquals(null, mInterface.RepeatInterface(null));
    }

    @Test
    public void testRepeatFd() throws RemoteException, IOException {
        ParcelFileDescriptor[] sockets = ParcelFileDescriptor.createReliableSocketPair();
        ParcelFileDescriptor socketIn = sockets[0];
        ParcelFileDescriptor socketOut = sockets[1];

        ParcelFileDescriptor repeatFd = mInterface.RepeatFd(socketIn);

        boolean isNativeRemote = mInterface.GetName().equals("CPP");
        try {
            socketOut.checkError();

            // Either native didn't properly call detach, or native properly handles detach, and
            // we should change the test to enforce that socket comms work.
            assertFalse("Native doesn't implement comm fd but did not get detach.", isNativeRemote);
        } catch (ParcelFileDescriptor.FileDescriptorDetachedException e) {
            assertTrue("Detach, so remote should be native", isNativeRemote);
        }

        // Both backends support these.
        socketIn.checkError();
        repeatFd.checkError();

        FileOutputStream repeatFdStream = new ParcelFileDescriptor.AutoCloseOutputStream(repeatFd);
        String testData = "asdf";
        byte[] output = testData.getBytes();
        repeatFdStream.write(output);
        repeatFdStream.close();

        FileInputStream fileInputStream = new ParcelFileDescriptor.AutoCloseInputStream(socketOut);
        byte[] input = new byte[output.length];

        assertEquals(input.length, fileInputStream.read(input));
        Assert.assertArrayEquals(input, output);
    }

    @Test
    public void testRepeatString() throws RemoteException {
        assertEquals("", mInterface.RepeatString(""));
        assertEquals("a", mInterface.RepeatString("a"));
        assertEquals("foo", mInterface.RepeatString("foo"));
    }

    @Test
    public void testRepeatPolygon() throws RemoteException {
        RegularPolygon polygon = new RegularPolygon();
        polygon.name = "hexagon";
        polygon.numSides = 6;
        polygon.sideLength = 1.0f;

        RegularPolygon result = mInterface.RepeatPolygon(polygon);

        assertEquals(polygon.name, result.name);
        assertEquals(polygon.numSides, result.numSides);
        assertEquals(polygon.sideLength, result.sideLength, 0.0f);
    }

    @Test
    public void testInsAndOuts() throws RemoteException {
        RegularPolygon polygon = new RegularPolygon();
        mInterface.RenamePolygon(polygon, "Jerry");
        assertEquals("Jerry", polygon.name);
    }

    @Test
    public void testArrays() throws RemoteException {
        {
            boolean[] value = {};
            boolean[] out1 = new boolean[0];
            boolean[] out2 = mInterface.RepeatBooleanArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            boolean[] value = {false, true, false};
            boolean[] out1 = new boolean[3];
            boolean[] out2 = mInterface.RepeatBooleanArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            byte[] value = {1, 2, 3};
            byte[] out1 = new byte[3];
            byte[] out2 = mInterface.RepeatByteArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            char[] value = {'h', 'a', '!'};
            char[] out1 = new char[3];
            char[] out2 = mInterface.RepeatCharArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            int[] value = {1, 2, 3};
            int[] out1 = new int[3];
            int[] out2 = mInterface.RepeatIntArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            long[] value = {1, 2, 3};
            long[] out1 = new long[3];
            long[] out2 = mInterface.RepeatLongArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            float[] value = {1.0f, 2.0f, 3.0f};
            float[] out1 = new float[3];
            float[] out2 = mInterface.RepeatFloatArray(value, out1);

            Assert.assertArrayEquals(value, out1, 0.0f);
            Assert.assertArrayEquals(value, out2, 0.0f);
        }
        {
            double[] value = {1.0, 2.0, 3.0};
            double[] out1 = new double[3];
            double[] out2 = mInterface.RepeatDoubleArray(value, out1);

            Assert.assertArrayEquals(value, out1, 0.0);
            Assert.assertArrayEquals(value, out2, 0.0);
        }
        {
            String[] value = {"aoeu", "lol", "brb"};
            String[] out1 = new String[3];
            String[] out2 = mInterface.RepeatStringArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
    }
}
