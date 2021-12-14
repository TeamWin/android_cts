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

package android.bluetooth.cts;

import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.test.AndroidTestCase;

public class BluetoothLeAudioCodecTest extends AndroidTestCase {
    private int[] mCodecTypeArray = new int[] {
        BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3,
        BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID,
    };

    public void testGetCodecNameAndType() {
        try {
            for (int codecIdx = 0; codecIdx < mCodecTypeArray.length; codecIdx++) {
                int codecType = mCodecTypeArray[codecIdx];

                BluetoothLeAudioCodecConfig leAudioCodecConfig =
                        new BluetoothLeAudioCodecConfig.Builder()
                            .setCodecType(codecType)
                            .build();

                if (codecType == BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3) {
                    assertEquals("LC3", leAudioCodecConfig.getCodecName());
                }
                if (codecType == BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID) {
                    assertEquals("INVALID CODEC", leAudioCodecConfig.getCodecName());
                }

                assertEquals(codecType, leAudioCodecConfig.getCodecType());
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testGetMaxCodecType() {
        try {
            // Checks the supported codec is greater than zero
            // Keeps the flexibility to allow custom codec.
            assertTrue(BluetoothLeAudioCodecConfig.getMaxCodecType() > 0);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
