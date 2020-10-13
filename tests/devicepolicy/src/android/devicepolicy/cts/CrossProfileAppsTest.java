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

import static com.android.compatibility.common.util.enterprise.DeviceState.UserType.PRIMARY_USER;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.CrossProfileApps;
import android.os.UserHandle;
import android.os.UserManager;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.enterprise.DeviceState;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasWorkProfile;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnWorkProfile;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class CrossProfileAppsTest {

    private static final String ID_USER_TEXTVIEW =
            "com.android.cts.devicepolicy:id/user_textview";
    private static final long TIMEOUT_WAIT_UI = TimeUnit.SECONDS.toMillis(10);
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final CrossProfileApps sCrossProfileApps =
            sContext.getSystemService(CrossProfileApps.class);
    private static final UserManager sUserManager = sContext.getSystemService(UserManager.class);

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @RequireRunOnPrimaryUser
    public void getTargetUserProfiles_callingFromPrimaryUser_doesNotContainPrimaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(sDeviceState.getPrimaryUser());
    }
    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void getTargetUserProfiles_callingFromPrimaryUser_doesNotContainSecondaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(sDeviceState.getSecondaryUser());
    }

    @Test
    @RequireRunOnWorkProfile
    public void getTargetUserProfiles_callingFromWorkProfile_containsPrimaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).contains(sDeviceState.getPrimaryUser());
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void getTargetUserProfiles_callingFromPrimaryUser_containsWorkProfile() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).contains(sDeviceState.getWorkProfile());
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installTestApp = false)
    public void getTargetUserProfiles_callingFromPrimaryUser_appNotInstalledInWorkProfile_doesNotContainWorkProfile() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(sDeviceState.getWorkProfile());
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER)
    public void getTargetUserProfiles_callingFromSecondaryUser_doesNotContainWorkProfile() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(
                sDeviceState.getWorkProfile(/* forUser= */ PRIMARY_USER));
    }

    @Test
    @RequireRunOnWorkProfile
    @Ignore // TODO(scottjonathan): Replace use of UIAutomator
    public void startMainActivity_callingFromWorkProfile_targetIsPrimaryUser_launches() {
        sCrossProfileApps.startMainActivity(
                new ComponentName(sContext, MainActivity.class), sDeviceState.getPrimaryUser());

        assertMainActivityLaunchedForUser(sDeviceState.getPrimaryUser());
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @Ignore // TODO(scottjonathan): Replace use of UIAutomator
    public void startMainActivity_callingFromPrimaryUser_targetIsWorkProfile_launches() {
        sCrossProfileApps.startMainActivity(
                new ComponentName(sContext, MainActivity.class), sDeviceState.getWorkProfile());

        assertMainActivityLaunchedForUser(sDeviceState.getWorkProfile());
    }

    private void assertMainActivityLaunchedForUser(UserHandle user) {
        // TODO(scottjonathan): Replace this with a standard event log or similar to avoid UI
        // Look for the text view to verify that MainActivity is started.
        UiObject2 textView = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .wait(
                        Until.findObject(By.res(ID_USER_TEXTVIEW)),
                        TIMEOUT_WAIT_UI);
        assertNotNull("Failed to start activity in target user", textView);
        // Look for the text in textview, it should be the serial number of target user.
        assertEquals("Activity is started in wrong user",
                String.valueOf(sUserManager.getSerialNumberForUser(user)),
                textView.getText());
    }

    @Test
    public void startMainActivity_activityNotExported_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    new ComponentName(sContext, NonExportedActivity.class),
                    sDeviceState.getPrimaryUser());
        });
    }

    @Test
    public void startMainActivity_activityNotMain_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    new ComponentName(sContext, NonMainActivity.class),
                    sDeviceState.getPrimaryUser());
        });
    }

    @Test
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
    @EnsureHasSecondaryUser
    public void
    startMainActivity_callingFromPrimaryUser_targetIsSecondaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    new ComponentName(sContext, MainActivity.class),
                    sDeviceState.getSecondaryUser());
        });
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER)
    public void
    startMainActivity_callingFromSecondaryUser_targetIsWorkProfile_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    new ComponentName(sContext, MainActivity.class),
                    sDeviceState.getWorkProfile(/* forUser= */ PRIMARY_USER));
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
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void getProfileSwitchingLabel_callingFromPrimaryUser_targetIsSecondaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingLabel(sDeviceState.getPrimaryUser());
        });
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER)
    public void getProfileSwitchingLabel_callingFromSecondaryUser_targetIsWorkProfile_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingLabel(
                    sDeviceState.getWorkProfile(/* forUser= */ PRIMARY_USER));
        });
    }

    @Test
    @RequireRunOnWorkProfile
    public void getProfileSwitchingLabel_callingFromWorProfile_targetIsPrimaryUser_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingLabel(
                sDeviceState.getPrimaryUser())).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void getProfileSwitchingLabel_callingFromPrimaryUser_targetIsWorkProfile_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingLabel(
                sDeviceState.getWorkProfile())).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    public void getProfileSwitchingLabelIconDrawable_callingFromPrimaryUser_targetIsPrimaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(sDeviceState.getPrimaryUser());
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void getProfileSwitchingLabelIconDrawable_callingFromPrimaryUser_targetIsSecondaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(sDeviceState.getSecondaryUser());
        });
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER)
    public void getProfileSwitchingLabelIconDrawable_callingFromSecondaryUser_targetIsWorkProfile_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(
                    sDeviceState.getWorkProfile(/* forUser= */ PRIMARY_USER));
        });
    }

    @Test
    @RequireRunOnWorkProfile
    public void getProfileSwitchingIconDrawable_callingFromWorkProfile_targetIsPrimaryUser_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingIconDrawable(
                sDeviceState.getPrimaryUser())).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void getProfileSwitchingIconDrawable_callingFromPrimaryUser_targetIsWorkProfile_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingIconDrawable(
                sDeviceState.getWorkProfile())).isNotNull();
    }
}