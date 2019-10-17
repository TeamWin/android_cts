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
package android.contentcaptureservice.cts2;

import android.app.Activity;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

public class OutsideOfPackageActivity extends Activity {

    private static final String TAG = OutsideOfPackageActivity.class.getSimpleName();

    TextView mUsernameLabel;
    EditText mUsername;
    TextView mPasswordLabel;
    EditText mPassword;

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "AutofillIds: " + "usernameLabel=" + mUsernameLabel.getAutofillId()
                + ", username=" + mUsername.getAutofillId()
                + ", passwordLabel=" + mPasswordLabel.getAutofillId()
                + ", password=" + mPassword.getAutofillId());
    }
}
