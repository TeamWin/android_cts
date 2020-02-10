/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.media.mediaparser.cts;

import android.media.MediaCodec;
import android.media.MediaParser;
import android.util.Pair;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;

import java.io.IOException;
import java.util.ArrayList;

public class MockMediaParserOutputConsumer implements MediaParser.OutputConsumer {

    private final FakeExtractorOutput mFakeExtractorOutput;
    private final ArrayList<TrackOutput> mTrackOutputs;

    public MockMediaParserOutputConsumer(FakeExtractorOutput fakeExtractorOutput) {
        mFakeExtractorOutput = fakeExtractorOutput;
        mTrackOutputs = new ArrayList<>();
    }

    @Override
    public void onSeekMap(MediaParser.SeekMap seekMap) {
        mFakeExtractorOutput.seekMap(
                new SeekMap() {
                    @Override
                    public boolean isSeekable() {
                        return seekMap.isSeekable();
                    }

                    @Override
                    public long getDurationUs() {
                        return seekMap.getDurationUs();
                    }

                    @Override
                    public SeekPoints getSeekPoints(long timeUs) {
                        return toExoPlayerSeekPoints(seekMap.getSeekPoints(timeUs));
                    }
                });
    }

    private static SeekMap.SeekPoints toExoPlayerSeekPoints(
            Pair<MediaParser.SeekPoint, MediaParser.SeekPoint> seekPoints) {
        return new SeekMap.SeekPoints(
                toExoPlayerSeekPoint(seekPoints.first), toExoPlayerSeekPoint(seekPoints.second));
    }

    private static SeekPoint toExoPlayerSeekPoint(MediaParser.SeekPoint seekPoint) {
        return new SeekPoint(seekPoint.timeUs, seekPoint.position);
    }

    @Override
    public void onTracksFound(int numberOfTracks) {
        // Do nothing.
    }

    @Override
    public void onTrackData(int trackIndex, MediaParser.TrackData trackData) {
        while (mTrackOutputs.size() < trackIndex) {
            mTrackOutputs.add(mFakeExtractorOutput.track(trackIndex, C.TRACK_TYPE_UNKNOWN));
        }
    }

    @Override
    public void onSampleData(int trackIndex, MediaParser.InputReader inputReader)
            throws IOException, InterruptedException {
        mFakeExtractorOutput
                .track(trackIndex, C.TRACK_TYPE_UNKNOWN)
                .sampleData(
                        new ExtractorInputAdapter(inputReader),
                        (int) inputReader.getLength(),
                        false);
    }

    @Override
    public void onSampleCompleted(
            int trackIndex,
            long timeUs,
            int flags,
            int size,
            int offset,
            MediaCodec.CryptoInfo cryptoData) {}

    private class ExtractorInputAdapter implements ExtractorInput {

        private final MediaParser.InputReader mInputReader;

        private ExtractorInputAdapter(MediaParser.InputReader inputReader) {
            mInputReader = inputReader;
        }

        @Override
        public int read(byte[] target, int offset, int length)
                throws IOException, InterruptedException {
            return mInputReader.read(target, offset, length);
        }

        @Override
        public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void readFully(byte[] target, int offset, int length)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int skip(int length) throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean skipFully(int length, boolean allowEndOfInput)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipFully(int length) throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int peek(byte[] target, int offset, int length)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void peekFully(byte[] target, int offset, int length)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean advancePeekPosition(int length, boolean allowEndOfInput)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void advancePeekPosition(int length) throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetPeekPosition() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getPeekPosition() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getPosition() {
            return mInputReader.getPosition();
        }

        @Override
        public long getLength() {
            return mInputReader.getLength();
        }

        @Override
        public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
            throw new UnsupportedOperationException();
        }
    }
}
