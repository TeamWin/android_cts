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

package android.media.tv.interactive.cts;

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.interactive.TvIAppInfo;
import android.media.tv.interactive.TvIAppManager;
import android.media.tv.interactive.TvIAppView;
import android.os.ConditionVariable;
import android.tv.cts.R;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link android.media.tv.interactive.TvIAppView}.
 */
@RunWith(AndroidJUnit4.class)
public class TvIAppViewTest {
    private static final long TIME_OUT_MS = 20000L;

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvIAppViewStubActivity> mActivityScenario;
    private TvIAppViewStubActivity mActivity;
    private TvIAppView mTvIAppView;
    private TvIAppManager mManager;
    private TvIAppInfo mStubInfo;

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    private final MockCallback mCallback = new MockCallback();

    public static class MockCallback extends TvIAppView.TvIAppCallback {
        private String mIAppServiceId;
        private int mState = -1;

        @Override
        public void onSessionStateChanged(String iAppServiceId, int state) {
            mIAppServiceId = iAppServiceId;
            mState = state;
        }
    }

    private TvIAppView findTvIAppViewById(int id) {
        return (TvIAppView) mActivity.findViewById(id);
    }

    private void runTestOnUiThread(final Runnable r) throws Throwable {
        final Throwable[] exceptions = new Throwable[1];
        mInstrumentation.runOnMainSync(new Runnable() {
            public void run() {
                try {
                    r.run();
                } catch (Throwable throwable) {
                    exceptions[0] = throwable;
                }
            }
        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    @Before
    public void setUp() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(mInstrumentation.getTargetContext(), TvIAppViewStubActivity.class);

        // DO NOT use ActivityScenario.launch(Class), which can cause ActivityNotFoundException
        // related to BootstrapActivity.
        mActivityScenario = ActivityScenario.launch(intent);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(TIME_OUT_MS);

        assertNotNull("Failed to acquire activity reference.", mActivity);
        mTvIAppView = findTvIAppViewById(R.id.tviappview);
        assertNotNull("Failed to find TvIAppView.", mTvIAppView);

        mManager = (TvIAppManager) mActivity.getSystemService(Context.TV_IAPP_SERVICE);
        assertNotNull("Failed to get TvIAppManager.", mManager);

        for (TvIAppInfo info : mManager.getTvIAppServiceList()) {
            if (info.getServiceInfo().name.equals(StubTvIAppService.class.getName())) {
                mStubInfo = info;
            }
        }
        assertNotNull(mStubInfo);
        mTvIAppView.setCallback(mCallback);
    }

    @After
    public void tearDown() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mTvIAppView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
        mActivity = null;
        mActivityScenario.close();
    }

    @Test
    public void testConstructor() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                new TvIAppView(mActivity);
                new TvIAppView(mActivity, null);
                new TvIAppView(mActivity, null, 0);
            }
        });
    }

    @Test
    public void testStartIApp() throws Throwable {
        mTvIAppView.prepareIApp(mStubInfo.getId(), 1);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mTvIAppView.getIAppSession() != null;
            }
        }.run();
        mTvIAppView.startIApp();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.mIAppServiceId == mStubInfo.getId()
                        && mCallback.mState == TvIAppManager.TV_IAPP_RTE_STATE_READY;
            }
        }.run();
    }
}
