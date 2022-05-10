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

package android.media.audio.cts

import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioProfile
import android.media.AudioTrack
import android.media.cts.NonMediaMainlineTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@NonMediaMainlineTest
@RunWith(AndroidJUnit4::class)
class DirectAudioProfilesForAttributesTest {

    private lateinit var audioManager: AudioManager

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assumeTrue(
            context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        )

        audioManager = context.getSystemService(AudioManager::class.java)
    }

    /**
     * Test that only AudioProfiles returned in getDirectProfilesForAttributes can create direct
     * AudioTracks
     */
    @Test
    fun testCreateDirectAudioTracksOnlyForGetDirectProfilesForAttributes() {
        for (usage in AudioAttributes.getSdkUsages()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .build()
            val allProfilesForAttributes =
                audioManager.getAudioDevicesForAttributes(audioAttributes).map { it.audioProfiles }
                    .flatten()
            val directProfiles = audioManager.getDirectProfilesForAttributes(audioAttributes)
            val nonDirectProfiles = allProfilesForAttributes.subtractAll(directProfiles)

            // All compressed format (non pcm) profiles can create direct AudioTracks.
            // getDirectProfilesForAttributes does not include profiles supporting
            // compressed playback in offload mode, so it is expected that all creation
            // succeeds even if the audio track is not explicitly created in offload mode.
            val compressedProfiles = directProfiles.filterOutPcmFormats()
            for (directProfile in compressedProfiles) {
                checkCreateAudioTracks(audioAttributes, directProfile, true)
            }

            // Any other available but not returned compressed format profile
            // can't create any direct AudioTrack
            val otherCompressedProfiles = nonDirectProfiles.filterOutPcmFormats()
            for (nonDirectProfile in otherCompressedProfiles) {
                checkCreateAudioTracks(audioAttributes, nonDirectProfile, false)
            }
        }
    }

    // Returns true if all the AudioTracks with all combinations of parameters that can be derived
    // from the passed audio profile can be created with the expected result.
    // Doesn't start the tracks.
    private fun checkCreateAudioTracks(
        audioAttributes: AudioAttributes,
        audioProfile: AudioProfile,
        expectedCreationSuccess: Boolean
    ) {
        if (audioProfile.format == AudioFormat.ENCODING_INVALID) {
            fail("Found INVALID audio format in audio profile ($audioProfile) " +
                    "when trying to create audio tracks with it!")
        }
        for (audioFormat in audioProfile.getAllAudioFormats()) {
            try {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .build()
                    .release()
                // allow a short time to free the AudioTrack resources
                Thread.sleep(150)
                if (!expectedCreationSuccess) {
                    fail(
                        "Created AudioTrack for attributes ($audioAttributes) and " +
                                "audio format ($audioFormat)!"
                    )
                }
            } catch (e: Exception) {
                if (expectedCreationSuccess) {
                    fail(
                        "Failed to create AudioTrack for attributes ($audioAttributes) and " +
                                "audio format ($audioFormat) with exception ($e)!"
                    )
                }
            }
        }
    }

    // Utils
    private fun AudioProfile.isSame(profile: AudioProfile) =
        format == profile.format &&
                encapsulationType == profile.encapsulationType &&
                sampleRates.contentEquals(profile.sampleRates) &&
                channelMasks.contentEquals(profile.channelMasks) &&
                channelIndexMasks.contentEquals(profile.channelIndexMasks)

    private fun AudioProfile.getAllAudioFormats() =
        sampleRates.map { sampleRate ->
            channelMasks.map { channelMask ->
                AudioFormat.Builder()
                    .setEncoding(format)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            }.plus(
                channelIndexMasks.map { channelIndexMask ->
                    AudioFormat.Builder()
                        .setEncoding(format)
                        .setSampleRate(sampleRate)
                        .setChannelIndexMask(channelIndexMask)
                        .build()
                }
            )
        }.flatten()

    private fun List<AudioProfile>.subtractAll(elements: List<AudioProfile>) =
        filter { profile -> elements.none { it.isSame(profile) } }

    private fun List<AudioProfile>.includesAll(elements: List<AudioProfile>) =
        elements.all { profile -> this@includesAll.any { it.isSame(profile) } }

    private fun List<AudioProfile>.filterOutPcmFormats() = filter { it.format !in pcmFormats }

    companion object {
        private val pcmFormats = listOf(
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT
        )
    }
}
