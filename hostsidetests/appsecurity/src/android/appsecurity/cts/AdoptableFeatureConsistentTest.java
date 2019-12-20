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

package android.appsecurity.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static android.appsecurity.cts.AdoptableHostTest.FEATURE_ADOPTABLE_STORAGE;
import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Set of tests that verify behavior of adopted storage media's consistency between the feature
 * flag and what we sniffed from the underlying fstab.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull(reason = "Instant applications can only be installed on internal storage")
public class AdoptableFeatureConsistentTest extends BaseHostJUnit4Test {

    private String mHasAdoptable;

    @Before
    public void setUp() throws Exception {
        // Caches the initial state of adoptable feature to restore after the tests
        mHasAdoptable = getDevice().executeShellCommand("sm has-adoptable").trim();
    }

    @After
    public void tearDown() throws Exception {
        // Restores the initial cache value
        getDevice().executeShellCommand("sm set-force-adoptable" + mHasAdoptable);
    }

    @Test
    public void testFeatureTrue() throws Exception {
        getDevice().executeShellCommand("sm set-force-adoptable true");
        checkConsistency();
    }

    @Test
    public void testFeatureFalse() throws Exception {
        getDevice().executeShellCommand("sm set-force-adoptable false");
        checkConsistency();
    }

    private void checkConsistency() throws Exception {
        // Reboots the device and blocks until the boot complete flag is set.
        getDevice().rebootUntilOnline();
        assertTrue("Device failed to boot", getDevice().waitForBootComplete(120000));

        final boolean hasFeature = getDevice().hasFeature(FEATURE_ADOPTABLE_STORAGE);
        final boolean hasFstab = Boolean.parseBoolean(getDevice()
                .executeShellCommand("sm has-adoptable").trim());
        if (hasFeature != hasFstab) {
            fail("Inconsistent adoptable storage status; feature claims " + hasFeature
                    + " but fstab claims " + hasFstab);
        }
    }
}
