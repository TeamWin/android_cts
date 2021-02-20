/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.media.NotProvisionedException;
import android.util.Log;
import androidx.test.runner.AndroidJUnit4;

import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertTrue;
import static org.testng.Assert.assertThrows;

@NonMediaMainlineTest
@RunWith(AndroidJUnit4.class)
public class MediaDrmTest {

    private final String TAG = this.getClass().getName();

    private void testSingleScheme(UUID scheme) throws Exception {
        MediaDrm md = new MediaDrm(scheme);
        assertTrue(md.getOpenSessionCount() <= md.getMaxSessionCount());
        assertThrows(() -> {
            md.closeSession(null);
        });
        md.close();
    }

    @Test
    public void testSupportedCryptoSchemes() throws Exception {
        List<UUID> supportedCryptoSchemes = MediaDrm.getSupportedCryptoSchemes();
        if (supportedCryptoSchemes.isEmpty()) {
            Log.w(TAG, "No supported crypto schemes reported");
        }
        for (UUID scheme : supportedCryptoSchemes) {
            Log.d(TAG, "supported scheme: " + scheme.toString());
            assertTrue(MediaDrm.isCryptoSchemeSupported(scheme));
            testSingleScheme(scheme);
        }
    }

    @Test
    public void testGetLogMessages() throws Exception {
        List<UUID> supportedCryptoSchemes = MediaDrm.getSupportedCryptoSchemes();
        for (UUID scheme : supportedCryptoSchemes) {
            MediaDrm drm = new MediaDrm(scheme);
            try {
                byte[] sid = drm.openSession();
                drm.closeSession(sid);
            } catch (NotProvisionedException e) {
                Log.w(TAG, scheme.toString() + ": not provisioned", e);
            }

            List<MediaDrm.LogMessage> logMessages;
            try {
                logMessages = drm.getLogMessages();
                Assert.assertFalse("Empty logs", logMessages.isEmpty());
            } catch (UnsupportedOperationException e) {
                Log.w(TAG, scheme.toString() + ": no LogMessage support", e);
                continue;
            }

            long end = System.currentTimeMillis();
            for (MediaDrm.LogMessage log: logMessages) {
                Assert.assertTrue("Log occurred in future",
                        log.timestampMillis <= end);
                Assert.assertTrue("Invalid log priority",
                        log.priority >= Log.VERBOSE &&
                                log.priority <= Log.ASSERT);
                Log.i(TAG, log.toString());
            }
        }
    }
}
