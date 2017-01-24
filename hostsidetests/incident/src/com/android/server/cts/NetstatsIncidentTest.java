/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts;

import android.service.NetworkIdentityProto;
import android.service.NetworkInterfaceProto;
import android.service.NetworkStatsCollectionKeyProto;
import android.service.NetworkStatsCollectionStatsProto;
import android.service.NetworkStatsHistoryBucketProto;
import android.service.NetworkStatsHistoryProto;
import android.service.NetworkStatsRecorderProto;
import android.service.NetworkStatsServiceDumpProto;

import java.util.List;

/**
 * Test for "dumpsys netstats --proto"
 *
 * Note most of the logic here is just heuristics.
 *
 * Usage:

  cts-tradefed run cts --skip-device-info --skip-preconditions \
      --skip-system-status-check \
       com.android.compatibility.common.tradefed.targetprep.NetworkConnectivityChecker \
       -a armeabi-v7a -m CtsIncidentHostTestCases -t com.android.server.cts.NetstatsIncidentTest

 */
public class NetstatsIncidentTest extends ProtoDumpTestCase {
    private static final String DEVICE_SIDE_TEST_APK = "CtsNetStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.netstats";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);

        super.tearDown();
    }

    /**
     * Parse the output of "dumpsys netstats --proto" and make sure all the values are probable.
     */
    public void testSanityCheck() throws Exception {
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        // Run the device side test which makes some network requests.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, null, null);

        // Also does ping for more network activity.
        getDevice().executeShellCommand("ping -c 8 -i 0 8.8.8.8");

        // Force refresh the output.
        getDevice().executeShellCommand("dumpsys netstats --poll");

        final NetworkStatsServiceDumpProto dump = getDump(NetworkStatsServiceDumpProto.parser(),
                "dumpsys netstats --proto");

        checkInterfaces(dump.getActiveInterfacesList());
        checkInterfaces(dump.getActiveUidInterfacesList());

        checkStats(dump.getDevStats(), /*withUid=*/ false, /*withTag=*/ false);
        checkStats(dump.getXtStats(), /*withUid=*/ false, /*withTag=*/ false);
        checkStats(dump.getUidStats(), /*withUid=*/ true, /*withTag=*/ false);
        checkStats(dump.getUidTagStats(), /*withUid=*/ true, /*withTag=*/ true);
    }

    private void assertPositive(String name, long value) {
        if (value > 0) return;
        fail(name + " expected to be positive, but was: " + value);
    }

    private void assertNotNegative(String name, long value) {
        if (value >= 0) return;
        fail(name + " expected to be zero or positive, but was: " + value);
    }

    private void checkInterfaces(List<NetworkInterfaceProto> interfaces) {
        /* Example:
    active_interfaces=[
      NetworkInterfaceProto {
        interface=wlan0
        identities=NetworkIdentitySetProto {
          identities=[
            NetworkIdentityProto {
              type=1
              subscriber_id=
              network_id="wifiap"
              roaming=false
              metered=false
            }
          ]
        }
      }
    ]
         */
        assertTrue("There must be at least one network device",
                interfaces.size() > 0);

        boolean allRoaming = true;
        boolean allMetered = true;

        for (NetworkInterfaceProto iface : interfaces) {
            assertTrue("Missing interface name", !iface.getInterface().isEmpty());

            assertPositive("# identities", iface.getIdentities().getIdentitiesList().size());

            for (NetworkIdentityProto iden : iface.getIdentities().getIdentitiesList()) {
                allRoaming &= iden.getRoaming();
                allMetered &= iden.getMetered();

                // TODO Can we check the other fields too?  type, subscriber_id, and network_id.
            }
        }
        assertFalse("There must be at least one non-roaming interface during CTS", allRoaming);
        assertFalse("There must be at least one non-metered interface during CTS", allMetered);
    }

    private void checkStats(NetworkStatsRecorderProto record, boolean withUid, boolean withTag) {
        /*
         * Example:
    dev_stats=NetworkStatsRecorderProto {
      pending_total_bytes=136
      complete_history=NetworkStatsCollectionProto {
        stats=[
          NetworkStatsCollectionStatsProto {
            key=NetworkStatsCollectionKeyProto {
              identity=NetworkIdentitySetProto {
                identities=[
                  NetworkIdentityProto {
                    type=1
                    subscriber_id=
                    network_id="wifiap"
                    roaming=false
                    metered=false
                  }
                ]
              }
              uid=-1
              set=-1
              tag=0
            }
            history=NetworkStatsHistoryProto {
              bucket_duration_ms=3600000
              buckets=[
                NetworkStatsHistoryBucketProto {
                  bucket_start_ms=2273694336
                  rx_bytes=2142
                  rx_packets=10
                  tx_bytes=1568
                  tx_packets=12
                  operations=0
                }
                NetworkStatsHistoryBucketProto {
                  bucket_start_ms=3196682880
                  rx_bytes=2092039
                  rx_packets=1987
                  tx_bytes=236735
                  tx_packets=1750
                  operations=0
                }
         */

        assertNotNegative("Pending bytes", record.getPendingTotalBytes());

        for (NetworkStatsCollectionStatsProto stats : record.getCompleteHistory().getStatsList()) {

            final NetworkStatsCollectionKeyProto key = stats.getKey();

            // TODO Check the key.

            final NetworkStatsHistoryProto hist = stats.getHistory();

            assertPositive("duration", hist.getBucketDurationMs());

            // Subtract one hour from duration to compensate for possible DTS.
            final long minInterval = hist.getBucketDurationMs() - (60 * 60 * 1000);

            NetworkStatsHistoryBucketProto prev = null;
            for (NetworkStatsHistoryBucketProto bucket : hist.getBucketsList()) {

                // Make sure the start time is increasing by at least the "duration",
                // except we subtract duration from one our to compensate possible DTS.

                if (prev != null) {
                    assertTrue(
                            String.format("Last start=%d, current start=%d, diff=%d, duration=%d",
                                    prev.getBucketStartMs(), bucket.getBucketStartMs(),
                                    (bucket.getBucketStartMs() - prev.getBucketStartMs()),
                                    minInterval),
                            (bucket.getBucketStartMs() - prev.getBucketStartMs()) >=
                                    minInterval);
                }
                assertNotNegative("RX bytes", bucket.getRxBytes());
                assertNotNegative("RX packets", bucket.getRxPackets());
                assertNotNegative("TX bytes", bucket.getTxBytes());
                assertNotNegative("TX packets", bucket.getTxPackets());

                // It should be safe to say # of bytes >= 10 * 10 of packets, due to headers, etc...
                final long FACTOR = 10;
                assertTrue(
                        String.format("# of bytes %d too small for # of packets %d",
                                bucket.getRxBytes(), bucket.getRxPackets()),
                        bucket.getRxBytes() >= bucket.getRxPackets() * FACTOR);
                assertTrue(
                        String.format("# of bytes %d too small for # of packets %d",
                                bucket.getTxBytes(), bucket.getTxPackets()),
                        bucket.getTxBytes() >= bucket.getTxPackets() * FACTOR);
            }
        }

        // TODO Make sure test app's UID actually shows up.
    }
}
