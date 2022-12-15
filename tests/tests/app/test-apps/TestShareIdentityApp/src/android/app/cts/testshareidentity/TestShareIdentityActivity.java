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
package android.app.cts.testshareidentity;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Activity running in a separate package to facilitate testing of {@link
 * android.app.ActivityOptions#setShareIdentityEnabled(boolean)}. {@link
 * android.app.cts.ShareIdentityTest} starts this activity with the test parameters as extras in
 * the delivered Intent, then this activity configures the appropriate environment for the test
 * and calls back into {@link android.app.cts.ShareIdentityTest.ShareIdentityTestActivity} to
 * ensure this package's identity is only shared when expected.
 */
public class TestShareIdentityActivity extends Activity {
    private static final String TAG = "TestShareIdentityActivity";
    /**
     * Activity within {@link android.app.cts.ShareIdentityTest} that will be launched by
     * this activity to verify this package's identity is only shared when expected.
     */
    private static final ComponentName SHARE_IDENTITY_ACTIVITY = new ComponentName(
            "android.app.cts", "android.app.cts.ShareIdentityTest$ShareIdentityTestActivity");

    /**
     * Key used to receive and send the unique ID of the current test.
     */
    private static final String TEST_ID_KEY = "testId";

    /**
     * Key used to receive the current test case.
     */
    private static final String TEST_CASE_KEY = "testCase";
    /**
     * Test case to verify the launching app's identity is not shared when the activity
     * is not launched with {@link android.app.ActivityOptions}.
     */
    private static final int DEFAULT_SHARING_TEST_CASE = 0;
    /**
     * Test case to verify the launching app's identity is shared when the activity is launched
     * with {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     * {@code true}.
     */
    private static final int EXPLICIT_OPT_IN_TEST_CASE = 1;
    /**
     * Test case to verify the launching app's identity is not shared when the activity is launched
     * with {@link android.app.ActivityOptions#setShareIdentityEnabled(boolean)} set to
     * {@code false}.
     */
    private static final int EXPLICIT_OPT_OUT_TEST_CASE = 2;
    /**
     * Test case to verify the sharing of an app's identity is not impacted by launching an
     * activity with {@link Activity#startActivityForResult(Intent, int)} since this method does
     * expose the app's identity from {@link Activity#getCallingPackage()}.
     */
    private static final int START_ACTIVITY_FOR_RESULT_TEST_CASE = 3;

    @Override
    public void onStart() {
        super.onStart();
        int testId = getIntent().getIntExtra(TEST_ID_KEY, -1);
        Intent intent = new Intent();
        intent.setComponent(SHARE_IDENTITY_ACTIVITY);
        intent.putExtra(TEST_ID_KEY, testId);
        Bundle bundle;
        int testCase = getIntent().getIntExtra(TEST_CASE_KEY, -1);
        switch(testCase) {
            case DEFAULT_SHARING_TEST_CASE:
                startActivity(intent);
                break;
            case EXPLICIT_OPT_IN_TEST_CASE:
                bundle = ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
                startActivity(intent, bundle);
                break;
            case EXPLICIT_OPT_OUT_TEST_CASE:
                bundle = ActivityOptions.makeBasic().setShareIdentityEnabled(false).toBundle();
                startActivity(intent, bundle);
                break;
            case START_ACTIVITY_FOR_RESULT_TEST_CASE:
                startActivityForResult(intent, 0);
                break;
            default:
                Log.e(TAG, "Unexpected test case received: " + testCase);
        }
        finish();
    }
}
