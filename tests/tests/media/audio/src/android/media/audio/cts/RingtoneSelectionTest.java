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

package android.media.audio.cts;

import static android.media.RingtoneSelection.FROM_URI_RINGTONE_SELECTION_ONLY;
import static android.media.RingtoneSelection.FROM_URI_RINGTONE_SELECTION_OR_SOUND;
import static android.media.RingtoneSelection.FROM_URI_RINGTONE_SELECTION_OR_VIBRATION;
import static android.media.RingtoneSelection.SOUND_SOURCE_DEFAULT;
import static android.media.RingtoneSelection.SOUND_SOURCE_OFF;
import static android.media.RingtoneSelection.SOUND_SOURCE_UNKNOWN;
import static android.media.RingtoneSelection.SOUND_SOURCE_URI;
import static android.media.RingtoneSelection.VIBRATION_SOURCE_APPLICATION_PROVIDED;
import static android.media.RingtoneSelection.VIBRATION_SOURCE_AUDIO_CHANNEL;
import static android.media.RingtoneSelection.VIBRATION_SOURCE_DEFAULT;
import static android.media.RingtoneSelection.VIBRATION_SOURCE_HAPTIC_GENERATOR;
import static android.media.RingtoneSelection.VIBRATION_SOURCE_OFF;
import static android.media.RingtoneSelection.VIBRATION_SOURCE_UNKNOWN;
import static android.media.RingtoneSelection.VIBRATION_SOURCE_URI;

import static com.android.internal.util.Preconditions.checkArgument;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.media.RingtoneSelection;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;

@ApiTest(apis = {
        "android.media.RingtoneSelection.Builder#setSoundSource",
        "android.media.RingtoneSelection.Builder#setVibrationSource",
        "android.media.RingtoneSelection#getSoundSource",
        "android.media.RingtoneSelection#getSoundUri",
        "android.media.RingtoneSelection#getVibrationSource",
        "android.media.RingtoneSelection#getVibrationUri",
})
@RunWith(AndroidJUnit4.class)
public class RingtoneSelectionTest {
    private static final String URI_BASE = "content://media/ringtone";

    private static final Uri SOUND_URI = Uri.parse("content://fake-uri/sound");
    private static final Uri VIBRATION_URI = Uri.parse("content://fake-uri/vibration");

    private static final ImmutableMap<Integer, String> SOUND_SOURCE_NAMES =
            new ImmutableMap.Builder()
                .put(SOUND_SOURCE_UNKNOWN, "SOUND_SOURCE_UNKNOWN")
                .put(SOUND_SOURCE_DEFAULT, "SOUND_SOURCE_DEFAULT")
                .put(SOUND_SOURCE_OFF, "SOUND_SOURCE_OFF")
                .put(SOUND_SOURCE_URI, "SOUND_SOURCE_URI")
                .build();
    private static final ImmutableMap<Integer, String> VIBRATION_SOURCE_NAMES =
            new ImmutableMap.Builder()
                    .put(VIBRATION_SOURCE_UNKNOWN, "VIBRATION_SOURCE_UNKNOWN")
                    .put(VIBRATION_SOURCE_DEFAULT, "VIBRATION_SOURCE_DEFAULT")
                    .put(VIBRATION_SOURCE_OFF, "VIBRATION_SOURCE_OFF")
                    .put(VIBRATION_SOURCE_URI, "VIBRATION_SOURCE_URI")
                    .put(VIBRATION_SOURCE_APPLICATION_PROVIDED,
                            "VIBRATION_SOURCE_APPLICATION_PROVIDED")
                    .put(VIBRATION_SOURCE_AUDIO_CHANNEL, "VIBRATION_SOURCE_AUDIO_CHANNEL")
                    .put(VIBRATION_SOURCE_HAPTIC_GENERATOR, "VIBRATION_SOURCE_HAPTIC_GENERATOR")
                    .build();

