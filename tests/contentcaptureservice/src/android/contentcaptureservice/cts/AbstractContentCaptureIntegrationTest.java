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

import static android.contentcaptureservice.cts.Helper.GENERIC_TIMEOUT_MS;
import static android.contentcaptureservice.cts.Helper.TAG;
import static android.contentcaptureservice.cts.Helper.resetService;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.contentcaptureservice.cts.common.ActivitiesWatcher;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.RequiredServiceRule;
import com.android.compatibility.common.util.SafeCleanerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;


/**
 * Base class for all (or most :-) integration tests in this CTS suite.
 */
@RunWith(AndroidJUnit4.class)
public abstract class AbstractContentCaptureIntegrationTest
        <A extends AbstractContentCaptureActivity> {

    protected static final Context sContext = InstrumentationRegistry.getTargetContext();

    protected ActivitiesWatcher mActivitiesWatcher;

    private final Class<A> mActivityClass;

    private final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule("content_capture");
    private final ContentCaptureLoggingTestRule mLoggingRule =
            new ContentCaptureLoggingTestRule(TAG);

    protected final SafeCleanerRule mSafeCleanerRule = new SafeCleanerRule()
            .setDumper(mLoggingRule)
            .add(() -> {
                return CtsSmartSuggestionsService.getExceptions();
            });

    @Rule
    public final RuleChain mLookAllTheseRules = RuleChain
            //
            // mRequiredServiceRule should be first so the test can be skipped right away
            .outerRule(mRequiredServiceRule)
            //
            // mLoggingRule wraps the test but doesn't interfere with it
            .around(mLoggingRule)
            //
            // mSafeCleanerRule will catch errors
            .around(mSafeCleanerRule)
            //
            // Finally, let subclasses set their ActivityTestRule
            .around(getActivityTestRule());


    protected AbstractContentCaptureIntegrationTest(@NonNull Class<A> activityClass) {
        mActivityClass = activityClass;
    }

    @Before
    public void registerLifecycleCallback() {
        Log.d(TAG, "Registering lifecycle callback");
        final Application app = (Application) sContext.getApplicationContext();
        mActivitiesWatcher = new ActivitiesWatcher(GENERIC_TIMEOUT_MS);
        app.registerActivityLifecycleCallbacks(mActivitiesWatcher);
    }

    @After
    public void unregisterLifecycleCallback() {
        if (mActivitiesWatcher != null) {
            Log.d(TAG, "Unregistering lifecycle callback");
            final Application app = (Application) sContext.getApplicationContext();
            app.unregisterActivityLifecycleCallbacks(mActivitiesWatcher);
        }
    }

    @After
    public void restoreDefaultService() {
        resetService();
    }

    /**
     * Gets the {@link ActivityTestRule} use to launch this activity.
     *
     * <p><b>NOTE: </b>implementation must return a static singleton, otherwise it might be
     * {@code null} when used it in this class' {@code @Rule}
     */
    protected abstract ActivityTestRule<A> getActivityTestRule();

    protected A launchActivity() {
        Log.d(TAG, "Launching " + mActivityClass.getSimpleName());

        return getActivityTestRule().launchActivity(new Intent(sContext, mActivityClass));
    }
}
