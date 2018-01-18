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

import android.media.MediaDrm;
import android.media.MediaDrm.MediaDrmStateException;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;
import android.util.Log;
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
        assertTrue(metrics.isEmpty());
    }

    public void testGetMetricsSession() throws Exception {
        MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID);
        assertNotNull(drm);
        byte[] sid = drm.openSession();
        assertNotNull(sid);
        byte[] sid2 = drm.openSession();
        assertNotNull(sid2);

        PersistableBundle metrics = drm.getMetrics();
        assertNotNull(metrics);
        assertEquals(1, metrics.keySet().size());

        assertEquals(2, metrics.getLong(
            MediaDrm.MetricsConstants.OPEN_SESSION_OK_COUNT, -1));
        assertEquals(0, metrics.getLong(
            MediaDrm.MetricsConstants.OPEN_SESSION_ERROR_COUNT));
    }

    public void testGetMetricsGetKeyRequest() throws Exception {
        MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID);
        assertNotNull(drm);
        byte[] sid = drm.openSession();
        assertNotNull(sid);

        try {
          drm.getKeyRequest(sid, null, "", 2, null);
        } catch (MediaDrmStateException e) {
          // Exception expected.
        }

        PersistableBundle metrics = drm.getMetrics();
        assertNotNull(metrics);
        Log.v(TAG, metrics.toString());
        Log.v(TAG, metrics.keySet().toString());
        for(String key : metrics.keySet()) {
          Log.v(TAG, "key, value: " + key + "," + metrics.get(key));
        }

        assertEquals(2, metrics.keySet().size());
        assertEquals(-1, metrics.getLong(
            MediaDrm.MetricsConstants.GET_KEY_REQUEST_OK_COUNT, -1));
        assertEquals(1, metrics.getLong(
            MediaDrm.MetricsConstants.GET_KEY_REQUEST_ERROR_COUNT));
    }
}
