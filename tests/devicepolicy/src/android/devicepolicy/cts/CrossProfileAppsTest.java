/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.CrossProfileApps;
import android.os.UserHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.enterprise.DeviceState;
import com.android.compatibility.common.util.enterprise.Preconditions;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnWorkProfile;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CrossProfileAppsTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final CrossProfileApps sCrossProfileApps =
            sContext.getSystemService(CrossProfileApps.class);

    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final Preconditions mPreconditions = new Preconditions();

    @Test
    @RequireRunOnPrimaryUser
    public void getTargetUserProfiles_callingFromPrimaryUser_doesNotContainPrimaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

         assertThat(targetProfiles).doesNotContain(sDeviceState.getPrimaryUser());
    }

    @Test
    @RequireRunOnWorkProfile
    public void getTargetUserProfiles_callingFromWorkProfile_containsPrimaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).contains(sDeviceState.getPrimaryUser());
    }

    // TODO(scottjonathan): Add
    //  startMainActivity_callingFromWorkProfile_targetIsPrimaryUser_launches

    @Test
    @RequireRunOnWorkProfile
    public void startMainActivity_activityNotExported_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    new ComponentName(
                            sContext, NonExportedActivity.class), sDeviceState.getPrimaryUser());
        });
    }

    @Test
    @RequireRunOnWorkProfile
    public void startMainActivity_activityNotMain_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    new ComponentName(
                            sContext, NonMainActivity.class), sDeviceState.getPrimaryUser());
        });
    }

    @Test
    @RequireRunOnWorkProfile
    @Ignore // TODO(scottjonathan): This requires another app to be installed which can be launched
    public void startMainActivity_activityIncorrectPackage_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {

        });
    }

    @Test
    @RequireRunOnPrimaryUser
    public void
    startMainActivity_callingFromPrimaryUser_targetIsPrimaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    new ComponentName(sContext, MainActivity.class), sDeviceState.getPrimaryUser());
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    public void getProfileSwitchingLabel_callingFromPrimaryUser_targetIsPrimaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingLabel(sDeviceState.getPrimaryUser());
        });
    }

    @Test
    @RequireRunOnWorkProfile
    public void getProfileSwitchingLabel_callingFromWorkUser_targetIsPrimaryUser_notNull() {
        assertThat(
                sCrossProfileApps.getProfileSwitchingLabel(
                        sDeviceState.getPrimaryUser())).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    public void getProfileSwitchingLabelIconDrawable_callingFromPrimaryUser_targetIsPrimaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(sDeviceState.getPrimaryUser());
        });
    }

    @Test
    @RequireRunOnWorkProfile
    public void getProfileSwitchingIconDrawable_callingFromWorkUser_targetIsPrimaryUser_notNull() {
        assertThat(
                sCrossProfileApps.getProfileSwitchingIconDrawable(
                        sDeviceState.getPrimaryUser())).isNotNull();
    }

}
