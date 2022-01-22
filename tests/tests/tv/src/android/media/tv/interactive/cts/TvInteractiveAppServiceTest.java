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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.interactive.TvInteractiveAppInfo;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppView;
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

import java.util.concurrent.Executor;

/**
 * Test {@link android.media.tv.interactive.TvInteractiveAppService}.
 */
@RunWith(AndroidJUnit4.class)
public class TvInteractiveAppServiceTest {
    private static final long TIME_OUT_MS = 20000L;

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvInteractiveAppViewStubActivity> mActivityScenario;
    private TvInteractiveAppViewStubActivity mActivity;
    private TvInteractiveAppView mTvIAppView;
    private TvInteractiveAppManager mManager;
    private TvInteractiveAppInfo mStubInfo;
    private StubTvInteractiveAppService.StubSessionImpl mSession;

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    private final MockCallback mCallback = new MockCallback();

    public static class MockCallback extends TvInteractiveAppView.TvInteractiveAppCallback {
        private int mRequestCurrentChannelUriCount = 0;

        private void resetCounts() {
            mRequestCurrentChannelUriCount = 0;
        }

        @Override
        public void onRequestCurrentChannelUri(String iAppServiceId) {
            mRequestCurrentChannelUriCount++;
        }
    }

    private TvInteractiveAppView findTvInteractiveAppViewById(int id) {
        return (TvInteractiveAppView) mActivity.findViewById(id);
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

    private Executor getExecutor() {
        return Runnable::run;
    }

    @Before
    public void setUp() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(
                mInstrumentation.getTargetContext(), TvInteractiveAppViewStubActivity.class);

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
        mTvIAppView = findTvInteractiveAppViewById(R.id.tviappview);
        assertNotNull("Failed to find TvInteractiveAppView.", mTvIAppView);

        mManager = (TvInteractiveAppManager) mActivity.getSystemService(
                Context.TV_INTERACTIVE_APP_SERVICE);
        assertNotNull("Failed to get TvInteractiveAppManager.", mManager);

        for (TvInteractiveAppInfo info : mManager.getTvInteractiveAppServiceList()) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())) {
                mStubInfo = info;
            }
        }
        assertNotNull(mStubInfo);
        mTvIAppView.setCallback(getExecutor(), mCallback);
        mTvIAppView.prepareInteractiveApp(mStubInfo.getId(), 1);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvIAppView.getInteractiveAppSession() != null);
        mSession = StubTvInteractiveAppService.sSession;
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
    public void testRequestCurrentChannelUri() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetCounts();
        mSession.requestCurrentChannelUri();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestCurrentChannelUriCount > 0);

        assertThat(mCallback.mRequestCurrentChannelUriCount).isEqualTo(1);
    }
}
