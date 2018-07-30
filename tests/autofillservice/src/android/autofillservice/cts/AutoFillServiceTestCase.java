/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.getContext;
import static android.autofillservice.cts.InstrumentedAutoFillService.SERVICE_NAME;
import static android.autofillservice.cts.common.ShellHelper.runShellCommand;

import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.autofillservice.cts.common.SettingsStateKeeperRule;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

/**
 * Placeholder for the base class for all integration tests:
 *
 * <ul>
 *   <li>{@link AutoActivityLaunch}
 *   <li>{@link ManualActivityLaunch}
 * </ul>
 *
 * <p>These classes provide the common infrastructure such as:
 *
 * <ul>
 *   <li>Preserving the autofill service settings.
 *   <li>Cleaning up test state.
 *   <li>Wrapping the test under autofill-specific test rules.
 *   <li>Launching the activity used by the test.
 * </ul>
 */
final class AutoFillServiceTestCase {

    /**
     * Base class for all test cases that use an {@link AutofillActivityTestRule} to
     * launch the activity.
     */
    // Must be public becaue of @ClassRule
    public abstract static class AutoActivityLaunch<A extends AbstractAutoFillActivity>
            extends BaseTestCase {

        @ClassRule
        public static final SettingsStateKeeperRule sPublicServiceSettingsKeeper =
                sTheRealServiceSettingsKeeper;

        protected AutoActivityLaunch() {
            super(sDefaultUiBot);
        }

        @Override
        protected TestRule getMainTestRule() {
            return getActivityRule();
        }

        /**
         * Gets the rule to launch the main activity for this test.
         *
         * <p><b>Note: </b>the rule must be either lazily generated or a static singleton, otherwise
         * this method could return {@code null} when the rule chain that uses it is constructed.
         *
         */
        protected abstract @NonNull AutofillActivityTestRule<A> getActivityRule();

        protected @NonNull A launchActivity(@NonNull Intent intent) {
            return getActivityRule().launchActivity(intent);
        }

        protected @NonNull A getActivity() {
            return getActivityRule().getActivity();
        }
    }

    /**
     * Base class for all test cases that don't require an {@link AutofillActivityTestRule}.
     */
    // Must be public becaue of @ClassRule
    public abstract static class ManualActivityLaunch extends BaseTestCase {

        @ClassRule
        public static final SettingsStateKeeperRule sPublicServiceSettingsKeeper =
                sTheRealServiceSettingsKeeper;

        protected ManualActivityLaunch() {
            this(sDefaultUiBot);
        }

        protected ManualActivityLaunch(@NonNull UiBot uiBot) {
            super(uiBot);
        }

