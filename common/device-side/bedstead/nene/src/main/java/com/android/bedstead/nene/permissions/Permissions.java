/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.permissions;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.packages.PackageReference;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;

import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Permission manager for tests. */
public final class Permissions {

    public static final String MANAGE_PROFILE_AND_DEVICE_OWNERS =
            "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS";
    public static final String MANAGE_DEVICE_ADMINS = "android.permission.MANAGE_DEVICE_ADMINS";
    public static final String NOTIFY_PENDING_SYSTEM_UPDATE =
            "android.permission.NOTIFY_PENDING_SYSTEM_UPDATE";

    private static final String LOG_TAG = "Permissions";

    private final List<PermissionContextImpl> mPermissionContexts =
            Collections.synchronizedList(new ArrayList<>());
    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final PackageReference sInstrumentedPackage =
            sTestApis.packages().find(sContext.getPackageName());
    private static final UserReference sUser = sTestApis.users().instrumented();
    private static final Package sShellPackage =
            sTestApis.packages().find("com.android.shell").resolve();
    private static final Set<String> sCheckedGrantPermissions = new HashSet<>();
    private static final Set<String> sCheckedDenyPermissions = new HashSet<>();
    private static final boolean SUPPORTS_ADOPT_SHELL_PERMISSIONS =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

    /**
     * Permissions which cannot be given to shell.
     *
     * <p>Each entry must include a comment with the reason it cannot be added.
     */
    private static final ImmutableSet EXEMPT_SHELL_PERMISSIONS = ImmutableSet.of(

    );

    // Permissions is a singleton as permission state must be application wide
    public static final Permissions sInstance = new Permissions();

    private Set<String> mExistingPermissions;

    private Permissions() {

    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are granted.
     *
     * <p>If the permissions cannot be granted, and are not already granted, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p = mTestApis.permissions().withPermission(PERMISSION1, PERMISSION2) {
     *     // Code which needs the permissions goes here
     * }
     * }
     */
    public PermissionContextImpl withPermission(String... permissions) {
        if (mPermissionContexts.isEmpty()) {
            recordExistingPermissions();
        }

        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withPermission(permissions);

        return permissionContext;
    }

    /**
     * Enter a {@link PermissionContext} where the given permissions are not granted.
     *
     * <p>If the permissions cannot be denied, and are not already denied, an exception will be
     * thrown.
     *
     * <p>Recommended usage:
     * {@code
     *
     * try (PermissionContext p =
     *         mTestApis.permissions().withoutPermission(PERMISSION1, PERMISSION2) {
     *     // Code which needs the permissions goes here
     * }
     */
    public PermissionContextImpl withoutPermission(String... permissions) {
        if (mPermissionContexts.isEmpty()) {
            recordExistingPermissions();
        }

        PermissionContextImpl permissionContext = new PermissionContextImpl(this);
        mPermissionContexts.add(permissionContext);

        permissionContext.withoutPermission(permissions);

        return permissionContext;
    }

    void undoPermission(PermissionContext permissionContext) {
        mPermissionContexts.remove(permissionContext);
        applyPermissions();
    }

    void applyPermissions() {
        if (mPermissionContexts.isEmpty()) {
            restoreExistingPermissions();
            return;
        }

        Package resolvedInstrumentedPackage = sInstrumentedPackage.resolve();

        if (SUPPORTS_ADOPT_SHELL_PERMISSIONS) {
            ShellCommandUtils.uiAutomation().dropShellPermissionIdentity();
        }
        Set<String> grantedPermissions = new HashSet<>();
        Set<String> deniedPermissions = new HashSet<>();

        synchronized (mPermissionContexts) {
            for (PermissionContextImpl permissionContext : mPermissionContexts) {
                for (String permission : permissionContext.grantedPermissions()) {
                    grantedPermissions.add(permission);
                    deniedPermissions.remove(permission);
                }

                for (String permission : permissionContext.deniedPermissions()) {
                    grantedPermissions.remove(permission);
                    deniedPermissions.add(permission);
                }
            }
        }

        Log.d(LOG_TAG, "Applying permissions granting: "
                + grantedPermissions + " denying: " + deniedPermissions);

        // We first try to use shell permissions, because they can be revoked/etc. much more easily

        Set<String> adoptedShellPermissions = new HashSet<>();

        for (String permission : grantedPermissions) {
            checkCanGrantOnAllSupportedVersions(permission, sUser, resolvedInstrumentedPackage);

            Log.d(LOG_TAG , "Trying to grant " + permission);
            if (resolvedInstrumentedPackage.grantedPermissions(sUser).contains(permission)) {
                // Already granted, can skip
                Log.d(LOG_TAG, permission + " already granted at runtime");
            } else if (resolvedInstrumentedPackage.requestedPermissions().contains(permission)
                    && sContext.checkSelfPermission(permission) == PERMISSION_GRANTED) {
                // Already granted, can skip
                Log.d(LOG_TAG, permission + " already granted from manifest");
            } else if (SUPPORTS_ADOPT_SHELL_PERMISSIONS
                    && sShellPackage.requestedPermissions().contains(permission)) {
                adoptedShellPermissions.add(permission);
            } else if (canGrantPermission(permission)) {
                sInstrumentedPackage.grantPermission(sUser, permission);
            } else {
                removePermissionContextsUntilCanApply();

                throwPermissionException("PermissionContext requires granting "
                        + permission + " but cannot.", permission, sUser,
                        resolvedInstrumentedPackage);
            }
        }

        for (String permission : deniedPermissions) {
            checkCanDenyOnAllSupportedVersions(permission, sUser, resolvedInstrumentedPackage);

            if (!resolvedInstrumentedPackage.grantedPermissions(sUser).contains(permission)) {
                // Already denied, can skip
            } else if (SUPPORTS_ADOPT_SHELL_PERMISSIONS
                    && !sShellPackage.requestedPermissions().contains(permission)) {
                adoptedShellPermissions.add(permission);
            } else { // We can't deny a permission to ourselves
                removePermissionContextsUntilCanApply();
                throwPermissionException("PermissionContext requires denying "
                        + permission + " but cannot.", permission, sUser,
                        resolvedInstrumentedPackage);
            }
        }

        if (!adoptedShellPermissions.isEmpty()) {
            Log.d(LOG_TAG, "Adopting " + adoptedShellPermissions);
            ShellCommandUtils.uiAutomation().adoptShellPermissionIdentity(
                    adoptedShellPermissions.toArray(new String[0]));
        }
    }

