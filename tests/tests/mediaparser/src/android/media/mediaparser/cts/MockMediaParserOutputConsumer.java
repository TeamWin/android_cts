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
import android.media.MediaFormat;
import android.media.MediaParser;
import android.util.Pair;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.ColorInfo;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;

public class MockMediaParserOutputConsumer implements MediaParser.OutputConsumer {

    private final FakeExtractorOutput mFakeExtractorOutput;
    private final ArrayList<TrackOutput> mTrackOutputs;

    public MockMediaParserOutputConsumer(FakeExtractorOutput fakeExtractorOutput) {
        mFakeExtractorOutput = fakeExtractorOutput;
        mTrackOutputs = new ArrayList<>();
    }

    @Override
    public void onSeekMapFound(MediaParser.SeekMap seekMap) {
        mFakeExtractorOutput.seekMap(
                new SeekMap() {
                    @Override
                    public boolean isSeekable() {
                        return seekMap.isSeekable();
                    }

                    @Override
                    public long getDurationUs() {
                        return seekMap.getDurationMicros();
                    }

                    @Override
                    public SeekPoints getSeekPoints(long timeUs) {
                        return toExoPlayerSeekPoints(seekMap.getSeekPoints(timeUs));
                    }
                });
    }

    @Override
    public void onTrackCountFound(int numberOfTracks) {
        // Do nothing.
    }

    @Override
    public void onTrackDataFound(int trackIndex, MediaParser.TrackData trackData) {
        while (mTrackOutputs.size() <= trackIndex) {
            mTrackOutputs.add(mFakeExtractorOutput.track(trackIndex, C.TRACK_TYPE_UNKNOWN));
        }
        mTrackOutputs.get(trackIndex).format(toExoPlayerFormat(trackData));
    }

    @Override
    public void onSampleDataFound(int trackIndex, MediaParser.InputReader inputReader)
            throws IOException {
        try {
            mFakeExtractorOutput
                    .track(trackIndex, C.TRACK_TYPE_UNKNOWN)
                    .sampleData(
                            new ExtractorInputAdapter(inputReader),
                            (int) inputReader.getLength(),
                            false);
        } catch (InterruptedException e) {
            // TODO: Remove this exception replacement once we update the ExoPlayer
            // version.
            throw new InterruptedIOException();
        }
    }

    @Override
    public void onSampleCompleted(
            int trackIndex,
            long timeUs,
            int flags,
            int size,
            int offset,
            MediaCodec.CryptoInfo cryptoData) {}

    // Internal methods.

    private static SeekMap.SeekPoints toExoPlayerSeekPoints(
            Pair<MediaParser.SeekPoint, MediaParser.SeekPoint> seekPoints) {
        return new SeekMap.SeekPoints(
                toExoPlayerSeekPoint(seekPoints.first), toExoPlayerSeekPoint(seekPoints.second));
    }

    private static SeekPoint toExoPlayerSeekPoint(MediaParser.SeekPoint seekPoint) {
        return new SeekPoint(seekPoint.timeMicros, seekPoint.position);
    }

