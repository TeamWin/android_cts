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

package android.telephony.cts.externalimsservice;

import android.content.Intent;
import android.os.IBinder;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.cts.ImsUtils;
import android.telephony.ims.cts.TestImsService;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

/**
 * A test ImsService that is used for GTS Testing. This package is separate from the main test
 * package because we need to have two packages available.
 */

public class TestExternalImsService extends TestImsService {
    private static final String TAG = "GtsImsTestDeviceImsService";
    // TODO: Use ImsService.SERVICE_INTERFACE definition when it becomes public.
    private static final String ACTION_BIND_IMS_SERVICE = "android.telephony.ims.ImsService";

    private final TestFrameworkConnection mBinder = new TestFrameworkConnection();

    // For local access of this Service.
    public class TestFrameworkConnection extends ITestExternalImsService.Stub {
        public boolean waitForLatchCountdown(int latchIndex) {
            return TestExternalImsService.this.waitForLatchCountdown(latchIndex);
        }

        public void setFeatureConfig(ImsFeatureConfiguration f) {
            TestExternalImsService.this.setFeatureConfig(f);
        }

        public boolean isRcsFeatureCreated() {
            return (getRcsFeature() != null);
        }

        public boolean isMmTelFeatureCreated() {
            return (getMmTelFeature() != null);
        }

        public void resetState() {
            TestExternalImsService.this.resetState();
        }

        public String getConfigString(int subId, int item) {
            ImsConfigImplBase imsConfig = TestExternalImsService.this.getImsService().getConfig(
                    subId);
            if (imsConfig != null) {
                return imsConfig.getConfigString(item);
            }
            return null;
        }

        public void triggerImsOnRegistered(int imsRadioTech) {
            ImsRegistrationImplBase imsReg = TestExternalImsService.this.getImsRegistration();
            imsReg.onRegistered(imsRadioTech);
        }

        public void triggerImsOnRegistering(int imsRadioTech) {
            ImsRegistrationImplBase imsReg = TestExternalImsService.this.getImsRegistration();
            imsReg.onRegistering(imsRadioTech);
        }

        public void triggerImsOnDeregistered(ImsReasonInfo info) {
            ImsRegistrationImplBase imsReg = TestExternalImsService.this.getImsRegistration();
            imsReg.onDeregistered(info);
        }

        public void triggerImsOnTechnologyChangeFailed(int imsRadioTech, ImsReasonInfo info) {
            ImsRegistrationImplBase imsReg = TestExternalImsService.this.getImsRegistration();
            imsReg.onTechnologyChangeFailed(imsRadioTech, info);
        }

        public void notifyRcsCapabilitiesStatusChanged(int capability) {
            RcsImsCapabilities capabilities = new RcsImsCapabilities(capability);
            getRcsFeature().notifyCapabilitiesStatusChanged(capabilities);
        }

        public boolean isRcsCapable(int capability, int radioTech) {
            return getRcsFeature().queryCapabilityConfiguration(capability, radioTech);
        }

        public boolean isRcsAvailable(int capability) {
            RcsImsCapabilities capabilityStatus = getRcsFeature().queryCapabilityStatus();
            return capabilityStatus.isCapable(capability);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (ACTION_BIND_IMS_SERVICE.equals(intent.getAction())) {
            if (ImsUtils.VDBG) {
                Log.i(TAG, "onBind-Remote");
            }
            return super.onBind(intent);
        }
        if (ImsUtils.VDBG) {
            Log.i(TAG, "onBind-Local");
        }
        return mBinder;
    }
}
