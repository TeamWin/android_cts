/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 *
 * This code was provided to AOSP by Zimperium Inc and was
 * written by:
 *
 * Simone "evilsocket" Margaritelli
 * Joshua "jduck" Drake
 */
package android.security.cts;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Looper;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.android.cts.security.R;


/**
 * Verify that the device is not vulnerable to any known Stagefright
 * vulnerabilities.
 */
public class StagefrightTest extends InstrumentationTestCase {
    static final String TAG = "StagefrightTest";

    private final long TIMEOUT_NS = 10000000000L;  // 10 seconds.

    public StagefrightTest() {
    }

    public void testStagefright_cve_2015_1538_1() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_1);
    }

    public void testStagefright_cve_2015_1538_2() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_2);
    }

    public void testStagefright_cve_2015_1538_3() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_3);
    }

    public void testStagefright_cve_2015_1538_4() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_4);
    }

    public void testStagefright_cve_2015_1539() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1539);
    }

    public void testStagefright_cve_2015_3824() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3824);
    }

    public void testStagefright_cve_2015_3826() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3826);
    }

    public void testStagefright_cve_2015_3827() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3827);
    }

    public void testStagefright_cve_2015_3828() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3828);
    }

    public void testStagefright_cve_2015_3829() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3829);
    }

    public void testStagefright_cve_2015_3864() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3864);
    }

    public void testStagefright_cve_2015_6598() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6598);
    }

    public void testStagefright_bug_26366256() throws Exception {
        doStagefrightTest(R.raw.bug_26366256);
    }

    public void testStagefright_cve_2015_3867() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3867);
    }

    public void testStagefright_cve_2015_3869() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3869);
    }

    public void testStagefright_cve_2015_3873_b_23248776() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_23248776);
    }

    public void testStagefright_cve_2015_3873_b_20718524() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_20718524);
    }

    public void testStagefright_cve_2015_3862_b_22954006() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3862_b_22954006);
    }

    public void testStagefright_cve_2015_3867_b_23213430() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3867_b_23213430);
    }

    public void testStagefright_cve_2015_3873_b_21814993() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_21814993);
    }
     
    public void testStagefright_bug_25812590() throws Exception {
        doStagefrightTest(R.raw.bug_25812590);
    }

    private void doStagefrightTest(final int rid) throws Exception {
        class MediaPlayerCrashListener
                implements MediaPlayer.OnErrorListener,
                    MediaPlayer.OnPreparedListener,
                    MediaPlayer.OnCompletionListener {
            @Override
            public boolean onError(MediaPlayer mp, int newWhat, int extra) {
                what = newWhat;
                lock.lock();
                condition.signal();
                lock.unlock();

                return true; // don't call oncompletion
            }

            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }

            @Override
            public void onCompletion(MediaPlayer mp) {
                what = 0;
                lock.lock();
                condition.signal();
                lock.unlock();
            }

            public int waitForError() throws InterruptedException {
                lock.lock();
                if (condition.awaitNanos(TIMEOUT_NS) <= 0) {
                    Log.d(TAG, "timed out on waiting for error");
                }
                lock.unlock();
                return what;
            }

            ReentrantLock lock = new ReentrantLock();
            Condition condition = lock.newCondition();
            int what;
        }

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                mp.setOnPreparedListener(mpcl);
                mp.setOnCompletionListener(mpcl);
                try {
                    AssetFileDescriptor fd = getInstrumentation().getContext().getResources()
                        .openRawResourceFd(rid);

                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());

                    mp.prepareAsync();
                } catch (Exception e) {
                }

                Looper.loop();
                mp.release();
            }
        });

        t.start();
        String name = getInstrumentation().getContext().getResources().getResourceEntryName(rid);
        String cve = name.replace("_", "-").toUpperCase();
        assertFalse("Device *IS* vulnerable to " + cve,
                    mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        t.interrupt();
    }

    private void doStagefrightTestMediaCodec(final int rid) throws Exception {
        Resources resources =  getInstrumentation().getContext().getResources();
        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        int numtracks = ex.getTrackCount();
        String rname = resources.getResourceEntryName(rid);
        Log.i(TAG, "start mediacodec test for: " + rname + ", which has " + numtracks + " tracks");
        for (int t = 0; t < numtracks; t++) {
            // find all the available decoders for this format
            ArrayList<String> matchingCodecs = new ArrayList<String>();
            MediaFormat format = ex.getTrackFormat(t);
            String mime = format.getString(MediaFormat.KEY_MIME);
            for (MediaCodecInfo info: codecList.getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (caps != null && caps.isFormatSupported(format)) {
                        matchingCodecs.add(info.getName());
                    }
                } catch (IllegalArgumentException e) {
                    // type is not supported
                }
            }

            if (matchingCodecs.size() == 0) {
                Log.w(TAG, "no codecs for track " + t + ", type " + mime);
            }
            // decode this track once with each matching codec
            ex.selectTrack(t);
            for (String codecName: matchingCodecs) {
                Log.i(TAG, "Decoding track " + t + " using codec " + codecName);
                ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                MediaCodec codec = MediaCodec.createByCodecName(codecName);
                Surface surface = null;
                if (mime.startsWith("video/")) {
                    surface = getDummySurface();
                }
                codec.configure(format, surface, null, 0);
                codec.start();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    int flags = ex.getSampleFlags();
                    long time = ex.getSampleTime();
                    int bufidx = codec.dequeueInputBuffer(5000);
                    if (bufidx >= 0) {
                        int n = ex.readSampleData(codec.getInputBuffer(bufidx), 0);
                        if (n < 0) {
                            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                            time = 0;
                            n = 0;
                        }
                        codec.queueInputBuffer(bufidx, 0, n, time, flags);
                        ex.advance();
                    }
                    int status = codec.dequeueOutputBuffer(info, 5000);
                    if (status >= 0) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break;
                        }
                        if (info.presentationTimeUs > TIMEOUT_NS / 1000) {
                            Log.d(TAG, "stopping after 10 seconds worth of data");
                            break;
                        }
                        codec.releaseOutputBuffer(status, true);
                    }
                }
                codec.release();
            }
        }
    }
}
