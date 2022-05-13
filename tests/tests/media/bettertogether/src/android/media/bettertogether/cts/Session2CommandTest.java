/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.media.bettertogether.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.Session2Command;
import android.os.Bundle;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests {@link android.media.Session2Command}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class Session2CommandTest {
    private static final int TEST_COMMAND_CODE = 10000;
    private static final int TEST_RESULT_CODE = 0;
    private static final String TEST_CUSTOM_ACTION = "testAction";
    private Bundle mTestCustomExtras;
    private Bundle mTestResultData;

    @Before
    public void setUp() {
        mTestCustomExtras = new Bundle();
        mTestResultData = new Bundle();
    }

    @Test
    public void testConstructorWithCommandCodeCustom() {
        try {
            Session2Command command = new Session2Command(Session2Command.COMMAND_CODE_CUSTOM);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected IllegalArgumentException
        }
    }

    @Test
    public void testConstructorWithNullAction() {
        try {
            Session2Command command = new Session2Command(null, null);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected IllegalArgumentException
        }
    }

    @Test
    public void testGetCommandCode() {
        Session2Command commandWithCode = new Session2Command(TEST_COMMAND_CODE);
        assertEquals(TEST_COMMAND_CODE, commandWithCode.getCommandCode());

        Session2Command commandWithAction = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertEquals(Session2Command.COMMAND_CODE_CUSTOM, commandWithAction.getCommandCode());
    }

    @Test
    public void testGetCustomAction() {
        Session2Command commandWithCode = new Session2Command(TEST_COMMAND_CODE);
        assertNull(commandWithCode.getCustomAction());

        Session2Command commandWithAction = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertEquals(TEST_CUSTOM_ACTION, commandWithAction.getCustomAction());
    }

    @Test
    public void testGetCustomExtras() {
        Session2Command commandWithCode = new Session2Command(TEST_COMMAND_CODE);
        assertNull(commandWithCode.getCustomExtras());

        Session2Command commandWithAction = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertEquals(mTestCustomExtras, commandWithAction.getCustomExtras());
    }

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        Session2Command command = new Session2Command(TEST_COMMAND_CODE);
        assertEquals(expected, command.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        Session2Command command = new Session2Command(TEST_CUSTOM_ACTION, null);
        Parcel dest = Parcel.obtain();
        command.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        assertEquals(command.getCommandCode(), dest.readInt());
        assertEquals(command.getCustomAction(), dest.readString());
        assertEquals(command.getCustomExtras(), dest.readBundle());
    }

    @Test
    public void testEquals() {
        Session2Command commandWithCode1 = new Session2Command(TEST_COMMAND_CODE);
        Session2Command commandWithCode2 = new Session2Command(TEST_COMMAND_CODE);
        assertTrue(commandWithCode1.equals(commandWithCode2));

        Session2Command commandWithAction1 = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        Session2Command commandWithAction2 = new Session2Command(TEST_CUSTOM_ACTION,
                mTestCustomExtras);
        assertTrue(commandWithAction1.equals(commandWithAction2));
    }

    @Test
    public void testHashCode() {
        Session2Command commandWithCode1 = new Session2Command(TEST_COMMAND_CODE);
        Session2Command commandWithCode2 = new Session2Command(TEST_COMMAND_CODE);
        assertEquals(commandWithCode1.hashCode(), commandWithCode2.hashCode());
    }

    @Test
    public void testGetResultCodeAndData() {
        Session2Command.Result result = new Session2Command.Result(TEST_RESULT_CODE,
                mTestResultData);
        assertEquals(TEST_RESULT_CODE, result.getResultCode());
        assertEquals(mTestResultData, result.getResultData());
    }
}
