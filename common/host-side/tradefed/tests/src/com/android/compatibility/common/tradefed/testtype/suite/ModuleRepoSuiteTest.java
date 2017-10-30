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
package com.android.compatibility.common.tradefed.testtype.suite;

import static org.junit.Assert.assertEquals;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ModuleRepoSuite}.
 */
@RunWith(JUnit4.class)
public class ModuleRepoSuiteTest {

    private ModuleRepoSuite mRepo;

    @Before
    public void setUp() {
        mRepo = new ModuleRepoSuite();
    }

    public static class TestInject implements IRemoteTest {
        @Option(name = "simple-string")
        public String test = null;
        @Option(name = "list-string")
        public List<String> testList = new ArrayList<>();
        @Option(name = "map-string")
        public Map<String, String> testMap = new HashMap<>();

        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        }
    }

    /**
     * Test that the different format for module-arg and test-arg can properly be passed to the
     * configuration.
     */
    @Test
    public void testInjectConfig() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        TestInject checker = new TestInject();
        config.setTest(checker);
        Map<String, List<String>> optionMap = new HashMap<String, List<String>>();
        List<String> option1 = new ArrayList<>();
        option1.add("value1");
        optionMap.put("simple-string", option1);

        List<String> option2 = new ArrayList<>();
        option2.add("value2");
        option2.add("value3");
        option2.add("set-option:moreoption");
        optionMap.put("list-string", option2);

        List<String> option3 = new ArrayList<>();
        option3.add("set-option:=moreoption");
        optionMap.put("map-string", option3);

        mRepo.injectOptionsToConfig(optionMap, config);

        assertEquals("value1", checker.test);
        assertEquals(option2, checker.testList);
        Map<String, String> resMap = new HashMap<>();
        resMap.put("set-option", "moreoption");
        assertEquals(resMap, checker.testMap);
    }
}
