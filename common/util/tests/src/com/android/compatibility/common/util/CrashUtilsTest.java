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
        expectedResults.add(new Crash(11071, 11189, "AudioOut_D", 3912761344L, "SIGSEGV"));
        expectedResults.add(new Crash(12736, 12761, "Binder:12736_2", 0L, "SIGSEGV"));
        expectedResults.add(new Crash(26201, 26227, "Binder:26201_3", 0L, "SIGSEGV"));
        expectedResults.add(new Crash(26246, 26282, "Binder:26246_5", 0L, "SIGSEGV"));
        expectedResults.add(new Crash(245, 245, "installd", null, "SIGABRT"));
        expectedResults.add(new Crash(6371, 8072, "media.codec", 3976200192L, "SIGSEGV"));
        expectedResults.add(new Crash(8373, 8414, "loo", null, "SIGABRT"));

        assertEquals(expectedResults, mCrashes);
    }

    @Test
    public void testValidCrash() throws Exception {
        assertTrue(CrashUtils.detectCrash(new String[]{"AudioOut_D"}, true, mCrashes));
    }

    @Test
    public void testMissingName() throws Exception {
        assertFalse(CrashUtils.detectCrash(new String[]{""}, true, mCrashes));
    }

    @Test
    public void testSIGABRT() throws Exception {
        assertFalse(CrashUtils.detectCrash(new String[]{"installd"}, true, mCrashes));
    }

    @Test
    public void testFaultAddressBelowMin() throws Exception {
        assertFalse(
            CrashUtils.detectCrash(new String[]{"Binder:12736_2"}, true, mCrashes));
    }

    @Test
    public void testIgnoreMinAddressCheck() throws Exception {
        assertTrue(
            CrashUtils.detectCrash(new String[]{"Binder:12736_2"}, false, mCrashes));
    }

    @Test
    public void testBadAbortMessage() throws Exception {
        assertFalse(CrashUtils.detectCrash(new String[]{"generic"}, true, mCrashes));
    }

    @Test
    public void testGoodAndBadCrashes() throws Exception {
        assertTrue(
            CrashUtils.detectCrash(new String[]{"AudioOut_D", "generic"}, true, mCrashes));
    }
}