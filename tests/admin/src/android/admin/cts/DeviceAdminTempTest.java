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

package android.admin.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.enterprise.DeviceState;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnWorkProfile;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.os.UserManager;

@RunWith(AndroidJUnit4.class)
public class DeviceAdminTempTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @RequireRunOnWorkProfile
    @Test
    public void testRunningOnWorkProfile() {
        assertThat(sContext.getSystemService(UserManager.class).isManagedProfile()).isTrue();
    }
}
