/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.cts.R;
import android.hardware.input.cts.InputAssistantActivity;
import android.server.wm.WindowManagerStateHelper;
import android.speech.RecognizerIntent;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UsbVoiceCommandTest extends InputHidTestCase {
    private static final String TAG = "UsbVoiceCommandTest";

    private final UiDevice mUiDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final PackageManager mPackageManager =
            InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
    private final Intent mVoiceIntent;
    private final List<String> mExcludedPackages = new ArrayList<String>();

    // Simulates the behavior of Google Gamepad with Voice Command buttons.
    public UsbVoiceCommandTest() {
        super(R.raw.google_gamepad_usb_register);
        mVoiceIntent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        mVoiceIntent.putExtra(RecognizerIntent.EXTRA_SECURE, true);
    }

    private void setPackageState(boolean enabled) throws Exception {
        runWithShellPermissionIdentity(mUiAutomation, () -> {
            for (int i = 0; i < mExcludedPackages.size(); i++) {
                if (enabled) {
                    mUiDevice.executeShellCommand("pm enable " + mExcludedPackages.get(i));
                } else {
                    mUiDevice.executeShellCommand("pm disable " + mExcludedPackages.get(i));
                }
            }
        });
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mUiAutomation.adoptShellPermissionIdentity();
        List<ResolveInfo> list = mPackageManager.queryIntentActivities(mVoiceIntent,
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);

        for (int i = 0; i < list.size(); i++) {
            ResolveInfo info = list.get(i);
            if (!info.activityInfo.packageName.equals(
                    mActivityRule.getActivity().getPackageName())) {
                mExcludedPackages.add(info.activityInfo.packageName);
            }
        }
        // Set packages state to be disabled.
        setPackageState(false);
    }

    @After
    public void tearDown() throws Exception {
        // Enable the packages.
        setPackageState(true);
        mExcludedPackages.clear();
        super.tearDown();
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.google_gamepad_keyeventtests);
    }

    /**
     * Assistant keyevent is not sent to apps, verify InputAssistantActivity launched and visible.
     */
    @Test
    public void testVoiceAssistantKey() throws Exception {

        final ResolveInfo resolveInfo = mPackageManager.resolveActivity(mVoiceIntent, 0);
        /* Verify InputAssistantActivity is the preferred activity by resolver */
        assertEquals("InputAssistantActivity should be the preferred voice assistant activity",
                mActivityRule.getActivity().getPackageName(),
                resolveInfo.activityInfo.packageName);
        /* Inject assistant key from hid device */
        testInputEvents(R.raw.google_gamepad_assistkey);

        WindowManagerStateHelper wmStateHelper = new WindowManagerStateHelper();

        /* InputAssistantActivity should be visible */
        final ComponentName inputAssistant =
                new ComponentName(mActivityRule.getActivity().getPackageName(),
                        InputAssistantActivity.class.getName());
        wmStateHelper.waitForValidState(inputAssistant);
        wmStateHelper.assertActivityDisplayed(inputAssistant);
    }
}
