/*
 * Copyright 2015 The Android Open Source Project
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

package android.media.misc.cts;

import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.media.MediaFormat;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class ResourceManagerTest {

    public static final boolean FIRST_SDK_IS_AT_LEAST_U =
            ApiLevelUtil.isFirstApiAfter(Build.VERSION_CODES.TIRAMISU);
    public static final boolean SDK_IS_AT_LEAST_U =
            ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU);

    @Rule
    public final ActivityTestRule<ResourceManagerStubActivity> mActivityRule =
            new ActivityTestRule<>(ResourceManagerStubActivity.class, false, false);

    private void doTestReclaimResource(int type1, int type2,
            boolean highResolutionForActivity1,
            boolean highResolutionForActivity2) throws Exception {
        boolean highResolution = highResolutionForActivity1 || highResolutionForActivity2;
        // Run high resolution test case only when the devices shipped on U.
        if (SDK_IS_AT_LEAST_U || !highResolution) {
            ResourceManagerStubActivity activity = mActivityRule.launchActivity(new Intent());
            activity.testReclaimResource(type1, type2, highResolutionForActivity1,
                    highResolutionForActivity2);
            activity.finish();
        } else {
            assumeTrue("The Device should be on at least SDK U", false);
        }
    }

    private void doTestVideoCodecReclaim(boolean highResolution, String mimeType)
            throws Exception {
        // Run high resolution test case only when the devices shipped on U.
        if (SDK_IS_AT_LEAST_U || !highResolution) {
            ResourceManagerStubActivity activity = mActivityRule.launchActivity(new Intent());
            activity.testVideoCodecReclaim(highResolution, mimeType);
            activity.finish();
        } else {
            assumeTrue("The Device should be on at least SDK U", false);
        }
    }

    // Following 6 test cases verify the below usecase:
    // Activity1 creates allowable number of secure and/or unsecure decoders at
    // lowest resolution supported in the background.
    // Activity2 creates 1 secure or unsecure decoder at the lowest supported resolution.
    @Test
    public void testReclaimResourceNonsecureVsNonsecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, false);
    }

    @Test
    public void testReclaimResourceNonsecureVsSecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_SECURE, false, false);
    }

    @Test
    public void testReclaimResourceSecureVsNonsecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_SECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, false);
    }

    @Test
    public void testReclaimResourceSecureVsSecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_SECURE,
                ResourceManagerTestActivityBase.TYPE_SECURE, false, false);
    }

    @Test
    public void testReclaimResourceMixVsNonsecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, false);
    }

    @Test
    public void testReclaimResourceMixVsSecure() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_SECURE, false, false);
    }

    // Following 6 test cases verify the below usecase:
    // Activity1 creates allowable number of secure and/or unsecure decoders at
    // highest resolution supported in the background.
    // Activity2 creates 1 secure or unsecure decoder at the highest supported resolution.
    @Test
    public void testReclaimResourceNonsecureVsNonsecureHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, true);
    }

    @Test
    public void testReclaimResourceNonsecureVsSecureHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_SECURE, true, true);
    }

    @Test
    public void testReclaimResourceSecureVsNonsecureHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_SECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, true);
    }

    @Test
    public void testReclaimResourceSecureVsSecureHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_SECURE,
                ResourceManagerTestActivityBase.TYPE_SECURE, true, true);
    }

    @Test
    public void testReclaimResourceMixVsNonsecureHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, true);
    }

    @Test
    public void testReclaimResourceMixVsSecureHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_SECURE, true, true);
    }

    // Following 6 test cases verify the below usecase:
    // Activity1 creates allowable number of secure and/or unsecure decoders at
    // lowest resolution supported in the background.
    // Activity2 creates 1 secure or unsecure decoder at the highest supported resolution.
    @Test
    public void testReclaimResourceNonsecureVsNonsecureLowHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, true);
    }

    @Test
    public void testReclaimResourceNonsecureVsSecureLowHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, true);
    }

    @Test
    public void testReclaimResourceSecureVsNonsecureLowHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, true);
    }

    @Test
    public void testReclaimResourceSecureVsSecureLowHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, true);
    }

    @Test
    public void testReclaimResourceMixVsNonsecureLowHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, false, true);
    }

    @Test
    public void testReclaimResourceMixVsSecureLowHighResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_SECURE, false, true);
    }

    // Following 6 test cases verify the below usecase:
    // Activity1 creates allowable number of secure and/or unsecure decoders at
    // highest resolution supported in the background.
    // Activity2 creates 1 secure or unsecure decoder at the lowest supported resolution.
    @Test
    public void testReclaimResourceNonsecureVsNonsecureHighLowResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, false);
    }

    @Test
    public void testReclaimResourceNonsecureVsSecureHighLowResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, false);
    }

    @Test
    public void testReclaimResourceSecureVsNonsecureHighLowResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, false);
    }

    @Test
    public void testReclaimResourceSecureVsSecureHighLowResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_NONSECURE,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, false);
    }

    @Test
    public void testReclaimResourceMixVsNonsecureHighLowResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_NONSECURE, true, false);
    }

    @Test
    public void testReclaimResourceMixVsSecureHighLowResolution() throws Exception {
        doTestReclaimResource(ResourceManagerTestActivityBase.TYPE_MIX,
                ResourceManagerTestActivityBase.TYPE_SECURE, true, false);
    }

    // Activity1 creates allowable number of AVC decoders and encoders at
    // lowest resolution supported in the background.
    // Activity2 starts MediaRecorder using camera and AVC encoder at
    // the lowest supported resolution.
    @Test
    public void testAVCVideoCodecReclaimLowResolution() throws Exception {
        doTestVideoCodecReclaim(false, MediaFormat.MIMETYPE_VIDEO_AVC);
    }

    // Activity1 creates allowable number of AVC decoders and encoders at
    // highest resolution supported in the background.
    // Activity2 starts MediaRecorder using camera and AVC encoder at
    // the highest supported resolution.
    @Test
    public void testAVCVideoCodecReclaimHighResolution() throws Exception {
        doTestVideoCodecReclaim(true, MediaFormat.MIMETYPE_VIDEO_AVC);
    }

    // Activity1 creates allowable number of HEVC decoders and encoders at
    // lowest resolution supported in the background.
    // Activity2 starts MediaRecorder using camera and HEVC encoder at
    // the lowest supported resolution.
    @Test
    public void testHEVCVideoCodecReclaimLowResolution() throws Exception {
        doTestVideoCodecReclaim(false, MediaFormat.MIMETYPE_VIDEO_HEVC);
    }

    // Activity1 creates allowable number of HEVC decoders and encoders at
    // highest resolution supported in the background.
    // Activity2 starts MediaRecorder using camera and HEVC encoder at
    // the highest supported resolution.
    @Test
    public void testHEVCVideoCodecReclaimHighResolution() throws Exception {
        doTestVideoCodecReclaim(true, MediaFormat.MIMETYPE_VIDEO_HEVC);
    }
}
