/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.content.pm.cts;

import static android.Manifest.permission.ACCESS_HIDDEN_PROFILES;
import static android.Manifest.permission.ACCESS_HIDDEN_PROFILES_FULL;
import static android.multiuser.Flags.FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS;
import static android.multiuser.Flags.FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES;
import static android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE;

import static com.android.bedstead.nene.permissions.CommonPermissions.QUERY_USERS;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class LauncherAppsForHiddenProfilesTest {

    private static final String TEST_APP = "com.android.bedstead.testapp.AccountManagementApp";
    private static final String TEST_ACTIVITY = "android.testapp.CrossProfileSharingActivity";
    private Context mContext;

    private LauncherApps mLauncherApps;

    private String mDefaultHome;

    private final TestApp mTestApp =
            new TestAppProvider().query().wherePackageName().isEqualTo(TEST_APP).get();
    private final ComponentName mTestAppComponent = new ComponentName(TEST_APP, TEST_ACTIVITY);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mDefaultHome = getDefaultLauncher(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES
    })
    public void testNotDefaultLauncher_hiddenProfileInfoStripped() {
        try (UserReference privateProfile = createProfileAndInstallTestApps()) {
            assertHiddenProfileInfoStripped(privateProfile.userHandle());
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES
    })
    public void testDefaultLauncherNoPerms_hiddenProfileInfoStripped() {
        try (UserReference privateProfile = createProfileAndInstallTestApps();
                PermissionContext p =
                        TestApis.permissions()
                                .withoutPermission(ACCESS_HIDDEN_PROFILES, QUERY_USERS)) {
            setSelfAsDefaultLauncher();
            assertHiddenProfileInfoStripped(privateProfile.userHandle());
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES
    })
    public void testDefaultLauncherWithSystemPerm_hiddenProfileInfoAvailable() {
        try (UserReference privateProfile = createProfileAndInstallTestApps();
                PermissionContext p =
                        TestApis.permissions().withoutPermission(ACCESS_HIDDEN_PROFILES);
                PermissionContext p2 =
                        TestApis.permissions().withPermission(ACCESS_HIDDEN_PROFILES_FULL)) {
            setSelfAsDefaultLauncher();
            assertHiddenProfileInfoAvailable(privateProfile.userHandle());
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES
    })
    public void testDefaultLauncherWithAccessHiddenProfiles_hiddenProfileInfoAvailable() {
        try (UserReference privateProfile = createProfileAndInstallTestApps();
                PermissionContext p = TestApis.permissions().withoutPermission(QUERY_USERS);
                PermissionContext p2 =
                        TestApis.permissions().withPermission(ACCESS_HIDDEN_PROFILES)) {
            setSelfAsDefaultLauncher();
            assertHiddenProfileInfoAvailable(privateProfile.userHandle());
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mDefaultHome != null) {
            setDefaultLauncher(InstrumentationRegistry.getInstrumentation(), mDefaultHome);
        }
    }

    private void setSelfAsDefaultLauncher() {
        setDefaultLauncher(InstrumentationRegistry.getInstrumentation(), mContext.getPackageName());
    }

    private void assertHiddenProfileInfoStripped(UserHandle targetUser) {
        assertThat(mLauncherApps.getLauncherUserInfo(targetUser)).isNull();
        assertThat(mLauncherApps.getPreInstalledSystemPackages(targetUser)).isNull();
        assertThat(mLauncherApps.getAppMarketActivityIntent(/* packageName= */ null, targetUser))
                .isNull();

        String packageName = mTestApp.packageName();
        assertThat(mLauncherApps.getActivityList(packageName, targetUser)).isEmpty();
        assertThat(mLauncherApps.isPackageEnabled(packageName, targetUser)).isFalse();
        assertThrows(
                PackageManager.NameNotFoundException.class,
                () -> mLauncherApps.getApplicationInfo(packageName, /* flags= */ 0, targetUser));
        assertThat(mLauncherApps.isActivityEnabled(mTestAppComponent, targetUser)).isFalse();
        assertThat(
                        mLauncherApps.resolveActivity(
                                new Intent().setComponent(mTestAppComponent), targetUser))
                .isNull();
    }

    private void assertHiddenProfileInfoAvailable(UserHandle targetUser) {
        assertThat(mLauncherApps.getLauncherUserInfo(targetUser)).isNotNull();
        assertThat(mLauncherApps.getPreInstalledSystemPackages(targetUser)).isNotNull();
        assertThat(mLauncherApps.getAppMarketActivityIntent(/* packageName= */ null, targetUser))
                .isNotNull();

        String packageName = mTestApp.packageName();
        assertThat(mLauncherApps.getActivityList(packageName, targetUser)).isNotEmpty();
        assertThat(mLauncherApps.isPackageEnabled(packageName, targetUser)).isTrue();
        try {
            assertThat(mLauncherApps.getApplicationInfo(packageName, /* flags= */ 0, targetUser))
                    .isNotNull();
        } catch (PackageManager.NameNotFoundException e) {
            fail("Unexpected NameNotFoundException exception " + e.getMessage());
        }
        assertThat(mLauncherApps.isActivityEnabled(mTestAppComponent, targetUser)).isTrue();
        assertThat(
                        mLauncherApps.resolveActivity(
                                new Intent().setComponent(mTestAppComponent), targetUser))
                .isNotNull();
    }

    private UserReference createProfileAndInstallTestApps() {
        UserReference reference =
                TestApis.users()
                        .createUser()
                        .parent(TestApis.users().instrumented())
                        .type(TestApis.users().supportedType("android.os.usertype.profile.PRIVATE"))
                        .create();

        mTestApp.install(reference.start());
        return reference;
    }
}
