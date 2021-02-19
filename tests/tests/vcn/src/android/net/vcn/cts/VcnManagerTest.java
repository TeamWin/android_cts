/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.vcn.cts;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.ipsec.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ipsec.ike.ChildSaProposal;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.SaProposal;
import android.net.ipsec.ike.TunnelModeChildSessionParams;
import android.net.vcn.VcnConfig;
import android.net.vcn.VcnControlPlaneIkeConfig;
import android.net.vcn.VcnGatewayConnectionConfig;
import android.net.vcn.VcnManager;
import android.os.ParcelUuid;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.CarrierPrivilegeUtils;
import android.telephony.cts.util.SubscriptionGroupUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class VcnManagerTest {
    private static final String TAG = VcnManagerTest.class.getSimpleName();

    private final Context mContext;
    private final VcnManager mVcnManager;
    private final SubscriptionManager mSubscriptionManager;
    private final TelephonyManager mTelephonyManager;
    private final ConnectivityManager mConnectivityManager;

    public VcnManagerTest() {
        mContext = InstrumentationRegistry.getContext();
        mVcnManager = mContext.getSystemService(VcnManager.class);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY));
    }

    private VcnConfig buildVcnConfig() {
        final IkeSaProposal ikeProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                ENCRYPTION_ALGORITHM_AES_GCM_12, SaProposal.KEY_LEN_AES_128)
                        .addDhGroup(DH_GROUP_2048_BIT_MODP)
                        .addPseudorandomFunction(PSEUDORANDOM_FUNCTION_AES128_XCBC)
                        .build();

        final String serverHostname = "2001:db8:1::100";
        final String testLocalId = "test.client.com";
        final String testRemoteId = "test.server.com";
        final byte[] psk = "psk".getBytes();

        // TODO: b/180521384: Build the IkeSessionParams without a Context when the no-arg
        // IkeSessionParams.Builder constructor is exposed.
        final IkeSessionParams ikeParams =
                new IkeSessionParams.Builder(mContext)
                        .setServerHostname(serverHostname)
                        .addSaProposal(ikeProposal)
                        .setLocalIdentification(new IkeFqdnIdentification(testLocalId))
                        .setRemoteIdentification(new IkeFqdnIdentification(testRemoteId))
                        .setAuthPsk(psk)
                        .build();

        final ChildSaProposal childProposal =
                new ChildSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                ENCRYPTION_ALGORITHM_AES_GCM_12, SaProposal.KEY_LEN_AES_128)
                        .build();
        final TunnelModeChildSessionParams childParams =
                new TunnelModeChildSessionParams.Builder().addSaProposal(childProposal).build();

        final VcnControlPlaneIkeConfig controlConfig =
                new VcnControlPlaneIkeConfig(ikeParams, childParams);

        final VcnGatewayConnectionConfig gatewayConnConfig =
                new VcnGatewayConnectionConfig.Builder(controlConfig)
                        .addExposedCapability(NET_CAPABILITY_INTERNET)
                        .addRequiredUnderlyingCapability(NET_CAPABILITY_INTERNET)
                        .setRetryInterval(
                                new long[] {
                                    TimeUnit.SECONDS.toMillis(1),
                                    TimeUnit.MINUTES.toMillis(1),
                                    TimeUnit.HOURS.toMillis(1)
                                })
                        .setMaxMtu(1360)
                        .build();

        return new VcnConfig.Builder(mContext)
                .addGatewayConnectionConfig(gatewayConnConfig)
                .build();
    }

    @Test(expected = SecurityException.class)
    public void testSetVcnConfig_noCarrierPrivileges() throws Exception {
        // TODO: b/180521384: Remove the assertion when constructing IkeSessionParams does not
        // require an active default network.
        assertNotNull(
                "You must have an active network connection to complete CTS",
                mConnectivityManager.getActiveNetwork());

        mVcnManager.setVcnConfig(new ParcelUuid(UUID.randomUUID()), buildVcnConfig());
    }

    @Test
    public void testSetVcnConfig_withCarrierPrivileges() throws Exception {
        // TODO: b/180521384: Remove the assertion when constructing IkeSessionParams does not
        // require an active default network.
        assertNotNull(
                "You must have an active network connection to complete CTS",
                mConnectivityManager.getActiveNetwork());

        final int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, dataSubId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, dataSubId, (subGrp) -> {
                mVcnManager.setVcnConfig(subGrp, buildVcnConfig());
            });
        });

        assertFalse(mTelephonyManager.createForSubscriptionId(dataSubId).hasCarrierPrivileges());
    }

    @Test(expected = SecurityException.class)
    public void testClearVcnConfig_noCarrierPrivileges() throws Exception {
        mVcnManager.clearVcnConfig(new ParcelUuid(UUID.randomUUID()));
    }

    @Test
    public void testClearVcnConfig_withCarrierPrivileges() throws Exception {
        final int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        CarrierPrivilegeUtils.withCarrierPrivileges(mContext, dataSubId, () -> {
            SubscriptionGroupUtils.withEphemeralSubscriptionGroup(mContext, dataSubId, (subGrp) -> {
                mVcnManager.clearVcnConfig(subGrp);
            });
        });
    }
}
