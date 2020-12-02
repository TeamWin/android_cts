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
 * limitations under the License.
 */

package com.android.cts.verifier.multiuser;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import android.os.Bundle;
import android.os.UserManager;
import android.view.View;
import android.widget.Button;

/**
 * CTS Verifier to test the 'MUST NOT allow users to interact with nor switch into the
 * Headless System User' CDD Automotive requirement.
 */
@CddTest(requirement="9.5/A-1-1")
public class HeadlessSystemUserTestActivity extends PassFailButtons.Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the UI.
        setContentView(R.layout.multiuser_headless_system_user);
        setPassFailButtonClickListeners();
        setInfoResources(
                R.string.multiuser_headless_sys_user_test,
                R.string.multiuser_headless_sys_user_info,
                -1);

        boolean supportsMultiUser = UserManager.getMaxSupportedUsers() > 1;

        if (supportsMultiUser) {
            findViewById(R.id.multiuser_headless_sys_user_allow_switching_layout)
                    .setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.multiuser_headless_sys_user_can_skip_txt)
                    .setVisibility(View.VISIBLE);
        }

        Button nextBtn = findViewById(R.id.multiuser_headless_sys_user_switchable_next_btn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.multiuser_headless_sys_user_adb_users_layout)
                        .setVisibility(View.VISIBLE);
            }
        });

        Button yesBtn = findViewById(R.id.multiuser_headless_sys_user_adb_users_yes_btn);
        yesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleInstructions(true);
            }
        });

        Button noBtn = findViewById(R.id.multiuser_headless_sys_user_adb_users_no_btn);
        noBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleInstructions(false);
            }
        });
    }

    private void toggleInstructions(boolean isUser0NameUnique) {
        if (isUser0NameUnique) {
            findViewById(R.id.multiuser_headless_sys_user_unique_txt).setVisibility(View.VISIBLE);
            findViewById(R.id.multiuser_headless_sys_user_common_txt).setVisibility(View.GONE);
        } else {
            findViewById(R.id.multiuser_headless_sys_user_unique_txt).setVisibility(View.GONE);
            findViewById(R.id.multiuser_headless_sys_user_common_txt).setVisibility(View.VISIBLE);
        }
    }
}