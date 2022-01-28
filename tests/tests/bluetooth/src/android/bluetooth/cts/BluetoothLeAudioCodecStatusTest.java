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

import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.os.Parcel;
import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.List;

public class BluetoothLeAudioCodecStatusTest extends AndroidTestCase {
    private static final BluetoothLeAudioCodecConfig LC3_STEREO_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                .setChannelMode(BluetoothLeAudioCodecConfig.CHANNEL_MODE_STEREO)
                .build();
    private static final BluetoothLeAudioCodecConfig LC3_MONO_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                .setChannelMode(BluetoothLeAudioCodecConfig.CHANNEL_MODE_MONO)
                .build();

    private static final List<BluetoothLeAudioCodecConfig> CAPABILITIES_CONFIG =
            new ArrayList() {{
                    add(LC3_STEREO_CONFIG);
                    add(LC3_MONO_CONFIG);
            }};

    private static final List<BluetoothLeAudioCodecConfig> SELECTABLE_CONFIG =
            new ArrayList() {{
                    add(LC3_STEREO_CONFIG);
                    add(LC3_MONO_CONFIG);
            }};

    private static final BluetoothLeAudioCodecStatus LE_STEREO_CODEC_STATUS =
            new BluetoothLeAudioCodecStatus(LC3_STEREO_CONFIG,
                        CAPABILITIES_CONFIG, SELECTABLE_CONFIG);

    private static final BluetoothLeAudioCodecStatus LE_MONO_CODEC_STATUS =
            new BluetoothLeAudioCodecStatus(LC3_MONO_CONFIG,
                        CAPABILITIES_CONFIG, SELECTABLE_CONFIG);

    public void testGetCodecConfig() {
        assertTrue(LE_STEREO_CODEC_STATUS.getCodecConfig().equals(LC3_STEREO_CONFIG));
        assertTrue(LE_MONO_CODEC_STATUS.getCodecConfig().equals(LC3_MONO_CONFIG));
    }

    public void testGetCodecsLocalCapabilities() {
        assertTrue(
                LE_STEREO_CODEC_STATUS.getCodecsLocalCapabilities().equals(CAPABILITIES_CONFIG));
    }

    public void testGetCodecsSelectableCapabilities() {
        assertTrue(
                LE_STEREO_CODEC_STATUS.getCodecsSelectableCapabilities().equals(SELECTABLE_CONFIG));
    }

    public void testIsCodecConfigSelectable() {
        assertTrue(LE_STEREO_CODEC_STATUS.isCodecConfigSelectable(LC3_STEREO_CONFIG));
        assertTrue(LE_STEREO_CODEC_STATUS.isCodecConfigSelectable(LC3_MONO_CONFIG));
    }

    public void testDescribeContents() {
        assertEquals(0, LE_STEREO_CODEC_STATUS.describeContents());
    }

    public void testReadWriteParcel() {
        Parcel parcel = Parcel.obtain();
        LE_STEREO_CODEC_STATUS.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BluetoothLeAudioCodecStatus codecStatusFromParcel =
                BluetoothLeAudioCodecStatus.CREATOR.createFromParcel(parcel);
        assertTrue(codecStatusFromParcel.getCodecConfig().equals(LC3_STEREO_CONFIG));
        assertTrue(
                codecStatusFromParcel.getCodecsLocalCapabilities().equals(CAPABILITIES_CONFIG));
        assertTrue(
                codecStatusFromParcel.getCodecsSelectableCapabilities().equals(SELECTABLE_CONFIG));
    }
}
