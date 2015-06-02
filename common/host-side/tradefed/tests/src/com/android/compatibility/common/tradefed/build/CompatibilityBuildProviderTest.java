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

package com.android.compatibility.common.tradefed.build;

import com.android.compatibility.tradefed.command.MockConsole;

import junit.framework.TestCase;

public class CompatibilityBuildProviderTest extends TestCase {

    private static final String ROOT_PROPERTY = "TESTS_ROOT";
    private static final String SUITE_FULL_NAME = "Compatibility Tests";
    private static final String SUITE_NAME = "TESTS";
    private static final String SUITE_VERSION = "1";
    private static final String SUITE_BUILD_ID = "2";

    // Make sure the mock is in the ClassLoader
    private MockConsole mMockConsole;

    @Override
    public void setUp() throws Exception {
        mMockConsole = new MockConsole();
    }

    @Override
    public void tearDown() throws Exception {
        setProperty(null);
        mMockConsole = null;
    }

    public void testManifestLoad() throws Exception {
        setProperty("/tmp/foobar");
        CompatibilityBuildProvider provider = new CompatibilityBuildProvider();
        CompatibilityBuildInfo info = provider.getCompatibilityBuild();
        assertEquals("Incorrect suite full name", SUITE_FULL_NAME, info.getSuiteFullName());
        assertEquals("Incorrect suite name", SUITE_NAME, info.getSuiteName());
        assertEquals("Incorrect suite version", SUITE_VERSION, info.getSuiteVersion());
        assertEquals("Incorrect suite build id", SUITE_BUILD_ID, info.getBuildId());
    }
    
    public void testProperty() throws Exception {
        setProperty(null);
        CompatibilityBuildProvider provider = new CompatibilityBuildProvider();
        try {
            // Should fail with root unset
            provider.getCompatibilityBuild();
            fail("Expected fail for unset root property");
        } catch (IllegalArgumentException e) {
            /* expected */
        }
        setProperty("/tmp/foobar");
        // Shouldn't fail with root set
        provider.getCompatibilityBuild();
    }

    /**
     * Sets the *_ROOT property of the build's installation location.
     *
     * @param value the value to set, or null to clear the property.
     */
    private void setProperty(String value) {
        if (value == null) {
            System.clearProperty(ROOT_PROPERTY);
        } else {
            System.setProperty(ROOT_PROPERTY, value);
        }
    }

}
