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

import static com.android.bedstead.harrier.OptionalBoolean.FALSE;
import static com.android.bedstead.harrier.OptionalBoolean.TRUE;
import static com.android.bedstead.harrier.UserType.PRIMARY_USER;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_PROFILES;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.START_CROSS_PROFILE_ACTIVITIES;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.ActivityOptions;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.CrossProfileApps;
import android.os.UserHandle;
import android.stats.devicepolicy.EventId;

import androidx.test.core.app.ApplicationProvider;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.packages.ProcessReference;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.eventlib.events.activities.ActivityCreatedEvent;
import com.android.eventlib.events.activities.ActivityEvents;
import com.android.queryable.queries.ActivityQuery;
import com.android.queryable.queries.IntentFilterQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class CrossProfileAppsTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final CrossProfileApps sCrossProfileApps =
            sContext.getSystemService(CrossProfileApps.class);

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final TestAppProvider sTestAppProvider = new TestAppProvider();

    private static final TestApp sCrossProfileTestApp = sTestAppProvider.query()
            .wherePermissions().contains("android.permission.INTERACT_ACROSS_PROFILES").get();
    private static final TestApp sNonCrossProfileTestApp = sTestAppProvider.query()
            .wherePermissions().doesNotContain("android.permission.INTERACT_ACROSS_PROFILES").get();
    private static final TestApp sTestAppWithMainActivity = sTestAppProvider.query()
            .whereActivities().contains(
                    ActivityQuery.activity().intentFilters().contains(
                            IntentFilterQuery.intentFilter().actions().contains(Intent.ACTION_MAIN))
            ).get();
    private static final TestApp sTestAppWithActivity = sTestAppWithMainActivity;

    // TODO(b/191637162): When we have permissions in test apps we won't need to use the
    //  instrumented app for this
    private static final ComponentName MAIN_ACTIVITY =
            new ComponentName(sContext, "android.devicepolicy.cts.MainActivity");
    private static final ComponentName NOT_EXPORTED_ACTIVITY =
            new ComponentName(sContext, "android.devicepolicy.cts.NotExportedMainActivity");
    private static final ComponentName NOT_MAIN_ACTIVITY =
            new ComponentName(sContext, "android.devicepolicy.cts.NotMainActivity");

    @Before
    @After
    public void cleanupOtherUsers() {
        // As these tests start this package on other users, we should kill all processes on other
        // users for this package

        Package pkg = TestApis.packages().instrumented();
        pkg.runningProcesses().stream()
                .filter(p -> !p.user().equals(TestApis.users().instrumented()))
                .forEach(ProcessReference::kill);
    }

    @Test
    @RequireRunOnPrimaryUser
    public void getTargetUserProfiles_callingFromPrimaryUser_doesNotContainPrimaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(sDeviceState.primaryUser().userHandle());
    }
    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void getTargetUserProfiles_callingFromPrimaryUser_doesNotContainSecondaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(sDeviceState.secondaryUser().userHandle());
    }

    @Test
    @RequireRunOnWorkProfile(installInstrumentedAppInParent = TRUE)
    public void getTargetUserProfiles_callingFromWorkProfile_containsPrimaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).contains(sDeviceState.primaryUser().userHandle());
    }

    @Test
    @RequireRunOnWorkProfile
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void getTargetUserProfiles_callingFromWorkProfile_doesNotContainSecondaryUser() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(sDeviceState.secondaryUser().userHandle());
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    public void getTargetUserProfiles_callingFromPrimaryUser_containsWorkProfile() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).contains(sDeviceState.workProfile().userHandle());
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = FALSE)
    public void getTargetUserProfiles_callingFromPrimaryUser_appNotInstalledInWorkProfile_doesNotContainWorkProfile() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(sDeviceState.workProfile().userHandle());
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER)
    public void getTargetUserProfiles_callingFromSecondaryUser_doesNotContainWorkProfile() {
        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(
                sDeviceState.workProfile(/* forUser= */ PRIMARY_USER).userHandle());
    }

    @Test
    @Postsubmit(reason = "new test")
    public void getTargetUserProfiles_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sCrossProfileApps.getTargetUserProfiles();

            assertThat(metrics.query()
                    .whereType().isEqualTo(
                            EventId.CROSS_PROFILE_APPS_GET_TARGET_USER_PROFILES_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
            ).wasLogged();
        }
    }

    @Test
    @RequireRunOnWorkProfile(installInstrumentedAppInParent = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_callingFromWorkProfile_targetIsPrimaryUser_launches() {
        sCrossProfileApps.startMainActivity(MAIN_ACTIVITY, sDeviceState.primaryUser().userHandle());

        assertThat(
                ActivityEvents.forActivity(MAIN_ACTIVITY, sDeviceState.primaryUser())
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_callingFromPrimaryUser_targetIsWorkProfile_launches() {
        sCrossProfileApps.startMainActivity(MAIN_ACTIVITY, sDeviceState.workProfile().userHandle());

        assertThat(
                ActivityEvents.forActivity(MAIN_ACTIVITY, sDeviceState.workProfile())
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_callingFromPrimaryUser_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sCrossProfileApps.startMainActivity(MAIN_ACTIVITY,
                    sDeviceState.workProfile().userHandle());

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.CROSS_PROFILE_APPS_START_ACTIVITY_AS_USER_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
            ).wasLogged();
        }
    }

    @Test
    @RequireRunOnWorkProfile(installInstrumentedAppInParent = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_callingFromWorkProfile_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sCrossProfileApps.startMainActivity(MAIN_ACTIVITY,
                    sDeviceState.primaryUser().userHandle());

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.CROSS_PROFILE_APPS_START_ACTIVITY_AS_USER_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
            ).wasLogged();
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_activityNotExported_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    NOT_EXPORTED_ACTIVITY, sDeviceState.workProfile().userHandle());
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_activityNotMain_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    NOT_MAIN_ACTIVITY, sDeviceState.workProfile().userHandle());
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_activityIncorrectPackage_throwsSecurityException() {
        try (TestAppInstance instance =
                     sTestAppWithMainActivity.install(sDeviceState.workProfile())) {

            TestAppActivityReference activity = instance.activities().query()
                            .whereActivity().exported().isTrue()
                            .whereActivity().intentFilters().contains(
                                    IntentFilterQuery.intentFilter().actions().contains(
                                            Intent.ACTION_MAIN
                                    )
                            )
                    .get();

            assertThrows(SecurityException.class, () -> {
                sCrossProfileApps.startMainActivity(
                        activity.component().componentName(),
                        sDeviceState.workProfile().userHandle());
            });
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_noComponent_throwsException() throws Exception {
        Intent intent = new Intent();
        intent.setAction("test");

        ActivityContext.runWithContext(activity ->
                assertThrows(NullPointerException.class, () ->
                        sCrossProfileApps.startActivity(
                                intent, sDeviceState.workProfile().userHandle(), activity)));
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_differentPackage_throwsException() throws Exception {
        try (TestAppInstance testAppInstance =
                     sTestAppWithActivity.install(sDeviceState.workProfile())) {
            TestAppActivityReference targetActivity = testAppInstance.activities().any();
            Intent intent = new Intent();
            intent.setComponent(targetActivity.component().componentName());

            ActivityContext.runWithContext(activity ->
                    assertThrows(SecurityException.class, () ->
                            sCrossProfileApps.startActivity(
                                    intent, sDeviceState.workProfile().userHandle(), activity)));
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_differentPackage_throwsException() throws Exception {
        try (TestAppInstance testAppInstance =
                     sTestAppWithActivity.install(sDeviceState.workProfile())) {
            TestAppActivityReference targetActivity = testAppInstance.activities().any();

            ActivityContext.runWithContext(activity ->
                    assertThrows(SecurityException.class, () ->
                            sCrossProfileApps.startActivity(
                                    targetActivity.component().componentName(),
                                    sDeviceState.workProfile().userHandle())));
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @EnsureDoesNotHavePermission({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES})
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withoutPermissions_throwsException() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        ActivityContext.runWithContext(activity ->
                assertThrows(SecurityException.class, () ->
                        sCrossProfileApps.startActivity(
                                intent, sDeviceState.workProfile().userHandle(), activity)));
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @EnsureDoesNotHavePermission({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES})
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_withoutPermissions_throwsException() throws Exception {
        ActivityContext.runWithContext(activity ->
                assertThrows(SecurityException.class, () ->
                        sCrossProfileApps.startActivity(
                                NOT_MAIN_ACTIVITY, sDeviceState.workProfile().userHandle())));
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureDoesNotHavePermission({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES})
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withPermission_startsActivity(
            @StringTestParameter({
                    INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
                    INTERACT_ACROSS_USERS_FULL}) String permission)
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        try (PermissionContext p = TestApis.permissions().withPermission(permission)) {
            ActivityContext.runWithContext(activity -> {
                sCrossProfileApps.startActivity(
                        intent, sDeviceState.workProfile().userHandle(), activity);
            });
        }

        assertThat(
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, sDeviceState.workProfile())
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(START_CROSS_PROFILE_ACTIVITIES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withCrossProfileActivitiesPermission_throwsException()
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        ActivityContext.runWithContext(activity -> {
            assertThrows(SecurityException.class, () -> sCrossProfileApps.startActivity(
                    intent, sDeviceState.workProfile().userHandle(), activity));
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureDoesNotHavePermission({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES})
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_withPermission_startsActivity(
            @StringTestParameter({
                    INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
                    INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES}) String permission)
            throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(permission)) {
            ActivityContext.runWithContext(activity -> {
                sCrossProfileApps.startActivity(
                        NOT_MAIN_ACTIVITY, sDeviceState.workProfile().userHandle());
            });
        }

        assertThat(
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, sDeviceState.workProfile())
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withOptionsBundle_startsActivity()
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        ActivityContext.runWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    intent, sDeviceState.workProfile().userHandle(), activity,
                    ActivityOptions.makeBasic().toBundle());
        });

        assertThat(
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, sDeviceState.workProfile())
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_notExported_startsActivity()
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_EXPORTED_ACTIVITY);

        ActivityContext.runWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    intent, sDeviceState.workProfile().userHandle(), activity);
        });

        assertThat(
                ActivityEvents.forActivity(NOT_EXPORTED_ACTIVITY, sDeviceState.workProfile())
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_notExported_throwsException()
            throws Exception {
        ActivityContext.runWithContext(activity -> {
            assertThrows(SecurityException.class, () -> sCrossProfileApps.startActivity(
                    NOT_EXPORTED_ACTIVITY, sDeviceState.workProfile().userHandle()));
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_sameTaskByDefault() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        int originalTaskId = ActivityContext.getWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    intent, sDeviceState.workProfile().userHandle(), activity);

            return activity.getTaskId();
        });

        ActivityCreatedEvent event =
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, sDeviceState.workProfile())
                        .activityCreated().waitForEvent();
        assertThat(event.taskId()).isEqualTo(originalTaskId);
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_logged() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            ActivityContext.runWithContext(activity ->
                    sCrossProfileApps.startActivity(
                    intent, sDeviceState.workProfile().userHandle(), activity));

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.START_ACTIVITY_BY_INTENT_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
                    .whereBoolean().isFalse() // Not from work profile
            ).wasLogged();
        }
    }

    @Test
    @RequireRunOnWorkProfile(installInstrumentedAppInParent = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_fromWorkProfile_logged() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            ActivityContext.runWithContext(activity ->
                    sCrossProfileApps.startActivity(
                            intent, sDeviceState.primaryUser().userHandle(), activity));

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.START_ACTIVITY_BY_INTENT_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
                    .whereBoolean().isTrue() // From work profile
            ).wasLogged();
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    public void
            startMainActivity_callingFromPrimaryUser_targetIsPrimaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sCrossProfileApps.startMainActivity(
                        MAIN_ACTIVITY, sDeviceState.primaryUser().userHandle()));
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    public void
    startMainActivity_callingFromPrimaryUser_targetIsSecondaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sCrossProfileApps.startMainActivity(MAIN_ACTIVITY,
                        sDeviceState.secondaryUser().userHandle()));
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER, installInstrumentedApp = TRUE)
    public void
    startMainActivity_callingFromSecondaryUser_targetIsWorkProfile_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sCrossProfileApps.startMainActivity(
                        MAIN_ACTIVITY,
                        sDeviceState.workProfile(/* forUser= */ PRIMARY_USER).userHandle()));
    }

    @Test
    @RequireRunOnPrimaryUser
    public void getProfileSwitchingLabel_callingFromPrimaryUser_targetIsPrimaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingLabel(sDeviceState.primaryUser().userHandle());
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void getProfileSwitchingLabel_callingFromPrimaryUser_targetIsSecondaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingLabel(sDeviceState.primaryUser().userHandle());
        });
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER)
    public void getProfileSwitchingLabel_callingFromSecondaryUser_targetIsWorkProfile_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingLabel(
                    sDeviceState.workProfile(/* forUser= */ PRIMARY_USER).userHandle());
        });
    }

    @Test
    @RequireRunOnWorkProfile(installInstrumentedAppInParent = TRUE)
    public void getProfileSwitchingLabel_callingFromWorProfile_targetIsPrimaryUser_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingLabel(
                sDeviceState.primaryUser().userHandle())).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void getProfileSwitchingLabel_callingFromPrimaryUser_targetIsWorkProfile_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingLabel(
                sDeviceState.workProfile().userHandle())).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    public void getProfileSwitchingLabelIconDrawable_callingFromPrimaryUser_targetIsPrimaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(
                    sDeviceState.primaryUser().userHandle());
        });
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void getProfileSwitchingLabelIconDrawable_callingFromPrimaryUser_targetIsSecondaryUser_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(
                    sDeviceState.secondaryUser().userHandle());
        });
    }

    @Test
    @RequireRunOnSecondaryUser
    @EnsureHasWorkProfile(forUser = PRIMARY_USER)
    public void getProfileSwitchingLabelIconDrawable_callingFromSecondaryUser_targetIsWorkProfile_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(
                    sDeviceState.workProfile(/* forUser= */ PRIMARY_USER).userHandle());
        });
    }

    @Test
    @RequireRunOnWorkProfile(installInstrumentedAppInParent = TRUE)
    public void getProfileSwitchingIconDrawable_callingFromWorkProfile_targetIsPrimaryUser_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingIconDrawable(
                sDeviceState.primaryUser().userHandle())).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    public void getProfileSwitchingIconDrawable_callingFromPrimaryUser_targetIsWorkProfile_notNull() {
        assertThat(sCrossProfileApps.getProfileSwitchingIconDrawable(
                sDeviceState.workProfile().userHandle())).isNotNull();
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_fromPersonalProfile_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = sDeviceState.profileOwner(WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.primaryUser());
             TestAppInstance workApp = sCrossProfileTestApp.install(
                sDeviceState.workProfile())) {
            profileOwner.setCrossProfilePackages(
                    sDeviceState.profileOwner(WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_fromWorkProfile_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = sDeviceState.profileOwner(WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.primaryUser());
             TestAppInstance workApp = sCrossProfileTestApp.install(
                sDeviceState.workProfile())) {
            profileOwner.setCrossProfilePackages(
                    sDeviceState.profileOwner(WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(workApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasNoWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_noOtherProfiles_returnsFalse()
            throws Exception {
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.primaryUser())) {

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isFalse();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_packageNotInAllowList_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = sDeviceState.profileOwner(WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.primaryUser());
             TestAppInstance workApp = sCrossProfileTestApp.install(
                sDeviceState.workProfile())) {
            profileOwner.setCrossProfilePackages(
                    sDeviceState.profileOwner(WORK_PROFILE).componentName(),
                    Collections.emptySet());

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_packageNotInstalledInPersonalProfile_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = sDeviceState.profileOwner(WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance workApp = sCrossProfileTestApp.install(
                sDeviceState.workProfile())) {
            profileOwner.setCrossProfilePackages(
                    sDeviceState.profileOwner(WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(workApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_packageNotInstalledInWorkProfile_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = sDeviceState.profileOwner(WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.primaryUser())) {
            profileOwner.setCrossProfilePackages(
                    sDeviceState.profileOwner(WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_permissionNotRequested_returnsFalse()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = sDeviceState.profileOwner(WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sNonCrossProfileTestApp.install(
                sDeviceState.primaryUser());
             TestAppInstance workApp = sNonCrossProfileTestApp.install(
                sDeviceState.workProfile())) {
            profileOwner.setCrossProfilePackages(
                    sDeviceState.profileOwner(WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isFalse();
        }
    }

    // TODO(b/199148889): add require INTERACT_ACROSS_PROFILE permission for the dpc.
    @Test
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void canRequestInteractAcrossProfiles_profileOwner_returnsFalse()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = sDeviceState.profileOwner(WORK_PROFILE)
                .devicePolicyManager();
        profileOwner.setCrossProfilePackages(
                sDeviceState.profileOwner(WORK_PROFILE).componentName(),
                Set.of(sDeviceState.profileOwner(WORK_PROFILE).componentName().getPackageName()));

        assertThat(
                sDeviceState.profileOwner(WORK_PROFILE).crossProfileApps()
                        .canRequestInteractAcrossProfiles()
        ).isFalse();
    }
}