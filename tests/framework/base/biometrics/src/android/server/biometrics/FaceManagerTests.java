/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.biometrics.face;

import static android.hardware.biometrics.Flags.FLAG_FACE_BACKGROUND_AUTHENTICATION;
import static android.Manifest.permission.USE_BACKGROUND_FACE_AUTHENTICATION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.face.FaceManager;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@Presubmit
public class FaceManagerTests {
    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule
    @Rule(order = 1)
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final FaceManager sFaceManager =
            sContext.getSystemService(FaceManager.class);

    // TODO(b/319198966): Add CTS test cases to verify the actual behavior.
    /** Make sure the {@link FaceManager} can be correctly obtained. */
    @RequiresFlagsEnabled(FLAG_FACE_BACKGROUND_AUTHENTICATION)
    @ApiTest(apis = {"android.content.Context#FACE_SERVICE"})
    @Test
    public void getSystemService() {
        if (sFaceManager == null) {
            return;
        }
        assertEquals((FaceManager) sContext.getSystemService(android.content.Context.FACE_SERVICE),
                sContext.getSystemService(FaceManager.class));
    }

    /**
     * Tests that the privileged apps with {@link USE_BACKGROUND_FACE_AUTHENTICATION} permission
     * have the access to the background face authentication method.
     */
    @RequiresFlagsEnabled(FLAG_FACE_BACKGROUND_AUTHENTICATION)
    @EnsureHasPermission(USE_BACKGROUND_FACE_AUTHENTICATION)
    @ApiTest(apis = {"android.hardware.face.FaceManager#authenticateInBackground"})
    @Test
    public void authenticateInBackground_withPermission_pass() {
        if (sFaceManager == null) {
            return;
        }
        sFaceManager.authenticateInBackground(
                null /* executor */,
                null /* crypto */,
                null /* cancel */,
                new BiometricPrompt.AuthenticationCallback() {});
    }

    /**
     * Tests that the privileged apps with {@link USE_BACKGROUND_FACE_AUTHENTICATION} permission
     * have the access to the face enrollment status query.
     */
    @RequiresFlagsEnabled(FLAG_FACE_BACKGROUND_AUTHENTICATION)
    @EnsureHasPermission(USE_BACKGROUND_FACE_AUTHENTICATION)
    @ApiTest(apis = {"android.hardware.face.FaceManager#hasEnrolledTemplates"})
    @Test
    public void hasEnrolledTemplates_withPermission_pass() {
        if (sFaceManager == null) {
            return;
        }
        boolean unused = sFaceManager.hasEnrolledTemplates();
    }

    /**
     * Tests that the apps without {@link USE_BACKGROUND_FACE_AUTHENTICATION} permission do not have
     * the access to the background face authentication method.
     */
    @RequiresFlagsEnabled(FLAG_FACE_BACKGROUND_AUTHENTICATION)
    @EnsureDoesNotHavePermission(USE_BACKGROUND_FACE_AUTHENTICATION)
    @ApiTest(apis = {"android.hardware.face.FaceManager#authenticateInBackground"})
    @Test
    public void authenticateInBackground_withoutPermission_throwsSecurityException() {
        if (sFaceManager == null) {
            return;
        }
        assertThrows(SecurityException.class, () -> sFaceManager.authenticateInBackground(
                null /* executor */,
                null /* crypto */,
                null /* cancel */,
                new BiometricPrompt.AuthenticationCallback() {}));
    }

    /**
     * Tests that the apps without {@link USE_BACKGROUND_FACE_AUTHENTICATION} permission do not have
     * the access to the face enrollment status query.
     */
    @RequiresFlagsEnabled(FLAG_FACE_BACKGROUND_AUTHENTICATION)
    @EnsureDoesNotHavePermission(USE_BACKGROUND_FACE_AUTHENTICATION)
    @ApiTest(apis = {"android.hardware.face.FaceManager#hasEnrolledTemplates"})
    @Test
    public void hasEnrolledTemplates_withoutPermission_throwsSecurityException() {
        if (sFaceManager == null) {
            return;
        }
        assertThrows(SecurityException.class, sFaceManager::hasEnrolledTemplates);
    }
}
