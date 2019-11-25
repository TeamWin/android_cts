/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import static android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON;

import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Test to verify accessibility button targeting sdk 29 APIs.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class AccessibilityButtonSdk29Test {

    private InstrumentedAccessibilityServiceTestRule<StubAccessibilityButtonSdk29Service>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    StubAccessibilityButtonSdk29Service.class);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mServiceRule)
            .around(mDumpOnFailureRule);

    private StubAccessibilityButtonSdk29Service mService;
    private AccessibilityServiceInfo mServiceInfo;

    @Before
    public void setUp() {
        mService = mServiceRule.getService();
        mServiceInfo = mService.getServiceInfo();

        assertTrue(mService.getApplicationInfo().targetSdkVersion == Build.VERSION_CODES.Q);
        assertTrue((mServiceInfo.flags & FLAG_REQUEST_ACCESSIBILITY_BUTTON)
                == FLAG_REQUEST_ACCESSIBILITY_BUTTON);
    }

    @Test
    public void testUpdateRequestAccessibilityButtonFlag_succeeds() {
        mServiceInfo.flags &= ~FLAG_REQUEST_ACCESSIBILITY_BUTTON;
        mService.setServiceInfo(mServiceInfo);
        assertTrue("Update flagRequestAccessibilityButton should succeed",
                mService.getServiceInfo().flags == mServiceInfo.flags);
    }
}
