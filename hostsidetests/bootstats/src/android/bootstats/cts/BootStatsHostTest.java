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

package android.bootstats.cts;

import static com.google.common.truth.Truth.assertThat;

import com.android.os.AtomsProto.Atom;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedList;


/**
 * Set of tests that verify statistics collection during boot.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class BootStatsHostTest implements IDeviceTest {

    private static final long MAX_WAIT_TIME_MS = 30000;
    private static final long WAIT_SLEEP_MS = 100;

    private static int[] ATOMS_EXPECTED = {
            Atom.BOOT_TIME_EVENT_DURATION_REPORTED_FIELD_NUMBER,
            Atom.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED_FIELD_NUMBER
    };

    private ITestDevice mDevice;

    @Test
    public void testBootStats() throws Exception {
        final int apiLevel = getDevice().getApiLevel();
        Assume.assumeFalse("Skipping test because boot time metrics were introduced"
                + " in Android 8.0. Current API Level " + apiLevel,
                apiLevel < 26 /* Build.VERSION_CODES.O */);

        // Clear buffer to make it easier to find new logs
        getDevice().executeShellCommand("logcat --buffer=events --clear");

        // reboot device
        getDevice().rebootUntilOnline();

        LinkedList<String> expectedAtomHeaders = new LinkedList<>();
        // example format: Atom 239->(total count)5, (error count)0
        for (int atom : ATOMS_EXPECTED) {
            expectedAtomHeaders.add("Atom " + atom + "->(total count)");
        }
        long timeoutMs = System.currentTimeMillis() + MAX_WAIT_TIME_MS;
        while (System.currentTimeMillis() < timeoutMs) {
            LinkedList<String> notExistingAtoms = checkAllExpectedAtoms(expectedAtomHeaders);
            if (notExistingAtoms.isEmpty()) {
                return;
            }
            Thread.sleep(WAIT_SLEEP_MS);
        }
        assertThat(checkAllExpectedAtoms(expectedAtomHeaders)).isEmpty();
    }

    /** Check all atoms are available and return atom headers not available */
    private LinkedList<String> checkAllExpectedAtoms(LinkedList<String> expectedAtomHeaders)
            throws Exception {
        LinkedList<String> notExistingAtoms = new LinkedList<>(expectedAtomHeaders);
        String log = getDevice().executeShellCommand("cmd stats print-stats");
        for (String atom : expectedAtomHeaders) {
            int atomIndex = log.indexOf(atom);
            if (atomIndex < 0) {
                continue;
            }
            int numberOfEvents = getIntValue(log, atomIndex + atom.length());
            if (numberOfEvents <= 0) {
                continue;
            }
            // valid event happened.
            notExistingAtoms.remove(atom);
        }
        return notExistingAtoms;
    }

    // extract the value from the string starting from index till ',''
    private int getIntValue(String str, int index) throws Exception {
        int lastIndex = index;
        for (int i = index; i < str.length(); i++) {
            if (str.charAt(i) == ',') {
                lastIndex = i;
                break;
            }
        }
        String valueStr = str.substring(index, lastIndex);
        int value = Integer.valueOf(valueStr);
        return value;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }
}
