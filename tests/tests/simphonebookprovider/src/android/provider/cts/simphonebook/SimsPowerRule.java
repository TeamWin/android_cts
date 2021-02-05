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

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.rules.ExternalResource;

import java.util.concurrent.TimeoutException;

/**
 * Rule that allows a test to power off SIM slots and powers them back on when it completes.
 *
 * <p>This should typically be used as a {@link org.junit.ClassRule} because changing the power
 * state is time-consuming.
 */
class SimsPowerRule extends ExternalResource {
    private static final String TAG = SimsPowerRule.class.getSimpleName();

    private TelephonyManager mTelephonyManager;
    private boolean mInitiallyOn;

    SimsPowerRule(boolean initiallyOn) {
        mInitiallyOn = initiallyOn;
        Context context = ApplicationProvider.getApplicationContext();
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
    }

    static SimsPowerRule off() {
        return new SimsPowerRule(false);
    }

    static SimsPowerRule on() {
        return new SimsPowerRule(true);
    }

    @Override
    protected void before() {
        if (mInitiallyOn) {
            return;
        }
        for (int i = 0; i < mTelephonyManager.getSupportedModemCount(); i++) {
            powerOff(i);
        }
    }

    @Override
    protected void after() {
        for (int i = 0; i < mTelephonyManager.getSupportedModemCount(); i++) {
            powerOn(i);
        }
    }

    void powerOn(int slotIndex) {
        setSimPower(slotIndex, 1);
    }

    void powerOff(int slotIndex) {
        setSimPower(slotIndex, 0);
    }

    private void setSimPower(int slotIndex, int powerState) {
        SettableFuture<Integer> resultFuture = SettableFuture.create();
        SystemUtil.runWithShellPermissionIdentity(() -> mTelephonyManager.setSimPowerStateForSlot(
                slotIndex, powerState, MoreExecutors.directExecutor(), resultFuture::set),
                Manifest.permission.MODIFY_PHONE_STATE, Manifest.permission.READ_PHONE_STATE);

        try {
            int result = resultFuture.get(30, SECONDS);
            if (result != TelephonyManager.SET_SIM_POWER_STATE_ALREADY_IN_STATE &&
                    result != TelephonyManager.SET_SIM_POWER_STATE_SUCCESS) {
                Log.w(TAG, "setSimPowerStateForSlot failed for slot=" + slotIndex + " result="
                        + result);
            }
        } catch (TimeoutException e) {
            Log.w(TAG, "Timed out waiting for setSimPowerStateForSlot", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        SubscriptionManager subscriptionManager = ApplicationProvider.getApplicationContext()
                .getSystemService(SubscriptionManager.class);
        SystemUtil.runWithShellPermissionIdentity(() -> PollingCheck.waitFor(30_000, () ->
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
                        != null), Manifest.permission.READ_PHONE_STATE);
    }
}
