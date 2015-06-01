/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;

public class BuildHelperTest extends TestCase {

    public void testBuildHelper() throws Exception {
        File root = FileUtil.createTempDir("root");
        try {
            new BuildHelper("", "blah", "", "", root);
            fail("Build helper validation succeeded on an invalid installation");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        File base = new File(root, "android-blah");
        base.mkdirs();
        File repo = new File(base, "repository");
        repo.mkdirs();
        File tests = new File(repo, "testcases");
        tests.mkdirs();
        File plans = new File(repo, "plans");
        plans.mkdirs();
        try {
            new BuildHelper("", "blah", "", "", root);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            fail("Build helper validation failed on a valid installation");
        }

        FileUtil.recursiveDelete(root);
    }

}
