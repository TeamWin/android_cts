/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Unit tests for {@link JarHostTest}.
 */
public class JarHostTestTest extends TestCase {

    private static final String TEST_JAR1 = "/testtype/testJar1.jar";
    private JarHostTest mTest;
    private File mTestDir = null;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTest = new JarHostTest();
        mTestDir = FileUtil.createTempDir("jarhostest");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestDir);
        super.tearDown();
    }

    /**
     * Helper to read a file from the res/testtype directory and return it.
     *
     * @param filename the name of the file in the res/testtype directory
     * @param parentDir dir where to put the jar. Null if in default tmp directory.
     * @return the extracted jar file.
     */
    protected File getJarResource(String filename, File parentDir) throws IOException {
        InputStream jarFileStream = getClass().getResourceAsStream(filename);
        File jarFile = FileUtil.createTempFile("test", ".jar", parentDir);
        FileUtil.writeToFile(jarFileStream, jarFile);
        return jarFile;
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(JUnit4.class)
    public static class Junit4TestClass  {
        public Junit4TestClass() {}
        @org.junit.Test
        public void testPass1() {}
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(JUnit4.class)
    public static class Junit4TestClass2  {
        public Junit4TestClass2() {}
        @org.junit.Test
        public void testPass2() {}
    }

    /**
     * Test that {@link JarHostTest#split()} inherited from {@link HostTest} is still good.
     */
    public void testSplit_withoutJar() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("class", "com.android.compatibility.common.tradefed.testtype."
                + "JarHostTestTest$Junit4TestClass");
        setter.setOptionValue("class", "com.android.compatibility.common.tradefed.testtype."
                + "JarHostTestTest$Junit4TestClass2");
        List<IRemoteTest> res = (List<IRemoteTest>)mTest.split();
        assertEquals(2, res.size());
        assertTrue(res.get(0) instanceof JarHostTest);
        assertTrue(res.get(1) instanceof JarHostTest);
    }

    /**
     * Test that {@link JarHostTest#split()} can split classes coming from a jar.
     */
    public void testSplit_withJar() throws Exception {
        File testJar = getJarResource(TEST_JAR1, mTestDir);
        mTest = new JarHostTest() {
            @Override
            CompatibilityBuildHelper createBuildHelper(IBuildInfo info) {
                return new CompatibilityBuildHelper(info) {
                    @Override
                    public File getTestsDir() throws FileNotFoundException {
                        return mTestDir;
                    }
                };
            }
        };
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("jar", testJar.getName());
        List<IRemoteTest> res = (List<IRemoteTest>)mTest.split();
        assertEquals(2, res.size());
        assertTrue(res.get(0) instanceof JarHostTest);
        assertEquals("[android.ui.cts.TaskSwitchingTest]",
                ((JarHostTest)res.get(0)).getClassNames().toString());
        assertTrue(res.get(1) instanceof JarHostTest);
        assertEquals("[android.ui.cts.InstallTimeTest]",
                ((JarHostTest)res.get(1)).getClassNames().toString());
    }

    /**
     * Test that {@link JarHostTest#getTestShard(int, int)} can split classes coming from a jar.
     */
    public void testGetTestShard_withJar() throws Exception {
        File testJar = getJarResource(TEST_JAR1, mTestDir);
        mTest = new JarHostTest() {
            @Override
            CompatibilityBuildHelper createBuildHelper(IBuildInfo info) {
                return new CompatibilityBuildHelper(info) {
                    @Override
                    public File getTestsDir() throws FileNotFoundException {
                        return mTestDir;
                    }
                };
            }
        };
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("jar", testJar.getName());
        IRemoteTest shard1 = mTest.getTestShard(3, 0);
        assertTrue(shard1 instanceof JarHostTest);
        assertEquals("[android.ui.cts.TaskSwitchingTest]",
                ((JarHostTest)shard1).getClassNames().toString());
        IRemoteTest shard2 = mTest.getTestShard(3, 1);
        assertTrue(shard2 instanceof JarHostTest);
        assertEquals("[android.ui.cts.InstallTimeTest]",
                ((JarHostTest)shard2).getClassNames().toString());
        // Not enough class for a real 3rd shard, so it's a stub placeholder instead.
        IRemoteTest shard3 = mTest.getTestShard(3, 2);
        assertTrue(shard3 instanceof StubTest);
    }
}
