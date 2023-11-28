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

package android.virtualdevice.cts.applaunch;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for IME behavior on virtual devices.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(Flags.FLAG_VDM_CUSTOM_IME)
public class VirtualDeviceImeTest {

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getContext();
    private final InputMethodManager mInputMethodManager =
            mContext.getSystemService(InputMethodManager.class);

    @Before
    public void setUp() throws Exception {
        assumeTrue(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS));
    }

    /** The virtualDeviceOnly attribute is propagated to InputMethodInfo. */
    @ApiTest(apis = {"android.R.attr#isVirtualDeviceOnly"})
    @Test
    public void virtualDeviceOnlyIme_reflectedInInputMethodInfo() {
        final InputMethodInfo virtualDeviceImi =
                getInputMethodInfo(VirtualDeviceTestIme.class.getName());

        assertThat(virtualDeviceImi).isNotNull();
        assertThat(virtualDeviceImi.isVirtualDeviceOnly()).isTrue();

        final InputMethodInfo defaultDeviceImi =
                getInputMethodInfo(DefaultDeviceTestIme.class.getName());

        assertThat(defaultDeviceImi).isNotNull();
        assertThat(defaultDeviceImi.isVirtualDeviceOnly()).isFalse();
    }

    private InputMethodInfo getInputMethodInfo(String className) {
        final String imeId = new ComponentName(mContext, className).flattenToShortString();
        return mInputMethodManager.getInputMethodList().stream()
                .filter(imi -> imi.getId().equals(imeId)).findFirst().orElse(null);
    }

    /**
     * Simple IME implementation forwarding the show input requests to a listener along with a
     * display id.
     */
    public static class DefaultDeviceTestIme extends InputMethodService {}

    /**
     * Simple IME implementation forwarding the show input requests to a listener along with a
     * display id.
     */
    public static class VirtualDeviceTestIme extends InputMethodService {}
}
