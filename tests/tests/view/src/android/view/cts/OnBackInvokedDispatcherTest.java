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

package android.view.cts;

import static org.junit.Assert.assertNotNull;

import android.app.Dialog;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link OnBackInvokedDispatcher}.
 */
@MediumTest
public class OnBackInvokedDispatcherTest {
    private OnBackInvokedDispatcherTestActivity mActivity;
    private Dialog mDialog;

    @Rule
    public ActivityScenarioRule<OnBackInvokedDispatcherTestActivity> mActivityRule =
            new ActivityScenarioRule<>(OnBackInvokedDispatcherTestActivity.class);

    @Before
    public void setUp() {
        mActivityRule.getScenario().moveToState(Lifecycle.State.RESUMED);
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
            mDialog = mActivity.getDialog();
        });
    }

    @Test
    public void testGetDispatcherOnDialog() {
        OnBackInvokedDispatcher dialogDispatcher = mDialog.getOnBackInvokedDispatcher();
        assertNotNull("OnBackInvokedDispatcher on Dialog should not be null", dialogDispatcher);
    }

    @Test
    public void testRegisterAndUnregisterCallbacks() {
        OnBackInvokedDispatcher dispatcher = mActivity.getOnBackInvokedDispatcher();
        OnBackInvokedCallback callback1 = createBackCallback();
        OnBackInvokedCallback callback2 = createBackCallback();
        dispatcher.registerOnBackInvokedCallback(
                callback1, OnBackInvokedDispatcher.PRIORITY_OVERLAY);
        dispatcher.registerOnBackInvokedCallback(
                callback2, OnBackInvokedDispatcher.PRIORITY_DEFAULT);
        dispatcher.unregisterOnBackInvokedCallback(callback2);
        dispatcher.unregisterOnBackInvokedCallback(callback1);
        dispatcher.unregisterOnBackInvokedCallback(callback2);
    }

    private OnBackInvokedCallback createBackCallback() {
        return new OnBackInvokedCallback() {
            @Override
            public void onBackInvoked() {}
        };
    }
}
