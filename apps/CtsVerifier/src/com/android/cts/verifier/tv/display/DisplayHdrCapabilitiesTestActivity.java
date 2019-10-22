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

package com.android.cts.verifier.tv.display;

import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Test to verify the HDR Capabilities API is correctly implemented.
 *
 * This test checks if
 * <a href="https://developer.android.com/reference/android/view/Display.html#isHdr()">Display.isHdr()</a>
 * and
 * <a href="https://developer.android.com/reference/android/view/Display.html#getHdrCapabilities()">Display.getHdrCapabilities()</a>
 * return correct results when 1. HDR Display is connected, 2. non-HDR
 * Display is connected and 3. no display is connected.
 */
public class DisplayHdrCapabilitiesTestActivity extends TvAppVerifierActivity {
    private TestSequence mTestSequence;

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_hdr_capabilities_test,
                R.string.tv_hdr_capabilities_test_info, -1);
    }

    @Override
    protected void createTestItems() {
        List<TestStep> testSteps = new ArrayList<>();
        testSteps.add(new NonHdrDisplayTestStep(this));
        testSteps.add(new HdrDisplayTestStep(this));
        testSteps.add(new NoDisplayTestStep(this));

        mTestSequence = new TestSequence(this, testSteps);
        mTestSequence.init();
    }

    private static class NonHdrDisplayTestStep extends TestStep {

        public NonHdrDisplayTestStep(TvAppVerifierActivity context) {
            super(context, "", R.string.tv_start_test);
        }

        @Override
        public boolean runTest() {
            // TODO: Add test logic
            return false;
        }
    }

    private static class HdrDisplayTestStep extends TestStep {

        public HdrDisplayTestStep(TvAppVerifierActivity context) {
            super(context, "", R.string.tv_start_test);
        }

        @Override
        public boolean runTest() {
            // TODO: Add test logic
            return false;
        }
    }

    private static class NoDisplayTestStep extends TestStep {
        public NoDisplayTestStep(TvAppVerifierActivity context) {
            super(context, "", R.string.tv_start_test);
        }

        @Override
        public boolean runTest() {
            // TODO: Add test logic
            return false;
        }
    }
}
