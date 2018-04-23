/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.permission.cts;

import static android.Manifest.permission.OBSERVE_APP_USAGE;

import static org.junit.Assert.assertTrue;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ObserveAppUsagePermissionTest {

    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        mPackageManager = InstrumentationRegistry.getTargetContext().getPackageManager();
    }

    @Test
    public void testNumberOfAppsWithPermission() {
        final List<PackageInfo> packagesWithPerm = mPackageManager.getPackagesHoldingPermissions(
                new String[]{OBSERVE_APP_USAGE}, 0);
        assertTrue("At most one app can hold the permission " + OBSERVE_APP_USAGE
                + ", but found more: " + packagesWithPerm, packagesWithPerm.size() <= 1);
    }
}
