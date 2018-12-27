/*
 * Copyright 2015 The Android Open Source Project
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

import static org.junit.Assert.assertNotEquals;

import android.media.cts.R;

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.icu.util.ULocale;
import android.media.AudioPresentation;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.StreamTokenizer;

public class MediaExtractorTest extends AndroidTestCase {
    private static final String TAG = "MediaExtractorTest";

    protected Resources mResources;
    protected MediaExtractor mExtractor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = getContext().getResources();
        mExtractor = new MediaExtractor();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mExtractor.release();
    }

    protected TestMediaDataSource getDataSourceFor(int resid) throws Exception {
        AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
        return TestMediaDataSource.fromAssetFd(afd);
    }

    protected TestMediaDataSource setDataSource(int resid) throws Exception {
        TestMediaDataSource ds = getDataSourceFor(resid);
        mExtractor.setDataSource(ds);
        return ds;
    }

    public void testNullMediaDataSourceIsRejected() throws Exception {
        try {
            mExtractor.setDataSource((MediaDataSource)null);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException ex) {
            // Expected, test passed.
        }
    }

    public void testMediaDataSourceIsClosedOnRelease() throws Exception {
        TestMediaDataSource dataSource = setDataSource(R.raw.testvideo);
        mExtractor.release();
        assertTrue(dataSource.isClosed());
    }

    public void testExtractorFailsIfMediaDataSourceThrows() throws Exception {
        TestMediaDataSource dataSource = getDataSourceFor(R.raw.testvideo);
        dataSource.throwFromReadAt();
        try {
            mExtractor.setDataSource(dataSource);
            fail("Expected IOException.");
        } catch (IOException e) {
            // Expected.
        }
    }

    public void testExtractorFailsIfMediaDataSourceReturnsAnError() throws Exception {
        TestMediaDataSource dataSource = getDataSourceFor(R.raw.testvideo);
        dataSource.returnFromReadAt(-2);
        try {
            mExtractor.setDataSource(dataSource);
            fail("Expected IOException.");
        } catch (IOException e) {
            // Expected.
        }
    }

    // Smoke test MediaExtractor reading from a DataSource.
    public void testExtractFromAMediaDataSource() throws Exception {
        TestMediaDataSource dataSource = setDataSource(R.raw.testvideo);
        // 1MB is enough for any sample.
        final ByteBuffer buf = ByteBuffer.allocate(1024*1024);
        final int trackCount = mExtractor.getTrackCount();

        for (int i = 0; i < trackCount; i++) {
            mExtractor.selectTrack(i);
        }

        for (int i = 0; i < trackCount; i++) {
            assertTrue(mExtractor.readSampleData(buf, 0) > 0);
            assertTrue(mExtractor.advance());
        }

        // verify some getMetrics() behaviors while we're here.
        PersistableBundle metrics = mExtractor.getMetrics();
        if (metrics == null) {
            fail("getMetrics() returns no data");
        } else {
            // ensure existence of some known fields
            int tracks = metrics.getInt(MediaExtractor.MetricsConstants.TRACKS, -1);
            if (tracks != trackCount) {
                fail("getMetrics() trackCount expect " + trackCount + " got " + tracks);
            }
        }

    }

    static boolean audioPresentationSetMatchesReference(
            Map<Integer, AudioPresentation> reference,
            List<AudioPresentation> actual) {
        if (reference.size() != actual.size()) {
            Log.w(TAG, "AudioPresentations set size is invalid, expected: " +
                    reference.size() + ", actual: " + actual.size());
            return false;
        }
        for (AudioPresentation ap : actual) {
            AudioPresentation refAp = reference.get(ap.getPresentationId());
            if (refAp == null) {
                Log.w(TAG, "AudioPresentation not found in the reference set, presentation id=" +
                        ap.getPresentationId());
                return false;
            }
            if (!refAp.equals(ap)) {
                Log.w(TAG, "AudioPresentations are different, reference: " +
                        refAp + ", actual: " + ap);
                return false;
            }
        }
        return true;
    }

    public void testGetAudioPresentations() throws Exception {
        final int resid = R.raw.MultiLangPerso_1PID_PC0_Select_AC4_H265_DVB_50fps;
        TestMediaDataSource dataSource = setDataSource(resid);
        int ac4TrackIndex = -1;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (MediaFormat.MIMETYPE_AUDIO_AC4.equals(mime)) {
                ac4TrackIndex = i;
                break;
            }
        }
        assertNotEquals(
                "AC4 track was not found in MultiLangPerso_1PID_PC0_Select_AC4_H265_DVB_50fps",
                -1, ac4TrackIndex);

        // The test file has two sets of audio presentations. The presentation set
        // changes for every 100 audio presentation descriptors between two presentations.
        // Instead of attempting to count the presentation descriptors, the test assumes
        // a particular order of the presentations and advances to the next reference set
        // once getAudioPresentations returns a set that doesn't match the current reference set.
        // Thus the test can match the set 0 several times, then it encounters set 1,
        // advances the reference set index, matches set 1 until it encounters set 2 etc.
        // At the end it verifies that all the reference sets were met.
        List<Map<Integer, AudioPresentation>> refPresentations = Arrays.asList(
                new HashMap<Integer, AudioPresentation>() {{  // First set.
                    put(10, new AudioPresentation.Builder(10)
                            .setLocale(ULocale.ENGLISH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(11, new AudioPresentation.Builder(11)
                            .setLocale(ULocale.ENGLISH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasAudioDescription(true)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(12, new AudioPresentation.Builder(12)
                            .setLocale(ULocale.FRENCH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasDialogueEnhancement(true)
                            .build());
                }},
                new HashMap<Integer, AudioPresentation>() {{  // Second set.
                    put(10, new AudioPresentation.Builder(10)
                            .setLocale(ULocale.GERMAN)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasAudioDescription(true)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(11, new AudioPresentation.Builder(11)
                            .setLocale(new ULocale("es"))
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasSpokenSubtitles(true)
                            .setHasDialogueEnhancement(true)
                            .build());
                    put(12, new AudioPresentation.Builder(12)
                            .setLocale(ULocale.ENGLISH)
                            .setMasteringIndication(AudioPresentation.MASTERED_FOR_SURROUND)
                            .setHasDialogueEnhancement(true)
                            .build());
                }},
                null,
                null
        );
        refPresentations.set(2, refPresentations.get(0));
        refPresentations.set(3, refPresentations.get(1));
        boolean[] presentationsMatched = new boolean[refPresentations.size()];
        mExtractor.selectTrack(ac4TrackIndex);
        // See b/120846068, the call to 'seek' is needed to guarantee a reset of the AP parser.
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        for (int i = 0; i < refPresentations.size(); ) {
            List<AudioPresentation> presentations = mExtractor.getAudioPresentations(ac4TrackIndex);
            assertNotNull(presentations);
            // Assumes all presentation sets have the same number of presentations.
            assertEquals(refPresentations.get(i).size(), presentations.size());
            if (!audioPresentationSetMatchesReference(refPresentations.get(i), presentations)) {
                    // Time to advance to the next presentation set.
                    i++;
                    continue;
            }
            Log.d(TAG, "Matched presentation " + i);
            presentationsMatched[i] = true;
            // No need to wait for another switch after the last presentation has been matched.
            if (i == presentationsMatched.length - 1 || !mExtractor.advance()) {
                break;
            }
        }
        for (int i = 0; i < presentationsMatched.length; i++) {
            assertTrue("Presentation set " + i + " was not found in the stream",
                    presentationsMatched[i]);
        }
    }

    /*
     * Makes sure if PTS(order) of a video file with BFrames matches the expected values in
     * the corresponding text file with just PTS values.
     */
    public void testVideoPresentationTimeStampsMatch() throws Exception {
        setDataSource(R.raw.binary_counter_320x240_30fps_600frames);
        // Select the only video track present in the file.
        final int trackCount = mExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            mExtractor.selectTrack(i);
        }

        Reader txtRdr = new BufferedReader(new InputStreamReader(mResources.openRawResource(
                R.raw.timestamps_binary_counter_320x240_30fps_600frames)));
        StreamTokenizer strTok = new StreamTokenizer(txtRdr);
        strTok.parseNumbers();

        boolean srcAdvance = false;
        long srcSampleTimeUs = -1;
        long testSampleTimeUs = -1;

        strTok.nextToken();
        do {
            srcSampleTimeUs = mExtractor.getSampleTime();
            testSampleTimeUs = (long) strTok.nval;

            // Ignore round-off error if any.
            if (Math.abs(srcSampleTimeUs - testSampleTimeUs) > 1) {
                Log.d(TAG, "srcSampleTimeUs:" + srcSampleTimeUs + " testSampleTimeUs:" +
                        testSampleTimeUs);
                fail("video presentation timestamp not equal");
            }

            srcAdvance = mExtractor.advance();
            // TODO: no need to reset strTok.nval to -1 once MediaExtractor.advance() bug -
            //       b/121204004 is fixed
            strTok.nval = -1;
            strTok.nextToken();
        } while (srcAdvance);
    }
}
