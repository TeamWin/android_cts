/*
* Copyright (C) 2015 The Android Open Source Project
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

package android.permissionpolicy.cts;

import static android.content.pm.PermissionInfo.FLAG_INSTALLED;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_INSTALLER;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_MODULE;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_PRIVILEGED;
import static android.content.pm.PermissionInfo.PROTECTION_FLAG_ROLE;
import static android.content.pm.PermissionInfo.PROTECTION_MASK_BASE;
import static android.os.Build.VERSION.SECURITY_PATCH;

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Process;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Xml;

import com.android.modules.utils.build.SdkLevel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tests for permission policy on the platform.
 */
@AppModeFull(reason = "Instant apps cannot read the system servers permission")
@RunWith(AndroidJUnit4.class)
public class PermissionPolicyTest {
    private static final String ACCESS_SMARTSPACE = "android.permission.ACCESS_SMARTSPACE";
    private static final String ACCESSIBILITY_MOTION_EVENT_OBSERVING =
            "android.permission.ACCESSIBILITY_MOTION_EVENT_OBSERVING";
    private static final String ALWAYS_UPDATE_WALLPAPER =
            "android.permission.ALWAYS_UPDATE_WALLPAPER";
    private static final String CAMERA_HEADLESS_SYSTEM_USER =
            "android.permission.CAMERA_HEADLESS_SYSTEM_USER";
    private static final String CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS =
            "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS";
    private static final String GET_BINDING_UID_IMPORTANCE =
            "android.permission.GET_BINDING_UID_IMPORTANCE";
    private static final String HIDE_NON_SYSTEM_OVERLAY_WINDOWS
            = "android.permission.HIDE_NON_SYSTEM_OVERLAY_WINDOWS";
    private static final String INTERNAL_SYSTEM_WINDOW =
            "android.permission.INTERNAL_SYSTEM_WINDOW";
    private static final String LAUNCH_PERMISSION_SETTINGS =
            "android.permission.LAUNCH_PERMISSION_SETTINGS";
    private static final String MANAGE_COMPANION_DEVICES =
            "android.permission.MANAGE_COMPANION_DEVICES";
    private static final String MANAGE_DISPLAYS = "android.permission.MANAGE_DISPLAYS";
    private static final String MANAGE_REMOTE_AUTH = "android.permission.MANAGE_REMOTE_AUTH";
    private static final String MEDIA_ROUTING_CONTROL = "android.permission.MEDIA_ROUTING_CONTROL";
    private static final String MODIFY_DAY_NIGHT_MODE = "android.permission.MODIFY_DAY_NIGHT_MODE";
    private static final String OBSERVE_APP_USAGE = "android.permission.OBSERVE_APP_USAGE";
    private static final String OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW =
            "android.permission.OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW";
    private static final String PREPARE_FACTORY_RESET = "android.permission.PREPARE_FACTORY_RESET";
    private static final String QUARANTINE_APPS = "android.permission.QUARANTINE_APPS";
    private static final String READ_DROPBOX_DATA = "android.permission.READ_DROPBOX_DATA";
    private static final String RECEIVE_SANDBOX_TRIGGER_AUDIO =
            "android.permission.RECEIVE_SANDBOX_TRIGGER_AUDIO";
    private static final String RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA =
            "android.permission.RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA";
    private static final String REGISTER_NSD_OFFLOAD_ENGINE =
            "android.permission.REGISTER_NSD_OFFLOAD_ENGINE";
    private static final String REPORT_USAGE_STATS = "android.permission.REPORT_USAGE_STATS";
    private static final String RESET_HOTWORD_TRAINING_DATA_EGRESS_COUNT =
            "android.permission.RESET_HOTWORD_TRAINING_DATA_EGRESS_COUNT";
    private static final String SHOW_CUSTOMIZED_RESOLVER =
            "android.permission.SHOW_CUSTOMIZED_RESOLVER";
    private static final String START_ACTIVITIES_FROM_SDK_SANDBOX =
            "android.permission.START_ACTIVITIES_FROM_SDK_SANDBOX";
    private static final String STATUS_BAR_SERVICE = "android.permission.STATUS_BAR_SERVICE";
    private static final String SUSPEND_APPS = "android.permission.SUSPEND_APPS";
    private static final String SYNC_FLAGS = "android.permission.SYNC_FLAGS";
    private static final String THREAD_NETWORK_PRIVILEGED =
            "android.permission.THREAD_NETWORK_PRIVILEGED";
    private static final String USE_COMPANION_TRANSPORTS =
            "android.permission.USE_COMPANION_TRANSPORTS";
    private static final String USE_REMOTE_AUTH = "android.permission.USE_REMOTE_AUTH";
    private static final String WRITE_FLAGS = "android.permission.WRITE_FLAGS";
    private static final String BIND_TV_AD_SERVICE = "android.permission.BIND_TV_AD_SERVICE";
    private static final String RECEIVE_SENSITIVE_NOTIFICATIONS =
            "android.permission.RECEIVE_SENSITIVE_NOTIFICATIONS";
    private static final String BIND_DOMAIN_SELECTION_SERVICE =
            "android.permission.BIND_DOMAIN_SELECTION_SERVICE";
    private static final String MANAGE_DEVICE_POLICY_THREAD_NETWORK =
            "android.permission.MANAGE_DEVICE_POLICY_THREAD_NETWORK";
    private static final String FOREGROUND_SERVICE_MEDIA_PROCESSING =
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING";
    private static final String RUN_BACKUP_JOBS = "android.permission.RUN_BACKUP_JOBS";
    private static final String EMERGENCY_INSTALL_PACKAGES =
            "android.permission.EMERGENCY_INSTALL_PACKAGES";
    private static final String ACCESS_LAST_KNOWN_CELL_ID =
            "android.permission.ACCESS_LAST_KNOWN_CELL_ID";
    private static final String GET_BACKGROUND_INSTALLED_PACKAGES =
            "android.permission.GET_BACKGROUND_INSTALLED_PACKAGES";
    private static final String MANAGE_ENHANCED_CONFIRMATION_STATES =
            "android.permission.MANAGE_ENHANCED_CONFIRMATION_STATES";
    private static final String USE_BACKGROUND_FACE_AUTHENTICATION =
            "android.permission.USE_BACKGROUND_FACE_AUTHENTICATION";
    private static final String REQUEST_OBSERVE_DEVICE_UUID_PRESENCE =
            "android.permission.REQUEST_OBSERVE_DEVICE_UUID_PRESENCE";
    private static final String READ_SYSTEM_GRAMMATICAL_GENDER =
            "android.permission.READ_SYSTEM_GRAMMATICAL_GENDER";
    private static final String SET_BIOMETRIC_DIALOG_LOGO =
            "android.permission.SET_BIOMETRIC_DIALOG_LOGO";
    private static final String MONITOR_STICKY_MODIFIER_STATE =
            "android.permission.MONITOR_STICKY_MODIFIER_STATE";
    private static final String INTERACT_ACROSS_USERS_FULL =
            "android.permission.INTERACT_ACROSS_USERS_FULL";
    private static final String BIND_NFC_SERVICE = "android.permission.BIND_NFC_SERVICE";
    private static final String SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE =
            "android.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE";
    private static final String MANAGE_ROLE_HOLDERS = "android.permission.MANAGE_ROLE_HOLDERS";
    private static final String OBSERVE_ROLE_HOLDERS = "android.permission.OBSERVE_ROLE_HOLDERS";

