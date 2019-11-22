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

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;

import androidx.annotation.StringRes;

import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import com.google.common.base.Throwables;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Test for verifying that the platform correctly reports display resolution and refresh rate. More
 * specifically Display.getMode() and Display.getSupportedModes() APIs are tested against reference
 * displays.
 */
public class DisplayModesTestActivity extends TvAppVerifierActivity {
    private static final int DISPLAY_DISCONNECT_WAIT_TIME_SECONDS = 5;
    private static final float REFRESH_RATE_PRECISION = 0.01f;

    private static final Subject.Factory<ModeSubject, Display.Mode> MODE_SUBJECT_FACTORY =
            (failureMetadata, mode) -> new ModeSubject(failureMetadata, mode);

    private static final Correspondence<Display.Mode, Mode> MODE_CORRESPONDENCE =
            new Correspondence<Display.Mode, Mode>() {
                @Override
                public boolean compare(Display.Mode displayMode, Mode mode) {
                    return mode.isEquivalent(displayMode, REFRESH_RATE_PRECISION);
                }

                @Override
                public String toString() {
                    return "is equivalent to";
                }
            };

    private TestSequence mTestSequence;
    private DisplayManager mDisplayManager;

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.tv_display_modes_test, R.string.tv_display_modes_test_info, -1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
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
            return mContext.getString(
                    R.string.tv_display_modes_disconnect_display,
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
            mContext.getPostTarget()
                    .postDelayed(
                            () -> {
                                try {
                                    // Verify the display APIs do not crash when the display is
                                    // disconnected
                                    Display display =
                                            mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
                                    display.getMode();
                                    display.getSupportedModes();
                                } catch (Exception e) {
                                    getAsserter().fail(Throwables.getStackTraceAsString(e));
                                }
                                done();
                            },
                            Duration.ofSeconds(DISPLAY_DISCONNECT_WAIT_TIME_SECONDS).toMillis());
        }
    }

    private class Display2160pTestStep extends SyncTestStep {
        public Display2160pTestStep(TvAppVerifierActivity context) {
            super(context);
        }

        @Override
        protected String getStepName() {
            return mContext.getString(R.string.tv_display_modes_test_step_2160p);
        }

        @Override
        protected String getInstructionText() {
            return mContext.getString(
                    R.string.tv_display_modes_connect_2160p_display,
                    mContext.getString(getButtonStringId()));
        }

        @Override
        protected @StringRes int getButtonStringId() {
            return R.string.tv_start_test;
        }

        @Override
        public void runTest() {
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            getAsserter()
                    .withMessage("Display.getMode()")
                    .about(MODE_SUBJECT_FACTORY)
                    .that(display.getMode())
                    .isEquivalentTo(new Mode(3840, 2160, 60f), REFRESH_RATE_PRECISION);

             Mode[] expected2160pSupportedModes = new Mode[]{
                    new Mode(720, 480, 60f),
                    new Mode(720, 576, 50f),
                    // 720p modes
                    new Mode(1280, 720, 50f),
                    new Mode(1280, 720, 60f),
                    // 1080p modes
                    new Mode(1920, 1080, 24f),
                    new Mode(1920, 1080, 25f),
                    new Mode(1920, 1080, 30f),
                    new Mode(1920, 1080, 50f),
                    new Mode(1920, 1080, 60f),
                    // 2160p modes
                    new Mode(3840, 2160, 24f),
                    new Mode(3840, 2160, 25f),
                    new Mode(3840, 2160, 30f),
                    new Mode(3840, 2160, 50f),
                    new Mode(3840, 2160, 60f)
            };
            getAsserter()
                    .withMessage("Display.getSupportedModes()")
                    .that(Arrays.asList(display.getSupportedModes()))
                    .comparingElementsUsing(MODE_CORRESPONDENCE)
                    .containsAllIn(expected2160pSupportedModes);
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
            return mContext.getString(
                    R.string.tv_display_modes_connect_1080p_display,
                    mContext.getString(getButtonStringId()));
        }

        @Override
        protected @StringRes int getButtonStringId() {
            return R.string.tv_start_test;
        }

        @Override
        public void runTest() {
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);

            getAsserter()
                    .withMessage("Display.getMode()")
                    .about(MODE_SUBJECT_FACTORY)
                    .that(display.getMode())
                    .isEquivalentTo(new Mode(1920, 1080, 60f), REFRESH_RATE_PRECISION);

            final Mode[] expected1080pSupportedModes = new Mode[]{
                    new Mode(720, 480, 60f),
                    new Mode(720, 576, 50f),
                    // 720p modes
                    new Mode(1280, 720, 50f),
                    new Mode(1280, 720, 60f),
                    // 1080p modes
                    new Mode(1920, 1080, 24f),
                    new Mode(1920, 1080, 25f),
                    new Mode(1920, 1080, 30f),
                    new Mode(1920, 1080, 50f),
                    new Mode(1920, 1080, 60f),
            };
            getAsserter()
                    .withMessage("Display.getSupportedModes()")
                    .that(Arrays.asList(display.getSupportedModes()))
                    .comparingElementsUsing(MODE_CORRESPONDENCE)
                    .containsAllIn(expected1080pSupportedModes);
        }
    }

    // We use a custom Mode class since the constructors of Display.Mode are hidden. Additionally,
    // we want to use fuzzy comparision for frame rates which is not used in Display.Mode.equals().
    private static class Mode {
        public int mWidth;
        public int mHeight;
        public float mRefreshRate;

        public Mode(int width, int height, float refreshRate) {
            this.mWidth = width;
            this.mHeight = height;
            this.mRefreshRate = refreshRate;
        }

        public boolean isEquivalent(Display.Mode displayMode, float refreshRatePrecision) {
            return mHeight == displayMode.getPhysicalHeight()
                    && mWidth == displayMode.getPhysicalWidth()
                    && Math.abs(mRefreshRate - displayMode.getRefreshRate()) < refreshRatePrecision;
        }

        @Override
        public String toString() {
            return String.format("%dx%d %.2f Hz", mWidth, mHeight, mRefreshRate);
        }
    }

    private static class ModeSubject extends Subject<ModeSubject, Display.Mode> {
        public ModeSubject(FailureMetadata failureMetadata, @Nullable Display.Mode subject) {
            super(failureMetadata, subject);
        }

        public void isEquivalentTo(Mode mode, float refreshRatePrecision) {
            if (!mode.isEquivalent(actual(), refreshRatePrecision)) {
                failWithActual("expected", mode);
            }
        }
    }
}
