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
package android.autofillservice.cts.saveui;

import static android.autofillservice.cts.activities.SimpleSaveActivity.ID_INPUT;
import static android.autofillservice.cts.activities.SimpleSaveActivity.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.findAutofillIdByResourceId;
import static android.autofillservice.cts.testcore.Helper.getContext;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertThat;

import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.SimpleSaveActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.UiBot;
import android.provider.DeviceConfig;
import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.CustomDescription;
import android.service.autofill.FillContext;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.util.DisplayMetrics;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.DeviceConfigStateManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.regex.Pattern;

/** Tests whether autofill save dialog height is configurable */
public class AutofillSaveDialogHeightTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<SimpleSaveActivity> {

    private SimpleSaveActivity mActivity;
    private static final Pattern MATCH_ALL = Pattern.compile("^(.*)$");
    private final int mCustomHeight = 10;
    private final int mDefaultHeight = 20;
    private final String mTestUsername = "USER";
    private final String mTestPassword = "PASSWORD";

    @Override
    protected AutofillActivityTestRule<SimpleSaveActivity> getActivityRule() {
        return new AutofillActivityTestRule<SimpleSaveActivity>(SimpleSaveActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Override
    protected TestRule getMainTestRule() {
        return RuleChain.outerRule(getActivityRule())
                .around(
                        new DeviceConfigStateChangerRule(
                                sContext,
                                DeviceConfig.NAMESPACE_AUTOFILL,
                                "autofill_save_dialog_portrait_body_height_max_percent",
                                Integer.toString(mCustomHeight)));
    }

    @Before
    public void prepareDevice() throws Exception {
        // Ignore tests on small screen
        mUiBot.assumeMinimumResolution(500);
        // Set device in portrait mode
        mUiBot.setScreenOrientation(UiBot.PORTRAIT);
    }

    @Test
    public void testSaveDialogHeightIsSameWithDefaultWhenNotSetInConfig() throws Exception {
        // Set service.
        enableService();

        // Set max scroll view height in config to null
        setScrollviewHeightInConfig(null);

        // Trigger save ui
        // Let contents fill up the scrollview to reach max height
        triggerSaveUiWithContents(mTestUsername.repeat(20), mTestPassword.repeat(20));

        // Get save ui object
        final UiObject2 saveUi = mUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Get scrollview from the UiObject2
        final UiObject2 scrollView = saveUi.findObject(By.scrollable(true));

        // Get scrollview height
        int scrollViewHeight = scrollView.getVisibleBounds().height();

        // Get screen height
        int screenHeight = getScreenHeight();

        // Assert scrollview height is 20% of screen height (the default)
        assertThat(scrollViewHeight).isEqualTo((int) (screenHeight * mDefaultHeight / 100));
    }

    @Test
    public void testSaveDialogHeightIsSameWithConfig() throws Exception {
        // Set service.
        enableService();

        // Trigger save ui
        // Let content fill up the scrollview to get it reach max height
        triggerSaveUiWithContents(mTestUsername.repeat(20), mTestPassword.repeat(20));

        // Get save ui object
        final UiObject2 saveUi = mUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Get scrollview from UiObject2
        final UiObject2 scrollView = saveUi.findObject(By.scrollable(true));

        // Get scrollview height
        int scrollViewHeight = scrollView.getVisibleBounds().height();

        // Get screen height
        int screenHeight = getScreenHeight();

        // Assert scrollview height is of customPercent compared to screen height
        assertThat(scrollViewHeight).isEqualTo((int) (screenHeight * mCustomHeight / 100));
    }

    @Test
    public void testSaveDialogWrapAroundContent() throws Exception {
        final int customHeightInTest = 90;

        // Set service.
        enableService();

        // Set scrollview max height
        setScrollviewHeightInConfig(Integer.toString(customHeightInTest));

        // Trigger save ui
        // Let content be smaller than max height set in config so scrollview will wrap around it
        // instead of reaching max height
        triggerSaveUiWithContents(mTestUsername, mTestPassword);

        // Get save ui object
        final UiObject2 saveUi = mUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Get textView from UiObject2
        final UiObject2 textView = saveUi.findObject(By.textContains(mTestUsername));

        // Get textview height
        int textViewHeight = textView.getVisibleBounds().height();

        // Get scrollview as textView's parents
        final UiObject2 scrollView = textView.getParent();

        // Get scrollview height
        int scrollViewHeight = scrollView.getVisibleBounds().height();

        // Get screen height
        int screenHeight = getScreenHeight();

        // Assert scrollview height is same with textview as it wraps around the text
        assertThat(scrollViewHeight).isEqualTo(textViewHeight);

        // Assert scrollview height is smaller than max height set in config as it wraps around the
        // text
        assertThat(scrollViewHeight).isLessThan((int) (screenHeight * customHeightInTest / 100));
    }

    private void setScrollviewHeightInConfig(String value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(
                        sContext,
                        DeviceConfig.NAMESPACE_AUTOFILL,
                        "autofill_save_dialog_portrait_body_height_max_percent");
        Helper.setDeviceConfig(deviceConfigStateManager, value);
    }

    private int getScreenHeight() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    private RemoteViews newTemplate(int resourceId) {
        return new RemoteViews(getContext().getPackageName(), resourceId);
    }

    private void triggerSaveUiWithContents(String username, String password) throws Exception {
        // Set Response
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT, ID_PASSWORD)
                        .setSaveInfoVisitor(
                                (contexts, builder) -> {
                                    final FillContext context = contexts.get(0);
                                    final AutofillId usernameId =
                                            findAutofillIdByResourceId(context, ID_INPUT);
                                    final AutofillId passwordId =
                                            findAutofillIdByResourceId(context, ID_PASSWORD);

                                    final CharSequenceTransformation usernameTrans =
                                            new CharSequenceTransformation.Builder(
                                                            usernameId, MATCH_ALL, "username: $1")
                                                    .build();
                                    final CharSequenceTransformation passwordTrans =
                                            new CharSequenceTransformation.Builder(
                                                            passwordId, MATCH_ALL, "password: $1")
                                                    .build();
                                    builder.setCustomDescription(
                                            new CustomDescription.Builder(
                                                            newTemplate(
                                                            R.layout
                                                                .two_horizontal_text_fields))
                                                    .addChild(R.id.first, usernameTrans)
                                                    .addChild(R.id.second, passwordTrans)
                                                    .build());
                                })
                        .build());

        // Trigger saveUi
        mActivity.syncRunOnUiThread(() -> mActivity.mInput.requestFocus());
        sReplier.getNextFillRequest();

        // Enter a long string to show the scrollview
        mActivity.setTextAndWaitTextChange(/* input= */ username, /* password= */ password);
        mActivity.syncRunOnUiThread(() -> mActivity.mCommit.performClick());
    }
}
