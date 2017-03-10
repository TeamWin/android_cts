/*
 * Copyright 2017 The Android Open Source Project
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

import android.media.cts.R;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.VolumeShaper;
import android.os.Parcel;
import android.os.PowerManager;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.util.Arrays;

public class VolumeShaperTest extends CtsAndroidTestCase {
    private static final String TAG = "VolumeShaperTest";
    private static final long RAMP_TIME_MS = 3000;
    private static final float TOLERANCE = 0.0000001f;

    private static final VolumeShaper.Configuration SILENCE =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                        new float[] { 0.f, 0.f } /* volumes */)
                .setDurationMs((double)RAMP_TIME_MS)
                .build();

    // Duck configurations go from 1.f down to 0.2f (not full ramp down).
    private static final VolumeShaper.Configuration LINEAR_DUCK =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                        new float[] { 1.f, 0.2f } /* volumes */)
                .setDurationMs((double)RAMP_TIME_MS)
                .build();

    // Ramp configurations go from 0.f up to 1.f
    private static final VolumeShaper.Configuration LINEAR_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.LINEAR_RAMP)
                .setDurationMs((double)RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration CUBIC_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.CUBIC_RAMP)
                .setDurationMs((double)RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration SINE_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.SINE_RAMP)
                .setDurationMs((double)RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration SCURVE_RAMP =
            new VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.SCURVE_RAMP)
            .setDurationMs((double)RAMP_TIME_MS)
            .build();

    // internal use only
    private static final VolumeShaper.Configuration LOG_RAMP =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                .setOptionFlags(VolumeShaper.Configuration.OPTION_FLAG_VOLUME_IN_DBFS)
                .setCurve(new float[] { 0.f, 1.f } /* times */,
                        new float[] { -80.f, 0.f } /* volumes */)
                .setDurationMs((double)RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration[] ALL_STANDARD_RAMPS = {
        LINEAR_RAMP,
        CUBIC_RAMP,
        SINE_RAMP,
        SCURVE_RAMP,
    };

    // this ramp should result in non-monotonic behavior with a typical cubic spline.
    private static final VolumeShaper.Configuration MONOTONIC_TEST =
            new VolumeShaper.Configuration.Builder()
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC_MONOTONIC)
                .setCurve(new float[] { 0.f, 0.3f, 0.7f, 1.f } /* times */,
                        new float[] { 0.f, 0.5f, 0.5f, 1.f } /* volumes */)
                .setDurationMs((double)RAMP_TIME_MS)
                .build();

    private static final VolumeShaper.Configuration MONOTONIC_TEST_FAIL =
            new VolumeShaper.Configuration.Builder(MONOTONIC_TEST)
                .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                .build();

    private static final VolumeShaper.Operation[] ALL_STANDARD_OPERATIONS = {
        VolumeShaper.Operation.PLAY,
        VolumeShaper.Operation.REVERSE,
    };

    private boolean hasAudioOutput() {
        return getContext().getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    private boolean isLowRamDevice() {
        return ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
                .isLowRamDevice();
    }

    private static AudioTrack createSineAudioTrack() {
        final int TEST_FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
        final int TEST_MODE = AudioTrack.MODE_STATIC;
        final int TEST_SR = 48000;
        final AudioFormat format = new AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(TEST_FORMAT)
                .setSampleRate(TEST_SR)
                .build();

        final int frameCount = AudioHelper.frameCountFromMsec(100 /*ms*/, format);
        final int frameSize = AudioHelper.frameSizeFromFormat(format);

        final AudioTrack audioTrack = new AudioTrack.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(frameCount * frameSize)
            .setTransferMode(TEST_MODE)
            .build();
        // create float array and write it
        final int sampleCount = frameCount * format.getChannelCount();
        final float[] vaf = AudioHelper.createSoundDataInFloatArray(
                sampleCount, TEST_SR,
                600 * format.getChannelCount() /* frequency */, 0 /* sweep */);
        assertEquals(vaf.length, audioTrack.write(vaf, 0 /* offsetInFloats */, vaf.length,
                AudioTrack.WRITE_NON_BLOCKING));
        audioTrack.setLoopPoints(0, frameCount, -1 /* loopCount */);
        return audioTrack;
    }

    private MediaPlayer createMediaPlayer(boolean offloaded) {
        // MP3 resource should be greater than 1m to introduce offloading
        final int RESOURCE_ID = R.raw.test1m1s;

        final MediaPlayer mediaPlayer = MediaPlayer.create(getContext(), RESOURCE_ID);
        mediaPlayer.setWakeMode(getContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setLooping(true);
        if (!offloaded) {
            final float PLAYBACK_SPEED = 1.1f; // force PCM mode
            mediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(PLAYBACK_SPEED));
        }
        return mediaPlayer;
    }

    private static void checkEqual(String testName,
            VolumeShaper.Configuration expected, VolumeShaper.Configuration actual) {
        assertEquals(testName + " configuration should be equal",
                expected, actual);
        assertEquals(testName + " configuration.hashCode() should be equal",
                expected.hashCode(), actual.hashCode());
        assertEquals(testName + " configuration.toString() should be equal",
                expected.toString(), actual.toString());
    }

    private static void checkNotEqual(String testName,
            VolumeShaper.Configuration notEqual, VolumeShaper.Configuration actual) {
        assertTrue(testName + " configuration should not be equal",
                !actual.equals(notEqual));
        assertTrue(testName + " configuration.hashCode() should not be equal",
                actual.hashCode() != notEqual.hashCode());
        assertTrue(testName + " configuration.toString() should not be equal",
                !actual.toString().equals(notEqual.toString()));
    }

    private static void testBuildRamp(int points) {
        float[] ramp = new float[points];
        final float fscale = 1.f / (points - 1);
        for (int i = 0; i < points; ++i) {
            ramp[i] = i * fscale;
        }
        ramp[points - 1] = 1.f;
        // does it build?
        final VolumeShaper.Configuration config = new VolumeShaper.Configuration.Builder()
                .setCurve(ramp, ramp)
                .build();
    }

    public void testVolumeShaperConfigurationBuilder() throws Exception {
        final String TEST_NAME = "testVolumeShaperConfigurationBuilder";

        // Verify that IllegalStateExceptions are properly triggered
        // for methods with no arguments.
        try {
            final VolumeShaper.Configuration config =
                    new VolumeShaper.Configuration.Builder().build();
            fail(TEST_NAME + " configuration builder should fail if no curve is specified");
        } catch (IllegalStateException e) {
            ; // expected
        }

        try {
            final VolumeShaper.Configuration config =
                    new VolumeShaper.Configuration.Builder()
                    .invertVolumes()
                    .build();
            fail(TEST_NAME + " configuration builder should fail if no curve is specified");
        } catch (IllegalStateException e) {
            ; // expected
        }

        try {
            final VolumeShaper.Configuration config =
                    new VolumeShaper.Configuration.Builder()
                    .reflectTimes()
                    .build();
            fail(TEST_NAME + " configuration builder should fail if no curve is specified");
        } catch (IllegalStateException e) {
            ; // expected
        }

        // Verify IllegalArgumentExceptions are properly triggered
        // for methods with arguments.
        final float[] ohOne = { 0.f, 1.f };
        final float[][] invalidCurves = {
                { -1.f, 1.f },
                { 0.5f },
                { 0.f, 2.f },
        };
        for (float[] invalidCurve : invalidCurves) {
            try {
                final VolumeShaper.Configuration config =
                        new VolumeShaper.Configuration.Builder()
                        .setCurve(invalidCurve, ohOne)
                        .build();
                fail(TEST_NAME + " configuration builder should fail on invalid curve");
            } catch (IllegalArgumentException e) {
                ; // expected
            }
            try {
                final VolumeShaper.Configuration config =
                        new VolumeShaper.Configuration.Builder()
                        .setCurve(ohOne, invalidCurve)
                        .build();
                fail(TEST_NAME + " configuration builder should fail on invalid curve");
            } catch (IllegalArgumentException e) {
                ; // expected
            }
        }

        try {
            final VolumeShaper.Configuration config =
                    new VolumeShaper.Configuration.Builder()
                    .setCurve(ohOne, ohOne)
                    .setDurationMs(-1.)
                    .build();
            fail(TEST_NAME + " configuration builder should fail on invalid duration");
        } catch (IllegalArgumentException e) {
            ; // expected
        }

        try {
            final VolumeShaper.Configuration config =
                    new VolumeShaper.Configuration.Builder()
                    .setCurve(ohOne, ohOne)
                    .setInterpolatorType(-1)
                    .build();
            fail(TEST_NAME + " configuration builder should fail on invalid interpolator type");
        } catch (IllegalArgumentException e) {
            ; // expected
        }

        // Verify defaults.
        // Use the Builder with setCurve(ohOne, ohOne).
        final VolumeShaper.Configuration config =
                new VolumeShaper.Configuration.Builder().setCurve(ohOne, ohOne).build();
        assertEquals(TEST_NAME + " default interpolation should be cubic",
                VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC, config.getInterpolatorType());
        assertEquals(TEST_NAME + " default duration should be 1000 ms",
                1000., config.getDurationMs());
        assertTrue(TEST_NAME + " times should be { 0.f, 1.f }",
                Arrays.equals(ohOne, config.getTimes()));
        assertTrue(TEST_NAME + " volumes should be { 0.f, 1.f }",
                Arrays.equals(ohOne, config.getVolumes()));

        // Due to precision problems, we cannot have ramps that do not have
        // perfect binary representation for equality comparison.
        // (For example, 0.1 is a repeating mantissa in binary,
        //  but 0.25, 0.5 can be expressed with few mantissa bits).
        final float[] binaryCurve1 = { 0.f, 0.25f, 0.5f, 0.625f,  1.f };
        final float[] binaryCurve2 = { 0.f, 0.125f, 0.375f, 0.75f, 1.f };
        final VolumeShaper.Configuration[] BINARY_RAMPS = {
            LINEAR_RAMP,
            CUBIC_RAMP,
            new VolumeShaper.Configuration.Builder()
                    .setCurve(binaryCurve1, binaryCurve2)
                    .build(),
        };

        // Verify volume inversion and time reflection work as expected
        // with ramps (which start at { 0.f, 0.f } and end at { 1.f, 1.f }).
        for (VolumeShaper.Configuration testRamp : BINARY_RAMPS) {
            VolumeShaper.Configuration ramp;
            ramp = new VolumeShaper.Configuration.Builder(testRamp).build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .setDurationMs(10)
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp).build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .invertVolumes()
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .invertVolumes()
                    .invertVolumes()
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .reflectTimes()
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .reflectTimes()
                    .reflectTimes()
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);

            // check scaling start and end volumes
            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .scaleToStartVolume(0.5f)
                    .build();
            checkNotEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .scaleToStartVolume(0.5f)
                    .scaleToStartVolume(0.f)
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);

            ramp = new VolumeShaper.Configuration.Builder(testRamp)
                    .scaleToStartVolume(0.5f)
                    .scaleToEndVolume(0.f)
                    .scaleToStartVolume(1.f)
                    .invertVolumes()
                    .build();
            checkEqual(TEST_NAME, testRamp, ramp);
        }

        // check that getMaximumCurvePoints() returns the correct value
        final int maxPoints = VolumeShaper.Configuration.getMaximumCurvePoints();

        testBuildRamp(maxPoints); // no exceptions here.

        if (maxPoints < Integer.MAX_VALUE) {
            try {
                testBuildRamp(maxPoints + 1);
                fail(TEST_NAME + " configuration builder "
                        + "should fail if getMaximumCurvePoints() exceeded");
            } catch (IllegalArgumentException e) {
                ; // expected exception
            }
        }

    } // testVolumeShaperConfigurationBuilder

    public void testVolumeShaperConfigurationParcelable() throws Exception {
        final String TEST_NAME = "testVolumeShaperConfigurationParcelable";

        for (VolumeShaper.Configuration config : ALL_STANDARD_RAMPS) {
            assertEquals(TEST_NAME + " no parceled file descriptors",
                    0 /* expected */, config.describeContents());

            final Parcel srcParcel = Parcel.obtain();
            config.writeToParcel(srcParcel, 0 /* flags */);

            final byte[] marshallBuffer = srcParcel.marshall();

            final Parcel dstParcel = Parcel.obtain();
            dstParcel.unmarshall(marshallBuffer, 0 /* offset */, marshallBuffer.length);
            dstParcel.setDataPosition(0);

            final VolumeShaper.Configuration restoredConfig =
                    VolumeShaper.Configuration.CREATOR.createFromParcel(dstParcel);
            assertEquals(TEST_NAME +
                    " marshalled/restored VolumeShaper.Configuration should match",
                    config, restoredConfig);
        }
    } // testVolumeShaperConfigurationParcelable

    public void testVolumeShaperOperationParcelable() throws Exception {
        final String TEST_NAME = "testVolumeShaperOperationParcelable";

        for (VolumeShaper.Operation operation : ALL_STANDARD_OPERATIONS) {
            assertEquals(TEST_NAME + " no parceled file descriptors",
                    0 /* expected */, operation.describeContents());

            final Parcel srcParcel = Parcel.obtain();
            operation.writeToParcel(srcParcel, 0 /* flags */);

            final byte[] marshallBuffer = srcParcel.marshall();

            final Parcel dstParcel = Parcel.obtain();
            dstParcel.unmarshall(marshallBuffer, 0 /* offset */, marshallBuffer.length);
            dstParcel.setDataPosition(0);

            final VolumeShaper.Operation restoredOperation =
                    VolumeShaper.Operation.CREATOR.createFromParcel(dstParcel);
            assertEquals(TEST_NAME +
                    " marshalled/restored VolumeShaper.Operation should match",
                    operation, restoredOperation);
        }
    } // testVolumeShaperOperationParcelable

    public void testAudioTrackDuck() throws Exception {
        final String TEST_NAME = "testAudioTrackDuck";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final VolumeShaper.Configuration[] configs = new VolumeShaper.Configuration[] {
                LINEAR_DUCK,
        };

        AudioTrack audioTrack = null;
        try {
            audioTrack = createSineAudioTrack();
            audioTrack.play();
            Thread.sleep(300 /* millis */); // warm up track

            for (VolumeShaper.Configuration config : configs) {
                try (VolumeShaper volumeShaper = audioTrack.createVolumeShaper(config)) {
                    assertEquals(TEST_NAME + " volume should be 1.f",
                            1.f, volumeShaper.getVolume(), TOLERANCE);

                    Log.d(TAG, TEST_NAME + " Duck");
                    volumeShaper.apply(VolumeShaper.Operation.PLAY);
                    Thread.sleep(RAMP_TIME_MS * 2);

                    assertEquals(TEST_NAME + " volume should be 0.2f",
                            0.2f, volumeShaper.getVolume(), TOLERANCE);

                    Log.d(TAG, TEST_NAME + " Unduck");
                    volumeShaper.apply(VolumeShaper.Operation.REVERSE);
                    Thread.sleep(RAMP_TIME_MS * 2);

                    assertEquals(TEST_NAME + " volume should be 1.f",
                            1.f, volumeShaper.getVolume(), TOLERANCE);
                }
            }
        } finally {
            if (audioTrack != null) {
                audioTrack.release();
            }
        }
    } // testAudioTrackDuck

    public void testAudioTrackRamp() throws Exception {
        final String TEST_NAME = "testAudioTrackRamp";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        AudioTrack audioTrack = null;
        VolumeShaper volumeShaper = null;
        try {
            audioTrack = createSineAudioTrack();
            volumeShaper = audioTrack.createVolumeShaper(SILENCE);
            volumeShaper.apply(VolumeShaper.Operation.PLAY);
            audioTrack.play();
            Thread.sleep(300 /* millis */); // warm up track

            for (VolumeShaper.Configuration config : ALL_STANDARD_RAMPS) {
                Log.d(TAG, TEST_NAME + " Play");
                volumeShaper.replace(config, VolumeShaper.Operation.PLAY, false /* join */);
                Thread.sleep(RAMP_TIME_MS / 2);

                // Reverse the direction of the volume shaper curve
                Log.d(TAG, TEST_NAME + " Reverse");
                volumeShaper.apply(VolumeShaper.Operation.REVERSE);
                Thread.sleep(RAMP_TIME_MS / 2 + 1000);

                Log.d(TAG, TEST_NAME + " Check Volume");
                assertEquals(TEST_NAME + " volume should be 0.f",
                        0.f, volumeShaper.getVolume(), TOLERANCE);

                // Forwards
                Log.d(TAG, TEST_NAME + " Play (2)");
                volumeShaper.apply(VolumeShaper.Operation.PLAY);
                Thread.sleep(RAMP_TIME_MS + 1000);

                Log.d(TAG, TEST_NAME + " Check Volume (2)");
                assertEquals(TEST_NAME + " volume should be 1.f",
                        1.f, volumeShaper.getVolume(), TOLERANCE);

                // Reverse
                Log.d(TAG, TEST_NAME + " Reverse (2)");
                volumeShaper.apply(VolumeShaper.Operation.REVERSE);
                Thread.sleep(RAMP_TIME_MS + 1000);

                Log.d(TAG, TEST_NAME + " Check Volume (3)");
                assertEquals(TEST_NAME + " volume should be 0.f",
                        0.f, volumeShaper.getVolume(), TOLERANCE);
                Log.d(TAG, TEST_NAME + " done");
            }
        } finally {
            if (volumeShaper != null) {
                volumeShaper.close();
            }
            if (audioTrack != null) {
                audioTrack.release();
            }
        }
    } // testAudioTrackRamp

    public void testAudioTrackJoin() throws Exception {
        final String TEST_NAME = "testAudioTrackJoin";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        AudioTrack audioTrack = null;
        VolumeShaper volumeShaper = null;
        try {
            audioTrack = createSineAudioTrack();
            volumeShaper = audioTrack.createVolumeShaper(SILENCE);
            volumeShaper.apply(VolumeShaper.Operation.PLAY);
            audioTrack.play();
            Thread.sleep(300 /* millis */); // warm up track

            final long duration = 10000;
            final long increment = 1000;
            for (long i = 0; i < duration; i += increment) {
                Log.d(TAG, TEST_NAME + " Play - join " + i);
                // we join several LINEAR_RAMPS together - this should effectively
                // be one long LINEAR_RAMP.
                volumeShaper.replace(new VolumeShaper.Configuration.Builder(LINEAR_RAMP)
                                        .setDurationMs((double)(duration - i))
                                        .build(),
                                VolumeShaper.Operation.PLAY, true /* join */);
                assertEquals(TEST_NAME + " linear ramp should continue on join",
                        (float)i / duration, volumeShaper.getVolume(), 0.01 /* epsilon */);
                Thread.sleep(increment);
            }
        } finally {
            if (volumeShaper != null) {
                volumeShaper.close();
            }
            if (audioTrack != null) {
                audioTrack.release();
            }
        }
    } // testAudioTrackJoin

    public void testAudioTrackCubicMonotonic() throws Exception {
        final String TEST_NAME = "testAudioTrackCubic";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final VolumeShaper.Configuration configurations[] =
                new VolumeShaper.Configuration[] {
                MONOTONIC_TEST,
                CUBIC_RAMP,
                SCURVE_RAMP,
                SINE_RAMP,
        };

        AudioTrack audioTrack = null;
        VolumeShaper volumeShaper = null;
        try {
            audioTrack = createSineAudioTrack();
            volumeShaper = audioTrack.createVolumeShaper(SILENCE);
            volumeShaper.apply(VolumeShaper.Operation.PLAY);
            audioTrack.play();
            Thread.sleep(300 /* millis */); // warm up track

            for (VolumeShaper.Configuration configuration : configurations) {
                // test configurations known monotonic
                Log.d(TAG, TEST_NAME + " starting test");

                float lastVolume = 0;
                final long incrementMs = 100;

                volumeShaper.replace(configuration,
                        VolumeShaper.Operation.PLAY, true /* join */);
                // monotonicity test
                for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                    final float volume = volumeShaper.getVolume();
                    assertTrue(TEST_NAME + " montonic volume should increase "
                            + volume + " >= " + lastVolume,
                            (volume >= lastVolume));
                    lastVolume = volume;
                    Thread.sleep(incrementMs);
                }
                Thread.sleep(300 /* millis */);
                lastVolume = volumeShaper.getVolume();
                assertEquals(TEST_NAME
                        + " final monotonic value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, TOLERANCE);

                Log.d(TAG, "invert");
                // invert
                VolumeShaper.Configuration newConfiguration =
                        new VolumeShaper.Configuration.Builder(configuration)
                            .invertVolumes()
                            .build();
                volumeShaper.replace(newConfiguration,
                        VolumeShaper.Operation.PLAY, true /* join */);
                // monotonicity test
                for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                    final float volume = volumeShaper.getVolume();
                    assertTrue(TEST_NAME + " montonic volume should decrease "
                            + volume + " <= " + lastVolume,
                            (volume <= lastVolume));
                    lastVolume = volume;
                    Thread.sleep(incrementMs);
                }
                Thread.sleep(300 /* millis */);
                lastVolume = volumeShaper.getVolume();
                assertEquals(TEST_NAME
                        + " final monotonic value should be 0.f, but is " + lastVolume,
                        0.f, lastVolume, TOLERANCE);

                // invert + reflect
                Log.d(TAG, "invert and reflect");
                newConfiguration =
                        new VolumeShaper.Configuration.Builder(configuration)
                            .invertVolumes()
                            .reflectTimes()
                            .build();
                volumeShaper.replace(newConfiguration,
                        VolumeShaper.Operation.PLAY, true /* join */);
                // monotonicity test
                for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                    final float volume = volumeShaper.getVolume();
                    assertTrue(TEST_NAME + " montonic volume should increase "
                            + volume + " >= " + lastVolume,
                            (volume >= lastVolume));
                    lastVolume = volume;
                    Thread.sleep(incrementMs);
                }
                Thread.sleep(300 /* millis */);
                lastVolume = volumeShaper.getVolume();
                assertEquals(TEST_NAME
                        + " final monotonic value should be 1.f, but is " + lastVolume,
                        1.f, lastVolume, TOLERANCE);

                // reflect
                Log.d(TAG, "reflect");
                newConfiguration =
                        new VolumeShaper.Configuration.Builder(configuration)
                            .reflectTimes()
                            .build();
                volumeShaper.replace(newConfiguration,
                        VolumeShaper.Operation.PLAY, true /* join */);
                // monotonicity test
                for (long i = 0; i < RAMP_TIME_MS; i += incrementMs) {
                    final float volume = volumeShaper.getVolume();
                    assertTrue(TEST_NAME + " montonic volume should decrease "
                            + volume + " <= " + lastVolume,
                            (volume <= lastVolume));
                    lastVolume = volume;
                    Thread.sleep(incrementMs);
                }
                Thread.sleep(300 /* millis */);
                lastVolume = volumeShaper.getVolume();
                assertEquals(TEST_NAME
                        + " final monotonic value should be 0.f, but is " + lastVolume,
                        0.f, lastVolume, TOLERANCE);
            }
        } finally {
            if (volumeShaper != null) {
                volumeShaper.close();
            }
            if (audioTrack != null) {
                audioTrack.release();
            }
        }
    } // testAudioTrackCubic

    public void testMediaPlayerDuck() throws Exception {
        final String TEST_NAME = "testMediaPlayerDuck";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        final VolumeShaper.Configuration[] configs = new VolumeShaper.Configuration[] {
                LINEAR_DUCK,
        };

        for (int i = 0; i < 2; ++i) {
            final boolean offloaded = i != 0;
            MediaPlayer mediaPlayer = null;
            try {
                mediaPlayer = createMediaPlayer(offloaded);
                mediaPlayer.start();

                Thread.sleep(300 /* millis */); // warm up player

                for (VolumeShaper.Configuration config : configs) {
                    try (VolumeShaper volumeShaper = mediaPlayer.createVolumeShaper(config)) {
                        assertEquals(TEST_NAME + " volume should be 1.f",
                                1.f, volumeShaper.getVolume(), TOLERANCE);

                        Log.d(TAG, TEST_NAME + " Duck");
                        volumeShaper.apply(VolumeShaper.Operation.PLAY);
                        Thread.sleep(RAMP_TIME_MS * 2);

                        assertEquals(TEST_NAME + " volume should be 0.2f",
                                0.2f, volumeShaper.getVolume(), TOLERANCE);

                        Log.d(TAG, TEST_NAME + " Unduck");
                        volumeShaper.apply(VolumeShaper.Operation.REVERSE);
                        Thread.sleep(RAMP_TIME_MS * 2);

                        assertEquals(TEST_NAME + " volume should be 1.f",
                                1.f, volumeShaper.getVolume(), TOLERANCE);
                    }
                }
            } finally {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                }
            }
        }
    } // testMediaPlayerDuck

    public void testMediaPlayerRamp() throws Exception {
        final String TEST_NAME = "testMediaPlayerRamp";
        if (!hasAudioOutput()) {
            Log.w(TAG, "AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL");
            return;
        }

        for (int i = 0; i < 2; ++i) {
            final boolean offloaded = i != 0;
            VolumeShaper volumeShaper = null;
            MediaPlayer mediaPlayer = null;
            try {
                mediaPlayer = createMediaPlayer(offloaded);
                volumeShaper = mediaPlayer.createVolumeShaper(SILENCE);
                mediaPlayer.start();

                Thread.sleep(300 /* millis */); // warm up player

                for (VolumeShaper.Configuration config : ALL_STANDARD_RAMPS) {
                    Log.d(TAG, TEST_NAME + " Play");
                    volumeShaper.replace(config, VolumeShaper.Operation.PLAY, false /* join */);
                    Thread.sleep(RAMP_TIME_MS / 2);

                    // Reverse the direction of the volume shaper curve
                    Log.d(TAG, TEST_NAME + " Reverse");
                    volumeShaper.apply(VolumeShaper.Operation.REVERSE);
                    Thread.sleep(RAMP_TIME_MS / 2 + 1000);

                    Log.d(TAG, TEST_NAME + " Check Volume");
                    assertEquals(TEST_NAME + " volume should be 0.f",
                            0.f, volumeShaper.getVolume(), TOLERANCE);

                    // Forwards
                    Log.d(TAG, TEST_NAME + " Play (2)");
                    volumeShaper.apply(VolumeShaper.Operation.PLAY);
                    Thread.sleep(RAMP_TIME_MS + 1000);

                    Log.d(TAG, TEST_NAME + " Check Volume (2)");
                    assertEquals(TEST_NAME + " volume should be 1.f",
                            1.f, volumeShaper.getVolume(), TOLERANCE);

                    // Reverse
                    Log.d(TAG, TEST_NAME + " Reverse (2)");
                    volumeShaper.apply(VolumeShaper.Operation.REVERSE);
                    Thread.sleep(RAMP_TIME_MS + 1000);

                    Log.d(TAG, TEST_NAME + " Check Volume (3)");
                    assertEquals(TEST_NAME + " volume should be 0.f",
                            0.f, volumeShaper.getVolume(), TOLERANCE);
                    Log.d(TAG, TEST_NAME + " done");
                }
            } finally {
                if (volumeShaper != null) {
                    volumeShaper.close();
                }
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                }
            }
        }
    } // testMediaPlayerRamp
}
