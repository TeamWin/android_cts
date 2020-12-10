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

import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.CapabilityExchangeEventListener;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.util.Log;

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
}
