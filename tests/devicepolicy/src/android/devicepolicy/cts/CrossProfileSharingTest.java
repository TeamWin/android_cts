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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.ACTION_DATA_SHARING_RESTRICTION_APPLIED;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE;

import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstanceReference;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BedsteadJUnit4.class)
public class CrossProfileSharingTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final TestAppProvider sTestAppProvider = new TestAppProvider();

    // TODO(b/198420874): rather than querying by package name, query apps by intents they need to
    // handle: "com.android.testapp.SOME_ACTION" and "android.intent.action.PICK"
    private static final TestApp sTestApp = sTestAppProvider.query().wherePackageName()
            .isEqualTo("com.android.bedstead.testapp.EmptyTestApp2").get();

    // Known action that is handled in the opposite profile, used to query forwarder activity.
    private static final String CROSS_PROFILE_ACTION = "com.android.testapp.SOME_ACTION";

    // TODO(b/191640667): use parametrization instead of looping once available.
    // These are the data sharing intents which can be forwarded to the primary profile.
    private static final Intent[] OPENING_INTENTS = {
            new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(
                    Intent.CATEGORY_OPENABLE),
            new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(
                    Intent.CATEGORY_OPENABLE),
            new Intent(Intent.ACTION_PICK).setType("*/*").addCategory(
                    Intent.CATEGORY_DEFAULT),
            new Intent(Intent.ACTION_PICK).addCategory(Intent.CATEGORY_DEFAULT)
    };

    /**
     * Test sharing initiated from the profile side i.e. user tries to pick up personal data within
     * a work app when DISALLOW_SHARE_INTO_MANAGED_PROFILE is enforced.
     */
    @Test
    @Postsubmit(reason = "new test")
    @RequireRunOnWorkProfile
    public void openingPersonalFromProfile_disallowShareIntoProfile_restrictionApplied()
            throws Exception {
        ResolveInfo toPersonalForwarderInfo = getToPersonalForwarderInfo();

        // Enforce the restriction and wait for it to be applied.
        setSharingIntoProfileEnabled(false);
        // Test app handles android.intent.action.PICK just in case no other app does.
        try (TestAppInstanceReference testAppParent =
                     sTestApp.install(sDeviceState.primaryUser())) {
            // Verify that the intents don't resolve into cross-profile forwarder.
            assertCrossProfileIntentsResolvability(OPENING_INTENTS,
                    toPersonalForwarderInfo, /* expectForwardable */ false);
        } finally {
            // Restore default state.
            setSharingIntoProfileEnabled(true);
        }
    }

    /**
     * Test sharing initiated from the profile side i.e. user tries to pick up personal data within
     * a work app when DISALLOW_SHARE_INTO_MANAGED_PROFILE is NOT enforced.
     */
    @Test
    @Postsubmit(reason = "new test")
    @RequireRunOnWorkProfile
    public void openingPersonalFromProfile_disallowShareIntoProfile_restrictionRemoved()
            throws Exception {
        ResolveInfo toPersonalForwarderInfo = getToPersonalForwarderInfo();

        // Enforce the restriction and wait for it to be applied, then remove it and wait again.
        setSharingIntoProfileEnabled(false);
        setSharingIntoProfileEnabled(true);

        // Test app handles android.intent.action.PICK just in case no other app does.
        try (TestAppInstanceReference testAppParent =
                     sTestApp.install(sDeviceState.primaryUser())) {
            // Verify that the intents resolve into cross-profile forwarder.
            assertCrossProfileIntentsResolvability(OPENING_INTENTS,
                    toPersonalForwarderInfo, /* expectForwardable */ true);
        }
    }

    /**
     * Finds ResolveInfo for a system activity that forwards cross-profile intent by resolving an
     * intent that it handled in the primary user from the profile user.
     * TODO(b/198759180): replace it with a @TestApi
     */
    private ResolveInfo getToPersonalForwarderInfo() {
        ResolveInfo forwarderInfo;
        try (TestAppInstanceReference testAppParent =
                     sTestApp.install(sDeviceState.primaryUser())) {
            // Set up cross profile intent filters so we can resolve these to find out framework's
            // intent forwarder activity as ground truth
            sDeviceState.dpc().devicePolicyManager()
                    .addCrossProfileIntentFilter(DPC_COMPONENT_NAME,
                            new IntentFilter(CROSS_PROFILE_ACTION),
                            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);
            try {
                forwarderInfo = getCrossProfileIntentForwarder(new Intent(CROSS_PROFILE_ACTION));
            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .clearCrossProfileIntentFilters(DPC_COMPONENT_NAME);
            }
        }
        return forwarderInfo;
    }

    //
    private ResolveInfo getCrossProfileIntentForwarder(Intent intent) {
        List<ResolveInfo> result = sTestApis.context().instrumentedContext().getPackageManager()
                .queryIntentActivities(intent, MATCH_DEFAULT_ONLY);
        assertWithMessage("Failed to get intent forwarder component")
                .that(result.size()).isEqualTo(1);
        ResolveInfo forwarder = result.get(0);
        assertWithMessage("Forwarder doesn't consider itself as such")
                .that(forwarder.isCrossProfileIntentForwarderActivity()).isTrue();
        return forwarder;
    }

    private void setSharingIntoProfileEnabled(boolean enabled) {
        IntentFilter filter = new IntentFilter(ACTION_DATA_SHARING_RESTRICTION_APPLIED);
        try (BlockingBroadcastReceiver receiver =
                     BlockingBroadcastReceiver.create(sContext, filter).register()) {
            if (enabled) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        DPC_COMPONENT_NAME, DISALLOW_SHARE_INTO_MANAGED_PROFILE);
            } else {
                sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        DPC_COMPONENT_NAME, DISALLOW_SHARE_INTO_MANAGED_PROFILE);
            }
        }
    }

    private void assertCrossProfileIntentsResolvability(
            Intent[] intents, ResolveInfo expectedForwarder, boolean expectForwardable) {
        for (Intent intent : intents) {
            List<ResolveInfo> resolveInfoList = sTestApis.context().instrumentedContext()
                    .getPackageManager().queryIntentActivities(intent, MATCH_DEFAULT_ONLY);
            if (expectForwardable) {
                assertWithMessage(String.format(
                        "Expect %s to be forwardable, but resolve list does not contain expected "
                                + "intent forwarder %s", intent, expectedForwarder))
                        .that(containsResolveInfo(resolveInfoList, expectedForwarder)).isTrue();
            } else {
                assertWithMessage(String.format(
                        "Expect %s not to be forwardable, but resolve list contains intent "
                                + "forwarder %s", intent, expectedForwarder))
                        .that(containsResolveInfo(resolveInfoList, expectedForwarder)).isFalse();
            }
        }
    }

    private boolean containsResolveInfo(List<ResolveInfo> list, ResolveInfo info) {
        for (ResolveInfo entry : list) {
            if (entry.activityInfo.packageName.equals(info.activityInfo.packageName)
                    && entry.activityInfo.name.equals(info.activityInfo.name)) {
                return true;
            }
        }
        return false;
    }
}
