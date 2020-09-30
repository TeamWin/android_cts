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

package android.app.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.app.StatusBarManager;
import android.app.StatusBarManager.DisableInfo;
import android.content.Context;

import android.content.pm.PackageManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StatusBarManagerTest {

    private StatusBarManager mStatusBarManager;
    private Context mContext;

    private boolean isWatch() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    /**
     * Setup
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        assumeFalse("Status bar service not supported", isWatch());
        mStatusBarManager = (StatusBarManager) mContext.getSystemService(
                Context.STATUS_BAR_SERVICE);
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.STATUS_BAR");
    }

    @After
    public void tearDown() {

        if (mStatusBarManager != null) {
            mStatusBarManager.setDisabledForSetup(false);
        }
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }


    /**
     * Test StatusBarManager.setDisabledForSetup(true)
     * @throws Exception
     */
    @Test
    public void testDisableForSetup_setDisabledTrue() throws Exception {
        mStatusBarManager.setDisabledForSetup(true);

        // Check for the default set of disable flags
        assertDefaultFlagsArePresent(mStatusBarManager.getDisableInfo());
    }

    private void assertDefaultFlagsArePresent(DisableInfo info) {
        assertTrue(info.isNotificationPeekingDisabled());
        assertTrue(info.isNavigateToHomeDisabled());
        assertTrue(info.isStatusBarExpansionDisabled());
        assertTrue(info.isRecentsDisabled());
        assertTrue(info.isSearchDisabled());
    }

    /**
     * Test StatusBarManager.setDisabledForSetup(false)
     * @throws Exception
     */
    @Test
    public void testDisableForSetup_setDisabledFalse() throws Exception {
        // First disable, then re-enable
        mStatusBarManager.setDisabledForSetup(true);
        mStatusBarManager.setDisabledForSetup(false);

        DisableInfo info = mStatusBarManager.getDisableInfo();

        assertTrue("Invalid disableFlags", info.areAllComponentsEnabled());
    }

    @Test
    public void testDisableForSimLock_setDisabledTrue() throws Exception {
        mStatusBarManager.setDisabledForSimNetworkLock(true);

        // Check for the default set of disable flags
        assertTrue(mStatusBarManager.getDisableInfo().isStatusBarExpansionDisabled());
    }

    @Test
    public void testDisableForSimLock_setDisabledFalse() throws Exception {
        // First disable, then re-enable
        mStatusBarManager.setDisabledForSimNetworkLock(true);
        mStatusBarManager.setDisabledForSimNetworkLock(false);

        DisableInfo info = mStatusBarManager.getDisableInfo();
        assertTrue("Invalid disableFlags", info.areAllComponentsEnabled());
    }
}
