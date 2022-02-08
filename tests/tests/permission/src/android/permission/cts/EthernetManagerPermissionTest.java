/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permission.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.EthernetManager;
import android.net.EthernetNetworkUpdateRequest;
import android.net.NetworkCapabilities;
import android.net.StaticIpConfiguration;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Test protected android.net.EthernetManager methods cannot be called without permissions.
 */
@RunWith(AndroidJUnit4.class)
public class EthernetManagerPermissionTest {
    private static final String TEST_IFACE = "test123abc789";
    private EthernetManager mEthernetManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mEthernetManager = mContext.getSystemService(EthernetManager.class);
        assertNotNull(mEthernetManager);
    }

    private void callUpdateConfiguration() {
        final StaticIpConfiguration ipConfig = new StaticIpConfiguration.Builder().build();
        final NetworkCapabilities networkCapabilities =
                new NetworkCapabilities.Builder().build();
        final EthernetNetworkUpdateRequest request =
                new EthernetNetworkUpdateRequest(ipConfig, networkCapabilities);
        mEthernetManager.updateConfiguration(TEST_IFACE, request, null, null);
    }

    /**
     * Verify that calling {@link EthernetManager#updateConfiguration(String,
     * EthernetNetworkUpdateRequest, Executor, BiConsumer)} requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#MANAGE_ETHERNET_NETWORKS}.
     */
    @Test
    public void testUpdateConfiguration() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call updateConfiguration without permission",
                SecurityException.class, () -> callUpdateConfiguration());
    }

    /**
     * Verify that calling {@link EthernetManager#connectNetwork(String, Executor, BiConsumer)}
     * requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#MANAGE_ETHERNET_NETWORKS}.
     */
    @Test
    public void testConnectNetwork() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call connectNetwork without permission",
                SecurityException.class,
                () -> mEthernetManager.connectNetwork(TEST_IFACE, null, null));
    }

    /**
     * Verify that calling {@link EthernetManager#disconnectNetwork(String, Executor, BiConsumer)}
     * requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#MANAGE_ETHERNET_NETWORKS}.
     */
    @Test
    public void testDisconnectNetwork() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call disconnectNetwork without permission",
                SecurityException.class,
                () -> mEthernetManager.disconnectNetwork(TEST_IFACE, null, null));
    }

    /**
     * Verify that calling {@link EthernetManager#updateConfiguration(
     * String, EthernetNetworkUpdateRequest, Executor, BiConsumer)} requires automotive feature.
     * <p>Tests Feature:
     *   {@link PackageManager#FEATURE_AUTOMOTIVE}.
     */
    @Test
    public void testUpdateConfigurationHasAutomotiveFeature() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call updateConfiguration without automotive feature",
                UnsupportedOperationException.class, () -> callUpdateConfiguration());
    }

    /**
     * Verify that calling {@link EthernetManager#connectNetwork(String, Executor, BiConsumer)}
     * requires automotive feature.
     * <p>Tests Feature:
     *   {@link PackageManager#FEATURE_AUTOMOTIVE}.
     */
    @Test
    public void testConnectNetworkHasAutomotiveFeature() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call connectNetwork without automotive feature",
                UnsupportedOperationException.class,
                () -> mEthernetManager.connectNetwork(TEST_IFACE, null, null));
    }

    /**
     * Verify that calling {@link EthernetManager#disconnectNetwork(String, Executor, BiConsumer)}
     * requires automotive feature.
     * <p>Tests Feature:
     *   {@link PackageManager#FEATURE_AUTOMOTIVE}.
     */
    @Test
    public void testDisconnectNetworkHasAutomotiveFeature() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));
        assertThrows("Should not be able to call disconnectNetwork without automotive feature",
                UnsupportedOperationException.class,
                () -> mEthernetManager.disconnectNetwork(TEST_IFACE, null, null));
    }
}
