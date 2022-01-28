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
package android.net.vcn.cts;

import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_ANY;
import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_FORBIDDEN;

import static org.junit.Assert.assertEquals;

import android.net.vcn.VcnWifiUnderlyingNetworkTemplate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class VcnWifiUnderlyingNetworkTemplateTest {
    private static final Set<String> SSIDS = Set.of("TestWifi");

    // Package private for use in VcnGatewayConnectionConfigTest
    static VcnWifiUnderlyingNetworkTemplate getTestNetworkTemplate() {
        return new VcnWifiUnderlyingNetworkTemplate.Builder()
                .setMetered(MATCH_FORBIDDEN)
                .setSsids(SSIDS)
                .build();
    }

    @Test
    public void testBuilderAndGetters() {
        final VcnWifiUnderlyingNetworkTemplate networkTemplate = getTestNetworkTemplate();
        assertEquals(MATCH_FORBIDDEN, networkTemplate.getMetered());
        assertEquals(SSIDS, networkTemplate.getSsids());
    }

    @Test
    public void testBuilderAndGettersForDefaultValues() {
        final VcnWifiUnderlyingNetworkTemplate networkTemplate =
                new VcnWifiUnderlyingNetworkTemplate.Builder().build();
        assertEquals(MATCH_ANY, networkTemplate.getMetered());
        assertEquals(new HashSet<String>(), networkTemplate.getSsids());
    }

    @Test
    public void testBuildWithEmptySets() {
        final VcnWifiUnderlyingNetworkTemplate networkTemplate =
                new VcnWifiUnderlyingNetworkTemplate.Builder()
                        .setSsids(new HashSet<String>())
                        .build();
        assertEquals(new HashSet<String>(), networkTemplate.getSsids());
    }
}
