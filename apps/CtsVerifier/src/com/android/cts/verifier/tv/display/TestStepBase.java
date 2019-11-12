/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.verifier.tv.display;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StandardSubjectBuilder;

import java.util.Arrays;

/**
 * Encapsulates the logic of a test step, which displays a human instructions and a button to start
 * the test.
 */
public abstract class TestStepBase {
    final protected TvAppVerifierActivity mContext;
    protected View mViewItem;
    private boolean mHasPassed;
    private Runnable mOnDoneListener;
    private String mFailureDetails;
    private StandardSubjectBuilder mAsserter;

    /**
     * Constructs a test step containing instruction to the user and a button.
     *
     * @param context The test activity which this test step is part of.
     */
    public TestStepBase(TvAppVerifierActivity context) {
        this.mContext = context;

        FailureStrategy failureStrategy = assertionError -> {
            appendFailureDetails(assertionError.getMessage());
            mHasPassed = false;
        };
        mAsserter = StandardSubjectBuilder.forCustomFailureStrategy(failureStrategy);
        mHasPassed = true;
    }

    public boolean hasPassed() {
        return mHasPassed;
    }

    /**
     * Creates the View for this test step in the context {@link TvAppVerifierActivity}.
     */
    public void createUiElements() {
        mViewItem = mContext.createUserItem(
                getInstructionText(),
                getButtonStringId(),
                (View view) -> {
                    appendInfoDetails("Running test step %s...", getStepName());
                    onButtonClickRunTest();
                });
    }

    /**
     * Enables the button of this test step.
     */
    public void enableButton() {
        TvAppVerifierActivity.setButtonEnabled(mViewItem, true);
    }

    /**
     * Disables the button of this test step.
     */
    public void disableButton() {
        TvAppVerifierActivity.setButtonEnabled(mViewItem, false);
    }

    public void setOnDoneListener(Runnable listener) {
        mOnDoneListener = listener;
    }

    public String getFailureDetails() {
        return mFailureDetails;
    }

    /**
     * Human readable name of this test step to be output to logs.
     */
    protected abstract String getStepName();

    protected abstract void onButtonClickRunTest();

    /**
     * Returns the text of the test instruction visible to the user.
     */
    protected abstract String getInstructionText();

    /**
     * Returns id of string resource containing the text of the button.
     */
    protected abstract @StringRes int getButtonStringId();

    protected void done() {
        TvAppVerifierActivity.setPassState(mViewItem, mHasPassed);
        if (mOnDoneListener != null) {
            mOnDoneListener.run();
        }
    }

    protected StandardSubjectBuilder getAsserter() {
        return mAsserter;
    }

    protected void appendInfoDetails(String infoFormat, Object... args) {
        String info = String.format(infoFormat, args);
        String details = String.format("Info: %s", info);
        appendDetails(details);
    }

    protected void appendFailureDetails(String failure) {
        String details = String.format("Failure: %s", failure);
        appendDetails(details);
        appendMessageToView(mViewItem, details);
    }

    protected void appendDetails(String details) {
        if (mFailureDetails == null) {
            mFailureDetails = new String();
        }
        mFailureDetails += details + "\n";
    }

    private static void appendMessageToView(View item, String message) {
        TextView instructions = item.findViewById(R.id.instructions);
        instructions.setText(instructions.getText() + "\n" + message);
    }
}
