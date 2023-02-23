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

package android.net.wifi.sharedconnectivity.service.cts;

import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.TetherNetwork;
import android.net.wifi.sharedconnectivity.service.SharedConnectivityService;
import android.util.Log;

public class TestSharedConnectivityService extends SharedConnectivityService {
    private static final String TAG = "TestSharedConnectivityService";

    /**
     * Service object is saved to a static attribute so that the instance can be accessed from the
     * CTS test. Service is initialized by Context.
     */
    private static TestSharedConnectivityService sService = null;

    public static TestSharedConnectivityService getInstance() {
        return TestSharedConnectivityService.sService;
    }

    private TetherNetwork mConnectTetherNetwork;
    private TetherNetwork mDisconnectTetherNetwork;
    private KnownNetwork mConnectKnownNetwork;
    private KnownNetwork mForgetKnownNetwork;

    @Override
    public void onBind() {
        TestSharedConnectivityService.sService = this;
    }

    @Override
    public void onConnectTetherNetwork(TetherNetwork network) {
        Log.i(TAG, "onConnectTetherNetwork");
        mConnectTetherNetwork = network;
    }

    @Override
    public void onDisconnectTetherNetwork(TetherNetwork network) {
        Log.i(TAG, "onDisconnectTetherNetwork");
        mDisconnectTetherNetwork = network;
    }

    @Override
    public void onConnectKnownNetwork(KnownNetwork network) {
        Log.i(TAG, "onConnectKnownNetwork");
        mConnectKnownNetwork = network;
    }

    @Override
    public void onForgetKnownNetwork(KnownNetwork network) {
        Log.i(TAG, "onForgetKnownNetwork");
        mForgetKnownNetwork = network;
    }

    public TetherNetwork getConnectTetherNetwork() {
        return mConnectTetherNetwork;
    }

    public TetherNetwork getDisconnectTetherNetwork() {
        return mDisconnectTetherNetwork;
    }

    public KnownNetwork getConnectKnownNetwork() {
        return mConnectKnownNetwork;
    }

    public KnownNetwork getForgetKnownNetwork() {
        return mForgetKnownNetwork;
    }

}
