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
package android.media.cts;

import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;

import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import android.cts.util.MediaUtils;
import com.google.android.collect.Lists;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

/**
 * Tests MediaDrm NDK APIs. ClearKey system uses a subset of NDK APIs,
 * this test only tests the APIs that are supported by ClearKey system.
 */
public class NativeClearKeySystemTest extends MediaPlayerTestBase {
    private static final String TAG = NativeClearKeySystemTest.class.getSimpleName();

    private static final int CONNECTION_RETRIES = 10;
    private static final int VIDEO_WIDTH_CENC = 1280;
    private static final int VIDEO_HEIGHT_CENC = 720;
    private static final String ISO_BMFF_VIDEO_MIME_TYPE = "video/avc";
    private static final String ISO_BMFF_AUDIO_MIME_TYPE = "audio/avc";
    private static final Uri CENC_AUDIO_URL = Uri.parse(
        "https://storage.googleapis.com/wvmedia/clear/h264/llama/" +
        "llama_aac_audio.mp4");

    private static final Uri CENC_CLEARKEY_VIDEO_URL = Uri.parse(
        "https://storage.googleapis.com/wvmedia/clearkey/" +
        "llama_h264_main_720p_8000.mp4");

    private static final int UUID_BYTE_SIZE = 16;
    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);
    private static final UUID BAD_SCHEME_UUID =
            new UUID(0xffffffffffffffffL, 0xffffffffffffffffL);
    private MediaCodecClearKeyPlayer mMediaCodecPlayer;

    static {
        try {
            System.loadLibrary("ctsmediadrm_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "NativeClearKeySystemTest: Error loading JNI library");
            e.printStackTrace();
        }
        try {
            System.loadLibrary("mediandk");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "NativeClearKeySystemTest: Error loading JNI library");
            e.printStackTrace();
        }
    }

    public static class PlaybackParams {
        public Surface surface;
        public String mimeType;
        public String audioUrl;
        public String videoUrl;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (false == deviceHasMediaDrm()) {
            tearDown();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private boolean deviceHasMediaDrm() {
        // ClearKey is introduced after KitKat.
        if (Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.KITKAT) {
            Log.i(TAG, "This test is designed to work after Android KitKat.");
            return false;
        }
        return true;
    }

    private static final byte[] uuidByteArray(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[UUID_BYTE_SIZE]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public void testIsCryptoSchemeSupported() throws Exception {
        assertTrue(isCryptoSchemeSupportedNative(uuidByteArray(CLEARKEY_SCHEME_UUID)));
    }

    public void testIsCryptoSchemeNotSupported() throws Exception {
        assertFalse(isCryptoSchemeSupportedNative(uuidByteArray(BAD_SCHEME_UUID)));
    }

    public void testPssh() throws Exception {
        assertTrue(testPsshNative(uuidByteArray(CLEARKEY_SCHEME_UUID),
                CENC_CLEARKEY_VIDEO_URL.toString()));
    }

    public void testGetPropertyString() throws Exception {
        StringBuffer value = new StringBuffer();
        testGetPropertyStringNative(uuidByteArray(CLEARKEY_SCHEME_UUID), "description", value);
        assertEquals("ClearKey CDM", value.toString());
    }

    public void testUnknownPropertyString() throws Exception {
        try {
            StringBuffer value = new StringBuffer();
            testGetPropertyStringNative(uuidByteArray(CLEARKEY_SCHEME_UUID),
                    "unknown-property", value);
        } catch (RuntimeException e) {
            Log.e(TAG, "testUnknownPropertyString error = '" + e.getMessage() + "'");
            assertThat(e.getMessage(), containsString("get property string returns"));
        }
    }

    /**
     * Tests native clear key system playback.
     */
    private void testClearKeyPlayback(
            String mimeType, /*String initDataType,*/ Uri audioUrl, Uri videoUrl,
            int videoWidth, int videoHeight) throws Exception {

        if (!isCryptoSchemeSupportedNative(uuidByteArray(CLEARKEY_SCHEME_UUID))) {
            throw new Error("Crypto scheme is not supported.");
        }

        IConnectionStatus connectionStatus = new ConnectionStatus(mContext);
        if (!connectionStatus.isAvailable()) {
            throw new Error("Network is not available, reason: " +
                    connectionStatus.getNotConnectedReason());
        }

        // If device is not online, recheck the status a few times.
        int retries = 0;
        while (!connectionStatus.isConnected()) {
            if (retries++ >= CONNECTION_RETRIES) {
                throw new Error("Device is not online, reason: " +
                        connectionStatus.getNotConnectedReason());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        connectionStatus.testConnection(videoUrl);

        if (!MediaUtils.checkCodecsForPath(mContext, videoUrl.toString())) {
            Log.i(TAG, "Device does not support " +
                  videoWidth + "x" + videoHeight + " resolution for " + mimeType);
            return;  // skip
        }

        PlaybackParams params = new PlaybackParams();
        params.surface = mActivity.getSurfaceHolder().getSurface();
        params.mimeType = mimeType;
        params.audioUrl = audioUrl.toString();
        params.videoUrl = videoUrl.toString();

        if (!testClearKeyPlaybackNative(
            uuidByteArray(CLEARKEY_SCHEME_UUID), params)) {
            Log.e(TAG, "Fails play back using native media drm APIs.");
        }
        params.surface.release();
    }

    /*
     * Compare version strings
     *
     * @param actual Actual platform's Android version
     * @param expected Minimum Android version
     *
     * @return 0 if the versions are identical
     * @return +v if actual is greater than expected
     * @return -ve if actual is less than expected
     */
    private static Integer compareVersion(String actual, String expected) {
        String[] part1 = actual.split("\\.");
        String[] part2 = expected.split("\\.");

        int idx = 0;
        for (; idx < part1.length && idx < part2.length; idx++) {
            String p1 = part1[idx];
            String p2 = part2[idx];

            int cmp;
            if (p1.matches("\\d+") && p2.matches("\\d+")) {
                cmp = new Integer(p1).compareTo(new Integer(p2));
            } else {
                cmp = part1[idx].compareTo(part2[idx]);
            }
            if (cmp != 0) return cmp;
        }

        if (part1.length == part2.length) {
            return 0;
        } else {
            boolean left = part1.length > idx;
            String[] parts = left ? part1 : part2;

            for (; idx < parts.length; idx++) {
                String p = parts[idx];
                int cmp;
                if (p.matches("\\d+")) {
                    cmp = new Integer(p).compareTo(0);
                } else {
                    cmp = 1;
                }
                if (cmp != 0) return left ? cmp : -cmp;
            }
            return 0;
        }
    }

    private static native boolean isCryptoSchemeSupportedNative(final byte[] uuid);

    private static native boolean testClearKeyPlaybackNative(final byte[] uuid,
            PlaybackParams params);

    private static native boolean testGetPropertyStringNative(final byte[] uuid,
            final String name, StringBuffer value);

    private static native boolean testPsshNative(final byte[] uuid, final String videoUrl);

    public void testClearKeyPlaybackCenc() throws Exception {
        if (compareVersion(Build.VERSION.RELEASE, "7.1.2") >= 0) {
            testClearKeyPlayback(
                    ISO_BMFF_VIDEO_MIME_TYPE,
                    CENC_AUDIO_URL,
                    CENC_CLEARKEY_VIDEO_URL,
                    VIDEO_WIDTH_CENC, VIDEO_HEIGHT_CENC);
        } else {
            Log.i(TAG, "Skip test, which is intended for Android 7.1.2 and above.");
        }
    }
}
