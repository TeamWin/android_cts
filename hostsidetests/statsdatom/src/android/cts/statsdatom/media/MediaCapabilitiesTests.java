/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.cts.statsdatom.media;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.stats.mediametrics.Mediametrics;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class MediaCapabilitiesTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_TV = "android.hardware.type.television";
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testSurroundSoundCapabilities() throws Exception {
        // Run this test only on TVs
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.MEDIA_CAPABILITIES_FIELD_NUMBER);

        // Store the original value of settings
        String isDtsEnabled = getDevice().executeShellCommand(
                "cmd audio get-is-surround-format-enabled 7").split(":")[1].trim();
        String isDolbyTrueHdEnabled = getDevice().executeShellCommand(
                "cmd audio get-is-surround-format-enabled 14").split(":")[1].trim();
        String encodedSurroundMode = getDevice().executeShellCommand(
                "cmd audio get-encoded-surround-mode").split(":")[1].trim();

        // Setting the values of audio setting via shell commands
        getDevice().executeShellCommand(
                "cmd audio set-surround-format-enabled 7 true");
        getDevice().executeShellCommand(
                "cmd audio set-surround-format-enabled 14 false");
        getDevice().executeShellCommand("cmd audio set-encoded-surround-mode 2");
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Trigger atom pull.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        // The list of atoms will be empty if the atom is not supported.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (AtomsProto.Atom atom : atoms) {
            assertThat(atom.getMediaCapabilities().getSurroundEncodings().getAudioEncodingsCount())
                    .isAtLeast(1);
            assertEquals(Mediametrics.EncodedSurroundOutputMode.ENCODED_SURROUND_OUTPUT_NEVER,
                    atom.getMediaCapabilities().getSurroundOutputMode());
            assertThat(Mediametrics.AudioEncoding.ENCODING_DTS).isIn(
                    atom.getMediaCapabilities()
                            .getUserEnabledSurroundEncodings().getAudioEncodingsList());
            assertThat(Mediametrics.AudioEncoding.ENCODING_DOLBY_TRUEHD).isNotIn(
                    atom.getMediaCapabilities()
                            .getUserEnabledSurroundEncodings().getAudioEncodingsList());
        }

        // Restore the original value of settings
        getDevice().executeShellCommand(
                "cmd audio set-surround-format-enabled 7 " + isDtsEnabled);
        getDevice().executeShellCommand(
                "cmd audio set-surround-format-enabled 14 " + isDolbyTrueHdEnabled);
        getDevice().executeShellCommand(
                "cmd audio set-encoded-surround-mode " + encodedSurroundMode);
    }

    public void testDisplayCapabilities() throws Exception {
        // Run this test only on TVs
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TV)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.MEDIA_CAPABILITIES_FIELD_NUMBER);

        // Store the original value of settings
        String userPrefDisplayMode = getDevice().executeShellCommand(
                "cmd display get-user-preferred-display-mode").split(":")[1].trim();
        String matchContentFrameRatePref = getDevice().executeShellCommand(
                "cmd display get-match-content-frame-rate-pref").split(":")[1].trim();
        String userDisabledHdrTypes = getDevice().executeShellCommand(
                "cmd display get-user-disabled-hdr-types").split(":")[1].trim();
        userDisabledHdrTypes.replaceAll(", ", " ");


        // Setting the values of display setting via shell commands
        getDevice().executeShellCommand(
                "cmd display set-user-preferred-display-mode 720 1020 60.0f");
        getDevice().executeShellCommand("cmd display set-match-content-frame-rate-pref 2");
        getDevice().executeShellCommand("cmd display set-user-disabled-hdr-types 0 1");
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Trigger atom pull.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        // The list of atoms will be empty if the atom is not supported.
        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());

        for (AtomsProto.Atom atom : atoms) {
            assertThat(atom.getMediaCapabilities().getSinkDisplayModes().getDisplayModesCount())
                    .isAtLeast(1);
            assertEquals(720, atom.getMediaCapabilities().getUserPreferredResolutionHeight());
            assertEquals(1020, atom.getMediaCapabilities().getUserPreferredResolutionWidth());
            assertEquals(60.0f, atom.getMediaCapabilities().getUserPreferredRefreshRate());
            assertEquals(Mediametrics.MatchContentFrameRatePreference
                            .MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY,
                    atom.getMediaCapabilities().getMatchContentRefreshRatePreference());
            assertThat(
                    atom.getMediaCapabilities().getUserDisabledHdrFormats().getHdrFormats(0))
                    .isAnyOf(
                            Mediametrics.HdrFormat.HDR_TYPE_DOLBY_VISION,
                            Mediametrics.HdrFormat.HDR_TYPE_HDR10);
            assertThat(
                    atom.getMediaCapabilities().getUserDisabledHdrFormats().getHdrFormats(1))
                    .isAnyOf(
                            Mediametrics.HdrFormat.HDR_TYPE_DOLBY_VISION,
                            Mediametrics.HdrFormat.HDR_TYPE_HDR10);
        }

        // Restore the original value of settings
        if (userPrefDisplayMode.equals("null")) {
            getDevice().executeShellCommand(
                    "cmd display clear-user-preferred-display-mode");
        } else {
            getDevice().executeShellCommand(
                    "cmd display set-user-preferred-display-mode " + userPrefDisplayMode);
        }
        getDevice().executeShellCommand("cmd display set-match-content-frame-rate-pref "
                + matchContentFrameRatePref);
        getDevice().executeShellCommand("cmd display set-user-disabled-hdr-types "
                + userDisabledHdrTypes);
    }
}
