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


import static android.media.projection.cts.MediaProjectionCustomIntentActivity.EXTRA_SCREEN_CAPTURE_INTENT;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link MediaProjectionManager}.
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionManagerTest
 */
@NonMainlineTest
public class MediaProjectionManagerTest {
    private Context mContext;

    private MediaProjectionManager mProjectionManager;

    @Rule
    public ActivityTestRule<MediaProjectionCustomIntentActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionCustomIntentActivity.class, false, false);

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(ActivityManager.getCurrentUser()));
        });
        mProjectionManager = mContext.getSystemService(MediaProjectionManager.class);
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#createScreenCaptureIntent")
    @Test
    public void testCreateScreenCaptureIntent() {
        assertThat(mProjectionManager.createScreenCaptureIntent()).isNotNull();
        assertThat(mProjectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay())).isNotNull();
        assertThat(mProjectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForUserChoice())).isNotNull();
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection() throws Exception {
        // Launch the activity.
        mActivityRule.launchActivity(null);
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();
        // Ensure mediaprojection instance is valid.
        MediaProjection mediaProjection = activity.waitForMediaProjection();
        assertThat(mediaProjection).isNotNull();
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection_displayConfig() throws Exception {
        validateValidMediaProjectionFromIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay());
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection_usersChoiceConfig() throws Exception {
        validateValidMediaProjectionFromIntent(MediaProjectionConfig.createConfigForUserChoice());
    }

    private void validateValidMediaProjectionFromIntent(MediaProjectionConfig config)
            throws Exception {
        // Build intent to launch test activity.
        Intent testActivityIntent = new Intent();
        testActivityIntent.setClass(mContext, MediaProjectionCustomIntentActivity.class);
        // Ensure test activity uses given intent when requesting permission.
        testActivityIntent.putExtra(EXTRA_SCREEN_CAPTURE_INTENT,
                mProjectionManager.createScreenCaptureIntent(config));
        // Launch the activity.
        mActivityRule.launchActivity(testActivityIntent);
        MediaProjectionCustomIntentActivity activity = mActivityRule.getActivity();
        // Ensure mediaprojection instance is valid.
        MediaProjection mediaProjection = activity.waitForMediaProjection();
        assertThat(mediaProjection).isNotNull();
    }
}
