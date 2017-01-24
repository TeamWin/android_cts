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

package android.dumpsys.cts;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.Set;

public class BaseDumpsysTest extends DeviceTestCase implements IBuildReceiver {
    protected static final String TAG = "DumpsysHostTest";

    /**
     * A reference to the device under test.
     */
    protected ITestDevice mDevice;

    protected IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    protected static long assertInteger(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            fail("Expected an integer but found \"" + input + "\"");
            // Won't be hit, above throws AssertException
            return -1;
        }
    }

    protected static double assertDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            fail("Expected a double but found \"" + input + "\"");
            return -1;
        }
    }

    protected static void assertSeenTag(Set<String> seenTags, String tag) {
        assertTrue("No line starting with \"" + tag + ",\"", seenTags.contains(tag));
    }
}
