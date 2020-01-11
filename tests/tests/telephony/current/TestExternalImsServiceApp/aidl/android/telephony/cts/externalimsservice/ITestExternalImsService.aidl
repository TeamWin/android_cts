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

import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.stub.ImsFeatureConfiguration;

/**
 * Interface for Testing an external ImsService implementation. Since it is not in the same process,
 * it can not be passed locally.
 */
interface ITestExternalImsService {
    boolean waitForLatchCountdown(int latchIndex);
    void setFeatureConfig(in ImsFeatureConfiguration f);
    boolean isRcsFeatureCreated();
    boolean isMmTelFeatureCreated();
    void resetState();
    void notifyRcsCapabilitiesStatusChanged(int capability);
    boolean isRcsCapable(int capability, int radioTech);
    boolean isRcsAvailable(int capability);

    // IMS registration status changed
    void triggerImsOnRegistered(int radioTech);
    void triggerImsOnRegistering(int radioTech);
    void triggerImsOnDeregistered(in ImsReasonInfo info);
    void triggerImsOnTechnologyChangeFailed(int imsRadioTech, in ImsReasonInfo info);
}
