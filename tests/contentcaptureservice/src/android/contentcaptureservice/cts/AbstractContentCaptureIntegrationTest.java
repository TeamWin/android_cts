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
import static android.contentcaptureservice.cts.Helper.resetService;
import static android.contentcaptureservice.cts.Helper.setService;
import static android.contentcaptureservice.cts.common.ShellHelper.runShellCommand;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.contentcaptureservice.cts.CtsContentCaptureService.ServiceWatcher;
import android.contentcaptureservice.cts.common.ActivitiesWatcher;
import android.contentcaptureservice.cts.common.ActivitiesWatcher.ActivityWatcher;
import android.contentcaptureservice.cts.common.Visitor;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    private final String mTag = getClass().getSimpleName();

    protected static final Context sContext = InstrumentationRegistry.getTargetContext();

    protected ActivitiesWatcher mActivitiesWatcher;

    private final Class<A> mActivityClass;

    private final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule("content_capture");
    private final ContentCaptureLoggingTestRule mLoggingRule =
            new ContentCaptureLoggingTestRule(mTag);


    protected final SafeCleanerRule mSafeCleanerRule = new SafeCleanerRule()
            .setDumper(mLoggingRule)
            .add(() -> {
                return CtsContentCaptureService.getExceptions();
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

    /**
     * Watcher set on {@link #enableService()} and used to wait until it's gone after the test
     * finishes.
     */
    private ServiceWatcher mServiceWatcher;

    protected AbstractContentCaptureIntegrationTest(@NonNull Class<A> activityClass) {
        mActivityClass = activityClass;
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

    // TODO(b/123539404): this method should be called from the SafeCleaner, but we'll need to
    // add a run() method that takes an object that can throw an exception
    @After
    public void restoreDefaultService() throws InterruptedException {
        Log.v(mTag, "@After: restoreDefaultService()");
        resetService();

        if (mServiceWatcher != null) {
            mServiceWatcher.waitOnDestroy();
        }
    }

    // TODO(b/123429736): temporary method until Autofill's StateChangerRule is moved to common
    @Nullable
    public static void setFeatureEnabled(@Nullable String enabled) {
        final String property = Settings.Secure.CONTENT_CAPTURE_ENABLED;
        if (enabled == null) {
            runShellCommand("settings delete secure %s", property);
        } else {
            runShellCommand("settings put secure %s %s", property, enabled);
        }
        SystemClock.sleep(1000); // We need to sleep as we're not waiting for the listener callback
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
