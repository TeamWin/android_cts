/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.compatibility.common.util.ApiLevelUtil;

import android.media.MediaDrm;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;

import java.util.UUID;


/**
 * MediaDrm tests covering {@link MediaDrm#getMetrics} and related
 * functionality.
 */
public class MediaDrmMetricsTest extends AndroidTestCase {
    private static final String TAG = MediaDrmMetricsTest.class.getSimpleName();

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0xe2719d58a985b3c9L, 0x781ab030af78d30eL);

    public void testGetMetricsEmpty() throws Exception {
        MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID);
        assertNotNull(drm);

        PersistableBundle metrics = drm.getMetrics();
        assertNotNull(metrics);

        // TODO: Add tests for the proper set of metrics.
        // This is a temporary placeholder for future metrics.
        assertEquals(
            "dummy",
            metrics.getString(MediaDrm.MetricsConstants.TEMPORARY));
    }
}
