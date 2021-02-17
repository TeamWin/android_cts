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

package android.provider.cts.simphonebook;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assume.assumeThat;

import android.Manifest;
import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotInfo;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;

import java.util.Arrays;
import java.util.Objects;

class SimPhonebookRequirementsRule extends ExternalResource {

    private final int mMinimumSimCount;

    SimPhonebookRequirementsRule() {
        this(1);
    }

    SimPhonebookRequirementsRule(int minimumSimCount) {
        mMinimumSimCount = minimumSimCount;
    }

    @Override
    protected void before() {
        Context context = ApplicationProvider.getApplicationContext();
        TelephonyManager telephonyManager = Objects.requireNonNull(
                context.getSystemService(TelephonyManager.class));
        UiccSlotInfo[] uiccSlots = SystemUtil.runWithShellPermissionIdentity(
                telephonyManager::getUiccSlotsInfo,
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        int nonEsimCount = (int) Arrays.stream(uiccSlots)
                .filter(info -> !info.getIsEuicc()).count();
        assumeThat(nonEsimCount, greaterThanOrEqualTo(mMinimumSimCount));

        SubscriptionManager subscriptionManager = Objects.requireNonNull(
                context.getSystemService(SubscriptionManager.class));
        SystemUtil.runWithShellPermissionIdentity(() ->
                // This is an assertion rather than an assumption because according to the
                // CTS setup all slots should have a SIM installed.
                assertWithMessage("A SIM must be installed in each SIM slot.").that(
                        subscriptionManager.getActiveSubscriptionInfoCount())
                        .isEqualTo(telephonyManager.getSupportedModemCount())
        );
    }
}
