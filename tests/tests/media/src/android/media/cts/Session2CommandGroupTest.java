/*
 * Copyright 2019 The Android Open Source Project
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

package android.media.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.Session2Command;
import android.media.Session2CommandGroup;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * Tests {@link android.media.Session2CommandGroup}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class Session2CommandGroupTest {
    private final int TEST_COMMAND_CODE_1 = 10000;
    private final int TEST_COMMAND_CODE_2 = 10001;
    private final int TEST_COMMAND_CODE_3 = 10002;

    @Test
    public void testHasCommand() {
        Session2Command testCommand = new Session2Command(TEST_COMMAND_CODE_1);
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(TEST_COMMAND_CODE_1);
        Session2CommandGroup commandGroup = builder.build();
        assertTrue(commandGroup.hasCommand(TEST_COMMAND_CODE_1));
        assertTrue(commandGroup.hasCommand(testCommand));
        assertFalse(commandGroup.hasCommand(TEST_COMMAND_CODE_2));
    }

    @Test
    public void testGetCommands() {
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(TEST_COMMAND_CODE_1);
        Session2CommandGroup commandGroup = builder.build();

        Set<Session2Command> commands = commandGroup.getCommands();
        assertTrue(commands.contains(new Session2Command(TEST_COMMAND_CODE_1)));
        assertFalse(commands.contains(new Session2Command(TEST_COMMAND_CODE_2)));
    }

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        Session2Command command = new Session2Command(TEST_COMMAND_CODE_1);
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(TEST_COMMAND_CODE_1);
        Session2CommandGroup commandGroup = builder.build();
        assertEquals(expected, commandGroup.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(TEST_COMMAND_CODE_1)
                .addCommand(TEST_COMMAND_CODE_2);
        Session2CommandGroup commandGroup = builder.build();
        Parcel dest = Parcel.obtain();
        commandGroup.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        Session2CommandGroup commandGroupFromParcel =
            Session2CommandGroup.CREATOR.createFromParcel(dest);
        assertEquals(commandGroup.getCommands(), commandGroupFromParcel.getCommands());
        dest.recycle();
    }

    @Test
    public void testBuilder() {
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(TEST_COMMAND_CODE_1);
        Session2CommandGroup commandGroup = builder.build();
        Session2CommandGroup.Builder newBuilder = new Session2CommandGroup.Builder(commandGroup);
        Session2CommandGroup newCommandGroup = newBuilder.build();
        assertEquals(commandGroup.getCommands(), newCommandGroup.getCommands());
    }

    @Test
    public void testAddAndRemoveCommand() {
        Session2Command testCommand = new Session2Command(TEST_COMMAND_CODE_1);
        Session2CommandGroup.Builder builder = new Session2CommandGroup.Builder()
                .addCommand(testCommand)
                .addCommand(TEST_COMMAND_CODE_2)
                .addCommand(TEST_COMMAND_CODE_3);
        builder.removeCommand(testCommand)
                .removeCommand(TEST_COMMAND_CODE_2);
        Session2CommandGroup commandGroup = builder.build();
        assertFalse(commandGroup.hasCommand(testCommand));
        assertFalse(commandGroup.hasCommand(TEST_COMMAND_CODE_2));
        assertTrue(commandGroup.hasCommand(TEST_COMMAND_CODE_3));
    }
}