    private static final Date HIDE_NON_SYSTEM_OVERLAY_WINDOWS_PATCH_DATE = parseDate("2017-11-01");
    private static final Date MANAGE_COMPANION_DEVICES_PATCH_DATE = parseDate("2020-07-01");

    private static final String LOG_TAG = "PermissionProtectionTest";

    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final String PLATFORM_ROOT_NAMESPACE = "android.";

    private static final String TAG_PERMISSION = "permission";
    private static final String TAG_PERMISSION_GROUP = "permission-group";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_PERMISSION_GROUP = "permissionGroup";
    private static final String ATTR_PERMISSION_FLAGS = "permissionFlags";
    private static final String ATTR_PROTECTION_LEVEL = "protectionLevel";
    private static final String ATTR_BACKGROUND_PERMISSION = "backgroundPermission";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    /** Permissions added since the FRC of U. */
    private static final ArraySet<String> permissionsAddedInUqpr2 = new ArraySet<>(
            new String[]{ACCESS_SMARTSPACE, ACCESSIBILITY_MOTION_EVENT_OBSERVING,
                    ALWAYS_UPDATE_WALLPAPER, CAMERA_HEADLESS_SYSTEM_USER,
                    GET_BINDING_UID_IMPORTANCE, LAUNCH_PERMISSION_SETTINGS, MANAGE_DISPLAYS,
                    MANAGE_REMOTE_AUTH, MEDIA_ROUTING_CONTROL,
                    OVERRIDE_SYSTEM_KEY_BEHAVIOR_IN_FOCUSED_WINDOW, PREPARE_FACTORY_RESET,
                    READ_DROPBOX_DATA, RECEIVE_SANDBOX_TRIGGER_AUDIO,
                    RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA, REGISTER_NSD_OFFLOAD_ENGINE,
                    REPORT_USAGE_STATS, RESET_HOTWORD_TRAINING_DATA_EGRESS_COUNT,
                    START_ACTIVITIES_FROM_SDK_SANDBOX, SHOW_CUSTOMIZED_RESOLVER, SYNC_FLAGS,
                    THREAD_NETWORK_PRIVILEGED, USE_COMPANION_TRANSPORTS, USE_REMOTE_AUTH,
                    QUARANTINE_APPS, WRITE_FLAGS, BIND_TV_AD_SERVICE,
                    RECEIVE_SENSITIVE_NOTIFICATIONS, BIND_DOMAIN_SELECTION_SERVICE,
                    MANAGE_DEVICE_POLICY_THREAD_NETWORK, FOREGROUND_SERVICE_MEDIA_PROCESSING,
                    RUN_BACKUP_JOBS, EMERGENCY_INSTALL_PACKAGES, ACCESS_LAST_KNOWN_CELL_ID,
                    GET_BACKGROUND_INSTALLED_PACKAGES, MANAGE_ENHANCED_CONFIRMATION_STATES,
                    USE_BACKGROUND_FACE_AUTHENTICATION, REQUEST_OBSERVE_DEVICE_UUID_PRESENCE,
                    READ_SYSTEM_GRAMMATICAL_GENDER, SET_BIOMETRIC_DIALOG_LOGO,
                    MONITOR_STICKY_MODIFIER_STATE});

