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
package android.abioverride.cts;

import android.abioverride.AbiOverrideActivity;
import android.test.ActivityInstrumentationTestCase2;

public class AbiOverrideTest extends ActivityInstrumentationTestCase2<AbiOverrideActivity> {
    /**
     * A reference to the activity whose shared preferences are being tested.
     */
    private AbiOverrideActivity mActivity;

    public AbiOverrideTest() {
        super(AbiOverrideActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Start the activity and get a reference to it.
        mActivity = getActivity();
        // Wait for the UI Thread to become idle.
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        // Scrub the activity so it can be freed. The next time the setUp will create a new activity
        // rather than reusing the old one.
        mActivity = null;
        super.tearDown();
    }

    /**
     * Test if the activity is run in a 32 bit process. In a 32 bit process,
     * the flag is a No-op (it is not meaningful to have multiple ABIs).
     *
     * @throws Exception
     */
    public void testRunIn32BitProcess() throws Exception {
        assertFalse("Process isn't 32 bit", mActivity.is64Bit());
    }
}
