package android.media.decoder.cts;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaStubActivity;
import android.media.cts.Preconditions;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.screenshot.ScreenCapture;
import androidx.test.runner.screenshot.Screenshot;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.Test;
import org.junit.Assert;
import org.junit.Assume;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

/**
 * Class that contains tests for the "Push blank buffers on stop" decoder feature.
 * <br>
 * In order to detect that a blank buffer has been pushed to the {@code Surface}that the codec works
 * on, we take a fullscreen screenshot before and after the call to {@code MediaCodec#stop}. This
 * workaround appears necessary at the time of writing because the usual APIs to extract the content
 * of a native {@code Surface} (such as {@code PixelCopy} or {@code ImageReader}) appear to fail for
 * this frame specifically.
 * <br>
 * This test class is inspired from the {@link DecoderTest} test class, but with specific setup code
 * to ensure the activity is launched in immersive mode and its title is removed.
 */
@MediaHeavyPresubmitTest
public class DecoderPushBlankBuffersOnStopTest {
    private static final String TAG = "DecoderPushBlankBufferOnStopTest";
    private static final String mInpPrefix = WorkDir.getMediaDirString();

    /**
     * Retrieve a file descriptor to a test resource from its file name.
     * @param res  Name from a resource in the media assets
     */
    private static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        final String mediaDirPath = WorkDir.getMediaDirString();
        File mediaFile = new File(mediaDirPath + res);
        Preconditions.assertTestFileExists(mediaDirPath + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(mediaFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    private static boolean isUniformlyBlank(Bitmap bitmap) {
        final var color = new Color(); // Defaults to opaque black in sRGB
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        // Check a subset of pixels against the first pixel of the image.
        // This is not strictly sufficient, but probably good enough and much more efficient.
        for (int y = 0; y < height; y+=4) {
            for (int x = 0; x < width; x+=4) {
                if (color.toArgb() != bitmap.getColor(x, y).toArgb()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void testPushBlankBuffersOnStop(String testVideo) throws Exception {
        // Configure the test activity to hide its title
        final var noTitle = new Intent(ApplicationProvider.getApplicationContext(),
                MediaStubActivity.class);
        noTitle.putExtra(MediaStubActivity.INTENT_EXTRA_NO_TITLE, true);
        try(ActivityScenario<MediaStubActivity> scenario = ActivityScenario.launch(noTitle)) {
            final var surface = new AtomicReference<Surface>();
            scenario.onActivity(activity -> {
                        surface.set(activity.getSurfaceHolder().getSurface());
                    });

            // Setup media extraction
            final AssetFileDescriptor fd = getAssetFileDescriptorFor(testVideo);
            final var extractor = new MediaExtractor();
            extractor.setDataSource(fd);
            fd.close();
            MediaFormat format = null;
            int trackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    trackIndex = i;
                    break;
                }
            }
            Assert.assertTrue("No video track was found", trackIndex >= 0);
            extractor.selectTrack(trackIndex);
            // Enable PUSH_BLANK_BUFFERS_ON_STOP
            format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);

            // Setup video codec
            final var mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            final String decoderName = mcl.findDecoderForFormat(format);
            Assume.assumeNotNull(String.format("No decoder for %s", format), format);
            final MediaCodec decoder = MediaCodec.createByCodecName(decoderName);
            // Boolean set from the decoding thread to signal that a frame has been decoded
            final var displayedFrame = new AtomicBoolean(false);
            // Lock used for thread synchronization
            final Lock lock = new ReentrantLock();
            // Condition that signals the decoding thread has made enough progress
            final Condition processingDone = lock.newCondition();
            final var cb = new MediaCodec.Callback() {
                    /** Queue input buffers until one buffer has been decoded. */
                    @Override
                    public void onInputBufferAvailable(MediaCodec codec, int index) {
                        lock.lock();
                        try {
                            // Stop queuing frames once a frame has been displayed
                            if (displayedFrame.get()) {
                                return;
                            }
                        } finally {
                            lock.unlock();
                        }

                        ByteBuffer inputBuffer = codec.getInputBuffer(index);
                        int sampleSize = extractor.readSampleData(inputBuffer,
                                0 /* offset */);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(index, 0 /* offset */, 0 /* sampleSize */,
                                    0 /* presentationTimeUs */,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            return;
                        }
                        final long presentationTimeMs = System.currentTimeMillis();
                        codec.queueInputBuffer(index, 0 /* offset */, sampleSize,
                                presentationTimeMs * 1000, 0 /* flags */);
                        extractor.advance();
                    }

                    /** Render the output buffer and signal that the processing is done. */
                    @Override
                    public void onOutputBufferAvailable(MediaCodec codec, int index,
                            MediaCodec.BufferInfo info) {
                        lock.lock();
                        try {
                            // Stop dequeuing frames once a frame has been displayed
                            if (displayedFrame.get()) {
                                return;
                            }
                        } finally {
                            lock.unlock();
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            return;
                        }
                        codec.releaseOutputBuffer(index, true);
                    }

                    /**
                     * Check if the error is transient. If it is, ignore it, otherwise signal end of
                     * processing.
                     */
                    @Override
                    public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                        if (e.isTransient()) {
                            return;
                        }
                        lock.lock();
                        try {
                            processingDone.signal();
                        } finally {
                            lock.unlock();
                        }
                    }

                    /** Ignore format changed events. */
                    @Override
                    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) { }
                };
            final var onFrameRenderedListener = new MediaCodec.OnFrameRenderedListener() {
                    @Override
                    public void onFrameRendered(MediaCodec codec, long presentationTimeUs,
                            long nanoTime) {
                        lock.lock();
                        try {
                            displayedFrame.set(true);
                            processingDone.signal();
                        } finally {
                            lock.unlock();
                        }
                    }
                };
            decoder.setCallback(cb);
            decoder.setOnFrameRenderedListener(onFrameRenderedListener, null /* handler */);
            scenario.onActivity(activity -> activity.hideSystemBars());
            decoder.configure(format, surface.get(), null /* MediaCrypto */, 0 /* flags */);
            // Start playback
            decoder.start();
            final long startTime = System.currentTimeMillis();
            // Wait until the codec has decoded a frame, or a timeout.
            lock.lock();
            try {
                long startTimeMs = System.currentTimeMillis();
                long timeoutMs = 1000;
                while ((System.currentTimeMillis() < startTimeMs + timeoutMs) &&
                        !displayedFrame.get()) {
                    processingDone.await(timeoutMs, TimeUnit.MILLISECONDS);
                }
            } finally {
                lock.unlock();
            }
            Assert.assertTrue("Could not render any frame.", displayedFrame.get());
            final ScreenCapture captureBeforeStop = Screenshot.capture();
            Assert.assertFalse("Frame is blank before stop.", isUniformlyBlank(
                            captureBeforeStop.getBitmap()));
            decoder.stop();
            final ScreenCapture captureAfterStop = Screenshot.capture();
            Assert.assertTrue("Frame is not blank after stop.", isUniformlyBlank(
                            captureAfterStop.getBitmap()));
            decoder.release();
            extractor.release();
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testPushBlankBuffersOnStopVp9() throws Exception {
        testPushBlankBuffersOnStop(
                "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testPushBlankBuffersOnStopAvc() throws Exception {
        testPushBlankBuffersOnStop(
                "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }
}
