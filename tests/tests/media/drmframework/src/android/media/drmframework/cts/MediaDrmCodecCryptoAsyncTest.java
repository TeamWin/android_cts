/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.drmframework.cts;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.cts.MediaCodecAsyncHelper;
import android.media.cts.MediaCodecCryptoAsyncHelper;
import com.android.compatibility.common.util.Preconditions;
import android.media.cts.Utils;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Test;
import org.junit.Assume;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.UUID;

/**
 * Media DRM Codec tests with CONFIGURE_FLAG_USE_CRYPTO_ASYNC.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class MediaDrmCodecCryptoAsyncTest {
    private static final String TAG = "MediaDrmCodecCryptoAsyncTest";
    private static final boolean IS_AT_LEAST_U = ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU);
    static final String mInpPrefix = WorkDir.getMediaDirString();

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        File inpFile = new File(mInpPrefix + res);
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    /**
     * Tests whether decoding a short encrypted group-of-pictures succeeds when codec is configured
     * with the flag CONFIGURE_FLAG_USE_CRYPTO_ASYNC.
     * The test queues a few encrypted video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    @Presubmit
    @SmallTest
    @RequiresDevice
    @Test
    public void testShortEncryptedVideoUsingNonSecureDecoder() throws InterruptedException {
        Assume.assumeTrue("Test needs Android 14", IS_AT_LEAST_U);
        MediaCodecAsyncHelper.runThread(
                (Boolean secure) -> runClearKeyVideoUsingCodec(secure /*secure*/), false);
    }

    @Presubmit
    @SmallTest
    @RequiresDevice
    @Test
    public void testShortEncryptedVideoUsingSecureDecoder() throws InterruptedException {
        Assume.assumeTrue("Test needs Android 14", IS_AT_LEAST_U);
        MediaCodecAsyncHelper.runThread(
                (Boolean secure) -> runClearKeyVideoUsingCodec(secure /*secure*/), true);
    }

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);
    private static final byte[] CLEAR_KEY_CENC = convert(new int[] {
            0x3f, 0x0a, 0x33, 0xf3, 0x40, 0x98, 0xb9, 0xe2,
            0x2b, 0xc0, 0x78, 0xe0, 0xa1, 0xb5, 0xe8, 0x54 });

    private static final byte[] DRM_INIT_DATA = convert(new int[] {
            // BMFF box header (4 bytes size + 'pssh')
            0x00, 0x00, 0x00, 0x34, 0x70, 0x73, 0x73, 0x68,
            // Full box header (version = 1 flags = 0)
            0x01, 0x00, 0x00, 0x00,
            // SystemID
            0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02, 0xac, 0xe3, 0x3c,
            0x1e, 0x52, 0xe2, 0xfb, 0x4b,
            // Number of key ids
            0x00, 0x00, 0x00, 0x01,
            // Key id
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30,
            // size of data, must be zero
            0x00, 0x00, 0x00, 0x00 });

    private static final long ENCRYPTED_CONTENT_FIRST_BUFFER_TIMESTAMP_US = 12083333;
    private static final long ENCRYPTED_CONTENT_LAST_BUFFER_TIMESTAMP_US = 15041666;

    private static byte[] convert(int[] intArray) {
        byte[] byteArray = new byte[intArray.length];
        for (int i = 0; i < intArray.length; ++i) {
            byteArray[i] = (byte)intArray[i];
        }
        return byteArray;
    }

    private void runClearKeyVideoUsingCodec(boolean secure) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCrypto crypto = null;
        try (final MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID)) {
            Uri uri = Uri.parse(Utils.getMediaPath() + "/clearkey/llama_h264_main_720p_8000.mp4");
            extractor.setDataSource(uri.toString(), null);
            extractor.selectTrack(0);
            extractor.seekTo(ENCRYPTED_CONTENT_FIRST_BUFFER_TIMESTAMP_US,
                MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            drm.setOnEventListener(
                    (MediaDrm mediaDrm, byte[] sessionId, int event, int extra, byte[] data) -> {
                        if (event == MediaDrm.EVENT_KEY_REQUIRED
                                || event == MediaDrm.EVENT_KEY_EXPIRED) {
                            MediaDrmClearkeyTest.retrieveKeys(
                                    mediaDrm, "cenc", sessionId, DRM_INIT_DATA,
                                    MediaDrm.KEY_TYPE_STREAMING,
                                    new byte[][] { CLEAR_KEY_CENC });
                        }
                    });
            byte[] sessionId = drm.openSession();
            MediaDrmClearkeyTest.retrieveKeys(
                    drm, "cenc", sessionId, DRM_INIT_DATA, MediaDrm.KEY_TYPE_STREAMING,
                    new byte[][] { CLEAR_KEY_CENC });

            if (sessionId != null) {
                crypto = new MediaCrypto(CLEARKEY_SCHEME_UUID, new byte[0] /* initData */);
                crypto.setMediaDrmSession(sessionId);
            }
                MediaCodecCryptoAsyncHelper.runDecodeShortClearKeyVideo(extractor,
                secure /*secure*/, ENCRYPTED_CONTENT_LAST_BUFFER_TIMESTAMP_US,
                crypto);
            drm.closeSession(sessionId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}