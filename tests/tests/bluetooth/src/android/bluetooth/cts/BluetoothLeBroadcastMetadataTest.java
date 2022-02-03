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

package android.bluetooth.cts;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothLeBroadcastMetadata;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastMetadataTest {
    public void testCreateMetadataFromBuilder() {
        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder();
        BluetoothLeBroadcastMetadata metadata =
                builder.setEncrypted(false).setBroadcastCode(null)
                        .setPaSyncInterval(1)
                        .setPresentationDelayMicros(2)
                        .setBroadcastId(3)
                        .build();
        assertEquals(1, metadata.getPaSyncInterval());
        assertEquals(2, metadata.getPaSyncInterval());
        assertEquals(3, metadata.getBroadcastId());
    }
}
