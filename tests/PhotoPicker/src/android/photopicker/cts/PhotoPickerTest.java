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

package android.photopicker.cts;

import android.app.Instrumentation;
import android.content.Intent;
import android.photopicker.cts.GetResultActivity;
import android.provider.MediaStore;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Photo Picker Device only tests.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class PhotoPickerTest {

    public static int REQUEST_CODE = 42;

    private GetResultActivity mActivity;

    @Before
    public void setUp() throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final Intent intent = new Intent(inst.getContext(), GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Wake up the device and dismiss the keyguard before the test starts
        final UiDevice device = UiDevice.getInstance(inst);
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        device.executeShellCommand("wm dismiss-keyguard");

        mActivity = (GetResultActivity) inst.startActivitySync(intent);
        // Wait for the UI Thread to become idle.
        inst.waitForIdleSync();
        mActivity.clearResult();
        device.waitForIdle();
    }

    @After
    public void tearDown() throws Exception {
        mActivity.finish();
    }

    /**
     * Simple test to verify that {@code ACTION_PICK_IMAGES} is a valid intent and there is an
     * activity that handles the intent.
     */
    @Test
    public void testSimple() throws Exception {
        Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
    }
}
