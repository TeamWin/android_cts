/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.app.cts;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import android.app.Activity;
import android.app.Fragment;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.stubs.FragmentResultActivity;
import android.app.stubs.FragmentTestActivity;
import android.app.stubs.R;
import android.content.Intent;
import android.content.IntentSender;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Tests Fragment's startActivityForResult and startIntentSenderForResult.
 */
public class FragmentReceiveResultTest extends
        ActivityInstrumentationTestCase2<FragmentTestActivity> {

    private FragmentTestActivity mActivity;
    private TestFragment mFragment;

    public FragmentReceiveResultTest() {
        super(FragmentTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mFragment = attachTestFragment();
    }

    @SmallTest
    public void testStartActivityForResultOk() {
        startActivityForResult(10, Activity.RESULT_OK, "content 10");

        assertTrue("Fragment should receive result", mFragment.mHasResult);
        assertEquals(10, mFragment.mRequestCode);
        assertEquals(Activity.RESULT_OK, mFragment.mResultCode);
        assertEquals("content 10", mFragment.mResultContent);
    }

    @SmallTest
    public void testStartActivityForResultCanceled() {
        startActivityForResult(20, Activity.RESULT_CANCELED, "content 20");

        assertTrue("Fragment should receive result", mFragment.mHasResult);
        assertEquals(20, mFragment.mRequestCode);
        assertEquals(Activity.RESULT_CANCELED, mFragment.mResultCode);
        assertEquals("content 20", mFragment.mResultContent);
    }

    @SmallTest
    public void testStartIntentSenderForResultOk() {
        startIntentSenderForResult(30, Activity.RESULT_OK, "content 30");

        assertTrue("Fragment should receive result", mFragment.mHasResult);
        assertEquals(30, mFragment.mRequestCode);
        assertEquals(Activity.RESULT_OK, mFragment.mResultCode);
        assertEquals("content 30", mFragment.mResultContent);
    }

    @SmallTest
    public void testStartIntentSenderForResultCanceled() {
        startIntentSenderForResult(40, Activity.RESULT_CANCELED, "content 40");

        assertTrue("Fragment should receive result", mFragment.mHasResult);
        assertEquals(40, mFragment.mRequestCode);
        assertEquals(Activity.RESULT_CANCELED, mFragment.mResultCode);
        assertEquals("content 40", mFragment.mResultContent);
    }

    private TestFragment attachTestFragment() {
        final TestFragment fragment = new TestFragment();
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().beginTransaction()
                        .add(R.id.content, fragment)
                        .addToBackStack(null)
                        .commitAllowingStateLoss();
                mActivity.getFragmentManager().executePendingTransactions();
            }
        });
        getInstrumentation().waitForIdleSync();
        return fragment;
    }

    private void startActivityForResult(final int requestCode, final int resultCode,
            final String content) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(mActivity, FragmentResultActivity.class);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT, content);

                mFragment.startActivityForResult(intent, requestCode);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private void startIntentSenderForResult(final int requestCode, final int resultCode,
            final String content) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(mActivity, FragmentResultActivity.class);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT, content);

                PendingIntent pendingIntent = PendingIntent.getActivity(mActivity,
                        requestCode, intent, 0);

                try {
                    mFragment.startIntentSenderForResult(pendingIntent.getIntentSender(),
                            requestCode, null, 0, 0, 0, null);
                } catch (IntentSender.SendIntentException e) {
                    fail("IntentSender failed");
                }
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public static class TestFragment extends Fragment {
        boolean mHasResult = false;
        int mRequestCode = -1;
        int mResultCode = 100;
        String mResultContent;

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            mHasResult = true;
            mRequestCode = requestCode;
            mResultCode = resultCode;
            mResultContent = data.getStringExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT);
        }
    }

}