    private void checkCanGrantOnAllSupportedVersions(
            String permission, UserReference user, Package instrumentedPackage) {
        if (sCheckedGrantPermissions.contains(permission)) {
            return;
        }

        if (Versions.isDevelopmentVersion()
                && !sShellPackage.requestedPermissions().contains(permission)
                && !EXEMPT_SHELL_PERMISSIONS.contains(permission)) {
            throwPermissionException(permission + " is not granted to shell on latest development"
                    + "version. You must add it to the com.android.shell manifest. If that is not"
                    + "possible add it to"
                    + "com.android.bedstead.nene.permissions.Permissions#EXEMPT_SHELL_PERMISSIONS",
                    permission, user, instrumentedPackage);
        }

        sCheckedGrantPermissions.add(permission);
    }

    private void checkCanDenyOnAllSupportedVersions(
            String permission, UserReference user, Package instrumentedPackage) {
        if (sCheckedDenyPermissions.contains(permission)) {
            return;
        }

        sCheckedDenyPermissions.add(permission);
    }

    private void throwPermissionException(
            String message, String permission, UserReference user, Package instrumentedPackage) {
        String protectionLevel = "Permission not found";
        try {
            protectionLevel = Integer.toString(sPackageManager.getPermissionInfo(
                    permission, /* flags= */ 0).protectionLevel);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Permission not found", e);
        }

        throw new NeneException(message + "\n\nRunning On User: " + user
                + "\nPermission: " + permission
                + "\nPermission protection level: " + protectionLevel
                + "\nPermission state: " + sContext.checkSelfPermission(permission)
                + "\nInstrumented Package: " + instrumentedPackage.packageName()
                + "\n\nGranted Permissions:\n"
                + instrumentedPackage.grantedPermissions(user)
                + "\n\nRequested Permissions:\n"
                + instrumentedPackage.requestedPermissions()
                + "\n\nCan adopt shell permissions: " + SUPPORTS_ADOPT_SHELL_PERMISSIONS
                + "\nShell permissions:"
                + sShellPackage.requestedPermissions()
                + "\nExempt Shell permissions: " + EXEMPT_SHELL_PERMISSIONS);
    }

    void clearPermissions() {
        mPermissionContexts.clear();
        applyPermissions();
    }

    private void removePermissionContextsUntilCanApply() {
        try {
            mPermissionContexts.remove(mPermissionContexts.size() - 1);
            applyPermissions();
        } catch (NeneException e) {
            // Suppress NeneException here as we may get a few as we pop through the stack
        }
    }

    private boolean canGrantPermission(String permission) {
        try {
            PermissionInfo p = sPackageManager.getPermissionInfo(permission, /* flags= */ 0);
            if ((p.protectionLevel & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) > 0) {
                return true;
            }
            if ((p.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) > 0) {
                return true;
            }

            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void recordExistingPermissions() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            return;
        }

        mExistingPermissions = ShellCommandUtils.uiAutomation().getAdoptedShellPermissions();
    }

    private void restoreExistingPermissions() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            return;
        }

        if (mExistingPermissions == null) {
            return; // We haven't recorded previous permissions
        } else if (mExistingPermissions.isEmpty()) {
            ShellCommandUtils.uiAutomation().dropShellPermissionIdentity();
        } else if (mExistingPermissions == UiAutomation.ALL_PERMISSIONS) {
            ShellCommandUtils.uiAutomation().adoptShellPermissionIdentity();
        } else {
            ShellCommandUtils.uiAutomation().adoptShellPermissionIdentity(
                    mExistingPermissions.toArray(new String[0]));
        }

        mExistingPermissions = null;
    }
}
