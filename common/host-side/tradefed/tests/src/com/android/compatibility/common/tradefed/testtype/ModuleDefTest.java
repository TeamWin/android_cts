/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.testtype;

import com.android.compatibility.common.util.AbiUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ModuleDefTest extends TestCase {

    private static final String NAME = "ModuleName";
    private static final String ABI = "mips64";
    private static final String ID = AbiUtils.createId(ABI, NAME);
    private static final String CLASS = "android.test.FoorBar";
    private static final String METHOD_1 = "testBlah1";
    private static final String TEST_1 = String.format("%s#%s", CLASS, METHOD_1);

    public void testAccessors() throws Exception {
        IAbi abi = new Abi(ABI, "");
        IModuleDef def = new ModuleDef(NAME, abi, new ArrayList<IRemoteTest>(),
                new ArrayList<ITargetPreparer>());
        assertEquals("Incorrect ID", ID, def.getId());
        assertEquals("Incorrect ABI", ABI, def.getAbi().getName());
        assertEquals("Incorrect Name", NAME, def.getName());
        assertNotNull("Expected tests", def.getTests());
        assertNotNull("Expected preparers", def.getPreparers());
    }

    public void testNameMatching() throws Exception {
        IAbi abi = new Abi(ABI, "");
        ModuleDef def = new ModuleDef(NAME, abi, new ArrayList<IRemoteTest>(),
                new ArrayList<ITargetPreparer>());
        assertTrue("Expected equality", def.nameMatches(Pattern.compile(NAME)));
        assertTrue("Expected regex equality", def.nameMatches(Pattern.compile(".*")));
        assertFalse("Expected no match to ID", def.nameMatches(Pattern.compile(ID)));
        assertFalse("Expected no match to empty", def.nameMatches(Pattern.compile("")));
    }

    public void testAddFilter() throws Exception {
        IAbi abi = new Abi(ABI, "");
        List<IRemoteTest> tests = new ArrayList<>();
        IRemoteTest mockTest = new MockRemoteTest();
        tests.add(mockTest);
        ModuleDef def = new ModuleDef(NAME, abi, tests, new ArrayList<ITargetPreparer>());
        def.addFilter(true, TEST_1);
        // TODO(stuartscott): When filters are supported, test the filter was set.
    }

    public class MockRemoteTest implements IRemoteTest {//, ITestFilterReceiver {

        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            // TODO(stuartscott): Auto-generated method stub

        }

    }

}