    /**
     * Map of permissions to their protection flags in the FRC for U which have changed since.
     */
    private static final Map<String, Integer> permissionsToLegacyProtection = new HashMap<>() {
        {
            put(CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS, PROTECTION_FLAG_PRIVILEGED);
            put(INTERNAL_SYSTEM_WINDOW, PROTECTION_FLAG_MODULE);
            put(MODIFY_DAY_NIGHT_MODE, PROTECTION_FLAG_PRIVILEGED);
            put(OBSERVE_APP_USAGE, PROTECTION_FLAG_PRIVILEGED);
            put(STATUS_BAR_SERVICE, 0x0);
            put(SUSPEND_APPS, PROTECTION_FLAG_ROLE);
            put(INTERACT_ACROSS_USERS_FULL, PROTECTION_FLAG_INSTALLER | PROTECTION_FLAG_ROLE);
            put(BIND_NFC_SERVICE, 0X0);
            put(SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE, PROTECTION_FLAG_ROLE);
            put(MANAGE_ROLE_HOLDERS, PROTECTION_FLAG_INSTALLER);
            put(OBSERVE_ROLE_HOLDERS, PROTECTION_FLAG_INSTALLER);
            put(USE_COMPANION_TRANSPORTS, PROTECTION_FLAG_MODULE);
        }
    };

