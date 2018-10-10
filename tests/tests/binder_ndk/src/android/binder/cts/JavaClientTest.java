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

import android.support.test.InstrumentationRegistry;

import android.util.Log;

import java.util.Arrays;
import java.util.Collection;

import test_package.IEmpty;
import test_package.ITest;
import test_package.RegularPolygon;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.Before;
import org.junit.Test;

@RunWith(Parameterized.class)
public class JavaClientTest {
    private final String TAG = "JavaClientTest";

    private Class mServiceClass;
    private ITest mInterface;

    public JavaClientTest(Class serviceClass) {
        mServiceClass = serviceClass;
    }

    @Parameterized.Parameters( name = "{0}" )
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { {NativeService.class}, {JavaService.class} });
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
    public void testRepeatString() throws RemoteException {
        IEmpty empty = new Empty();

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
}
