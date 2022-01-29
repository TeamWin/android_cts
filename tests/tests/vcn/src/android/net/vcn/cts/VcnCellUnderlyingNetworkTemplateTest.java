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
import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_REQUIRED;

import static org.junit.Assert.assertEquals;

import android.net.vcn.VcnCellUnderlyingNetworkTemplate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class VcnCellUnderlyingNetworkTemplateTest {
    private static final Set<String> ALLOWED_PLMN_IDS = Set.of("123456", "234567");
    private static final Set<Integer> ALLOWED_CARRIER_IDS = Set.of(100, 101);

    // Package private for use in VcnGatewayConnectionConfigTest
    static VcnCellUnderlyingNetworkTemplate getTestNetworkTemplate() {
        return new VcnCellUnderlyingNetworkTemplate.Builder()
                .setMetered(MATCH_REQUIRED)
                .setRoaming(MATCH_REQUIRED)
                .setOpportunistic(MATCH_FORBIDDEN)
                .setOperatorPlmnIds(ALLOWED_PLMN_IDS)
                .setSimSpecificCarrierIds(ALLOWED_CARRIER_IDS)
                .build();
    }

    @Test
    public void testBuilderAndGetters() {
        final VcnCellUnderlyingNetworkTemplate networkTemplate = getTestNetworkTemplate();
        assertEquals(MATCH_REQUIRED, networkTemplate.getMetered());
        assertEquals(MATCH_REQUIRED, networkTemplate.getRoaming());
        assertEquals(MATCH_FORBIDDEN, networkTemplate.getOpportunistic());
        assertEquals(ALLOWED_PLMN_IDS, networkTemplate.getOperatorPlmnIds());
        assertEquals(ALLOWED_CARRIER_IDS, networkTemplate.getSimSpecificCarrierIds());
    }

    @Test
    public void testBuilderAndGettersForDefaultValues() {
        final VcnCellUnderlyingNetworkTemplate networkTemplate =
                new VcnCellUnderlyingNetworkTemplate.Builder().build();
        assertEquals(MATCH_ANY, networkTemplate.getMetered());
        assertEquals(MATCH_ANY, networkTemplate.getRoaming());
        assertEquals(MATCH_ANY, networkTemplate.getOpportunistic());
        assertEquals(new HashSet<String>(), networkTemplate.getOperatorPlmnIds());
        assertEquals(new HashSet<Integer>(), networkTemplate.getSimSpecificCarrierIds());
    }

    @Test
    public void testBuildWithEmptySets() {
        final VcnCellUnderlyingNetworkTemplate networkTemplate =
                new VcnCellUnderlyingNetworkTemplate.Builder()
                        .setOperatorPlmnIds(new HashSet<String>())
                        .setSimSpecificCarrierIds(new HashSet<Integer>())
                        .build();
        assertEquals(new HashSet<String>(), networkTemplate.getOperatorPlmnIds());
        assertEquals(new HashSet<Integer>(), networkTemplate.getSimSpecificCarrierIds());
    }
}
