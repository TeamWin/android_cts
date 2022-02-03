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

package android.mediapc.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static android.mediapc.cts.Utils.MIN_MEMORY_PERF_CLASS_CANDIDATE_MB;
import static android.mediapc.cts.Utils.MIN_MEMORY_PERF_CLASS_T_MB;
import static android.util.DisplayMetrics.DENSITY_400;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.media.MediaFormat;
import android.media.UnsupportedSchemeException;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Tests the basic aspects of the media performance class.
 */
public class PerformanceClassTest {
    private static final String TAG = "PerformanceClassTest";
    private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    static ArrayList<String> mMimeSecureSupport = new ArrayList<>();

    static {
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_VP9);
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_AV1);
    }


    private boolean isHandheld() {
        // handheld nature is not exposed to package manager, for now
        // we check for touchscreen and NOT watch and NOT tv
        PackageManager pm =
            InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_TELEVISION)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

    @SmallTest
    @Test
    // TODO(b/218771970) Add @CddTest annotation
    public void testSecureHwDecodeSupport() throws IOException {
        ArrayList<String> noSecureHwDecoderForMimes = new ArrayList<>();
        for (String mime : mMimeSecureSupport) {
            boolean isSecureHwDecoderFoundForMime = false;
            boolean isHwDecoderFoundForMime = false;
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
            for (MediaCodecInfo info : codecInfos) {
                if (info.isEncoder() || !info.isHardwareAccelerated() || info.isAlias()) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (caps != null) {
                        isHwDecoderFoundForMime = true;
                        if (caps.isFeatureSupported(FEATURE_SecurePlayback))
                            isSecureHwDecoderFoundForMime = true;
                    }
                } catch (Exception ignored) {
                }
            }
            if (isHwDecoderFoundForMime && !isSecureHwDecoderFoundForMime)
                noSecureHwDecoderForMimes.add(mime);
        }
        if (Utils.isTPerfClass()) {
            assertTrue(
                    "For MPC >= Android T, if HW decoder is present for a mime, secure HW decoder" +
                            " must be present for the mime. HW decoder present but secure HW " +
                            "decoder not available for mimes: " + noSecureHwDecoderForMimes,
                    noSecureHwDecoderForMimes.isEmpty());
        } else {
            DeviceReportLog log =
                    new DeviceReportLog("MediaPerformanceClassLogs", "SecureHwDecodeSupport");
            log.addValue("SecureHwDecodeSupportForMimesWithHwDecoders",
                    noSecureHwDecoderForMimes.isEmpty(), ResultType.NEUTRAL, ResultUnit.NONE);
            // TODO(b/218771970) Log CDD sections
            log.setSummary("MPC 13: Widevine/Secure codec requirements", 0, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    @SmallTest
    @Test
    // TODO(b/218771970) Add @CddTest annotation
    public void testWidevineSupport() throws UnsupportedSchemeException {
        boolean isWidevineSupported = MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID);
        boolean isL1Supported = false;
        boolean isL1Tier3Supported = false;
        boolean isOemCrypto17Plus = false;
        boolean isWidevineCdm17Plus = false;
        if (isWidevineSupported) {
            MediaDrm mediaDrm = new MediaDrm(WIDEVINE_UUID);
            isL1Supported = mediaDrm.getPropertyString("securityLevel").equals("L1");
            int tier = Integer.parseInt(mediaDrm.getPropertyString("resourceRatingTier"));
            isL1Tier3Supported = tier >= 3;

            String oemCryptoVersionProperty = mediaDrm.getPropertyString("oemCryptoApiVersion");
            int oemCryptoVersion = Integer.parseInt(oemCryptoVersionProperty);
            isOemCrypto17Plus = oemCryptoVersion >= 17;

            String cdmVersionProperty = mediaDrm.getPropertyString(MediaDrm.PROPERTY_VERSION);
            int cdmMajorVersion = Integer.parseInt(cdmVersionProperty.split("\\.", 2)[0]);
            isWidevineCdm17Plus = cdmMajorVersion >= 17;
        }

        if (Utils.isTPerfClass()) {
            assertTrue("Widevine support required for MPC >= Android T", isWidevineSupported);
            assertTrue("Widevine L1 support required for MPC >= Android T", isL1Supported);
            assertTrue("Widevine L1 Resource Rating Tier 3 support required for MPC >= Android T",
                    isL1Tier3Supported);
            assertTrue("OEMCrypto min version 17.x required for MPC >= Android T",
                    isOemCrypto17Plus);
            assertTrue("Widevine CDM min version 17.x required for MPC >= Android T",
                    isWidevineCdm17Plus);
        } else {
            DeviceReportLog log =
                    new DeviceReportLog("MediaPerformanceClassLogs", "WidevineSupport");
            log.addValue("Widevine Support", isWidevineSupported, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.addValue("Widevine L1 Support", isL1Supported, ResultType.NEUTRAL, ResultUnit.NONE);
            log.addValue("Widevine L1 Resource Rating Tier 3 Support", isL1Tier3Supported,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            log.addValue("OEMCrypto min version 17.x Support", isOemCrypto17Plus,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            log.addValue("Widevine CDM min version 17.x Support", isWidevineCdm17Plus,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            // TODO(b/218771970) Log CDD sections
            log.setSummary("MPC 13: Widevine/Secure codec requirements", 0, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    @SmallTest
    @Test
    public void testMediaPerformanceClassScope() throws Exception {
        // if device is not of a performance class, we are done.
        Assume.assumeTrue("not a device of a valid media performance class", Utils.isPerfClass());

        if (Utils.isPerfClass()) {
            assertTrue("performance class is only defined for Handheld devices", isHandheld());
        }
    }

    @Test
    @CddTest(requirement="2.2.7.3/7.1.1.1,7.1.1.3,7.6.1/H-1-1,H-2-1")
    public void testMinimumMemory() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        // Verify minimum screen density and resolution
        assertMinDpiAndPixels(context, DENSITY_400, 1920, 1080);
        // Verify minimum memory
        assertMinMemoryMb(context);
    }

    /** Asserts that the given values conform to the specs in CDD */
    private void assertMinDpiAndPixels(Context context, int minDpi, int minLong, int minShort) {
        // Verify display DPI. We only seem to be able to get the primary display.
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager =
            (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int density = metrics.densityDpi;
        int longPix = Math.max(metrics.widthPixels, metrics.heightPixels);
        int shortPix = Math.min(metrics.widthPixels, metrics.heightPixels);

        Log.i(TAG, String.format("minDpi=%d minSize=%dx%dpix", minDpi, minLong, minShort));
        Log.i(TAG, String.format("dpi=%d size=%dx%dpix", density, longPix, shortPix));

        if (Utils.isPerfClass()) {
            assertTrue("Display density " + density + " must be at least " + minDpi + "dpi",
                    density >= minDpi);
            assertTrue("Display resolution " + longPix + "x" + shortPix + "pix must be at least " +
                            minLong + "x" + minShort + "pix",
                    longPix >= minLong && shortPix >= minShort);
        } else {
            int pc = density >= minDpi && longPix >= minLong && shortPix >= minShort
                    ? Build.VERSION_CODES.S : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",  "Display");
            log.addValue("DisplayDensity", density, ResultType.HIGHER_BETTER, ResultUnit.NONE);
            log.addValue("ResolutionLong", longPix, ResultType.HIGHER_BETTER, ResultUnit.NONE);
            log.addValue("ResolutionShort", shortPix, ResultType.HIGHER_BETTER, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.3/7.1.1.1,7.1.1.3/H-1-1,H-2-1 performance_class", pc,
                    ResultType.HIGHER_BETTER, ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    /** Asserts that the given values conform to the specs in CDD 7.6.1 */
    private void assertMinMemoryMb(Context context) {
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        long totalMemoryMb = getTotalMemory(activityManager) / 1024 / 1024;

        Log.i(TAG, String.format("Total device memory = %,d MB", totalMemoryMb));
        if (Utils.isPerfClass()) {
            long minMb = Utils.isTPerfClass() ? MIN_MEMORY_PERF_CLASS_T_MB :
                    Utils.MIN_MEMORY_PERF_CLASS_CANDIDATE_MB;
            Log.i(TAG, String.format("Minimum required memory = %,d MB", minMb));
            assertTrue(String.format("Does not meet minimum memory requirements (CDD 7.6.1)."
                    + "Found = %d, Minimum = %d", totalMemoryMb, minMb), totalMemoryMb >= minMb);
        } else {
            int pc = 0;
            if (totalMemoryMb >= MIN_MEMORY_PERF_CLASS_T_MB)
                pc = Build.VERSION_CODES.TIRAMISU;
            else if (totalMemoryMb >= MIN_MEMORY_PERF_CLASS_CANDIDATE_MB)
                pc = Build.VERSION_CODES.S;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs", "MinMemory");
            log.addValue("MemoryMB", totalMemoryMb, ResultType.HIGHER_BETTER, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.3/7.6.1/H-1-1,H-2-1  performance_class", pc,
                    ResultType.HIGHER_BETTER, ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    /**
     * @return the total memory accessible by the kernel as defined by
     * {@code ActivityManager.MemoryInfo}.
     */
    private long getTotalMemory(ActivityManager activityManager) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem;
    }
}
