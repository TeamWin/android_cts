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

import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Container for Compatibility test info.
 */
public interface IModuleDef extends Comparable<IModuleDef>, IBuildReceiver, IDeviceTest, IRemoteTest {

    /**
     * @return The name of this module.
     */
    String getName();

    /**
     * @return a {@link String} to uniquely identify this module.
     */
    String getId();

    /**
     * @return the abi of this test module.
     */
    IAbi getAbi();

    /**
     * @return a list of preparers used for setup or teardown of test cases in this module
     */
    List<ITargetPreparer> getPreparers();

    /**
     * @return a {@link List} of {@link IRemoteTest}s to run the test module.
     */
    List<IRemoteTest> getTests();

    /**
     * Adds a filter to include a specific test
     *
     * @param name the name of the test. Can be <package>, <package>.<class>,
     * <package>.<class>#<method> or <native_name>
     */
    void addIncludeFilter(String name);

    /**
     * Adds a filter to exclude a specific test
     *
     * @param name the name of the test. Can be <package>, <package>.<class>,
     * <package>.<class>#<method> or <native_name>
     */
    void addExcludeFilter(String name);

    /**
     * @return true iff this module's name matches the give regular expression pattern.
     */
    boolean nameMatches(Pattern pattern);

}
