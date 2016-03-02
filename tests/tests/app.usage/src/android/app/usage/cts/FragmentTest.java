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
package android.app.usage.cts;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.usage.cts.R;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import java.util.concurrent.atomic.AtomicInteger;

public class FragmentTest extends ActivityInstrumentationTestCase2<FragmentTestActivity> {
    protected FragmentTestActivity mActivity;

    public FragmentTest() {
        super(FragmentTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        mActivity = getActivity();
    }

    @UiThreadTest
    public void testOnCreateOrder() throws Throwable {
        TestFragment fragment1 = new TestFragment();
        TestFragment fragment2 = new TestFragment();
        mActivity.getFragmentManager()
                .beginTransaction()
                .add(R.id.container, fragment1)
                .add(R.id.container, fragment2)
                .commitNow();
        assertEquals(0, fragment1.createOrder);
        assertEquals(1, fragment2.createOrder);
    }

    @TargetApi(VERSION_CODES.HONEYCOMB)
    public static class TestFragment extends Fragment {
        private static AtomicInteger sOrder = new AtomicInteger();
        public int createOrder = -1;

        public TestFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            createOrder = sOrder.getAndIncrement();
            super.onCreate(savedInstanceState);
        }
    }
}
