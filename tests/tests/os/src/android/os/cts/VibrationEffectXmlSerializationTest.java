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

package android.os.cts;

import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SPIN;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.os.VibrationEffect;
import android.os.vibrator.persistence.VibrationXmlParser;
import android.os.vibrator.persistence.VibrationXmlSerializer;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

/**
 * Tests for XML serialization of {@link VibrationEffect} via {@link VibrationXmlSerializer}
 * and {@link VibrationXmlParser}.
 */
@RunWith(JUnitParamsRunner.class)
@ApiTest(apis = {
        "android.os.vibrator.persistence.VibrationXmlParser#parse",
        "android.os.vibrator.persistence.VibrationXmlSerializer#serialize"
})
public class VibrationEffectXmlSerializationTest {

    @Test
    @Parameters(method = "getEffectsAndSerializations")
    public void testParseValidVibrationEffect(VibrationEffect expectedEffect, String xml)
            throws Exception {
        assertThat(parse(xml)).isEqualTo(expectedEffect);
    }

    @Test
    @Parameters(method = "getEffectsAndSerializations")
    public void testSerializeValidVibrationEffect(VibrationEffect effect, String expectedXml)
            throws Exception {
        StringWriter writer = new StringWriter();
        VibrationXmlSerializer.serialize(effect, writer);
        assertSameXml(expectedXml, writer.toString());
    }

    @Test
    @Parameters(method = "getEffectsAndSerializations")
    @SuppressWarnings("unused") // Unused serialization argument to reuse parameters for round trip
    public void testParseSerializeRoundTrip(VibrationEffect effect, String unusedXml)
            throws Exception {
        StringWriter writer = new StringWriter();
        // Serialize effect
        VibrationXmlSerializer.serialize(effect, writer);
        // Parse serialized effect
        StringReader reader = new StringReader(writer.toString());
        VibrationEffect parsedEffect = VibrationXmlParser.parse(reader);
        assertThat(parsedEffect).isEqualTo(effect);
    }

    @Test
    public void testParseValidVibrationEffectWithCommentsAndSpaces() throws Exception {
        assertThat(parse(
                """

                <!-- comment before root tag -->

                <vibration>
                    <!--
                            multi-lined
                            comment
                    -->
                    <predefined-effect name="click"/>
                    <!-- comment before closing root tag -->
                </vibration>

                <!-- comment after root tag -->
                """))
                .isEqualTo(VibrationEffect.createPredefined(EFFECT_CLICK));
    }

    @Test
    public void testParseInvalidXmlIsNull() throws Exception {
        assertThat(parse("")).isNull();
        assertThat(parse("<!-- pure comment -->")).isNull();
        assertThat(parse("invalid")).isNull();
        // Malformed vibration tag
        assertThat(parse("<vibration")).isNull();
        // Open vibration tag is never closed
        assertThat(parse("<vibration>")).isNull();
        // Open predefined-effect tag is never closed before vibration is closed
        assertThat(parse("<vibration><predefined-effect name=\"click\"></vibration>")).isNull();
    }

    @Test
    public void testParseInvalidElementsOnStartIsNull() throws Exception {
        assertThat(parse(
                """
                # some invalid initial text
                <vibration>
                    <predefined-effect name="click"/>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <invalid-first-tag/>
                <vibration>
                    <predefined-effect name="click"/>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <invalid-root-tag>
                    <predefined-effect name="click"/>
                </invalid-root-tag>
                """))
                .isNull();
    }