    private static final ImmutableSet<Integer> VIBRATION_SOURCE_USING_SOUND_URI =
            ImmutableSet.of(VIBRATION_SOURCE_AUDIO_CHANNEL, VIBRATION_SOURCE_HAPTIC_GENERATOR);

    private static final ImmutableMap<Integer, String> FROM_URI_BEHAVIOR_NAMES =
            new ImmutableMap.Builder()
                    .put(FROM_URI_RINGTONE_SELECTION_OR_SOUND,
                            "FROM_URI_RINGTONE_SELECTION_OR_SOUND")
                    .put(FROM_URI_RINGTONE_SELECTION_OR_VIBRATION,
                            "FROM_URI_RINGTONE_SELECTION_OR_VIBRATION")
                    .put(FROM_URI_RINGTONE_SELECTION_ONLY, "FROM_URI_RINGTONE_SELECTION_ONLY")
                    .build();

    // Included in assertions via msgAssert, to provide loop context.
    private String mContextMessage;

    @Test
    public void testRingtoneSelectionBuilder_sourcesOffOrUri() {
        RingtoneSelection ringtoneSelection = new RingtoneSelection.Builder()
                .setSoundSource(SOUND_SOURCE_OFF)
                .setVibrationSource(VIBRATION_SOURCE_OFF)
                .build();
        assertSoundSource(ringtoneSelection, SOUND_SOURCE_OFF);
        assertVibrationSource(ringtoneSelection, VIBRATION_SOURCE_OFF);

        RingtoneSelection copy = new RingtoneSelection.Builder(ringtoneSelection).build();
        assertSoundSource(copy, SOUND_SOURCE_OFF);
        assertVibrationSource(copy, VIBRATION_SOURCE_OFF);

        // Setting Uri after source will overwrite the original source.
        ringtoneSelection = new RingtoneSelection.Builder()
                .setSoundSource(SOUND_SOURCE_OFF)
                .setVibrationSource(VIBRATION_SOURCE_OFF)
                .setSoundSource(SOUND_URI)
                .setVibrationSource(VIBRATION_URI)
                .build();
        assertSoundUriSource(ringtoneSelection, SOUND_URI);
        assertVibrationUriSource(ringtoneSelection, VIBRATION_URI);

        // Setting source after Uri can overwrite the Uri.
        ringtoneSelection = new RingtoneSelection.Builder()
                .setSoundSource(SOUND_URI)
                .setVibrationSource(VIBRATION_URI)
                .setSoundSource(SOUND_SOURCE_OFF)
                .setVibrationSource(VIBRATION_SOURCE_OFF)
                .build();
        assertSoundSource(ringtoneSelection, SOUND_SOURCE_OFF);
        assertVibrationSource(ringtoneSelection, VIBRATION_SOURCE_OFF);
    }

    @Test
    public void testRingtoneSelectionBuilder_allSourcesNotUriBased() {
        for (int soundSource : SOUND_SOURCE_NAMES.keySet()) {
            if (soundSource == SOUND_SOURCE_URI || soundSource == SOUND_SOURCE_UNKNOWN) {
                continue;  // Skip sources with dedicated tests.
            }
            for (int vibrationSource : VIBRATION_SOURCE_NAMES.keySet()) {
                if (vibrationSource == VIBRATION_SOURCE_URI
                        || vibrationSource == VIBRATION_SOURCE_UNKNOWN
                        || VIBRATION_SOURCE_USING_SOUND_URI.contains(vibrationSource)) {
                    continue;  // Skip source wsith dedicated tests.
                }
                mContextMessage = String.format("soundSource=%s, vibrationSource=%s",
                        SOUND_SOURCE_NAMES.get(soundSource),
                        VIBRATION_SOURCE_NAMES.get(vibrationSource));

                // Setting exactly the source should "stick".
                RingtoneSelection ringtoneSelection = new RingtoneSelection.Builder()
                        .setSoundSource(soundSource)
                        .setVibrationSource(vibrationSource)
                        .build();
                assertSoundSource(ringtoneSelection, soundSource);
                assertVibrationSource(ringtoneSelection, vibrationSource);

                // If a uri source is set first, the uri is cleared when setting the replacement.
                ringtoneSelection = new RingtoneSelection.Builder()
                        .setSoundSource(SOUND_URI)
                        .setVibrationSource(VIBRATION_URI)
                        .setSoundSource(soundSource)
                        .setVibrationSource(vibrationSource)
                        .build();
                assertSoundSource(ringtoneSelection, soundSource);
                assertVibrationSource(ringtoneSelection, vibrationSource);
            }
        }
    }

