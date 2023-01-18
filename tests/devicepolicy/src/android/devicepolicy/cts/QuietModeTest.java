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

package android.devicepolicy.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.Bundle;

import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to ensure apps are properly restricted when the user is in quiet mode.
 */
@RunWith(BedsteadJUnit4.class)
public class QuietModeTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities()
            .contains(activity().where().exported().isTrue())
            .get();

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void startActivityInQuietProfile_quietModeDialogShown() throws Exception {
        try (TestAppInstance instance = sTestApp.install(sDeviceState.workProfile())) {
            // Override "Turn on work apps" dialog title to avoid depending on a particular string.
            TestApis.devicePolicy().resources().strings().set(
                    "Core.UNLAUNCHABLE_APP_WORK_PAUSED_TITLE", R.string.test_string_1);

            TestAppActivityReference activityReference =
                    instance.activities().query().whereActivity().exported().isTrue().get();

            sDeviceState.workProfile().setQuietMode(true);

            Intent intent = new Intent()
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(activityReference.component().componentName());
            TestApis.context().instrumentedContext()
                    .startActivityAsUser(
                            intent, new Bundle(), sDeviceState.workProfile().userHandle());

            assertThat(TestApis.ui().device()
                    .findObject(new UiSelector().text("test string 1"))
                    .exists()).isTrue();
        } finally {
            TestApis.devicePolicy().resources().strings()
                    .reset("Core.UNLAUNCHABLE_APP_WORK_PAUSED_TITLE");
            sDeviceState.workProfile().setQuietMode(false);
        }
    }
}