        @Override
        protected TestRule getMainTestRule() {
            return new TestRule() {

                @Override
                public Statement apply(Statement base, Description description) {
                    // Returns a no-op statements
                    return new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            base.evaluate();
                        }
                    };
                }
            };
        }
    }

    @RunWith(AndroidJUnit4.class)
    private abstract static class BaseTestCase {

        private static final String TAG = "AutoFillServiceTestCase";

        protected static final Replier sReplier = InstrumentedAutoFillService.getReplier();

        protected static final Context sContext = InstrumentationRegistry.getTargetContext();

        // Hack because JUnit requires that @ClassRule instance belong to a public class.
        protected static final SettingsStateKeeperRule sTheRealServiceSettingsKeeper =
                new SettingsStateKeeperRule(sContext, Settings.Secure.AUTOFILL_SERVICE) {
            @Override
            protected void preEvaluate(Description description) {
                JUnitHelper.setCurrentTestClass(description.getClassName());
            }

            @Override
            protected void postEvaluate(Description description) {
                JUnitHelper.setCurrentTestClass(null);
            }
        };

        private final TestWatcher mTestWatcher = new AutofillTestWatcher();

        private final RetryRule mRetryRule = new RetryRule(1);

        private final AutofillLoggingTestRule mLoggingRule = new AutofillLoggingTestRule(TAG);

        private final RequiredFeatureRule mRequiredFeatureRule =
                new RequiredFeatureRule(PackageManager.FEATURE_AUTOFILL);

        protected final SafeCleanerRule mSafeCleanerRule = new SafeCleanerRule()
                .setDumper(mLoggingRule)
                .run(() -> sReplier.assertNoUnhandledFillRequests())
                .run(() -> sReplier.assertNoUnhandledSaveRequests())
                .add(() -> { return sReplier.getExceptions(); });

        @Rule
        public final RuleChain mLookAllTheseRules = RuleChain
                //
                // mRequiredFeatureRule should be first so the test can be skipped right away
                .outerRule(mRequiredFeatureRule)
                //
                // mTestWatcher should always be one the first rules, as it defines the name of the
                // test being ran and finishes dangling activities at the end
                .around(mTestWatcher)
                //
                // mLoggingRule wraps the test but doesn't interfere with it
                .around(mLoggingRule)
                //
                // mRetryRule will retry test in case of failure
                .around(mRetryRule)
                //
                // mSafeCleanerRule should be closest to the main test as possible.
                .around(mSafeCleanerRule)
                //
                // Finally, let subclasses add their own rules (like ActivityTestRule)
                .around(getMainTestRule());


        protected final Context mContext = sContext;
        protected final String mPackageName;
        protected final UiBot mUiBot;

        private BaseTestCase(@NonNull UiBot uiBot) {
            mPackageName = mContext.getPackageName();
            mUiBot = uiBot;
            mUiBot.reset();
        }

        /**
         * Gets the test-specific {@link Rule @Rule}.
         *
         * <p>Sub-class <b>MUST</b> override this method instead of annotation their own rules,
         * so the order is preserved.
         *
         */
        @NonNull
        protected abstract TestRule getMainTestRule();

        @Before
        public void prepareDevice() throws Exception {
            Log.v(TAG, "@Before: prepareDevice()");

            // Unlock screen.
            runShellCommand("input keyevent KEYCODE_WAKEUP");

            // Dismiss keyguard, in case it's set as "Swipe to unlock".
            runShellCommand("wm dismiss-keyguard");

            // Collapse notifications.
            runShellCommand("cmd statusbar collapse");

            // Set orientation as portrait, otherwise some tests might fail due to elements not
            // fitting in, IME orientation, etc...
            mUiBot.setScreenOrientation(UiBot.PORTRAIT);

            // Wait until device is idle to avoid flakiness
            mUiBot.waitForIdle();
        }

        @Before
        public void preTestCleanup() {
            Log.v(TAG, "@Before: preTestCleanup()");

            prepareServicePreTest();

            InstrumentedAutoFillService.resetStaticState();
            AuthenticationActivity.resetStaticState();
            sReplier.reset();
        }

        /**
         * Prepares the service before each test - by default, disables it
         */
        protected void prepareServicePreTest() {
            Log.v(TAG, "prepareServicePreTest(): calling disableService()");
            disableService();
        }

        /**
         * Enables the {@link InstrumentedAutoFillService} for autofill for the current user.
         */
        protected void enableService() {
            Helper.enableAutofillService(getContext(), SERVICE_NAME);
        }

        /**
         * Disables the {@link InstrumentedAutoFillService} for autofill for the current user.
         */
        protected void disableService() {
            Helper.disableAutofillService(getContext());
        }

        /**
         * Asserts that the {@link InstrumentedAutoFillService} is enabled for the default user.
         */
        protected void assertServiceEnabled() {
            Helper.assertAutofillServiceStatus(SERVICE_NAME, true);
        }

        /**
         * Asserts that the {@link InstrumentedAutoFillService} is disabled for the default user.
         */
        protected void assertServiceDisabled() {
            Helper.assertAutofillServiceStatus(SERVICE_NAME, false);
        }

        protected RemoteViews createPresentation(String message) {
            final RemoteViews presentation = new RemoteViews(getContext()
                    .getPackageName(), R.layout.list_item);
            presentation.setTextViewText(R.id.text1, message);
            return presentation;
        }
    }

    protected static final UiBot sDefaultUiBot = new UiBot();

    private AutoFillServiceTestCase() {
        throw new UnsupportedOperationException("Contain static stuff only");
    }
}
