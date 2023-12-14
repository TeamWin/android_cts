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

package android.virtualdevice.cts.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.role.RoleManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.function.Consumer;

/**
 * A test rule that creates a {@link CompanionDeviceManager} association with the instrumented
 * package for the duration of the test.
 */
public class FakeAssociationRule extends ExternalResource {

    private static final String FAKE_ASSOCIATION_ADDRESS = "00:00:00:00:00:10";

    private static final int TIMEOUT_MS = 10000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final RoleManager mRoleManager = mContext.getSystemService(RoleManager.class);

    private final String mDeviceProfile;

    @Mock
    private CompanionDeviceManager.OnAssociationsChangedListener mOnAssociationsChangedListener;

    private AssociationInfo mAssociationInfo;
    private CompanionDeviceManager mCompanionDeviceManager;

    public FakeAssociationRule() {
        this(AssociationRequest.DEVICE_PROFILE_APP_STREAMING);
    }

    public FakeAssociationRule(String deviceProfile) {
        mDeviceProfile = deviceProfile;
        mCompanionDeviceManager = mContext.getSystemService(CompanionDeviceManager.class);
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        MockitoAnnotations.initMocks(this);
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));

        Consumer<Boolean> callback = mock(Consumer.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mRoleManager.setBypassingRoleQualification(true);
            mRoleManager.addRoleHolderAsUser(
                    mDeviceProfile, mContext.getPackageName(),
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, Process.myUserHandle(),
                    mContext.getMainExecutor(), callback);
            verify(callback, timeout(TIMEOUT_MS)).accept(eq(true));
            mCompanionDeviceManager.addOnAssociationsChangedListener(
                    mContext.getMainExecutor(), mOnAssociationsChangedListener);
        });

        clearExistingAssociations();
        SystemUtil.runShellCommand(String.format("cmd companiondevice associate %d %s %s %s",
                Process.myUserHandle().getIdentifier(),
                mContext.getPackageName(),
                FAKE_ASSOCIATION_ADDRESS,
                mDeviceProfile));

        verify(mOnAssociationsChangedListener, timeout(TIMEOUT_MS)).onAssociationsChanged(any());
        List<AssociationInfo> associations = mCompanionDeviceManager.getMyAssociations();
        assertThat(associations).hasSize(1);
        mAssociationInfo = associations.get(0);
    }

    @Override
    protected void after() {
        super.after();
        if (mAssociationInfo != null) {
            for (AssociationInfo associationInfo : mCompanionDeviceManager.getMyAssociations()) {
                if (associationInfo.getId() == mAssociationInfo.getId()) {
                    disassociate();
                    assertThat(mCompanionDeviceManager.getMyAssociations()).isEmpty();
                    break;
                }
            }
        }

        Consumer<Boolean> callback = mock(Consumer.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mRoleManager.removeRoleHolderAsUser(
                    mDeviceProfile, mContext.getPackageName(),
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, Process.myUserHandle(),
                    mContext.getMainExecutor(), callback);
            verify(callback, timeout(TIMEOUT_MS)).accept(eq(true));
            mRoleManager.setBypassingRoleQualification(false);
            mCompanionDeviceManager.removeOnAssociationsChangedListener(
                    mOnAssociationsChangedListener);
        });
    }

    private void clearExistingAssociations() {
        List<AssociationInfo> associations = mCompanionDeviceManager.getMyAssociations();
        for (AssociationInfo association : associations) {
            disassociate(association.getId());
        }
        assertThat(mCompanionDeviceManager.getMyAssociations()).isEmpty();
    }

    public AssociationInfo getAssociationInfo() {
        return mAssociationInfo;
    }

    public void disassociate() {
        disassociate(mAssociationInfo.getId());
    }

    private void disassociate(int associationId) {
        reset(mOnAssociationsChangedListener);
        mCompanionDeviceManager.disassociate(associationId);
        verify(mOnAssociationsChangedListener, timeout(TIMEOUT_MS)).onAssociationsChanged(any());
    }
}
