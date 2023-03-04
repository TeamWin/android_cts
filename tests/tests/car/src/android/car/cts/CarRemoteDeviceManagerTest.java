/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;

import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager;
import android.car.test.ApiCheckerRule;
import android.content.pm.PackageInfo;
import android.platform.test.annotations.AppModeFull;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Test relies on other server to connect to.")
public final class CarRemoteDeviceManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarRemoteDeviceManagerTest.class.getSimpleName();

    private CarRemoteDeviceManager mRemoteDeviceManager;
    private CarOccupantZoneManager mOccupantZoneManager;

    // TODO(b/242350638): add missing annotations, remove (on child bug of 242350638)
    @Override
    protected void configApiCheckerRule(ApiCheckerRule.Builder builder) {
        Log.w(TAG, "Disabling API requirements check");
        builder.disableAnnotationsCheck();
    }

    @Before
    public void setUp() {
        mRemoteDeviceManager = getCar().getCarManager(CarRemoteDeviceManager.class);
        // CarRemoteDeviceManager is available on multi-display builds only.
        // TODO(b/265091454): annotate the test with @RequireMultipleUsersOnMultipleDisplays.
        assumeNotNull(
                "Skip the test because CarRemoteDeviceManager is not available on this build",
                mRemoteDeviceManager);

        mOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);
    }

    @Test
    @ApiTest(apis = {"android.car.CarRemoteDeviceManager#getEndpointPackageInfo"})
    public void testGetEndpointPackageInfo() {
        OccupantZoneInfo myZone = mOccupantZoneManager.getMyOccupantZone();
        PackageInfo myPackageInfo = mRemoteDeviceManager.getEndpointPackageInfo(myZone);

        assertThat(myPackageInfo).isNotNull();

        List<OccupantZoneInfo> allZones = mOccupantZoneManager.getAllOccupantZones();
        for (OccupantZoneInfo zone : allZones) {
            PackageInfo packageInfo = mRemoteDeviceManager.getEndpointPackageInfo(zone);

            // The package info of the peer apps should have the same package name & signing info
            // & long version code since they run on the same Android instance.
            // TODO(b/257118327): update this test for multi-SoC.
            if (packageInfo != null) {
                assertThat(packageInfo.packageName).isEqualTo(myPackageInfo.packageName);
                assertThat(packageInfo.signingInfo).isEqualTo(myPackageInfo.signingInfo);
                assertThat(packageInfo.getLongVersionCode())
                        .isEqualTo(myPackageInfo.getLongVersionCode());
            }
        }
    }
}
