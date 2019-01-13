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
package android.contentcaptureservice.cts;

import android.contentcaptureservice.cts.common.DoubleVisitor;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewStructure;

import androidx.annotation.NonNull;

public class CustomViewActivity extends AbstractContentCaptureActivity {

    private static final String TAG = CustomViewActivity.class.getSimpleName();

    private static DoubleVisitor<CustomView, ViewStructure> sCustomViewDelegate;

    CustomView mCustomView;

    /**
     * Sets a delegate that provides the behavior of
     * {@link CustomView#onProvideContentCaptureStructure(ViewStructure, int)}.
     */
    static void setCustomViewDelegate(@NonNull DoubleVisitor<CustomView, ViewStructure> delegate) {
        sCustomViewDelegate = delegate;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_view_activity);
        mCustomView = findViewById(R.id.custom_view);
        Log.d(TAG, "onCreate(): custom view id is " + mCustomView.getAutofillId());
        if (sCustomViewDelegate != null) {
            Log.d(TAG, "Setting delegate on " + mCustomView);
            mCustomView.setContentCaptureDelegate(
                    (structure) -> sCustomViewDelegate.visit(mCustomView, structure));
        }
    }
}
