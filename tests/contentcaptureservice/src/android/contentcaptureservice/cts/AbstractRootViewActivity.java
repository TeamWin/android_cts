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

import static android.contentcaptureservice.cts.Helper.TAG;

import android.contentcaptureservice.cts.common.DoubleVisitor;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

/**
 * Base class for classes that have a {@code root_view} root view.
 */
abstract class AbstractRootViewActivity extends AbstractContentCaptureActivity {

    private static DoubleVisitor<AbstractContentCaptureActivity, LinearLayout> sRootViewVisitor;

    private LinearLayout mRootView;

    /**
     * Applies a visitor to the root view {@code onCreate()}.
     */
    static void onRootView(
            @NonNull DoubleVisitor<AbstractContentCaptureActivity, LinearLayout> visitor) {
        sRootViewVisitor = visitor;
    }

    @Override
    protected final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewOnCreate(savedInstanceState);

        mRootView = findViewById(R.id.root_view);

        if (sRootViewVisitor != null) {
            Log.d(TAG, "Applying visitor to " + this + "/" + mRootView);
            sRootViewVisitor.visit(this, mRootView);
        }
    }

    public LinearLayout getRootView() {
        return mRootView;
    }

    /**
     * The real "onCreate" method that should be extended by subclasses.
     *
     */
    protected abstract void setContentViewOnCreate(Bundle savedInstanceState);
}
