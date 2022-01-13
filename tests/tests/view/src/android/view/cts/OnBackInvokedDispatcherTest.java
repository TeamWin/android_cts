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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Dialog;
import android.view.OnBackInvokedCallback;
import android.view.OnBackInvokedDispatcher;
import android.view.View;

import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link OnBackInvokedDispatcher}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class OnBackInvokedDispatcherTest {
    private View mView;
    private OnBackInvokedDispatcherTestActivity mActivity;
    private Dialog mDialog;
    private View mViewInDialog;

    @Rule
    public ActivityTestRule<OnBackInvokedDispatcherTestActivity> mActivityRule =
            new ActivityTestRule<>(OnBackInvokedDispatcherTestActivity.class);

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
        mView = mActivity.findViewById(R.id.test_view);
        mDialog = mActivity.getDialog();
        mViewInDialog = mDialog.findViewById(R.id.test_view_in_dialog);
    }

    @Test
    public void testGetDispatcherOnView() {
        OnBackInvokedDispatcher viewDispatcher = mView.getOnBackInvokedDispatcher();
        assertEquals(viewDispatcher, mActivity.getOnBackInvokedDispatcher());
        assertNotNull("OnBackInvokedDispatcher on View should not be null", viewDispatcher);
    }

    @Test
    public void testGetDispatcherOnDialog() {
        OnBackInvokedDispatcher dialogDispatcher = mDialog.getOnBackInvokedDispatcher();
        OnBackInvokedDispatcher dialogViewDispatcher = mViewInDialog.getOnBackInvokedDispatcher();
        assertEquals(dialogDispatcher, dialogViewDispatcher);
        assertNotNull("OnBackInvokedDispatcher on Dialog should not be null", dialogDispatcher);
    }

    @Test
    public void testRegisterAndUnregisterCallbacks() {
        OnBackInvokedDispatcher dispatcher = mView.getOnBackInvokedDispatcher();
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
