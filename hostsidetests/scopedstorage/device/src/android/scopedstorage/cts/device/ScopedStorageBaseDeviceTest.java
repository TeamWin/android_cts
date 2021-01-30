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

package android.scopedstorage.cts.device;

import static android.scopedstorage.cts.lib.TestUtils.getExternalFilesDir;
import static android.scopedstorage.cts.lib.TestUtils.pollForExternalStorageState;
import static android.scopedstorage.cts.lib.TestUtils.resetDefaultExternalStorageVolume;
import static android.scopedstorage.cts.lib.TestUtils.setExternalStorageVolume;
import static android.scopedstorage.cts.lib.TestUtils.setupDefaultDirectories;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.provider.MediaStore;
import android.scopedstorage.cts.lib.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
class ScopedStorageBaseDeviceTest {
    @BeforeClass
    public static void setup() throws Exception {
        createPublicVolume();
        setupStorage();
    }

    private static void createPublicVolume() throws Exception {
        if (TestUtils.getCurrentPublicVolumeName() == null) {
            TestUtils.createNewPublicVolume();
        }
    }

    private static void setupStorage() throws Exception {
        if (!getContext().getPackageManager().isInstantApp()) {
            pollForExternalStorageState();
            getExternalFilesDir().mkdirs();
        }
    }

    void setupExternalStorage(String volumeName) {
        assertThat(volumeName).isNotNull();
        if (volumeName.equals(MediaStore.VOLUME_EXTERNAL)) {
            resetDefaultExternalStorageVolume();
            TestUtils.assertDefaultVolumeIsPrimary();
        } else {
            setExternalStorageVolume(volumeName);
            TestUtils.assertDefaultVolumeIsPublic();
        }
        setupDefaultDirectories();
    }

    static List<String> getTestParameters() {
        return Arrays.asList(
                MediaStore.VOLUME_EXTERNAL,
                TestUtils.getCurrentPublicVolumeName()
        );
    }
}
