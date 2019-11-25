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

import android.view.Display;

import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final @Display.HdrCapabilities.HdrType
    int[] EXPECTED_SUPPORTED_HDR_TYPES_SORTED = {
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
            Display.HdrCapabilities.HDR_TYPE_HDR10,
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS,
            Display.HdrCapabilities.HDR_TYPE_HLG
    };
    private static final float MAX_EXPECTED_LUMINANCE = 10_000f;

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
            super(context,
                    R.string.tv_hdr_connect_no_hdr_display,
                    R.string.tv_start_test);
        }

        @Override
        public boolean runTest() {
            Display display = mContext.getWindowManager().getDefaultDisplay();
            return !display.isHdr() && (display.getHdrCapabilities() == null
                    || display.getHdrCapabilities().getSupportedHdrTypes().length == 0);
        }
    }

    private static class HdrDisplayTestStep extends TestStep {

        public HdrDisplayTestStep(TvAppVerifierActivity context) {
            super(context,
                    R.string.tv_hdr_connect_hdr_display,
                    R.string.tv_start_test);
        }

        @Override
        public boolean runTest() {
            Display display = mContext.getWindowManager().getDefaultDisplay();
            return display.isHdr()
                    && hasExpectedHdrSupportedTypes(display)
                    && hasSaneLuminanceValues(display);
        }

        private static boolean hasExpectedHdrSupportedTypes(Display display) {
            Display.HdrCapabilities actualHdrCapabilities = display.getHdrCapabilities();
            int[] actualSupportedHdrTypes = actualHdrCapabilities.getSupportedHdrTypes();
            return Arrays.equals(EXPECTED_SUPPORTED_HDR_TYPES_SORTED, actualSupportedHdrTypes);
        }

        private static boolean hasSaneLuminanceValues(Display display) {
            Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();

            float maxLuminance = hdrCapabilities.getDesiredMaxLuminance();
            float maxAvgLuminance = hdrCapabilities.getDesiredMaxAverageLuminance();
            float minLuminance = hdrCapabilities.getDesiredMinLuminance();

            if(!(0f < maxLuminance && maxLuminance <= MAX_EXPECTED_LUMINANCE)) {
                return false;
            }

            if(!(0f < maxAvgLuminance && maxAvgLuminance <= MAX_EXPECTED_LUMINANCE)) {
                return false;
            }

            if (!(minLuminance < maxAvgLuminance && maxAvgLuminance <= maxLuminance)) {
                return false;
            }

            return true;
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