    @Test
    public void testParseInvalidElementsOnEndIsNull() throws Exception {
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                </vibration>
                # some invalid text
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                </vibration>
                <invalid-trailing-tag/>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                </vibration>
                </invalid-trailing-end-tag>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                    </invalid-trailing-end-tag>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseEmptyVibrationTagIsNull() throws Exception {
        assertThat(parse("<vibration/>")).isNull();
    }

    @Test
    public void testParseMultipleVibrationTagsIsNull() throws Exception {
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                </vibration>
                <vibration>
                    <predefined-effect name="click"/>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseEffectTagWrongAttributesIsNull() throws Exception {
        // Missing name attribute
        assertThat(parse("<vibration><predefined-effect/></vibration>")).isNull();

        // Wrong attribute
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect id="0"/>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click" extra="0"/>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseHiddenPredefinedEffectIsNull() throws Exception {
        // Hidden effect id
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="texture_tick"/>
                </vibration>
                """))
                .isNull();

        // Non-default fallback flag
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="tick" fallback="false"/>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParsePrimitiveTagWrongAttributesIsNull() throws Exception {
        // Missing name attribute
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect scale="1" delayMs="10"/>
                </vibration>
                """))
                .isNull();

        // Wrong attribute "delay" instead of "delayMs"
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click" delay="10"/>
                </vibration>
                """))
                .isNull();

        // Wrong attribute
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click" extra="0"/>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseWaveformEffectAndRepeatingTagsAnyAttributeIsNull() throws Exception {
        // Waveform with wrong attribute
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect extra="0">
                        <waveform-entry durationMs="10" amplitude="10"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Repeating with wrong attribute
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <repeating extra="0">
                            <waveform-entry durationMs="10" amplitude="10"/>
                        </repeating>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseWaveformEntryTagWrongAttributesIsNull() throws Exception {
        // Missing amplitude attribute
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Missing durationMs attribute
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry amplitude="100"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Wrong attribute "duration" instead of "durationMs"
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry amplitude="100" duration="10"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Wrong attribute
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry amplitude="100" durationMs="10" extra="0"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseInvalidPredefinedEffectNameIsNull() throws Exception {
        // Invalid effect name
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="lick"/>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParsePredefinedFollowedAnyEffectIsNull() throws Exception {
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                    <predefined-effect name="tick"/>
                </vibration>
                """))
                .isNull();

        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                    <primitive-effect name="click"/>
                </vibration>
                """))
                .isNull();

        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click"/>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseRepeatingPredefinedEffectsIsNull() throws Exception {
        assertThat(parse(
                """
                <vibration>
                    <repeating>
                        <predefined-effect name="click"/>
                    </repeating>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseAnyTagInsidePredefinedEffectIsNull() throws Exception {
        // Predefined inside predefined effect
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click">
                        <predefined-effect name="click"/>
                    </predefined-effect>
                </vibration>
                """))
                .isNull();

        // Primitive inside predefined effect.
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click">
                        <primitive-effect name="click"/>
                    </predefined-effect>
                </vibration>
                """))
                .isNull();

        // Waveform inside predefined effect.
        assertThat(parse(
                """
                <vibration>
                    <predefined-effect name="click">
                        <waveform-effect>
                            <waveform-entry amplitude="default" durationMs="10"/>
                        </waveform-effect>"
                    </predefined-effect>"
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseInvalidPrimitiveNameAndAttributesIsNull() throws Exception {
        // Invalid primitive name.
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="lick"/>
                </vibration>
                """))
                .isNull();

        // Invalid primitive scale.
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click" scale="-1"/>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click" scale="2"/>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click" scale="NaN"/>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click" scale="Infinity"/>
                </vibration>
                """))
                .isNull();

        // Invalid primitive delay.
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click" delayMs="-1"/>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParsePrimitiveFollowedByOtherEffectsIsNull() throws Exception {
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click"/>
                    <predefined-effect name="click"/>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click"/>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>"
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseAnyTagInsidePrimitiveIsNull() throws Exception {
        // Predefined inside primitive effect.
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click">
                        <predefined-effect name="click"/>
                    </primitive-effect>
                </vibration>
                """))
                .isNull();

        // Primitive inside primitive effect.
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click">
                        <primitive-effect name="click"/>
                    </primitive-effect>
                </vibration>
                """))
                .isNull();

        // Waveform inside primitive effect.
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click">
                        <waveform-effect>
                            <waveform-entry amplitude="default" durationMs="10"/>
                        </waveform-effect>
                    </primitive-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseRepeatingPrimitivesIsNull() throws Exception {
        assertThat(parse(
                """
                <vibration>
                    <primitive-effect name="click"/>
                    <repeating>
                        <primitive-effect name="tick"/>
                    </repeating>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseInvalidWaveformEntryAttributesIsNull() throws Exception {
        // Invalid amplitude.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="-1"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Invalid duration.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="-1" amplitude="default"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseInvalidTagInsideWaveformEffectIsNull() throws Exception {
        // Primitive inside waveform or repeating.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <primitive-effect name="click"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating><primitive-effect name="click"/></repeating>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Predefined inside waveform or repeating.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <predefined-effect name="click"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating>
                            <predefined-effect name="click"/>
                        </repeating>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Waveform effect inside waveform or repeating.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <waveform-effect>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </waveform-effect>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                        <repeating>
                            <waveform-effect>
                                <waveform-entry durationMs="10" amplitude="default"/>
                            </waveform-effect>
                        </repeating>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseInvalidVibrationWaveformIsNull() throws Exception {
        // Empty waveform.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect></waveform-effect>
                </vibration>
                """))
                .isNull();

        // Empty repeating block.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="0" amplitude="10"/>
                        <repeating/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Waveform with multiple repeating blocks.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <repeating>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </repeating>
                        <repeating>
                            <waveform-entry durationMs="100" amplitude="default"/>
                        </repeating>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Waveform with entries after repeating block.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <repeating>
                            <waveform-entry durationMs="10" amplitude="default"/>
                        </repeating>
                        <waveform-entry durationMs="100" amplitude="default"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();

        // Waveform with total duration zero.
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="0" amplitude="10"/>
                        <waveform-entry durationMs="0" amplitude="20"/>
                        <waveform-entry durationMs="0" amplitude="30"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testParseWaveformFollowedAnyEffectIsNull() throws Exception {
        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <predefined-effect name="tick"/>
                </vibration>
                """))
                .isNull();

        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <primitive-effect name="click"/>
                </vibration>
                """))
                .isNull();

        assertThat(parse(
                """
                <vibration>
                    <waveform-effect>
                        <waveform-entry durationMs="10" amplitude="default"/>
                    </waveform-effect>
                    <waveform-effect>
                        <waveform-entry amplitude="default" durationMs="10"/>
                    </waveform-effect>
                </vibration>
                """))
                .isNull();
    }

    @Test
    public void testSerializeVibrationEffectFromNonPublicApiIsFalse() {
        StringWriter writer = new StringWriter();

        // Predefined effect with non-default fallback flag.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.get(VibrationEffect.EFFECT_TICK, /* fallback= */ false),
                        writer));
        assertThat(writer.toString()).isEmpty();

        // Predefined effect with hidden effect id.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.get(VibrationEffect.EFFECT_TEXTURE_TICK), writer));
        assertThat(writer.toString()).isEmpty();

        // Step with non-default frequency.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform(targetFrequency(150f))
                                .addSustain(Duration.ofMillis(100))
                                .build(), writer));
        assertThat(writer.toString()).isEmpty();

        // Step with non-integer amplitude.
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform(targetAmplitude(0.00123f))
                                .addSustain(Duration.ofMillis(100))
                                .build(), writer));
        assertThat(writer.toString()).isEmpty();

        // Waveform with ramp segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startWaveform()
                                .addSustain(Duration.ofMillis(100))
                                .addTransition(Duration.ofMillis(50), targetAmplitude(1))
                                .build(), writer));
        assertThat(writer.toString()).isEmpty();

        // Composition with non-primitive segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startComposition()
                                .addPrimitive(PRIMITIVE_CLICK)
                                .addEffect(VibrationEffect.createPredefined(EFFECT_CLICK))
                                .compose(), writer));
        assertThat(writer.toString()).isEmpty();

        // Composition with repeating primitive segments
        assertThrows(VibrationXmlSerializer.SerializationFailedException.class,
                () -> VibrationXmlSerializer.serialize(
                        VibrationEffect.startComposition()
                                .repeatEffectIndefinitely(
                                        VibrationEffect.startComposition()
                                                .addPrimitive(PRIMITIVE_CLICK)
                                                .addPrimitive(PRIMITIVE_TICK, 1f, /* delay= */ 100)
                                                .compose())
                                .compose(), writer));
        assertThat(writer.toString()).isEmpty();
    }

    private static VibrationEffect parse(String xml) throws IOException {
        return VibrationXmlParser.parse(new StringReader(xml));
    }

    @SuppressWarnings("unused") // Used in tests with @Parameters
    private Object[] getEffectsAndSerializations() {
        return new Object[]{
                new Object[]{
                        // On-off pattern
                        VibrationEffect.createWaveform(new long[]{10, 20, 30, 40},
                                /* repeat= */ -1),
                        """
                        <vibration>
                            <waveform-effect>
                                <waveform-entry durationMs="10" amplitude="0"/>
                                <waveform-entry durationMs="20" amplitude="default"/>
                                <waveform-entry durationMs="30" amplitude="0"/>
                                <waveform-entry durationMs="40" amplitude="default"/>
                            </waveform-effect>
                        </vibration>
                        """,
                },
                new Object[]{
                        // Repeating on-off pattern
                        VibrationEffect.createWaveform(new long[]{100, 200, 300, 400},
                                /* repeat= */ 2),
                        """
                        <vibration>
                            <waveform-effect>
                                <waveform-entry durationMs="100" amplitude="0"/>
                                <waveform-entry durationMs="200" amplitude="default"/>
                                <repeating>
                                    <waveform-entry durationMs="300" amplitude="0"/>
                                    <waveform-entry durationMs="400" amplitude="default"/>
                                </repeating>
                            </waveform-effect>
                        </vibration>
                        """,
                },
                new Object[]{
                        // Amplitude waveform
                        VibrationEffect.createWaveform(new long[]{100, 200, 300},
                                new int[]{1, VibrationEffect.DEFAULT_AMPLITUDE, 250},
                                /* repeat= */ -1),
                        """
                        <vibration>
                            <waveform-effect>
                                <waveform-entry amplitude="1" durationMs="100"/>
                                <waveform-entry amplitude="default" durationMs="200"/>
                                <waveform-entry durationMs="300" amplitude="250"/>
                            </waveform-effect>
                        </vibration>
                        """,
                },
                new Object[]{
                        // Repeating amplitude waveform
                        VibrationEffect.createWaveform(new long[]{123, 456, 789, 0},
                                new int[]{254, 1, 255, 0}, /* repeat= */ 0),
                        """
                        <vibration>
                            <waveform-effect>
                                <repeating>
                                    <waveform-entry durationMs="123" amplitude="254"/>
                                    <waveform-entry durationMs="456" amplitude="1"/>
                                    <waveform-entry durationMs="789" amplitude="255"/>
                                    <waveform-entry durationMs="0" amplitude="0"/>
                                </repeating>
                            </waveform-effect>
                        </vibration>
                        """,
                },
                new Object[]{
                        // Predefined effect
                        VibrationEffect.createPredefined(EFFECT_CLICK),
                        """
                        <vibration><predefined-effect name="click"/></vibration>
                        """,
                },
                new Object[]{
                        // Primitive composition
                        VibrationEffect.startComposition()
                                .addPrimitive(PRIMITIVE_CLICK)
                                .addPrimitive(PRIMITIVE_TICK, 0.2497f)
                                .addPrimitive(PRIMITIVE_LOW_TICK, 1f, 356)
                                .addPrimitive(PRIMITIVE_SPIN, 0.6364f, 7)
                                .compose(),
                        """
                        <vibration>
                            <primitive-effect name="click"/>
                            <primitive-effect name="tick" scale="0.2497"/>
                            <primitive-effect name="low_tick" delayMs="356"/>
                            <primitive-effect name="spin" scale="0.6364" delayMs="7"/>
                        </vibration>
                        """,
                },
        };
    }

    static void assertSameXml(String expectedXml, String actualXml)
            throws ParserConfigurationException {
        // DocumentBuilderFactory does not support setValidating(true) in Android, so the method
        // setIgnoringElementContentWhitespace does not work. Remove whitespace manually.
        expectedXml = removeWhitespaceBetweenXmlTags(expectedXml);
        actualXml = removeWhitespaceBetweenXmlTags(actualXml);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document expectedDoc, actualDoc;

        try {
            expectedDoc = db.parse(new ByteArrayInputStream(expectedXml.getBytes()));
            expectedDoc.normalizeDocument();
        } catch (IOException | SAXException e) {
            throw new RuntimeException("Failed to parse XML for comparison:\n" + expectedXml, e);
        }

        try {
            actualDoc = db.parse(new ByteArrayInputStream(actualXml.getBytes()));
            actualDoc.normalizeDocument();
        } catch (IOException | SAXException e) {
            throw new RuntimeException("Failed to parse XML for comparison:\n" + actualXml, e);
        }

        assertWithMessage("Expected XML:\n%s\n\nActual XML:\n%s", expectedXml, actualXml)
                .that(expectedDoc.isEqualNode(actualDoc)).isTrue();
    }

    private static String removeWhitespaceBetweenXmlTags(String xml) {
        return xml.replaceAll(">\\s+<", "><");
    }
}
