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
import static android.contentcaptureservice.cts.Helper.SYSTEM_SERVICE_NAME;
import static android.contentcaptureservice.cts.Helper.resetService;
import static android.contentcaptureservice.cts.Helper.sContext;
import static android.contentcaptureservice.cts.Helper.setService;
import static android.provider.Settings.Secure.CONTENT_CAPTURE_ENABLED;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import android.app.Application;
import android.content.Intent;
import android.contentcaptureservice.cts.CtsContentCaptureService.ServiceWatcher;
import android.contentcaptureservice.cts.common.ActivitiesWatcher;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.contentcaptureservice.cts.common.Visitor;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.RequiredServiceRule;
import com.android.compatibility.common.util.SafeCleanerRule;
import com.android.compatibility.common.util.SettingsStateChangerRule;
import com.android.compatibility.common.util.SettingsUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Base class for all (or most :-) integration tests in this CTS suite.
 */
@RunWith(AndroidJUnit4.class)
public abstract class AbstractContentCaptureIntegrationTest
        <A extends AbstractContentCaptureActivity> {

    private static final String TAG = AbstractContentCaptureIntegrationTest.class.getSimpleName();

    private final String mTag = getClass().getSimpleName();

    protected ActivitiesWatcher mActivitiesWatcher;

    private final Class<A> mActivityClass;

    private final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(SYSTEM_SERVICE_NAME);

    private final ContentCaptureLoggingTestRule mLoggingRule = new ContentCaptureLoggingTestRule();


    /**
     * Watcher set on {@link #enableService()} and used to wait until it's gone after the test
     * finishes.
     */
    private ServiceWatcher mServiceWatcher;

    protected final SafeCleanerRule mSafeCleanerRule = new SafeCleanerRule()
            .setDumper(mLoggingRule)
            .run(() -> {
                Log.v(mTag, "@SafeCleaner: resetDefaultService()");
                resetService();

                if (mServiceWatcher != null) {
                    mServiceWatcher.waitOnDestroy();
                }

            })
            .add(() -> {
                return CtsContentCaptureService.getExceptions();
            });

    private final SettingsStateChangerRule mFeatureEnablerRule = new SettingsStateChangerRule(
            sContext, CONTENT_CAPTURE_ENABLED, "true");

    @Rule
    public final RuleChain mLookAllTheseRules = RuleChain
            //
            // mRequiredServiceRule should be first so the test can be skipped right away
            .outerRule(mRequiredServiceRule)
            // enable it as soon as possible, as it have to wait for the listener
            .around(mFeatureEnablerRule)
            //
            // mLoggingRule wraps the test but doesn't interfere with it
            .around(mLoggingRule)
            //
            // mSafeCleanerRule will catch errors
            .around(mSafeCleanerRule)
            //
            // Finally, let subclasses set their own rule
            .around(getMainTestRule());

    protected AbstractContentCaptureIntegrationTest(@NonNull Class<A> activityClass) {
        mActivityClass = activityClass;
    }

    @BeforeClass
    public static void disableDefaultService() {
        Log.v(TAG, "@BeforeClass: disableDefaultService()");
        Helper.setDefaultServiceEnabled(false);
    }

    @AfterClass
    public static void enableDefaultService() {
        Log.v(TAG, "@AfterClass: enableDefaultService()");
        Helper.setDefaultServiceEnabled(true);
    }

    @Before
    public void prepareDevice() throws Exception {
        Log.v(mTag, "@Before: prepareDevice()");

        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");

        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");

        // Collapse notifications.
        runShellCommand("cmd statusbar collapse");
    }

    @Before
    public void clearState() {
        Log.v(mTag, "@Before: clearState()");
        CtsContentCaptureService.resetStaticState();
    }

    @Before
    public void registerLifecycleCallback() {
        Log.v(mTag, "@Before: Registering lifecycle callback");
        final Application app = (Application) sContext.getApplicationContext();
        mActivitiesWatcher = new ActivitiesWatcher(GENERIC_TIMEOUT_MS);
        app.registerActivityLifecycleCallbacks(mActivitiesWatcher);
    }

    @After
    public void unregisterLifecycleCallback() {
        Log.d(mTag, "@After: Unregistering lifecycle callback: " + mActivitiesWatcher);
        if (mActivitiesWatcher != null) {
            final Application app = (Application) sContext.getApplicationContext();
            app.unregisterActivityLifecycleCallbacks(mActivitiesWatcher);
        }
    }

    @Nullable
    public static void setFeatureEnabled(@Nullable String enabled) {
        if (enabled == null) {
            SettingsUtils.syncDelete(sContext, CONTENT_CAPTURE_ENABLED);
        } else {
            SettingsUtils.syncSet(sContext, CONTENT_CAPTURE_ENABLED, enabled);
        }
    }

    /**
     * Sets {@link CtsContentCaptureService} as the service for the current user and waits until
     * its created.
     */
    public CtsContentCaptureService enableService() throws InterruptedException {
        if (mServiceWatcher != null) {
            throw new IllegalStateException("There Can Be Only One!");
        }
        mServiceWatcher = CtsContentCaptureService.setServiceWatcher();
        setService(CtsContentCaptureService.SERVICE_NAME);

        return mServiceWatcher.waitOnCreate();
    }

    /**
     * Gets the {@link ActivityTestRule} use to launch this activity.
     *
     * <p><b>NOTE: </b>implementation must return a static singleton, otherwise it might be
     * {@code null} when used it in this class' {@code @Rule}
     */
    protected abstract ActivityTestRule<A> getActivityTestRule();

    /**
     * Gets the test-specific {@link Rule}.
     *
     * <p>By default it returns {@link #getActivityTestRule()}, but subclasses with more than one
     * rule can override it to return a {@link RuleChain}.
     */
    @NonNull
    protected TestRule getMainTestRule() {
        return getActivityTestRule();
    }

    protected A launchActivity() {
        Log.d(mTag, "Launching " + mActivityClass.getSimpleName());

        return getActivityTestRule().launchActivity(new Intent(sContext, mActivityClass));
    }

    protected A launchActivity(@Nullable Visitor<Intent> visitor) {
        Log.d(mTag, "Launching " + mActivityClass.getSimpleName());

        final Intent intent = new Intent(sContext, mActivityClass);
        if (visitor != null) {
            visitor.visit(intent);
        }
        return getActivityTestRule().launchActivity(intent);
    }

    @NonNull
    protected ActivityWatcher startWatcher() {
        return mActivitiesWatcher.watch(mActivityClass);
    }
}
