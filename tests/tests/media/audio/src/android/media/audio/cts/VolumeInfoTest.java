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

package android.media.audio.cts;

import android.media.AudioManager;
import android.media.VolumeInfo;
import android.media.cts.NonMediaMainlineTest;
import android.os.Parcel;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CtsAndroidTestCase;


@NonMediaMainlineTest
public class VolumeInfoTest extends CtsAndroidTestCase {

    private static final String TAG = "VolumeInfoTest";
    private static final int MIN_VOL = 0;
    private static final int MAX_VOL = 100;
    private static final int SET_VOL = 77;

    /**
     * Verify marshalled VolumeInfo has the same information as the original.
     * @throws Exception
     */
    @ApiTest(apis = {"android.media.VolumeInfo",
            "android.media.VolumeInfo.Builder"})
    public void testParcelableWriteToParcelCreate() throws Exception {
        // test VolumeInfo with stream type, no mute command
        exerciseParcelableWriteToParcelCreateStreamType(false /*has mute*/, true /*ignored*/);
        // test VolumeInfo with stream type, mute command set to mute
        exerciseParcelableWriteToParcelCreateStreamType(true /*has mute*/, true /*mute*/);
        // test VolumeInfo with stream type, mute command set to unmute
        exerciseParcelableWriteToParcelCreateStreamType(true /*has mute*/, false /*mute*/);
    }

    private void exerciseParcelableWriteToParcelCreateStreamType(boolean hasMute, boolean mute)
            throws Exception {
        final VolumeInfo.Builder srcVIB = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC)
                .setMinVolumeIndex(MIN_VOL)
                .setMaxVolumeIndex(MAX_VOL)
                .setVolumeIndex(SET_VOL);
        if (hasMute) {
            srcVIB.setMuted(mute);
        }
        final VolumeInfo srcVI = srcVIB.build();
        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        srcVI.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final VolumeInfo targetVI = VolumeInfo.CREATOR.createFromParcel(dstParcel);

        // test getters
        assertEquals("Marshalled/restored hasStreamType doesn't match",
                srcVI.hasStreamType(), targetVI.hasStreamType());
        assertEquals("Marshalled/restored stream type doesn't match",
                srcVI.getStreamType(), targetVI.getStreamType());
        assertEquals("Marshalled/restored min volume index doesn't match",
                srcVI.getMinVolumeIndex(), targetVI.getMinVolumeIndex());
        assertEquals("Marshalled/restored max volume index doesn't match",
                srcVI.getMaxVolumeIndex(), targetVI.getMaxVolumeIndex());
        assertEquals("Marshalled/restored volume index doesn't match",
                srcVI.getVolumeIndex(), targetVI.getVolumeIndex());
        assertEquals("Marshalled/restored has mute command doesn't match",
                srcVI.hasMuteCommand(), targetVI.hasMuteCommand());
        if (hasMute) {
            assertEquals("set source mute command not as retrieved",
                    mute, srcVI.isMuted());
            assertEquals("Marshalled/restored mute command doesn't match",
                    srcVI.isMuted(), targetVI.isMuted());
        }

        // test equality
        assertEquals(srcVI, targetVI);
    }

    @ApiTest(apis = {"android.media.VolumeInfo",
            "android.media.VolumeInfo#getDefaultVolumeInfo"})
    public void testDefaultVolInfo() throws Exception {
        final VolumeInfo defaultVI = VolumeInfo.getDefaultVolumeInfo();
        assertNotNull(defaultVI);
        boolean hasStream = defaultVI.hasStreamType();
        assertEquals(!hasStream, defaultVI.hasVolumeGroup());

    }
}
