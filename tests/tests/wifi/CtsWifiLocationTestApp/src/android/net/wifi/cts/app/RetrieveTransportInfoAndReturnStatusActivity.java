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

package android.net.wifi.cts.app;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import java.util.Objects;

/**
 * An activity that retrieves Transport info and returns status.
 */
public class RetrieveTransportInfoAndReturnStatusActivity extends Activity {
    private static final String TAG = "RetrieveTransportInfoAndReturnStatusActivity";
    private static final String STATUS_EXTRA = "android.net.wifi.cts.app.extra.STATUS";

    public static boolean canRetrieveSsidFromTransportInfo(
            String logTag, ConnectivityManager connectivityManager) {
        // Assumes wifi network is the default route.
        Network[] networks = connectivityManager.getAllNetworks();
        if (networks == null || networks.length == 0) {
            Log.e(logTag, " Failed to get any networks");
            return false;
        }
        NetworkCapabilities wifiNetworkCapabilities = null;
        for (Network network : networks) {
            NetworkCapabilities networkCapabilities =
                    connectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities == null) {
                Log.e(logTag, "Failed to get network capabilities for network: " + network);
                continue;
            }
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifiNetworkCapabilities = networkCapabilities;
                break;
            }
        }
        if (wifiNetworkCapabilities == null) {
            Log.e(logTag, "Failed to get network capabilities for wifi network."
                    + " Available networks: " + networks);
            return false;
        }
        TransportInfo transportInfo = wifiNetworkCapabilities.getTransportInfo();
        if (!(transportInfo instanceof WifiInfo)) {
            Log.e(logTag, " Failed to retrieve WifiInfo");
            return false;
        }
        WifiInfo wifiInfo = (WifiInfo) transportInfo;
        boolean succeeded = !Objects.equals(wifiInfo.getSSID(), WifiManager.UNKNOWN_SSID);
        if (succeeded) {
            Log.v(logTag, "SSID from transport info retrieval succeeded");
        } else {
            Log.v(logTag, "Failed to retrieve SSID from transport info");
        }
        return succeeded;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConnectivityManager connectivityManager  = getSystemService(ConnectivityManager.class);
        setResult(RESULT_OK, new Intent().putExtra(
                STATUS_EXTRA, canRetrieveSsidFromTransportInfo(TAG, connectivityManager)));
        finish();
    }
}
