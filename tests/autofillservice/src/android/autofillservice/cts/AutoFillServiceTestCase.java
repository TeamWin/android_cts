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
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.widget.RemoteViews;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;

/**
 * Base class for all other tests.
 */
@RunWith(AndroidJUnit4.class)
// NOTE: @ClassRule requires it to be public
public abstract class AutoFillServiceTestCase {
    private static final String TAG = "AutoFillServiceTestCase";

    static final UiBot sDefaultUiBot = new UiBot();

    protected static final Replier sReplier = InstrumentedAutoFillService.getReplier();

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    @ClassRule
    public static final SettingsStateKeeperRule mServiceSettingsKeeper =
            new SettingsStateKeeperRule(sContext, Settings.Secure.AUTOFILL_SERVICE);

    private final TestWatcher mTestWatcher = new AutofillTestWatcher();

    private final RetryRule mRetryRule = new RetryRule(2);

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
            .outerRule(mTestWatcher)
            .around(mRequiredFeatureRule)
            .around(mLoggingRule)
            .around(mSafeCleanerRule)
            .around(mRetryRule);

    protected final Context mContext = sContext;
    protected final String mPackageName;
    protected final UiBot mUiBot;

    /**
     * Stores the previous logging level so it's restored after the test.
     */
    private String mLoggingLevel;

    protected AutoFillServiceTestCase() {
        this(sDefaultUiBot);
    }

    protected AutoFillServiceTestCase(UiBot uiBot) {
        mPackageName = mContext.getPackageName();
        mUiBot = uiBot;
        mUiBot.reset();
    }

    @BeforeClass
    public static void prepareScreen() throws Exception {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");

        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");

        // Collapse notifications.
        runShellCommand("cmd statusbar collapse");

        // Set orientation as portrait, otherwise some tests might fail due to elements not fitting
        // in, IME orientation, etc...
        sDefaultUiBot.setScreenOrientation(UiBot.PORTRAIT);
    }

    @Before
    public void preTestCleanup() {
        Log.d(TAG, "preTestCleanup()");

        disableService();

        InstrumentedAutoFillService.resetStaticState();
        AuthenticationActivity.resetStaticState();
        sReplier.reset();
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
        Helper.disableAutofillService(getContext(), SERVICE_NAME);
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