    @Test
    public void testRingtoneSelectionBuilder_vibrationSourceDependingOnSound() {
        for (int source : VIBRATION_SOURCE_USING_SOUND_URI) {
            mContextMessage = VIBRATION_SOURCE_NAMES.get(source);

            // Source with no sound.
            RingtoneSelection ringtoneSelection = new RingtoneSelection.Builder()
                    .setVibrationSource(source)
                    .setSoundSource(SOUND_SOURCE_OFF)
                    .build();
            assertSoundSource(ringtoneSelection, SOUND_SOURCE_OFF);
            assertVibrationSource(ringtoneSelection, VIBRATION_SOURCE_DEFAULT);

            // Source with sound (even if sound set after).
            ringtoneSelection = new RingtoneSelection.Builder()
                    .setVibrationSource(source)
                    .setSoundSource(SOUND_URI)
                    .build();
            assertSoundUriSource(ringtoneSelection, SOUND_URI);
            assertVibrationSource(ringtoneSelection, source);

            // Setting a vibration URI after setting the source will override the vibration source.
            ringtoneSelection = new RingtoneSelection.Builder()
                    .setVibrationSource(source)
                    .setSoundSource(SOUND_URI)
                    .setVibrationSource(VIBRATION_URI)
                    .build();
            assertSoundUriSource(ringtoneSelection, SOUND_URI);
            assertVibrationUriSource(ringtoneSelection, VIBRATION_URI);
        }
    }

    @Test
    public void testRingtoneSelectionBuilder_unknownSourceBecomesDefaultOrUri() {
        RingtoneSelection ringtoneSelection = new RingtoneSelection.Builder()
                .setSoundSource(SOUND_SOURCE_UNKNOWN)
                .setVibrationSource(VIBRATION_SOURCE_UNKNOWN)
                .build();
        assertSoundSource(ringtoneSelection, SOUND_SOURCE_DEFAULT);
        assertVibrationSource(ringtoneSelection, VIBRATION_SOURCE_DEFAULT);

        // If a URI is already set, then unknown stays as Uri.
        ringtoneSelection = new RingtoneSelection.Builder()
                .setSoundSource(SOUND_URI)
                .setVibrationSource(VIBRATION_URI)
                .setSoundSource(SOUND_SOURCE_UNKNOWN)
                .setVibrationSource(VIBRATION_SOURCE_UNKNOWN)
                .build();
        assertSoundUriSource(ringtoneSelection, SOUND_URI);
        assertVibrationUriSource(ringtoneSelection, VIBRATION_URI);
    }

