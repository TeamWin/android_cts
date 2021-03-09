/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.autofillservice.cts.activities;

import android.autofillservice.cts.R;
import android.autofillservice.cts.testcore.ClientAutofillRequestCallback;
import android.autofillservice.cts.testcore.Helper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Activity that has the following fields:
 *
 * <ul>
 *   <li>Username EditText (id: username, no input-type)
 *   <li>Password EditText (id: "username", input-type textPassword)
 *   <li>Clear Button
 *   <li>Save Button
 *   <li>Login Button
 * </ul>
 */
public class ClientSuggestionsActivity extends LoginActivity {
    private static final String TAG = "ClientSuggestionsActivity";
    private Handler mHandler;
    ClientAutofillRequestCallback mRequestCallback;

    private final Map<String, AutofillId> mMap = new ArrayMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.myLooper());

        mMap.put(Helper.ID_USERNAME, findViewById(R.id.username).getAutofillId());
        mMap.put(Helper.ID_PASSWORD, findViewById(R.id.password).getAutofillId());
        mHandler = new Handler(Looper.getMainLooper());
        Executor executor = new Executor(){
            @Override
            public void execute(Runnable command) {
                mHandler.post(command);
            }
        };
        mRequestCallback = new ClientAutofillRequestCallback(mHandler, (id)-> mMap.get(id));
        getAutofillManager().setAutofillRequestCallback(executor, mRequestCallback);
    }

    @Override
    public void addChild(View child) {
        throw new AssertionError("Uses addChild(View, String) instead");
    }

    public void addChild(View child, String id) {
        Log.d(TAG, "addChild(" + child + "): id=" + child.getAutofillId());
        super.addChild(child);

        mMap.put(id, child.getAutofillId());
    }

    public ClientAutofillRequestCallback.Replier getReplier() {
        return mRequestCallback.getReplier();
    }
}
