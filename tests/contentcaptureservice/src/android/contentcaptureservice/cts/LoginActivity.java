/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.contentcaptureservice.cts;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

public class LoginActivity extends AbstractRootViewActivity {

    TextView mUsernameLabel;
    EditText mUsername;
    TextView mPasswordLabel;
    EditText mPassword;

    @Override
    protected void setContentViewOnCreate(Bundle savedInstanceState) {
        setContentView(R.layout.login_activity);

        mUsernameLabel = findViewById(R.id.username_label);
        mUsername = findViewById(R.id.username);
        mPasswordLabel = findViewById(R.id.password_label);
        mPassword = findViewById(R.id.password);
    }
}
