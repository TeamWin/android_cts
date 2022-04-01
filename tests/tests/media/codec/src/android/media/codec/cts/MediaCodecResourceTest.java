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

package android.media.codec.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

// This class verifies the resource management aspects of MediaCodecs.
@Presubmit
@SmallTest
@RequiresDevice
public class MediaCodecResourceTest {
    private static final String TAG = "MediaCodecResourceTest";

    // Codec information that is pertinent to creating codecs for resource management testing.
    private static class CodecInfo {
        CodecInfo(String name, int maxSupportedInstances, String mime, MediaFormat mediaFormat) {
            this.name = name;
            this.maxSupportedInstances = maxSupportedInstances;
            this.mime = mime;
            this.mediaFormat = mediaFormat;
        }
        public final String name;
        public final int maxSupportedInstances;
        public final String mime;
        public final MediaFormat mediaFormat;
    }

    // Resources are reclaimed from lower priority processes by higher priority processes.
    private static int sLowPriorityPid = -1;
    private static int sLowPriorityUid = -1;
    private static int sHighPriorityPid = -1;
    private static int sHighPriorityUid = -1;

    @BeforeClass
    public static void setup() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        //
        // Start the low priority activity - set via the manifest to run in a different process.
        //
        {
            Intent intent = new Intent(context, MediaCodecResourceTestLowPriorityActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
        //
        // Start the high priority activity - set via the manifest to run in a different process.
        //
        {
            Intent intent = new Intent(context, MediaCodecResourceTestHighPriorityActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        // It takes a non-trivial amount of time to start processes and have them swap into
        // the background. There is no signal for when a process has been backgrounded for which we
        // can wait on.
        try {
            Thread.sleep(1000);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sleep.");
        }
        //
        // Scan all running processes to find the high and low processes (1 second grace period).
        //
        try {
            // Permission needed to retrieve process information.
            instrumentation.getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.REAL_GET_TASKS);
            ActivityManager activityManager = context.getSystemService(ActivityManager.class);
            for (RunningAppProcessInfo info : activityManager.getRunningAppProcesses()) {
                if (info.processName.contains("MediaCodecResourceTestLowPriorityProcess")) {
                    sLowPriorityPid = info.pid;
                    sLowPriorityUid = info.uid;
                }
                if (info.processName.contains("MediaCodecResourceTestHighPriorityProcess")) {
                    sHighPriorityPid = info.pid;
                    sHighPriorityUid = info.uid;
                }
            }
            if (sLowPriorityPid == -1 || sHighPriorityPid == -1) {
                throw new IllegalStateException("No low and high priority processes found.");
            }
        } finally {
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testCreateCodecForAnotherProcessWithoutPermissionsThrows() throws Exception {
        CodecInfo codecInfo = getFirstVideoHardwareCodec();
        assumeTrue("No video hardware codec found.", codecInfo != null);

        boolean wasSecurityExceptionThrown = false;
        try {
            MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                    sLowPriorityPid, sLowPriorityUid);
            fail("No SecurityException thrown when creating a codec for another process");
        } catch (SecurityException ex) {
            // expected
        }
    }

    // A process with lower priority (e.g. background app) should not be able to reclaim
    // MediaCodec resources from a process with higher priority (e.g. foreground app).
    @Test
    public void testLowerPriorityProcessFailsToReclaimResources() throws Exception {
        CodecInfo codecInfo = getFirstVideoHardwareCodec();
        assumeTrue("No video hardware codec found.", codecInfo != null);
        assertTrue("Expected at least one max supported codec instance.",
                codecInfo.maxSupportedInstances > 0);

        List<MediaCodec> mediaCodecList = new ArrayList<>();
        try {
            // This permission is required to create MediaCodecs on behalf of other processes.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.MEDIA_RESOURCE_OVERRIDE_PID);

            // Create more codecs than are supported by the device on behalf of a high-priority
            // process.
            boolean wasInitialInsufficientResourcesExceptionThrown = false;
            for (int i = 0; i <= codecInfo.maxSupportedInstances; ++i) {
                try {
                    MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                            sHighPriorityPid, sHighPriorityUid);
                    mediaCodecList.add(mediaCodec);
                    mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null,
                            /* crypto= */ null, /* flags= */ 0);
                    mediaCodec.start();
                } catch (MediaCodec.CodecException ex) {
                    if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                        wasInitialInsufficientResourcesExceptionThrown = true;
                    } else {
                        throw ex;
                    }
                }
            }
            // For the same process, insufficient resources should be thrown.
            assertTrue(String.format("No MediaCodec.Exception thrown with insufficient"
                    + " resources after creating too many %d codecs for %s on behalf of the"
                    + " same process", codecInfo.maxSupportedInstances, codecInfo.name),
                    wasInitialInsufficientResourcesExceptionThrown);

            // Attempt to create the codec again, but this time, on behalf of a low priority
            // process.
            boolean wasLowPriorityInsufficientResourcesExceptionThrown = false;
            try {
                MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                        sLowPriorityPid, sLowPriorityUid);
                mediaCodecList.add(mediaCodec);
                mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null, /* crypto= */ null,
                        /* flags= */ 0);
                mediaCodec.start();
            } catch (MediaCodec.CodecException ex) {
                if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                    wasLowPriorityInsufficientResourcesExceptionThrown = true;
                } else {
                    throw ex;
                }
            }
            assertTrue(String.format("No MediaCodec.Exception thrown with insufficient"
                    + " resources after creating a follow-up codec for %s on behalf of a lower"
                    + " priority process", codecInfo.mime),
                    wasLowPriorityInsufficientResourcesExceptionThrown);
        } finally {
            for (MediaCodec mediaCodec : mediaCodecList) {
                mediaCodec.release();
            }
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    // A process with higher priority (e.g. foreground app) should be able to reclaim
    // MediaCodec resources from a process with lower priority (e.g. background app).
    @Test
    public void testHigherPriorityProcessReclaimsResources() throws Exception {
        CodecInfo codecInfo = getFirstVideoHardwareCodec();
        assumeTrue("No video hardware codec found.", codecInfo != null);
        assertTrue("Expected at least one max supported codec instance.",
                codecInfo.maxSupportedInstances > 0);

        List<MediaCodec> mediaCodecList = new ArrayList<>();
        try {
            // This permission is required to create MediaCodecs on behalf of other processes.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.MEDIA_RESOURCE_OVERRIDE_PID);

            // Create more codecs than are supported by the device on behalf of a low-priority
            // process.
            boolean wasInitialInsufficientResourcesExceptionThrown = false;
            for (int i = 0; i <= codecInfo.maxSupportedInstances; ++i) {
                try {
                    MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                            sLowPriorityPid, sLowPriorityUid);
                    mediaCodecList.add(mediaCodec);
                    mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null,
                            /* crypto= */ null, /* flags= */ 0);
                    mediaCodec.start();
                } catch (MediaCodec.CodecException ex) {
                    if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                        wasInitialInsufficientResourcesExceptionThrown = true;
                    } else {
                        throw ex;
                    }
                }
            }
            // For the same process, insufficient resources should be thrown.
            assertTrue(String.format("No MediaCodec.Exception thrown with insufficient"
                    + " resources after creating too many %d codecs for %s on behalf of the"
                    + " same process", codecInfo.maxSupportedInstances, codecInfo.mime),
                    wasInitialInsufficientResourcesExceptionThrown);

            // Attempt to create the codec again, but this time, on behalf of a high-priority
            // process.
            boolean wasHighPriorityInsufficientResourcesExceptionThrown = false;
            try {
                MediaCodec mediaCodec = MediaCodec.createByCodecNameForClient(codecInfo.name,
                        sHighPriorityPid, sHighPriorityUid);
                mediaCodecList.add(mediaCodec);
                mediaCodec.configure(codecInfo.mediaFormat, /* surface= */ null, /* crypto= */ null,
                        /* flags= */ 0);
                mediaCodec.start();
            } catch (MediaCodec.CodecException ex) {
                if (ex.getErrorCode() == CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                    wasHighPriorityInsufficientResourcesExceptionThrown = true;
                } else {
                    throw ex;
                }
            }
            assertFalse(String.format("Resource reclaiming should occur when creating a"
                    + " follow-up codec for %s on behalf of a higher priority process, but"
                    + " received an insufficient resource CodecException instead",
                    codecInfo.mime), wasHighPriorityInsufficientResourcesExceptionThrown);
        } finally {
            for (MediaCodec mediaCodec : mediaCodecList) {
                mediaCodec.release();
            }
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }

    // Find the first hardware video decoder and create a media format for it.
    @Nullable
    private CodecInfo getFirstVideoHardwareCodec() {
        MediaCodecList allMediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo mediaCodecInfo : allMediaCodecList.getCodecInfos()) {
            if (mediaCodecInfo.isSoftwareOnly()) {
                continue;
            }
            if (mediaCodecInfo.isEncoder()) {
                continue;
            }
            String mime = mediaCodecInfo.getSupportedTypes()[0];
            CodecCapabilities codecCapabilities = mediaCodecInfo.getCapabilitiesForType(mime);
            VideoCapabilities videoCapabilities = codecCapabilities.getVideoCapabilities();
            if (videoCapabilities != null) {
                int height = videoCapabilities.getSupportedHeights().getUpper();
                int width = videoCapabilities.getSupportedWidthsFor(height).getUpper();
                MediaFormat mediaFormat = new MediaFormat();
                mediaFormat.setString(MediaFormat.KEY_MIME, mime);
                mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
                mediaFormat.setInteger(MediaFormat.KEY_WIDTH, width);
                return new CodecInfo(mediaCodecInfo.getName(),
                        codecCapabilities.getMaxSupportedInstances(), mime, mediaFormat);
            }
        }
        return null;
    }
}
