/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.projection.cts;

import static android.media.cts.MediaProjectionActivity.CANCEL_RESOURCE_ID;
import static android.media.cts.MediaProjectionActivity.ENTIRE_SCREEN_STRING_RES_NAME;
import static android.media.cts.MediaProjectionActivity.SINGLE_APP_STRING_RES_NAME;
import static android.media.cts.MediaProjectionActivity.SPINNER_RESOURCE_ID;
import static android.media.cts.MediaProjectionActivity.getResourceString;
import static android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay;
import static android.media.projection.MediaProjectionConfig.createConfigForUserChoice;
import static android.media.projection.cts.MediaProjectionPermissionDialogTestActivity.EXTRA_MEDIA_PROJECTION_CONFIG;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link MediaProjection} compat change dependent logic
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionCompatChangeTest
 */
@NonMainlineTest
public class MediaProjectionCompatChangeTest {
    private static final String LOG_COMPAT_CHANGE = "android.permission.LOG_COMPAT_CHANGE";
    private static final String READ_COMPAT_CHANGE_CONFIG =
            "android.permission.READ_COMPAT_CHANGE_CONFIG";
    private static final long OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION = 316897322L;

    private Context mContext;
    private UiDevice mDevice;

    private String mEntireScreenString;
    private String mSingleAppString;

    @Rule
    public ActivityTestRule<MediaProjectionPermissionDialogTestActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionPermissionDialogTestActivity.class, false, false);

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(LOG_COMPAT_CHANGE, READ_COMPAT_CHANGE_CONFIG);
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mEntireScreenString = getResourceString(mContext, ENTIRE_SCREEN_STRING_RES_NAME);
        mSingleAppString = getResourceString(mContext, SINGLE_APP_STRING_RES_NAME);
    }

    @After
    public void tearDown() {
        mActivityRule.finishActivity();
    }

    // MediaProjectionConfig#createConfigForDefaultDisplay should cause the single app option to be
    // disabled and entire screen to be the default option. This test ensures that when the
    // per-app override is enabled, that the MediaProjectionConfig is overridden, and the single app
    // option is enabled and the default option.
    @Test
    public void testMediaProjectionPermissionDialog_overrideDefaultDisplayConfig() {
        runShellCommand("am compat enable --no-kill "
                + OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION + " "
                + mContext.getPackageName());

        testMediaProjectionPermissionDialog(createConfigForDefaultDisplay(), mSingleAppString);
    }

    // MediaProjectionConfig#createConfigForUserChoice should cause the single app option to be
    // enabled and be the default option. This test ensures that when the per-app override is
    // enabled, this behaviour is not changed.
    @Test
    public void testMediaProjectionPermissionDialog_overrideUserChoiceConfig() {
        runShellCommand("am compat enable --no-kill "
                + OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION + " "
                + mContext.getPackageName());

        testMediaProjectionPermissionDialog(createConfigForDefaultDisplay(), mSingleAppString);
    }

    // MediaProjectionConfig#createConfigForDefaultDisplay should cause the single app option to be
    // disabled and entire screen to be the default option. This test ensures that this behaviour is
    // not changed.
    @Test
    public void testMediaProjectionPermissionDialog_defaultDisplayConfig() {
        testMediaProjectionPermissionDialog(createConfigForDefaultDisplay(), mEntireScreenString);
    }

    // MediaProjectionConfig#createConfigForUserChoice should cause the single app option to be
    // enabled and be the default option. This test ensures that this behaviour is not changed.
    @Test
    public void testMediaProjectionPermissionDialog_userChoiceConfig() {
        testMediaProjectionPermissionDialog(createConfigForUserChoice(), mSingleAppString);
    }

    private void testMediaProjectionPermissionDialog(
            MediaProjectionConfig config, String expectedSpinnerString) {
        Intent testActivityIntent = new Intent().putExtra(
                EXTRA_MEDIA_PROJECTION_CONFIG, config);
        mActivityRule.launchActivity(testActivityIntent);
        mDevice.waitForIdle();

        // check if we can find a spinner which is has the expected default option
        assertThat(mDevice.hasObject(
                By.res(SPINNER_RESOURCE_ID)
                        .hasChild(
                                By.text(expectedSpinnerString)))).isTrue();

        // close the dialog so it doesn't linger for subsequent tests
        UiObject2 cancelButton = mDevice.findObject(By.res(CANCEL_RESOURCE_ID));
        cancelButton.click();

        // disable compat flag for subsequent tests
        runShellCommand("am compat disable --no-kill "
                + OVERRIDE_DISABLE_MEDIA_PROJECTION_SINGLE_APP_OPTION + " "
                + mContext.getPackageName());

    }
}