    @Test
    public void shellIsOnlySystemAppThatRequestsRevokePostNotificationsWithoutKill() {
        List<PackageInfo> pkgs = sContext.getPackageManager().getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PERMISSIONS | PackageManager.MATCH_ALL));
        int shellUid = Process.myUserHandle().getUid(Process.SHELL_UID);
        for (PackageInfo pkg : pkgs) {
            Assert.assertFalse(pkg.applicationInfo.uid != shellUid
                    && hasRevokeNotificationNoKillPermission(pkg));
        }
    }

    @Test
    public void platformPermissionPolicyIsUnaltered() throws Exception {
        Map<String, PermissionInfo> declaredPermissionsMap =
                getPermissionsForPackage(sContext, PLATFORM_PACKAGE_NAME);

        List<String> offendingList = new ArrayList<>();

        List<PermissionGroupInfo> declaredGroups = sContext.getPackageManager()
                .getAllPermissionGroups(0);
        Set<String> declaredGroupsSet = new ArraySet<>();
        for (PermissionGroupInfo declaredGroup : declaredGroups) {
            declaredGroupsSet.add(declaredGroup.name);
        }

        Set<String> expectedPermissionGroups = loadExpectedPermissionGroupNames(
                R.raw.android_manifest);
        List<ExpectedPermissionInfo> expectedPermissions = loadExpectedPermissions(
                R.raw.android_manifest);

        if (sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            expectedPermissions.addAll(loadExpectedPermissions(R.raw.automotive_android_manifest));
            String carServicePackageName = SystemProperties.get("ro.android.car.carservice.package",
                    null);

            assertWithMessage("Car service package not defined").that(
                    carServicePackageName).isNotNull();

            declaredPermissionsMap.putAll(
                    getPermissionsForPackage(sContext, carServicePackageName));

            // Load signature permission declared in CarService-builtin
            String carServiceBuiltInPackageName = "com.android.car";
            Map<String, PermissionInfo> carServiceBuiltInPermissionsMap = getPermissionsForPackage(
                    sContext, carServiceBuiltInPackageName);
            // carServiceBuiltInPermissionsMap should only have signature permissions and those
            // permissions should not be defined in car service updatable.
            for (Map.Entry<String, PermissionInfo> permissionData : carServiceBuiltInPermissionsMap
                    .entrySet()) {
                PermissionInfo carServiceBuiltInDeclaredPermission = permissionData.getValue();
                String carServiceBuiltInDeclaredPermissionName = permissionData.getKey();

                // Signature only permission should be defined in built-in car service
                if ((carServiceBuiltInDeclaredPermission
                        .getProtection() != PermissionInfo.PROTECTION_SIGNATURE)
                        || (carServiceBuiltInDeclaredPermission.getProtectionFlags() != 0)) {
                    offendingList.add("Permission " + carServiceBuiltInDeclaredPermissionName
                            + " should be signature only permission to be declared in"
                            + " carServiceBuiltIn package.");
                    continue;
                }

                if (declaredPermissionsMap.get(carServiceBuiltInDeclaredPermissionName) != null) {
                    offendingList.add("Permission " + carServiceBuiltInDeclaredPermissionName
                            + " from car service builtin is already declared in other packages.");
                    continue;
                }
            }
            declaredPermissionsMap.putAll(carServiceBuiltInPermissionsMap);
        }

        for (ExpectedPermissionInfo expectedPermission : expectedPermissions) {
            String expectedPermissionName = expectedPermission.name;
            if (shouldSkipPermission(expectedPermissionName)) {
                // This permission doesn't need to exist yet, but will exist in
                // a future SPL. It is acceptable to declare the permission
                // even in an earlier SPL, so we remove it here so it doesn't
                // trigger a failure after the loop.
                declaredPermissionsMap.remove(expectedPermissionName);
                continue;
            }

            // OEMs cannot remove permissions
            PermissionInfo declaredPermission = declaredPermissionsMap.get(expectedPermissionName);
            if (declaredPermission == null) {
                // If expected permission is not found, it is possible that this build doesn't yet
                // contain certain new permissions added after the FRC for U, in which case we skip
                // the check.
                if (permissionsAddedInUqpr2.contains(expectedPermissionName)) {
                    continue;
                } else {
                    offendingList.add("Permission " + expectedPermissionName + " must be declared");
                    continue;
                }
            }

            // We want to end up with OEM defined permissions and groups to check their namespace
            declaredPermissionsMap.remove(expectedPermissionName);

            // OEMs cannot change permission protection
            final int expectedProtection = expectedPermission.protectionLevel
                    & PROTECTION_MASK_BASE;
            final int declaredProtection = declaredPermission.protectionLevel
                    & PROTECTION_MASK_BASE;
            if (expectedProtection != declaredProtection) {
                offendingList.add(
                        String.format(
                                "Permission %s invalid protection level %x, expected %x",
                                expectedPermissionName, declaredProtection, expectedProtection));
            }

            // OEMs cannot change permission flags
            final int expectedFlags = expectedPermission.flags;
            final int declaredFlags = (declaredPermission.flags & ~FLAG_INSTALLED);
            if (expectedFlags != declaredFlags) {
                offendingList.add(
                        String.format(
                                "Permission %s invalid flags %x, expected %x",
                                expectedPermissionName,
                                declaredFlags,
                                expectedFlags));
            }

            // OEMs cannot change permission protection flags
            final int expectedProtectionFlags =
                    expectedPermission.protectionLevel & ~PROTECTION_MASK_BASE;
            final int declaredProtectionFlags = declaredPermission.getProtectionFlags();
            if (expectedProtectionFlags != declaredProtectionFlags) {
                // If expected and declared protection flags do not match, it is possible that
                // this build doesn't yet contain certain protection flags expanded in U QPR 2,
                // in which case we check that the declared protection flags match those in U
                // or U QPR 1.
                if (permissionsToLegacyProtection.getOrDefault(expectedPermissionName, -1)
                        == declaredProtectionFlags) {
                    continue;
                } else {
                    offendingList.add(
                        String.format(
                                "Permission %s invalid enforced protection %x, expected %x",
                                expectedPermissionName,
                                declaredProtectionFlags,
                                expectedProtectionFlags));
                }
            }

            // OEMs cannot change permission grouping
            if ((declaredPermission.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) != 0) {
                if (!Objects.equals(expectedPermission.group, declaredPermission.group)) {
                    offendingList.add(
                            "Permission " + expectedPermissionName + " not in correct group "
                            + "(expected=" + expectedPermission.group + " actual="
                                    + declaredPermission.group);
                }

                if (declaredPermission.group != null
                        && !declaredGroupsSet.contains(declaredPermission.group)) {
                    offendingList.add(
                            "Permission group " + expectedPermission.group + " must be defined");
                }
            }

            // OEMs cannot change background permission mapping
            if (!Objects.equals(expectedPermission.backgroundPermission,
                    declaredPermission.backgroundPermission)) {
                offendingList.add(
                        String.format(
                                "Permission %s invalid background permission %s, expected %s",
                                expectedPermissionName,
                                declaredPermission.backgroundPermission,
                                expectedPermission.backgroundPermission));
            }
        }

        // OEMs cannot define permissions in the platform namespace
        for (String permission : declaredPermissionsMap.keySet()) {
            if (permission.startsWith(PLATFORM_ROOT_NAMESPACE)) {
                final PermissionInfo permInfo = declaredPermissionsMap.get(permission);
                offendingList.add(
                        "Cannot define permission " + permission
                        + ", package " + permInfo.packageName
                        + " in android namespace");
            }
        }

        // OEMs cannot define groups in the platform namespace
        for (PermissionGroupInfo declaredGroup : declaredGroups) {
            if (!expectedPermissionGroups.contains(declaredGroup.name)) {
                if (declaredGroup.name != null) {
                    if (declaredGroup.packageName.equals(PLATFORM_PACKAGE_NAME)
                            && declaredGroup.name.startsWith(PLATFORM_ROOT_NAMESPACE)) {
                        offendingList.add(
                                "Cannot define group " + declaredGroup.name
                                + ", package " + declaredGroup.packageName
                                + " in android namespace");
                    }
                }
            }
        }

        // OEMs cannot define new ephemeral permissions
        for (String permission : declaredPermissionsMap.keySet()) {
            PermissionInfo info = declaredPermissionsMap.get(permission);
            if ((info.protectionLevel & PermissionInfo.PROTECTION_FLAG_INSTANT) != 0) {
                offendingList.add("Cannot define new instant permission " + permission);
            }
        }

        // Fail on any offending item
        assertWithMessage("list of offending permissions").that(offendingList).isEmpty();
    }

    private boolean hasRevokeNotificationNoKillPermission(PackageInfo info) {
        if (info.requestedPermissions == null) {
            return false;
        }

        for (int i = 0; i < info.requestedPermissions.length; i++) {
            if (Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL.equals(
                    info.requestedPermissions[i])) {
                return true;
            }
        }
        return false;
    }

    private List<ExpectedPermissionInfo> loadExpectedPermissions(int resourceId) throws Exception {
        List<ExpectedPermissionInfo> permissions = new ArrayList<>();
        try (InputStream in = sContext.getResources().openRawResource(resourceId)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);

            final int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                if (TAG_PERMISSION.equals(parser.getName())) {
                    ExpectedPermissionInfo permissionInfo = new ExpectedPermissionInfo(
                            parser.getAttributeValue(null, ATTR_NAME),
                            parser.getAttributeValue(null, ATTR_PERMISSION_GROUP),
                            parser.getAttributeValue(null, ATTR_BACKGROUND_PERMISSION),
                            parsePermissionFlags(
                                    parser.getAttributeValue(null, ATTR_PERMISSION_FLAGS)),
                            parseProtectionLevel(
                                    parser.getAttributeValue(null, ATTR_PROTECTION_LEVEL)));
                    permissions.add(permissionInfo);
                } else {
                    Log.e(LOG_TAG, "Unknown tag " + parser.getName());
                }
            }
        }

        return permissions;
    }

    private Set<String> loadExpectedPermissionGroupNames(int resourceId) throws Exception {
        ArraySet<String> permissionGroups = new ArraySet<>();
        try (InputStream in = sContext.getResources().openRawResource(resourceId)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);

            final int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                if (TAG_PERMISSION_GROUP.equals(parser.getName())) {
                    permissionGroups.add(parser.getAttributeValue(null, ATTR_NAME));
                } else {
                    Log.e(LOG_TAG, "Unknown tag " + parser.getName());
                }
            }
        }
        return permissionGroups;
    }

    private static int parsePermissionFlags(@Nullable String permissionFlagsString) {
        if (permissionFlagsString == null) {
            return 0;
        }

        int protectionFlags = 0;
        String[] fragments = permissionFlagsString.split("\\|");
        for (String fragment : fragments) {
            switch (fragment.trim()) {
                case "removed": {
                    protectionFlags |= PermissionInfo.FLAG_REMOVED;
                } break;
                case "costsMoney": {
                    protectionFlags |= PermissionInfo.FLAG_COSTS_MONEY;
                } break;
                case "hardRestricted": {
                    protectionFlags |= PermissionInfo.FLAG_HARD_RESTRICTED;
                } break;
                case "immutablyRestricted": {
                    protectionFlags |= PermissionInfo.FLAG_IMMUTABLY_RESTRICTED;
                } break;
                case "softRestricted": {
                    protectionFlags |= PermissionInfo.FLAG_SOFT_RESTRICTED;
                } break;
            }
        }
        return protectionFlags;
    }

    private static int parseProtectionLevel(String protectionLevelString) {
        int protectionLevel = 0;
        String[] fragments = protectionLevelString.split("\\|");
        for (String fragment : fragments) {
            switch (fragment.trim()) {
                case "normal": {
                    protectionLevel |= PermissionInfo.PROTECTION_NORMAL;
                } break;
                case "dangerous": {
                    protectionLevel |= PermissionInfo.PROTECTION_DANGEROUS;
                } break;
                case "signature": {
                    protectionLevel |= PermissionInfo.PROTECTION_SIGNATURE;
                } break;
                case "signatureOrSystem": {
                    protectionLevel |= PermissionInfo.PROTECTION_SIGNATURE;
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_SYSTEM;
                } break;
                case "internal": {
                    protectionLevel |= PermissionInfo.PROTECTION_INTERNAL;
                } break;
                case "system": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_SYSTEM;
                } break;
                case "installer": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_INSTALLER;
                } break;
                case "verifier": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_VERIFIER;
                } break;
                case "preinstalled": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_PREINSTALLED;
                } break;
                case "pre23": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_PRE23;
                } break;
                case "appop": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_APPOP;
                } break;
                case "development": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_DEVELOPMENT;
                } break;
                case "privileged": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_PRIVILEGED;
                } break;
                case "oem": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_OEM;
                } break;
                case "vendorPrivileged": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_VENDOR_PRIVILEGED;
                } break;
                case "setup": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_SETUP;
                } break;
                case "textClassifier": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_SYSTEM_TEXT_CLASSIFIER;
                } break;
                case "configurator": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_CONFIGURATOR;
                } break;
                case "incidentReportApprover": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_INCIDENT_REPORT_APPROVER;
                } break;
                case "appPredictor": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_APP_PREDICTOR;
                } break;
                case "instant": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_INSTANT;
                } break;
                case "runtime": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY;
                } break;
                case "companion": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_COMPANION;
                } break;
                case "retailDemo": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_RETAIL_DEMO;
                } break;
                case "recents": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_RECENTS;
                } break;
                case "role": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_ROLE;
                } break;
                case "knownSigner": {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_KNOWN_SIGNER;
                } break;
                case "module" : {
                    protectionLevel |= PermissionInfo.PROTECTION_FLAG_MODULE;
                } break;
            }
        }
        return protectionLevel;
    }

    private static Map<String, PermissionInfo> getPermissionsForPackage(Context context, String pkg)
            throws NameNotFoundException {
        PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
        Map<String, PermissionInfo> declaredPermissionsMap = new ArrayMap<>();

        for (PermissionInfo declaredPermission : packageInfo.permissions) {
            declaredPermissionsMap.put(declaredPermission.name, declaredPermission);
        }
        return declaredPermissionsMap;
    }

    private static Date parseDate(String date) {
        Date patchDate = new Date();
        try {
            SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd");
            patchDate = template.parse(date);
        } catch (ParseException e) {
        }

        return patchDate;
    }

    private boolean shouldSkipPermission(String permissionName) {
        switch (permissionName) {
            case HIDE_NON_SYSTEM_OVERLAY_WINDOWS:
                return parseDate(SECURITY_PATCH).before(HIDE_NON_SYSTEM_OVERLAY_WINDOWS_PATCH_DATE);
            case MANAGE_COMPANION_DEVICES:
                return parseDate(SECURITY_PATCH).before(MANAGE_COMPANION_DEVICES_PATCH_DATE);
            default:
                return false;
        }
    }

    private class ExpectedPermissionInfo {
        final @NonNull String name;
        final @Nullable String group;
        final @Nullable String backgroundPermission;
        final int flags;
        final int protectionLevel;

        private ExpectedPermissionInfo(@NonNull String name, @Nullable String group,
                @Nullable String backgroundPermission, int flags, int protectionLevel) {
            this.name = name;
            this.group = group;
            this.backgroundPermission = backgroundPermission;
            this.flags = flags;
            this.protectionLevel = protectionLevel;
        }
    }
}
