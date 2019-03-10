/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.permission.cts;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_CONTACTS;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_FOREGROUND;
import static android.app.AppOpsManager.permissionToOp;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.permission.PermissionControllerManager.REASON_INSTALLER_POLICY_VIOLATION;
import static android.permission.PermissionControllerManager.REASON_MALWARE;

import static com.google.common.truth.Truth.assertThat;

import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.content.Context;
import android.permission.PermissionControllerManager;
import android.platform.test.annotations.AppModeFull;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test {@link PermissionControllerManager}
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot talk to permission controller")
public class PermissionControllerTest {
    private static final String APP = "android.permission.cts.appthataccesseslocation";

    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final PermissionControllerManager sController =
            sContext.getSystemService(PermissionControllerManager.class);

    private void resetAppState() throws Exception {
        sUiAutomation.grantRuntimePermission(APP, ACCESS_FINE_LOCATION);
        sUiAutomation.grantRuntimePermission(APP, ACCESS_BACKGROUND_LOCATION);
        setAppOp(APP, ACCESS_FINE_LOCATION, MODE_ALLOWED);
    }

    @Before
    public void setup() throws Exception {
        sUiAutomation.adoptShellPermissionIdentity();
        resetAppState();
    }

    private @NonNull Map<String, List<String>> revoke(@NonNull Map<String, List<String>> request,
            boolean doDryRun, int reason, @NonNull Executor executor)
            throws Exception {
        AtomicReference<Map<String, List<String>>> result = new AtomicReference<>();

        sController.revokeRuntimePermissions(request, doDryRun, reason, executor,
                new PermissionControllerManager.OnRevokeRuntimePermissionsCallback() {
            @Override
            public void onRevokeRuntimePermissions(@NonNull Map<String, List<String>> r) {
                synchronized (result) {
                    result.set(r);
                    result.notifyAll();
                }
            }
        });

        synchronized (result) {
            while (result.get() == null) {
                result.wait();
            }
        }

        return result.get();
    }

    private @NonNull Map<String, List<String>> revoke(@NonNull Map<String, List<String>> request,
            boolean doDryRun) throws Exception {
        return revoke(request, doDryRun, REASON_MALWARE, sContext.getMainExecutor());
    }

    private void setAppOp(@NonNull String pkg, @NonNull String perm, int mode) throws Exception {
        sContext.getSystemService(AppOpsManager.class).setUidMode(permissionToOp(perm),
                sContext.getPackageManager().getPackageUid(pkg, 0), mode);
    }

    private Map<String, List<String>> buildRequest(@NonNull String app,
            @NonNull String permission) {
        return Collections.singletonMap(app, Collections.singletonList(permission));
    }