    @Test
    public void testDefaultUriStringProducesDefaults() {
        Uri defaultsUri = Uri.parse(RingtoneSelection.DEFAULT_SELECTION_URI_STRING);
        // The default Uri is equivalent to an empty builder.
        RingtoneSelection defaultBuild = new RingtoneSelection.Builder().build();
        assertSoundSource(defaultBuild, SOUND_SOURCE_DEFAULT);
        assertVibrationSource(defaultBuild, VIBRATION_SOURCE_DEFAULT);
        assertThat(defaultBuild.toUri()).isEqualTo(defaultsUri);

        // All behaviors parse the default Uri to defaults.
        for (int behavior : FROM_URI_BEHAVIOR_NAMES.keySet()) {
            mContextMessage = FROM_URI_BEHAVIOR_NAMES.get(behavior);
            RingtoneSelection selection = RingtoneSelection.fromUri(defaultsUri, behavior);
            assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
            assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);
            assertThat(selection.toUri()).isEqualTo(defaultsUri);
        }

    }

    @Test
    public void testFromSettingsUriSounds() {
        // A null uri is interpreted as a silent sound uri.
        RingtoneSelection selection = RingtoneSelection.fromUri(null,
                FROM_URI_RINGTONE_SELECTION_OR_SOUND);
        assertSoundSource(selection, SOUND_SOURCE_OFF);
        assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);

        // A non-ringtone uri is interpreted as a sound Uri.
        selection = RingtoneSelection.fromUri(SOUND_URI, FROM_URI_RINGTONE_SELECTION_OR_SOUND);
        assertSoundUriSource(selection, SOUND_URI);
        assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);

        // Whether a uri is bad or not isn't enforced here.
        String badUri = "i am not well formed";
        selection = RingtoneSelection.fromUri(Uri.parse(badUri),
                FROM_URI_RINGTONE_SELECTION_OR_SOUND);
        assertThat(selection.getSoundUri().toString()).isEqualTo(badUri);
    }

    @Test
    public void testFromSettingsUriRingtoneSelections() {
        // A null uri is interpreted as a silent vibration uri.
        RingtoneSelection selection =
                RingtoneSelection.fromUri(null, FROM_URI_RINGTONE_SELECTION_ONLY);
        assertVibrationSource(selection, SOUND_SOURCE_DEFAULT);
        assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);

        // A non-ringtone uri reverts to defaults.
        selection = RingtoneSelection.fromUri(SOUND_URI, FROM_URI_RINGTONE_SELECTION_ONLY);
        assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
        assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);

        // Whether a uri is bad or not isn't enforced here, but unrecognized ones go to defaults.
        String badUri = "i am not well formed";
        selection = RingtoneSelection.fromUri(Uri.parse(badUri), FROM_URI_RINGTONE_SELECTION_ONLY);
        assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
        assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);
    }

    @Test
    public void testFromSettingsUriVibrations() {
        // A null uri is interpreted as a silent vibration uri.
        RingtoneSelection selection = RingtoneSelection.fromUri(null,
                FROM_URI_RINGTONE_SELECTION_OR_VIBRATION);
        assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
        assertVibrationSource(selection, VIBRATION_SOURCE_OFF);

        // A non-ringtone uri is interpreted as a vibration Uri.
        selection = RingtoneSelection.fromUri(VIBRATION_URI,
                FROM_URI_RINGTONE_SELECTION_OR_VIBRATION);
        assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
        assertVibrationUriSource(selection, VIBRATION_URI);

        // Whether a uri is bad or not isn't enforced here.
        String badUri = "i am not well formed";
        selection = RingtoneSelection.fromUri(Uri.parse(badUri),
                FROM_URI_RINGTONE_SELECTION_OR_VIBRATION);
        assertThat(selection.getVibrationUri().toString()).isEqualTo(badUri);
    }

    @Test
    public void testIsRingtoneSelectionUri() {
        assertThat(RingtoneSelection.isRingtoneSelectionUri(SOUND_URI)).isFalse();
        assertThat(RingtoneSelection.isRingtoneSelectionUri(null)).isFalse();

        for (String trueStr : new String[] {
                RingtoneSelection.DEFAULT_SELECTION_URI_STRING,
                URI_BASE,
                "content://media/ringtone",
                "content://media/ringtone?",
                "content://media/ringtone?ss=off",
                "content://media/ringtone?param",
                "content://media/ringtone#fragment",
                "content://media/ringtone?param&param#fragment",
        }) {
            assertWithMessage(trueStr)
                    .that(RingtoneSelection.isRingtoneSelectionUri(Uri.parse(trueStr))).isTrue();
        }

        for (String falseStr : new String[] {
                "",
                "some uri",
                "ringtone",
                "//media/ringtone",
                "content2://media/ringtone",
                "content://com.media/ringtone",
                "content://media/ringtone2",
                "content://media/ringtone/",
                "content://media/audio/something",
        }) {
            assertWithMessage(falseStr)
                    .that(RingtoneSelection.isRingtoneSelectionUri(Uri.parse(falseStr))).isFalse();
        }
    }

    @Test
    public void testUrisEncodedForm() {
        for (int behavior : FROM_URI_BEHAVIOR_NAMES.keySet()) {
            mContextMessage = FROM_URI_BEHAVIOR_NAMES.get(behavior);

            String uriStr = String.format("%s?su=%s&vu=%s", URI_BASE, SOUND_URI, VIBRATION_URI);
            RingtoneSelection selection = RingtoneSelection.fromUri(Uri.parse(uriStr), behavior);
            assertSoundUriSource(selection, SOUND_URI);
            assertVibrationUriSource(selection, VIBRATION_URI);

            // The same Uri, fully uri-encoded, comes out identically.
            String uriEnc = String.format("%s?su=%s&vu=%s", URI_BASE,
                    Uri.encode(SOUND_URI.toString()),
                    Uri.encode(VIBRATION_URI.toString()));
            assertThat(uriStr).isNotEqualTo(uriEnc);  // encoding changed something.
            RingtoneSelection selection2 = RingtoneSelection.fromUri(Uri.parse(uriEnc), behavior);
            assertSoundUriSource(selection2, SOUND_URI);
            assertVibrationUriSource(selection2, VIBRATION_URI);
        }
    }

    @Test
    public void testFromRingtoneSettingsUri_goodValues() {
        for (int behavior : FROM_URI_BEHAVIOR_NAMES.keySet()) {
            mContextMessage = FROM_URI_BEHAVIOR_NAMES.get(behavior);

            String uri = URI_BASE + "?ss=off&vs=off";
            RingtoneSelection selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundSource(selection, SOUND_SOURCE_OFF);
            assertVibrationSource(selection, VIBRATION_SOURCE_OFF);
            assertThat(selection.toUri().toString()).isEqualTo(uri);

            uri = URI_BASE + "?su=mysound&vu=myvib";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundUriSource(selection, "mysound");
            assertVibrationUriSource(selection, "myvib");
            assertThat(selection.toUri().toString()).isEqualTo(uri);

            uri = URI_BASE + "?ss=off&vs=app";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundSource(selection, SOUND_SOURCE_OFF);
            assertVibrationSource(selection, VIBRATION_SOURCE_APPLICATION_PROVIDED);
            assertThat(selection.toUri().toString()).isEqualTo(uri);

            uri = URI_BASE + "?su=mysound&vs=hg";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundUriSource(selection, "mysound");
            assertVibrationSource(selection, VIBRATION_SOURCE_HAPTIC_GENERATOR);
            assertThat(selection.toUri().toString()).isEqualTo(uri);

            uri = URI_BASE + "?su=mysound&vs=ac";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundUriSource(selection, "mysound");
            assertVibrationSource(selection, VIBRATION_SOURCE_AUDIO_CHANNEL);
            assertThat(selection.toUri().toString()).isEqualTo(uri);
        }
    }

    @Test
    public void testFromRingtoneSettingsUri_fallbackBehavior() {
        // Behavior has no effect when parsing Uris with the right ringtone selection authority/path
        for (int behavior : FROM_URI_BEHAVIOR_NAMES.keySet()) {
            mContextMessage = FROM_URI_BEHAVIOR_NAMES.get(behavior);

            // No sound uri, but vibration depends on it: reverts to default (incl for toUri).
            String uri = URI_BASE + "?ss=off&vs=ac";
            RingtoneSelection selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundSource(selection, SOUND_SOURCE_OFF);
            assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);
            // toUri has changed the original value to drop the incompatible vibration source.
            assertThat(selection.toUri().toString()).isEqualTo(URI_BASE + "?ss=off");

            // Same but no sound source at all
            uri = URI_BASE + "?vs=ac";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
            assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);
            // toUri has changed the original value to drop the incompatible vibration source.
            assertThat(selection.toUri().toString()).isEqualTo(URI_BASE);

            // Both a Uri and a sound-uri based source. The vibration source "wins" but then also
            // reverts to default due to the lack of sound uri.
            uri = URI_BASE + "?vs=ac&vu=myvib";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
            assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);
            // Fully default Uri now.
            assertThat(selection.toUri().toString()).isEqualTo(URI_BASE);
        }
    }

    @Test
    public void testFromRingtoneSettingsUri_badValues() {
        // Behavior has no effect when parsing Uris with the right ringtone selection authority/path
        for (int behavior : FROM_URI_BEHAVIOR_NAMES.keySet()) {
            mContextMessage = FROM_URI_BEHAVIOR_NAMES.get(behavior);

            // Unknown source values become default.
            String uri = URI_BASE + "?ss=zip&vs=zap";
            RingtoneSelection selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
            assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);

            // Unknown source values with a valid Uri retain the Uri.
            uri = URI_BASE + "?ss=zip&vs=zap&su=mysound&vu=myvib";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundUriSource(selection, "mysound");
            assertVibrationUriSource(selection, "myvib");

            // Only unknown params becomes just defaults.
            uri = URI_BASE + "?foo=bar&zip=zap";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundSource(selection, SOUND_SOURCE_DEFAULT);
            assertVibrationSource(selection, VIBRATION_SOURCE_DEFAULT);

            // Same parameters specified multiple times uses the first occurrence.
            uri = URI_BASE
                    + "?ss=default&vs=default&su=mysound&vu=myvib&ss=off&vs=off&su=ms2&vu=mv2";
            selection = RingtoneSelection.fromUri(Uri.parse(uri), behavior);
            assertSoundUriSource(selection, "mysound");
            assertVibrationUriSource(selection, "myvib");
        }
    }

    // Helpers to keep the test concise: sound or vibration must either have a URI with the URI
    // source, or else not have a URI.
    private void assertSoundUriSource(RingtoneSelection selection, Uri soundUri) {
        msgAssert().that(selection.getSoundSource()).isEqualTo(SOUND_SOURCE_URI);
        msgAssert().that(selection.getSoundUri()).isEqualTo(soundUri);
    }

    private void assertSoundUriSource(RingtoneSelection selection, String soundUri) {
        assertSoundUriSource(selection, Uri.parse(soundUri));
    }

    private void assertVibrationUriSource(RingtoneSelection selection, Uri vibrationUri) {
        msgAssert().that(selection.getVibrationSource()).isEqualTo(VIBRATION_SOURCE_URI);
        msgAssert().that(selection.getVibrationUri()).isEqualTo(vibrationUri);
    }
    private void assertVibrationUriSource(RingtoneSelection selection, String vibrationUri) {
        assertVibrationUriSource(selection, Uri.parse(vibrationUri));
    }

    private void assertSoundSource(RingtoneSelection selection,
            @RingtoneSelection.SoundSource int soundSource) {
        checkArgument(soundSource != SOUND_SOURCE_URI, "Use assertSoundUriSource");
        msgAssert().that(selection.getSoundSource()).isEqualTo(soundSource);
        msgAssert().that(selection.getSoundUri()).isNull();
    }

    private void assertVibrationSource(RingtoneSelection selection,
            @RingtoneSelection.VibrationSource int vibrationSource) {
        checkArgument(vibrationSource != VIBRATION_SOURCE_URI, "Use assertVibrationUriSource");
        msgAssert().that(selection.getVibrationSource()).isEqualTo(vibrationSource);
        msgAssert().that(selection.getVibrationUri()).isNull();
    }

    /** Returns an assertion initialized with a context-based message, if one is set. */
    private StandardSubjectBuilder msgAssert() {
        if (mContextMessage != null) {
            return assertWithMessage(mContextMessage);
        } else {
            return Truth.assert_();
        }
    }
}
