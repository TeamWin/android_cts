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

import android.os.Bundle;

import androidx.annotation.StringRes;

import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Test for verifying that the platform correctly reports display resolution and refresh rate. More
 * specifically Display.getMode() and Display.getSupportedModes() APIs are tested against reference
 * displays.
 */
public class DisplayModesTestActivity extends TvAppVerifierActivity {
    private static final int DISPLAY_DISCONNECT_WAIT_TIME_SECONDS = 5;

    private TestSequence mTestSequence;

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_display_modes_test,
                R.string.tv_display_modes_test_info, -1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void createTestItems() {
        List<TestStepBase> testSteps = new ArrayList<>();
        testSteps.add(new NoDisplayTestStep(this));
        testSteps.add(new Display2160pTestStep(this));
        testSteps.add(new Display1080pTestStep(this));
        mTestSequence = new TestSequence(this, testSteps);
        mTestSequence.init();
    }

    @Override
    public String getTestDetails() {
        return mTestSequence.getFailureDetails();
    }

    private class NoDisplayTestStep extends AsyncTestStep {
        public NoDisplayTestStep(TvAppVerifierActivity context) {
            super(context);
        }

        @Override
        protected String getStepName() {
            return mContext.getString(R.string.tv_display_modes_test_step_no_display);
        }

        @Override
        protected String getInstructionText() {
            return mContext.getString(R.string.tv_display_modes_disconnect_display,
                    mContext.getString(getButtonStringId()),
                    DISPLAY_DISCONNECT_WAIT_TIME_SECONDS,
                    DISPLAY_DISCONNECT_WAIT_TIME_SECONDS + 1);
        }

        @Override
        protected @StringRes int getButtonStringId() {
            return R.string.tv_start_test;
        }

        @Override
        public void runTestAsync() {
            mContext.getPostTarget().postDelayed(() -> {
                // TODO: implement
                done();
            }, Duration.ofSeconds(DISPLAY_DISCONNECT_WAIT_TIME_SECONDS).toMillis());
        }
    }

    private class Display2160pTestStep extends SyncTestStep {
        public Display2160pTestStep (TvAppVerifierActivity context) {
            super(context);
        }

        @Override
        protected String getStepName() {
            return mContext.getString(R.string.tv_display_modes_test_step_2160p);
        }

        @Override
        protected String getInstructionText() {
            return mContext.getString(R.string.tv_display_modes_connect_2160p_display,
                    mContext.getString(getButtonStringId()));
        }

        @Override
        protected @StringRes int getButtonStringId() {
            return R.string.tv_start_test;
        }

        @Override
        public void runTest() {
            // TODO: implement
        }
    }


    private class Display1080pTestStep extends SyncTestStep {
        public Display1080pTestStep(TvAppVerifierActivity context) {
            super(context);
        }
        @Override
        protected String getStepName() {
            return mContext.getString(R.string.tv_display_modes_test_step_1080p);
        }

        @Override
        protected String getInstructionText() {
            return mContext.getString(R.string.tv_display_modes_connect_1080p_display,
                    mContext.getString(getButtonStringId()));
        }

        @Override
        protected @StringRes int getButtonStringId() {
            return R.string.tv_start_test;
        }

        @Override
        public void runTest() {
            // TODO: implement
        }
    }
}
