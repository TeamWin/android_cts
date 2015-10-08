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

import com.android.compatibility.common.tradefed.testtype.ModuleRepo.ConfigFilter;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleRepoTest extends TestCase {

    private static final String TOKEN =
            "<target_preparer class=\"com.android.compatibility.common.tradefed.targetprep.TokenRequirement\">\n"
            + "<option name=\"token\" value=\"%s\" />\n"
            + "</target_preparer>\n";
    private static final String CONFIG =
            "<configuration description=\"Auto Generated File\">\n"
            + "%s"
            + "<test class=\"com.android.tradefed.testtype.AndroidJUnitTest\" />\n"
            + "</configuration>";
    private static final String FOOBAR_TOKEN = "foobar";
    private static final String SERIAL1 = "abc";
    private static final String SERIAL2 = "def";
    private static final String SERIAL3 = "ghi";
    private static final Set<String> SERIALS = new HashSet<>();
    private static final Set<IAbi> ABIS = new HashSet<>();
    private static final List<String> DEVICE_TOKENS = new ArrayList<>();
    private static final List<String> INCLUDES = new ArrayList<>();
    private static final List<String> EXCLUDES = new ArrayList<>();
    private static final Set<String> FILES = new HashSet<>();
    static {
        SERIALS.add(SERIAL1);
        SERIALS.add(SERIAL2);
        SERIALS.add(SERIAL3);
        ABIS.add(new Abi("armeabi-v7a", "32"));
        ABIS.add(new Abi("arm64-v8a", "64"));
        DEVICE_TOKENS.add(String.format("%s:%s", SERIAL3, FOOBAR_TOKEN));
        FILES.add("One.config");
        FILES.add("Two.config");
        FILES.add("Three.config");
    }
    private IModuleRepo repo;
    private File mTestsDir;

    @Override
    public void setUp() throws Exception {
        mTestsDir = setUpConfigs();
        ModuleRepo.sInstance = null;// Clear the instance so it gets recreated.
        repo = ModuleRepo.getInstance();
    }

    @Override
    public void tearDown() throws Exception {
        tearDownConfigs(mTestsDir);
    }

    public void testInitialization() throws Exception {
        repo.initialize(3, mTestsDir, ABIS, DEVICE_TOKENS, INCLUDES, EXCLUDES);
        assertTrue("Should be initialized", repo.isInitialized());
        assertEquals("Wrong number of shards", 3, repo.getNumberOfShards());
        assertEquals("Wrong number of modules per shard", 2, repo.getModulesPerShard());
        Set<IModuleDef> modules = repo.getRemainingModules();
        Map<String, Set<String>> deviceTokens = repo.getDeviceTokens();
        assertEquals("Wrong number of devices with tokens", 1, deviceTokens.size());
        Set<String> tokens = deviceTokens.get(SERIAL3);
        assertEquals("Wrong number of tokens", 1, tokens.size());
        assertTrue("Unexpected device token", tokens.contains(FOOBAR_TOKEN));
        assertEquals("Wrong number of modules", 4, modules.size());
        Set<IModuleDef> tokenModules = repo.getRemainingWithTokens();
        assertEquals("Wrong number of modules with tokens", 2, tokenModules.size());
        List<IModuleDef> serial1Modules = repo.getModules(SERIAL1);
        assertEquals("Wrong number of modules", 2, serial1Modules.size());
        List<IModuleDef> serial2Modules = repo.getModules(SERIAL2);
        assertEquals("Wrong number of modules", 2, serial2Modules.size());
        List<IModuleDef> serial3Modules = repo.getModules(SERIAL3);
        assertEquals("Wrong number of modules", 2, serial3Modules.size());
        // Serial 3 should have the modules with tokens
        for (IModuleDef module : serial3Modules) {
            assertEquals("Wrong module", "Three", module.getName());
        }
        Set<String> serials = repo.getSerials();
        assertEquals("Wrong number of serials", 3, serials.size());
        assertTrue("Unexpected device serial", serials.containsAll(SERIALS));
    }
    public void testConfigFilter() throws Exception {
        File[] configFiles = mTestsDir.listFiles(new ConfigFilter());
        assertEquals("Wrong number of config files found.", 3, configFiles.length);
        for (File file : configFiles) {
            assertTrue(String.format("Unrecognised file: %s", file.getAbsolutePath()),
                    FILES.contains(file.getName()));
        }
    }

    private File setUpConfigs() throws IOException {
        File testsDir = FileUtil.createNamedTempDir("testcases");
        createConfig(testsDir, "One", null);
        createConfig(testsDir, "Two", null);
        createConfig(testsDir, "Three", FOOBAR_TOKEN);
        return testsDir;
    }

    private void tearDownConfigs(File testsDir) {
        FileUtil.recursiveDelete(testsDir);
    }

    private void createConfig(File testsDir, String name, String token) throws IOException {
        File config = new File(testsDir, String.format("%s.config", name));
        String preparer = "";
        if (token != null) {
            preparer = String.format(TOKEN, token);
        }
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(config);
            writer.format(CONFIG, preparer);
            writer.flush();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