    private static Format toExoPlayerFormat(MediaParser.TrackData trackData) {
        MediaFormat mediaFormat = trackData.mediaFormat;
        String sampleMimeType =
                mediaFormat.getString(MediaFormat.KEY_MIME, /* defaultValue= */ null);
        String id =
                mediaFormat.containsKey(MediaFormat.KEY_TRACK_ID)
                        ? String.valueOf(mediaFormat.getInteger(MediaFormat.KEY_TRACK_ID))
                        : null;
        String codecs =
                mediaFormat.getString(MediaFormat.KEY_CODECS_STRING, /* defaultValue= */ null);
        int bitrate =
                mediaFormat.getInteger(
                        MediaFormat.KEY_BIT_RATE, /* defaultValue= */ Format.NO_VALUE);
        int maxInputSize =
                mediaFormat.getInteger(
                        MediaFormat.KEY_MAX_INPUT_SIZE, /* defaultValue= */ Format.NO_VALUE);
        int width =
                mediaFormat.getInteger(MediaFormat.KEY_WIDTH, /* defaultValue= */ Format.NO_VALUE);
        int height =
                mediaFormat.getInteger(MediaFormat.KEY_HEIGHT, /* defaultValue= */ Format.NO_VALUE);
        float frameRate =
                mediaFormat.getFloat(
                        MediaFormat.KEY_FRAME_RATE, /* defaultValue= */ Format.NO_VALUE);
        int rotationDegrees =
                mediaFormat.getInteger(
                        MediaFormat.KEY_ROTATION, /* defaultValue= */ Format.NO_VALUE);
        ArrayList<byte[]> initData = null;
        if (mediaFormat.containsKey("csd-0")) {
            initData = new ArrayList<>();
            int index = 0;
            while (mediaFormat.containsKey("csd-" + index)) {
                initData.add(mediaFormat.getByteBuffer("csd-" + index++).array());
            }
        }
        float pixelAspectWidth =
                (float)
                        mediaFormat.getInteger(
                                MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH, /* defaultValue= */ 0);
        float pixelAspectHeight =
                (float)
                        mediaFormat.getInteger(
                                MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT, /* defaultValue= */ 0);
        float pixelAspectRatio =
                pixelAspectHeight == 0 || pixelAspectWidth == 0
                        ? Format.NO_VALUE
                        : pixelAspectWidth / pixelAspectHeight;
        ColorInfo colorInfo = getExoPlayerColorInfo(mediaFormat);
        DrmInitData drmInitData = getExoPlayerDrmInitData(trackData.drmInitData);

        int selectionFlags =
                mediaFormat.getInteger(MediaFormat.KEY_IS_AUTOSELECT, /* defaultValue= */ 0) != 0
                        ? C.SELECTION_FLAG_AUTOSELECT
                        : 0;
        selectionFlags |=
                mediaFormat.getInteger(MediaFormat.KEY_IS_FORCED_SUBTITLE, /* defaultValue= */ 0)
                                != 0
                        ? C.SELECTION_FLAG_FORCED
                        : 0;
        selectionFlags |=
                mediaFormat.getInteger(MediaFormat.KEY_IS_DEFAULT, /* defaultValue= */ 0) != 0
                        ? C.SELECTION_FLAG_DEFAULT
                        : 0;

        String language = mediaFormat.getString(MediaFormat.KEY_LANGUAGE, /* defaultValue= */ null);

        // TODO: Replace this with Format.Builder once available.
        if (MimeTypes.isVideo(sampleMimeType)) {
            return Format.createVideoSampleFormat(
                    id,
                    sampleMimeType,
                    codecs,
                    bitrate,
                    maxInputSize,
                    width,
                    height,
                    frameRate,
                    initData,
                    rotationDegrees,
                    pixelAspectRatio,
                    /* projectionData= */ null,
                    /* stereoMode= */ Format.NO_VALUE,
                    colorInfo,
                    drmInitData);
        } else if (MimeTypes.isAudio(sampleMimeType)) {
            int channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            return Format.createAudioContainerFormat(
                    id,
                    /* label= */ null,
                    /* containerMimeType= */ null,
                    sampleMimeType,
                    codecs,
                    /* metadata= */ null,
                    bitrate,
                    channels,
                    sampleRate,
                    initData,
                    selectionFlags,
                    /* roleFlags= */ 0,
                    language);
        } else { // Application or Text.
            return Format.createTextSampleFormat(
                    id,
                    sampleMimeType,
                    codecs,
                    bitrate,
                    selectionFlags,
                    language,
                    /* accessibilityChannel= */ 0, // TODO: Add once ag/9864463 is submitted.
                    /* drmInitData= */ drmInitData,
                    /* subsampleOffsetUs= */ Format.OFFSET_SAMPLE_RELATIVE,
                    initData);
        }
    }

    private static DrmInitData getExoPlayerDrmInitData(android.media.DrmInitData drmInitData) {
        // TODO: Implement once ag/10253368 is resolved.
        return null;
    }

    private static ColorInfo getExoPlayerColorInfo(MediaFormat mediaFormat) {
        int colorSpace = Format.NO_VALUE;
        if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
            switch (mediaFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)) {
                case MediaFormat.COLOR_STANDARD_BT601_NTSC:
                case MediaFormat.COLOR_STANDARD_BT601_PAL:
                    colorSpace = C.COLOR_SPACE_BT601;
                    break;
                case MediaFormat.COLOR_STANDARD_BT709:
                    colorSpace = C.COLOR_SPACE_BT709;
                    break;
                case MediaFormat.COLOR_STANDARD_BT2020:
                    colorSpace = C.COLOR_SPACE_BT2020;
                    break;
                default:
                    colorSpace = Format.NO_VALUE;
            }
        }

        int colorRange = Format.NO_VALUE;
        if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
            switch (mediaFormat.getInteger(MediaFormat.KEY_COLOR_RANGE)) {
                case MediaFormat.COLOR_RANGE_FULL:
                    colorRange = C.COLOR_RANGE_FULL;
                    break;
                case MediaFormat.COLOR_RANGE_LIMITED:
                    colorRange = C.COLOR_RANGE_LIMITED;
                    break;
                default:
                    colorRange = Format.NO_VALUE;
            }
        }

        int colorTransfer = Format.NO_VALUE;
        if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
            switch (mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER)) {
                case MediaFormat.COLOR_TRANSFER_HLG:
                    colorTransfer = C.COLOR_TRANSFER_HLG;
                    break;
                case MediaFormat.COLOR_TRANSFER_SDR_VIDEO:
                    colorTransfer = C.COLOR_TRANSFER_SDR;
                    break;
                case MediaFormat.COLOR_TRANSFER_ST2084:
                    colorTransfer = C.COLOR_TRANSFER_ST2084;
                    break;
                case MediaFormat.COLOR_TRANSFER_LINEAR:
                    // Fall through, there's no mapping.
                default:
                    colorTransfer = Format.NO_VALUE;
            }
        }
        boolean hasHdrInfo = mediaFormat.containsKey(MediaFormat.KEY_HDR_STATIC_INFO);
        if (colorSpace == Format.NO_VALUE
                && colorRange == Format.NO_VALUE
                && colorTransfer == Format.NO_VALUE
                && !hasHdrInfo) {
            return null;
        } else {
            return new ColorInfo(
                    colorSpace,
                    colorRange,
                    colorTransfer,
                    hasHdrInfo
                            ? mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO).array()
                            : null);
        }
    }

    // Internal classes.

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
