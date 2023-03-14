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

package android.devicepolicy.cts.utils;

import static android.app.admin.PolicyUpdateReceiver.ACTION_DEVICE_POLICY_SET_RESULT;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_BUNDLE_KEY;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_KEY;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_TARGET_USER_ID;
import static android.app.admin.PolicyUpdateReceiver.EXTRA_POLICY_UPDATE_RESULT_KEY;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.Bundle;

import com.android.bedstead.harrier.DeviceState;

import java.time.Duration;

public class PolicySetResultUtils {

    public static void assertPolicySetResultReceived(
            DeviceState deviceState, String policyIdentifier, int resultKey, int targetUser,
            Bundle policyExtraBundle) {

        final Intent receivedIntent = deviceState.dpc().events().broadcastReceived()
                .whereIntent().action()
                .isEqualTo(ACTION_DEVICE_POLICY_SET_RESULT)
                .poll(Duration.ofMinutes(1)).intent();
        assertThat(receivedIntent).isNotNull();

        assertThat(receivedIntent.getStringExtra(EXTRA_POLICY_KEY)).isEqualTo(policyIdentifier);
        assertThat(receivedIntent.getIntExtra(EXTRA_POLICY_UPDATE_RESULT_KEY, /* default= */ -100))
                .isEqualTo(resultKey);
        assertThat(receivedIntent.getIntExtra(EXTRA_POLICY_TARGET_USER_ID, /* default= */ -100))
                .isEqualTo(targetUser);

        // TODO: add checks on bundle values.
        for (String key : policyExtraBundle.keySet()) {
            assertThat(receivedIntent.getBundleExtra(EXTRA_POLICY_BUNDLE_KEY).containsKey(key))
                    .isTrue();
        }
    }
}
