/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.cts.TestUtils.Monitor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.view.Surface;
import android.webkit.cts.CtsTestServer;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.impl.io.SocketOutputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.util.CharArrayBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.Adler32;

@SmallTest
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class NativeDecoderTest extends MediaPlayerTestBase {
    private static final String TAG = "DecoderTest";

    private static final boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    static final String mInpPrefix = WorkDir.getMediaDirString();
    short[] mMasterBuffer;

    static {
        // Load jni on initialization.
        Log.i("@@@", "before loadlibrary");
        System.loadLibrary("ctsmediacodec_jni");
        Log.i("@@@", "after loadlibrary");
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    // check that native extractor behavior matches java extractor

    private void compareArrays(String message, int[] a1, int[] a2) {
        if (a1 == a2) {
            return;
        }

        assertNotNull(message + ": array 1 is null", a1);
        assertNotNull(message + ": array 2 is null", a2);

        assertEquals(message + ": arraylengths differ", a1.length, a2.length);
        int length = a1.length;

        for (int i = 0; i < length; i++)
            if (a1[i] != a2[i]) {
                Log.i("@@@@", Arrays.toString(a1));
                Log.i("@@@@", Arrays.toString(a2));
                fail(message + ": at index " + i);
            }
    }

    @Ignore
    @Test
    public void SKIP_testExtractor() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest#testExtract where
        // checksum is computed over track format attributes, track buffer and buffer
        // info in both SDK and NDK side and checked for equality
        testExtractor("sinesweepogg.ogg");
        testExtractor("sinesweepoggmkv.mkv");
        testExtractor("sinesweepoggmp4.mp4");
        testExtractor("sinesweepmp3lame.mp3");
        testExtractor("sinesweepmp3smpb.mp3");
        testExtractor("sinesweepopus.mkv");
        testExtractor("sinesweepopusmp4.mp4");
        testExtractor("sinesweepm4a.m4a");
        testExtractor("sinesweepflacmkv.mkv");
        testExtractor("sinesweepflac.flac");
        testExtractor("sinesweepflacmp4.mp4");
        testExtractor("sinesweepwav.wav");

        testExtractor("video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
        testExtractor("bbb_s3_1280x720_webm_vp8_8mbps_60fps_opus_6ch_384kbps_48000hz.webm");
        testExtractor("bbb_s4_1280x720_webm_vp9_0p31_4mbps_30fps_opus_stereo_128kbps_48000hz.webm");
        testExtractor("video_1280x720_webm_av1_2000kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
        testExtractor("video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz.3gp");
        testExtractor("video_480x360_mp4_mpeg2_1500kbps_30fps_aac_stereo_128kbps_48000hz.mp4");
        testExtractor("video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz.mp4");

        CtsTestServer foo = new CtsTestServer(mContext);
        testExtractor(foo.getAssetUrl("noiseandchirps.ogg"), null, null);
        testExtractor(foo.getAssetUrl("ringer.mp3"), null, null);
        testExtractor(foo.getRedirectingAssetUrl("ringer.mp3"), null, null);

        String[] keys = new String[] {"header0", "header1"};
        String[] values = new String[] {"value0", "value1"};
        testExtractor(foo.getAssetUrl("noiseandchirps.ogg"), keys, values);
        HttpRequest req = foo.getLastRequest("noiseandchirps.ogg");
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            String value = values[i];
            Header[] header = req.getHeaders(key);
            assertTrue("expecting " + key + ":" + value + ", saw " + Arrays.toString(header),
                    header.length == 1 && header[0].getValue().equals(value));
        }

        String[] emptyArray = new String[0];
        testExtractor(foo.getAssetUrl("noiseandchirps.ogg"), emptyArray, emptyArray);
    }

    /**
     * |keys| and |values| should be arrays of the same length.
     *
     * If keys or values is null, test {@link MediaExtractor#setDataSource(String)}
     * and NDK counter part, i.e. set data source without headers.
     *
     * If keys or values is zero length, test {@link MediaExtractor#setDataSource(String, Map))}
     * and NDK counter part with null headers.
     *
     */
    private void testExtractor(String path, String[] keys, String[] values) throws Exception {
        int[] jsizes = getSampleSizes(path, keys, values);
        int[] nsizes = getSampleSizesNativePath(path, keys, values, /* testNativeSource = */ false);
        int[] nsizes2 = getSampleSizesNativePath(path, keys, values, /* testNativeSource = */ true);

        compareArrays("different samplesizes", jsizes, nsizes);
        compareArrays("different samplesizes native source", jsizes, nsizes2);
    }

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        File inpFile = new File(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    private void testExtractor(final String res) throws Exception {
        AssetFileDescriptor fd = getAssetFileDescriptorFor(res);

        int[] jsizes = getSampleSizes(
                fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        int[] nsizes = getSampleSizesNative(
                fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength());

        fd.close();
        compareArrays("different samples", jsizes, nsizes);
    }

    private static int[] getSampleSizes(String path, String[] keys, String[] values) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        if (keys == null || values == null) {
            ex.setDataSource(path);
        } else {
            Map<String, String> headers = null;
            int numheaders = Math.min(keys.length, values.length);
            for (int i = 0; i < numheaders; i++) {
                if (headers == null) {
                    headers = new HashMap<>();
                }
                String key = keys[i];
                String value = values[i];
                headers.put(key, value);
            }
            ex.setDataSource(path, headers);
        }

        return getSampleSizes(ex);
    }

    private static int[] getSampleSizes(FileDescriptor fd, long offset, long size)
            throws IOException {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(fd, offset, size);
        return getSampleSizes(ex);
    }

    private static int[] getSampleSizes(MediaExtractor ex) {
        ArrayList<Integer> foo = new ArrayList<Integer>();
        ByteBuffer buf = ByteBuffer.allocate(1024*1024);
        int numtracks = ex.getTrackCount();
        assertTrue("no tracks", numtracks > 0);
        foo.add(numtracks);
        for (int i = 0; i < numtracks; i++) {
            MediaFormat format = ex.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                foo.add(0);
                foo.add(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                foo.add(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                foo.add((int)format.getLong(MediaFormat.KEY_DURATION));
            } else if (mime.startsWith("video/")) {
                foo.add(1);
                foo.add(format.getInteger(MediaFormat.KEY_WIDTH));
                foo.add(format.getInteger(MediaFormat.KEY_HEIGHT));
                foo.add((int)format.getLong(MediaFormat.KEY_DURATION));
            } else {
                fail("unexpected mime type: " + mime);
            }
            ex.selectTrack(i);
        }
        while(true) {
            int n = ex.readSampleData(buf, 0);
            if (n < 0) {
                break;
            }
            foo.add(n);
            foo.add(ex.getSampleTrackIndex());
            foo.add(ex.getSampleFlags());
            foo.add((int)ex.getSampleTime()); // just the low bits should be OK
            byte[] foobar = new byte[n];
            buf.get(foobar, 0, n);
            foo.add(adler32(foobar));
            ex.advance();
        }

        int [] ret = new int[foo.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = foo.get(i);
        }
        return ret;
    }

    private static native int[] getSampleSizesNative(int fd, long offset, long size);
    private static native int[] getSampleSizesNativePath(
            String path, String[] keys, String[] values, boolean testNativeSource);

    @Presubmit
    @Ignore
    @Test
    public void SKIP_testExtractorFileDurationNative() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$FunctionalityTest#testExtract where
        // checksum is computed over track format attributes, track buffer and buffer
        // info in both SDK and NDK side and checked for equality. KEY_DURATION for each track is
        // part of the checksum.
        testExtractorFileDurationNative(
                "video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    private void testExtractorFileDurationNative(final String res) throws Exception {
        AssetFileDescriptor fd = getAssetFileDescriptorFor(res);
        long durationUs = getExtractorFileDurationNative(
                fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength());

        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());

        int numtracks = ex.getTrackCount();
        long aDurationUs = -1, vDurationUs = -1;
        for (int i = 0; i < numtracks; i++) {
            MediaFormat format = ex.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                aDurationUs = format.getLong(MediaFormat.KEY_DURATION);
            } else if (mime.startsWith("video/")) {
                vDurationUs = format.getLong(MediaFormat.KEY_DURATION);
            }
        }

        assertTrue("duration inconsistency",
                durationUs < 0 || durationUs >= aDurationUs && durationUs >= vDurationUs);

    }

    private static native long getExtractorFileDurationNative(int fd, long offset, long size);

    @Presubmit
    public void SKIP_testExtractorCachedDurationNative() throws Exception {
        // duplicate of CtsMediaV2TestCases:ExtractorTest$SetDataSourceTest#testDataSourceNative
        CtsTestServer foo = new CtsTestServer(mContext);
        String url = foo.getAssetUrl("ringer.mp3");
        long cachedDurationUs = getExtractorCachedDurationNative(url, /* testNativeSource = */ false);
        assertTrue("cached duration negative", cachedDurationUs >= 0);
        cachedDurationUs = getExtractorCachedDurationNative(url, /* testNativeSource = */ true);
        assertTrue("cached duration negative native source", cachedDurationUs >= 0);
    }

    private static native long getExtractorCachedDurationNative(String uri, boolean testNativeSource);



    private final static Adler32 checksummer = new Adler32();
    // simple checksum computed over every decoded buffer
    static int adler32(byte[] input) {
        checksummer.reset();
        checksummer.update(input);
        int ret = (int) checksummer.getValue();
        Log.i("@@@", "adler " + input.length + "/" + ret);
        return ret;
    }

    @Presubmit
    @Test
    public void testFormat() throws Exception {
        assertTrue("media format fail, see log for details", testFormatNative());
    }

    private static native boolean testFormatNative();

    @Presubmit
    @Test
    public void testPssh() throws Exception {
        testPssh("psshtest.mp4");
    }

    private void testPssh(final String res) throws Exception {
        AssetFileDescriptor fd = getAssetFileDescriptorFor(res);

        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(fd.getParcelFileDescriptor().getFileDescriptor(),
                fd.getStartOffset(), fd.getLength());
        testPssh(ex);
        ex.release();

        boolean ret = testPsshNative(
                fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength());
        assertTrue("native pssh error", ret);
    }

    private static void testPssh(MediaExtractor ex) {
        Map<UUID, byte[]> map = ex.getPsshInfo();
        Set<UUID> keys = map.keySet();
        for (UUID uuid: keys) {
            Log.i("@@@", "uuid: " + uuid + ", data size " +
                    map.get(uuid).length);
        }
    }

    private static native boolean testPsshNative(int fd, long offset, long size);

    @Test
    public void testCryptoInfo() throws Exception {
        assertTrue("native cryptoinfo failed, see log for details", testCryptoInfoNative());
    }

    private static native boolean testCryptoInfoNative();

    @Presubmit
    @Test
    public void testMediaFormat() throws Exception {
        assertTrue("native mediaformat failed, see log for details", testMediaFormatNative());
    }

    private static native boolean testMediaFormatNative();

    @Presubmit
    @Test
    public void testAMediaDataSourceClose() throws Throwable {

        final CtsTestServer slowServer = new SlowCtsTestServer();
        final String url = slowServer.getAssetUrl("noiseandchirps.ogg");
        final long ds = createAMediaDataSource(url);
        final long ex = createAMediaExtractor();

        try {
            setAMediaExtractorDataSourceAndFailIfAnr(ex, ds);
        } finally {
            slowServer.shutdown();
            deleteAMediaExtractor(ex);
            deleteAMediaDataSource(ds);
        }

    }

    private void setAMediaExtractorDataSourceAndFailIfAnr(final long ex, final long ds)
            throws Throwable {
        final Monitor setAMediaExtractorDataSourceDone = new Monitor();
        final int HEAD_START_MILLIS = 1000;
        final int ANR_TIMEOUT_MILLIS = 2500;
        final int JOIN_TIMEOUT_MILLIS = 1500;

        Thread setAMediaExtractorDataSourceThread = new Thread() {
            public void run() {
                setAMediaExtractorDataSource(ex, ds);
                setAMediaExtractorDataSourceDone.signal();
            }
        };

        try {
            setAMediaExtractorDataSourceThread.start();
            Thread.sleep(HEAD_START_MILLIS);
            closeAMediaDataSource(ds);
            boolean closed = setAMediaExtractorDataSourceDone.waitForSignal(ANR_TIMEOUT_MILLIS);
            assertTrue("close took longer than " + ANR_TIMEOUT_MILLIS, closed);
        } finally {
            setAMediaExtractorDataSourceThread.join(JOIN_TIMEOUT_MILLIS);
        }

    }

    private class SlowCtsTestServer extends CtsTestServer {

        private static final int SERVER_DELAY_MILLIS = 5000;
        private final CountDownLatch mDisconnected = new CountDownLatch(1);

        SlowCtsTestServer() throws Exception {
            super(mContext);
        }

        @Override
        protected DefaultHttpServerConnection createHttpServerConnection() {
            return new SlowHttpServerConnection(mDisconnected, SERVER_DELAY_MILLIS);
        }

        @Override
        public void shutdown() {
            mDisconnected.countDown();
            super.shutdown();
        }
    }

    private static class SlowHttpServerConnection extends DefaultHttpServerConnection {

        private final CountDownLatch mDisconnected;
        private final int mDelayMillis;

        public SlowHttpServerConnection(CountDownLatch disconnected, int delayMillis) {
            mDisconnected = disconnected;
            mDelayMillis = delayMillis;
        }

        @Override
        protected SessionOutputBuffer createHttpDataTransmitter(
                Socket socket, int buffersize, HttpParams params) throws IOException {
            return createSessionOutputBuffer(socket, buffersize, params);
        }

        SessionOutputBuffer createSessionOutputBuffer(
                Socket socket, int buffersize, HttpParams params) throws IOException {
            return new SocketOutputBuffer(socket, buffersize, params) {
                @Override
                public void write(byte[] b) throws IOException {
                    write(b, 0, b.length);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    while (len-- > 0) {
                        write(b[off++]);
                    }
                }

                @Override
                public void writeLine(String s) throws IOException {
                    delay();
                    super.writeLine(s);
                }

                @Override
                public void writeLine(CharArrayBuffer buffer) throws IOException {
                    delay();
                    super.writeLine(buffer);
                }

                @Override
                public void write(int b) throws IOException {
                    delay();
                    super.write(b);
                }

                private void delay() throws IOException {
                    try {
                        mDisconnected.await(mDelayMillis, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignored
                    }
                }

            };
        }
    }

    private static native long createAMediaExtractor();
    private static native long createAMediaDataSource(String url);
    private static native int  setAMediaExtractorDataSource(long ex, long ds);
    private static native void closeAMediaDataSource(long ds);
    private static native void deleteAMediaExtractor(long ex);
    private static native void deleteAMediaDataSource(long ds);

}

