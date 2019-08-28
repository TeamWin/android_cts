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

package com.android.compatibility.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.math.BigInteger;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class CrashUtilsTest extends TestCase {

    private List<Crash> mCrashes;

    @Before
    public void setUp() throws IOException {
        BufferedReader txtReader =
            new BufferedReader(
                new InputStreamReader(
                    getClass().getClassLoader().getResourceAsStream("logcat.txt")));
        try {
            StringBuffer input = new StringBuffer();
            String tmp;
            while ((tmp = txtReader.readLine()) != null) {
                input.append(tmp + "\n");
            }
            mCrashes = CrashUtils.getAllCrashes(input.toString());
        } finally {
            txtReader.close();
        }
    }

    @Test
    public void testGetAllCrashes() throws Exception {
        List<Crash> expectedResults = new ArrayList<>();
        expectedResults.add(new Crash(11071, 11189, "AudioOut_D", "/system/bin/audioserver",
                new BigInteger("e9380000", 16), "SIGSEGV"));
        expectedResults.add(new Crash(12736, 12761, "Binder:12736_2", "/system/bin/audioserver",
                new BigInteger("0", 16), "SIGSEGV"));
        expectedResults.add(new Crash(26201, 26227, "Binder:26201_3", "/system/bin/audioserver",
                new BigInteger("0", 16), "SIGSEGV"));
        expectedResults.add(new Crash(26246, 26282, "Binder:26246_5", "/system/bin/audioserver",
                new BigInteger("0", 16), "SIGSEGV"));
        expectedResults.add(new Crash(245, 245, "installd", "/system/bin/installd",
                null, "SIGABRT"));
        expectedResults.add(new Crash(6371, 8072, "media.codec", "omx@1.0-service",
                new BigInteger("ed000000", 16), "SIGSEGV"));
        expectedResults.add(new Crash(8373, 8414, "loo", "com.android.bluetooth",
                null, "SIGABRT"));
        expectedResults.add(new Crash(11071, 11189, "synthetic_thread", "synthetic_process_0",
                new BigInteger("e9380000", 16), "SIGSEGV"));
        expectedResults.add(new Crash(12736, 12761, "synthetic_thread", "synthetic_process_1",
                new BigInteger("0", 16), "SIGSEGV"));

        assertEquals(expectedResults, mCrashes);
    }

    @Test
    public void testValidCrash() throws Exception {
        assertTrue(CrashUtils.securityCrashDetected(mCrashes, true,
                Pattern.compile("synthetic_process_0")));
    }

    @Test
    public void testMissingName() throws Exception {
        assertFalse(CrashUtils.securityCrashDetected(mCrashes, true,
                Pattern.compile("")));
    }

    @Test
    public void testSIGABRT() throws Exception {
        assertFalse(CrashUtils.securityCrashDetected(mCrashes, true,
                Pattern.compile("installd")));
    }

    @Test
    public void testFaultAddressBelowMin() throws Exception {
        assertFalse(CrashUtils.securityCrashDetected(mCrashes, true,
                Pattern.compile("synthetic_process_1")));
    }

    @Test
    public void testIgnoreMinAddressCheck() throws Exception {
        assertTrue(CrashUtils.securityCrashDetected(mCrashes, false,
                Pattern.compile("synthetic_process_1")));
    }

    @Test
    public void testBadAbortMessage() throws Exception {
        assertFalse(CrashUtils.securityCrashDetected(mCrashes, true,
                Pattern.compile("generic")));
    }

    @Test
    public void testGoodAndBadCrashes() throws Exception {
        assertTrue(CrashUtils.securityCrashDetected(mCrashes, true,
                Pattern.compile("synthetic_process_0"), Pattern.compile("generic")));
    }
}
