/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.verifier.p2p.testcase;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pConfig;

import com.android.cts.verifier.R;

/**
 * Test case to join a p2p group with wps push button.
 */
public class P2pClientConfigTestCase extends ConnectReqTestCase {

    public P2pClientConfigTestCase(Context context) {
        super(context);
    }

    @Override
    protected boolean executeTest() throws InterruptedException {

        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName("DIRECT-XY-HELLO")
                .setPassphrase("DEADBEEF")
                .build();

        return connectTest(config);
    }

    private String getListenerError(ListenerTest listener) {
        StringBuilder sb = new StringBuilder();
        sb.append(mContext.getText(R.string.p2p_receive_invalid_response_error));
        sb.append(listener.getReason());
        return sb.toString();
    }

    @Override
    public String getTestName() {
        return "Join p2p group test (config)";
    }
}
