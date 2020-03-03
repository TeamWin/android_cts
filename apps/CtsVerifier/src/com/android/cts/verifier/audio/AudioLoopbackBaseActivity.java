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

package com.android.cts.verifier.audio;

import android.app.AlertDialog;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.R;
import com.android.cts.verifier.PassFailButtons;

import android.content.Context;

import android.media.AudioManager;
import android.media.AudioTrack;

import android.os.Bundle;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import android.widget.Button;

/**
 * Base class for testing activitiees that require audio loopback hardware..
 */
public class AudioLoopbackBaseActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioLoopbackActivity";

    OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    Button mLoopbackPortYesBtn;
    Button mLoopbackPortNoBtn;

    //
    // Common UI Handling
    void enableLayout(int layoutId, boolean enable) {
        ViewGroup group = (ViewGroup)findViewById(layoutId);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enable);
        }
    }

    private void connectLoopbackUI() {
        // Has Loopback - Yes
        mLoopbackPortYesBtn = (Button)findViewById(R.id.loopback_tests_yes_btn);
        mLoopbackPortYesBtn.setOnClickListener(mBtnClickListener);

        // Has Looback - No
        mLoopbackPortNoBtn = (Button)findViewById(R.id.loopback_tests_no_btn);
        mLoopbackPortNoBtn.setOnClickListener(mBtnClickListener);

        // Loopback Info
        findViewById(R.id.loopback_tests_info_btn).setOnClickListener(mBtnClickListener);

        enableTestUI(false);
    }

    private void showLoopbackInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.loopback_dlg_caption)
                .setMessage(R.string.loopback_dlg_text)
                .setPositiveButton(R.string.audio_general_ok, null)
                .show();
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.loopback_tests_yes_btn:
                    Log.i(TAG, "User confirms Headset Port existence");
                    recordLoopbackStatus(true);
                    mLoopbackPortYesBtn.setEnabled(false);
                    mLoopbackPortNoBtn.setEnabled(false);
                    break;

                case R.id.loopback_tests_no_btn:
                    Log.i(TAG, "User denies Headset Port existence");
                    recordLoopbackStatus(false);
                    getPassButton().setEnabled(true);
                    mLoopbackPortYesBtn.setEnabled(false);
                    mLoopbackPortNoBtn.setEnabled(false);
                    break;

                case R.id.loopback_tests_info_btn:
                    showLoopbackInfoDialog();
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectLoopbackUI();
    }

    private void recordLoopbackStatus(boolean has) {
        getReportLog().addValue(
                "User reported loopback availability: ",
                has ? 1.0 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    //
    // Overrides
    //
    void enableTestUI(boolean enable) {

    }
}
