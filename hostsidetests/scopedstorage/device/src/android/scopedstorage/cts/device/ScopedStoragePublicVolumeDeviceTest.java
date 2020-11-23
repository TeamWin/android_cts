/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.scopedstorage.cts.device;

import static com.google.common.truth.Truth.assertThat;

import android.scopedstorage.cts.lib.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test suite to run ScopedStorageDeviceTest on a public volume.
 */
@RunWith(AndroidJUnit4.class)
public class ScopedStoragePublicVolumeDeviceTest extends ScopedStorageDeviceTest {

    @BeforeClass
    public static void setupPublicVolume() throws Exception {
        TestUtils.createNewPublicVolume();
        final String volumeName = TestUtils.getPublicVolumeName();
        assertThat(volumeName).isNotNull();
        TestUtils.setExternalStorageVolume(volumeName);
    }

    @Test
    public void verifySetup() {
        assertThat(TestUtils.getExternalFilesDir().getAbsolutePath().indexOf("emulated"))
                .isEqualTo(-1);
    }

    @AfterClass
    public static void deletePublicVolumes() throws Exception {
        TestUtils.resetDefaultExternalStorageVolume();
    }
}
