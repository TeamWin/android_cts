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

package android.telephony.ims.cts;

import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.CapabilityChangeRequest.CapabilityPair;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.CapabilityExchangeEventListener;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.util.Log;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

public class TestRcsFeature extends RcsFeature {

    private static final String TAG = "CtsTestImsService";

    private final TestImsService.ReadyListener mReadyListener;
    private final TestImsService.RemovedListener mRemovedListener;
    private final TestImsService.CapabilitiesSetListener mCapSetListener;
    private final TestImsService.RcsCapabilitySetListener mRcsCapabilitySetListener;

    private TestRcsCapabilityExchangeImpl mCapExchangeImpl;
    private CapabilityExchangeEventListener mCapEventListener;
    private TestImsService.DeviceCapPublishListener mDeviceCapPublishListener;

    private CapabilityChangeRequest mCapabilityChangeRequest;
    private int mCapabilitiesChangedResult = ImsFeature.CAPABILITY_SUCCESS;

    TestRcsFeature(TestImsService.ReadyListener readyListener,
            TestImsService.RemovedListener listener,
            TestImsService.CapabilitiesSetListener setListener,
            TestImsService.RcsCapabilitySetListener uceCallbackListener) {
        mReadyListener = readyListener;
        mRemovedListener = listener;
        mCapSetListener = setListener;
        mRcsCapabilitySetListener = uceCallbackListener;

        setFeatureState(STATE_READY);
    }

    public void setDeviceCapPublishListener(TestImsService.DeviceCapPublishListener listener) {
        mDeviceCapPublishListener = listener;
    }

    public void overrideCapabilitiesEnabledResult(int result) {
        mCapabilitiesChangedResult = result;
    }

    @Override
    public void onFeatureReady() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.onFeatureReady called");
        }
        mReadyListener.onReady();
    }

    @Override
    public void onFeatureRemoved() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.onFeatureRemoved called");
        }
        mRemovedListener.onRemoved();
    }

    public RcsCapabilityExchangeImplBase createCapabilityExchangeImpl(Executor executor,
            CapabilityExchangeEventListener listener) {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.createCapabilityExchangeImpl called");
        }
        mCapEventListener = listener;
        mCapExchangeImpl = new TestRcsCapabilityExchangeImpl(executor, mDeviceCapPublishListener);
        mRcsCapabilitySetListener.onSet();
        return mCapExchangeImpl;
    }

    public void removeCapabilityExchangeImpl(RcsCapabilityExchangeImplBase capExchangeImpl) {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestRcsFeature.removeCapabilityExchangeImpl called");
        }
        mRcsCapabilitySetListener.onSet();
    }

    public CapabilityExchangeEventListener getEventListener() {
        return mCapEventListener;
    }

    public TestRcsCapabilityExchangeImpl getRcsCapabilityExchangeImpl() {
        return mCapExchangeImpl;
    }

    @Override
    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            CapabilityCallbackProxy c) {
        // Trigger the error callback if the result is failed
        if (mCapabilitiesChangedResult != ImsFeature.CAPABILITY_SUCCESS) {
            CapabilityChangeRequest.CapabilityPair capPair = request.getCapabilitiesToEnable()
                    .get(0);
            c.onChangeCapabilityConfigurationError(capPair.getCapability(), capPair.getRadioTech(),
                    ImsFeature.CAPABILITY_ERROR_GENERIC);
            return;
        }
        mCapabilityChangeRequest = request;
        // Notify that the capabilities is changed.
        mCapSetListener.onSet();
    }

    @Override
    public boolean queryCapabilityConfiguration(int capability, int radioTech) {
        List<CapabilityPair> pairList =  mCapabilityChangeRequest.getCapabilitiesToEnable();
        if (pairList == null) return false;
        Optional<CapabilityPair> queryResult = pairList.stream().filter(pair -> {
            return (pair.getCapability() == capability) && (pair.getRadioTech() == radioTech);
        }).findAny();
        return queryResult.isPresent();
    }
}
