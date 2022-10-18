/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.disapproveime;

import static android.content.Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS;
import static android.view.inputmethod.cts.util.ConstantsUtils.ACTION_ON_CREATE;
import static android.view.inputmethod.cts.util.ConstantsUtils.EXTRA_LINKAGE_ERROR_RESULT;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.widget.LinearLayout;

/**
 * This IME simulates deprecated or inapplicable operations for test.
 */
public final class DisapproveInputMethodService extends InputMethodService {

    @Override
    public View onCreateInputView() {
        return new LinearLayout(this);
    }

    @Override
    public void onCreate() {
        boolean hasLinkageError = false;
        final Intent intent = new Intent();
        intent.setAction(ACTION_ON_CREATE);
        intent.addFlags(FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS);
        try {
            super.onCreate();
        } catch (Throwable e) {
            if (e instanceof LinkageError) {
                hasLinkageError = true;
            }
        }
        intent.putExtra(EXTRA_LINKAGE_ERROR_RESULT, hasLinkageError);
        sendBroadcast(intent);
    }

    /**
     * Override the deprecated {@link InputMethodService#onCreateInputMethodSessionInterface}
     * for test.
     */
    @Override
    public AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface() {
        return new MockInputMethodSessionImpl();
    }

    private class MockInputMethodSessionImpl extends InputMethodSessionImpl {
        // no-op
    }
}