    @Test
    public void dryRunRevokeSinglePermission() throws Exception {
        Map<String, List<String>> request = buildRequest(APP, ACCESS_BACKGROUND_LOCATION);

        Map<String, List<String>> result = revoke(request, true);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(APP)).isNotNull();
        assertThat(result.get(APP)).containsExactly(ACCESS_BACKGROUND_LOCATION);
    }

    @Test
    public void revokeSinglePermission() throws Exception {
        Map<String, List<String>> request = buildRequest(APP, ACCESS_BACKGROUND_LOCATION);

        revoke(request, false);

        assertThat(sContext.getPackageManager().checkPermission(ACCESS_BACKGROUND_LOCATION,
                APP)).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void doNotRevokeAlreadyRevokedPermission() throws Exception {
        // Properly revoke the permission
        sUiAutomation.revokeRuntimePermission(APP, ACCESS_BACKGROUND_LOCATION);
        setAppOp(APP, ACCESS_FINE_LOCATION, MODE_FOREGROUND);

        Map<String, List<String>> request = buildRequest(APP, ACCESS_BACKGROUND_LOCATION);

        Map<String, List<String>> result = revoke(request, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void dryRunRevokeForegroundPermission() throws Exception {
        Map<String, List<String>> request = buildRequest(APP, ACCESS_FINE_LOCATION);

        Map<String, List<String>> result = revoke(request, true);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(APP)).isNotNull();
        assertThat(result.get(APP)).containsExactly(ACCESS_FINE_LOCATION,
                ACCESS_BACKGROUND_LOCATION);
    }

    @Test
    public void revokeUnrequestedPermission() throws Exception {
        Map<String, List<String>> request = buildRequest(APP, READ_CONTACTS);

        Map<String, List<String>> result = revoke(request, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void revokeFromUnknownPackage() throws Exception {
        Map<String, List<String>> request = buildRequest("invalid.app", READ_CONTACTS);

        Map<String, List<String>> result = revoke(request, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void revokeFromUnknownPermission() throws Exception {
        Map<String, List<String>> request = buildRequest(APP, "unknown.permission");

        Map<String, List<String>> result = revoke(request, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void revokePolicyViolationFromWrongPackage() throws Exception {
        Map<String, List<String>> request = buildRequest(APP, ACCESS_FINE_LOCATION);

        Map<String, List<String>> result = revoke(request, false,
                REASON_INSTALLER_POLICY_VIOLATION, sContext.getMainExecutor());

        assertThat(result).isEmpty();
    }

    @Test
    public void useExecutorForCallback() throws Exception {
        Map<String, List<String>> request = buildRequest(APP, ACCESS_BACKGROUND_LOCATION);

        AtomicBoolean wasRunOnExecutor = new AtomicBoolean();
        revoke(request, true, REASON_MALWARE, command -> {
            wasRunOnExecutor.set(true);
            command.run();
        });

        assertThat(wasRunOnExecutor.get()).isTrue();
    }

    @Test(expected = NullPointerException.class)
    public void nullPkg() throws Exception {
        Map<String, List<String>> request = Collections.singletonMap(null,
                Collections.singletonList(ACCESS_FINE_LOCATION));

        revoke(request, true);
    }

    @Test(expected = NullPointerException.class)
    public void nullPermissions() throws Exception {
        Map<String, List<String>> request = Collections.singletonMap(APP, null);

        revoke(request, true);
    }

    @Test(expected = NullPointerException.class)
    public void nullPermission() throws Exception {
        Map<String, List<String>> request = Collections.singletonMap(APP,
                Collections.singletonList(null));

        revoke(request, true);
    }

    @Test(expected = NullPointerException.class)
    public void nullRequests() {
        sController.revokeRuntimePermissions(null, false, REASON_MALWARE,
                sContext.getMainExecutor(),
                new PermissionControllerManager.OnRevokeRuntimePermissionsCallback() {
            @Override
            public void onRevokeRuntimePermissions(@NonNull Map<String, List<String>> revoked) {
            }
        });
    }

    @Test(expected = NullPointerException.class)
    public void nullCallback() {
        Map<String, List<String>> request = buildRequest(APP, ACCESS_BACKGROUND_LOCATION);

        sController.revokeRuntimePermissions(request, false, REASON_MALWARE,
                sContext.getMainExecutor(), null);
    }

    @Test(expected = NullPointerException.class)
    public void nullExecutor() {
        Map<String, List<String>> request = buildRequest(APP, ACCESS_BACKGROUND_LOCATION);

        sController.revokeRuntimePermissions(request, false, REASON_MALWARE, null,
                new PermissionControllerManager.OnRevokeRuntimePermissionsCallback() {
            @Override
            public void onRevokeRuntimePermissions(@NonNull Map<String, List<String>> revoked) {

            }
        });
    }

    @Test(expected = SecurityException.class)
    public void tryToRevokeWithoutPermission() throws Exception {
        sUiAutomation.dropShellPermissionIdentity();
        try {
            Map<String, List<String>> request = buildRequest(APP, ACCESS_BACKGROUND_LOCATION);

            // This will fail as the test-app does not have the required permission
            revoke(request, true);
        } finally {
            sUiAutomation.adoptShellPermissionIdentity();
        }
    }

    @After
    public void dropShellPermissions() throws Exception {
        resetAppState();
        sUiAutomation.dropShellPermissionIdentity();
    }
}
