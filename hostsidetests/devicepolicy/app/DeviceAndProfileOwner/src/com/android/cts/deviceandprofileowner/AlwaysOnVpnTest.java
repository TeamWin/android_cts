/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import android.net.ConnectivityManager.NetworkCallback;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructPollfd;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.IPPROTO_ICMP;
import static android.system.OsConstants.POLLIN;
import static android.system.OsConstants.SOCK_DGRAM;

/**
 * Validates that a device owner or profile owner can set an always-on VPN without user action.
 *
 * A trivial VPN app is installed which reflects ping packets back to the sender. One ping packet is
 * sent, received after its round-trip, and compared to the original packet to make sure nothing
 * strange happened on the way through the VPN.
 *
 * All of the addresses in this test are fictional and any resemblance to real addresses is the
 * result of a misconfigured network.
 */
public class AlwaysOnVpnTest extends BaseDeviceAdminTest {

    private static final String VPN_PACKAGE = "com.android.cts.vpnfirewall";
    private static final int NETWORK_TIMEOUT_MS = 5000;
    private static final int NETWORK_SETTLE_GRACE_MS = 100;
    private static final int SOCKET_TIMEOUT_MS = 5000;

    private static final int ICMP_ECHO_REQUEST = 0x08;
    private static final int ICMP_ECHO_REPLY = 0x00;

    @Override
    public void tearDown() throws Exception {
        mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, null, false);
        super.tearDown();
    }

    public void testAlwaysOnVpn() throws Exception {
        // test always-on is null by default
        assertNull(mDevicePolicyManager.getAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT));

        final CountDownLatch vpnLatch = new CountDownLatch(1);

        final ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(mContext.CONNECTIVITY_SERVICE);

        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        final ConnectivityManager.NetworkCallback callback
                = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network net) {
                vpnLatch.countDown();
            }
        };

        cm.registerNetworkCallback(request, callback);
        try {
            mDevicePolicyManager.setAlwaysOnVpnPackage(ADMIN_RECEIVER_COMPONENT, VPN_PACKAGE, true);
            assertEquals(VPN_PACKAGE, mDevicePolicyManager.getAlwaysOnVpnPackage(
                    ADMIN_RECEIVER_COMPONENT));
            assertTrue("Took too long waiting to establish a VPN-backed connection",
                    vpnLatch.await(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS));

            // Give the VPN a moment to start transmitting data.
            Thread.sleep(NETWORK_SETTLE_GRACE_MS);
        } catch (InterruptedException e) {
            fail("Interrupted while waiting to send ping: " + e);
        } finally {
            cm.unregisterNetworkCallback(callback);
        }

        // IP address reserved for documentation by rfc5737
        checkPing("192.0.2.4");
    }

    private static void checkPing(String host) throws ErrnoException, IOException {
        FileDescriptor socket = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);

        // Create an ICMP message
        final int identifier = 0x7E57;
        final String message = "test packet";
        byte[] echo = createIcmpMessage(ICMP_ECHO_REQUEST, 0x00, identifier, 0, message.getBytes());

        // Send the echo packet.
        int port = new InetSocketAddress(0).getPort();
        Os.connect(socket, InetAddress.getByName(host), port);
        Os.write(socket, echo, 0, echo.length);

        // Expect a reply.
        StructPollfd pollfd = new StructPollfd();
        pollfd.events = (short) POLLIN;
        pollfd.fd = socket;
        int ret = Os.poll(new StructPollfd[] { pollfd }, SOCKET_TIMEOUT_MS);
        assertEquals("Expected reply after sending ping", 1, ret);

        byte[] reply = new byte[echo.length];
        int read = Os.read(socket, reply, 0, echo.length);
        assertEquals(echo.length, read);

        // Ignore control type differences since echo=8, reply=0.
        assertEquals(echo[0], ICMP_ECHO_REQUEST);
        assertEquals(reply[0], ICMP_ECHO_REPLY);
        echo[0] = 0;
        reply[0] = 0;

        // Fix ICMP ID which kernel will have changed on the way out.
        InetSocketAddress local = (InetSocketAddress) Os.getsockname(socket);
        port = local.getPort();
        echo[4] = (byte) ((port >> 8) & 0xFF);
        echo[5] = (byte) (port & 0xFF);

        // Ignore checksum differences since the types are not supposed to match.
        echo[2] = echo[3] = 0;
        reply[2] = reply[3] = 0;

        assertTrue("Packet contents do not match."
                + "\nEcho packet:  " + Arrays.toString(echo)
                + "\nReply packet: " + Arrays.toString(reply), Arrays.equals(echo, reply));
    }

    private static byte[] createIcmpMessage(int type, int code, int extra1, int extra2,
            byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeByte(type);
        stream.writeByte(code);
        stream.writeShort(/* checksum */ 0);
        stream.writeShort((short) extra1);
        stream.writeShort((short) extra2);
        stream.write(data, 0, data.length);
        return output.toByteArray();
    }
}

