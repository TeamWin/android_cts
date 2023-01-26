/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.display.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.view.Display.HdrCapabilities;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class HdrConversionTest {
    private DisplayManager mDisplayManager;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.MODIFY_HDR_CONVERSION_MODE);

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue("Need an Android TV device to run this test.", FeatureUtil.isTV());
        assertTrue("Physical display is expected.", DisplayUtil.isDisplayConnected(context)
                || MediaUtils.onCuttlefish());

        mDisplayManager = context.getSystemService(DisplayManager.class);
    }

    @Test
    public void testSetHdrConversionModeThrowsExceptionWithInvalidArgument() {
        final HdrConversionMode hdrConversionMode1 = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_SYSTEM,
                HdrCapabilities.HDR_TYPE_DOLBY_VISION);
        assertThrows(
                "Preferred HDR output type should not be set if the conversion mode is "
                        + "PASSTHROUGH or SYSTEM",
                IllegalArgumentException.class,
                () -> mDisplayManager.setHdrConversionMode(hdrConversionMode1));

        final HdrConversionMode hdrConversionMode2 = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_PASSTHROUGH,
                HdrCapabilities.HDR_TYPE_DOLBY_VISION);
        assertThrows(
                "Preferred HDR output type should not be set if the conversion mode is "
                        + "PASSTHROUGH or SYSTEM",
                IllegalArgumentException.class,
                () -> mDisplayManager.setHdrConversionMode(hdrConversionMode2));
    }

    @Test
    public void testSetHdrConversionMode() {
        HdrConversionMode hdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_SYSTEM);
        mDisplayManager.setHdrConversionMode(hdrConversionMode);
        assertEquals(hdrConversionMode.getConversionMode(),
                mDisplayManager.getHdrConversionMode().getConversionMode());

        hdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_PASSTHROUGH);
        mDisplayManager.setHdrConversionMode(hdrConversionMode);
        assertEquals(hdrConversionMode.getConversionMode(),
                mDisplayManager.getHdrConversionMode().getConversionMode());

        hdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_FORCE,
                HdrCapabilities.HDR_TYPE_DOLBY_VISION);
        mDisplayManager.setHdrConversionMode(hdrConversionMode);
        assertEquals(hdrConversionMode.getConversionMode(),
                mDisplayManager.getHdrConversionMode().getConversionMode());
        assertEquals(hdrConversionMode.getPreferredHdrOutputType(),
                mDisplayManager.getHdrConversionMode().getPreferredHdrOutputType());
    }

    @Test
    public void testGetSupportedHdrOutputTypes() {
        int[] hdrTypes = mDisplayManager.getSupportedHdrOutputTypes();
        List<Integer> permissibleHdrTypes = new ArrayList<>(Arrays.asList(
                HdrCapabilities.HDR_TYPE_DOLBY_VISION,
                HdrCapabilities.HDR_TYPE_HDR10,
                HdrCapabilities.HDR_TYPE_HLG,
                HdrCapabilities.HDR_TYPE_HDR10_PLUS));
        for (int hdrType : hdrTypes) {
            assertTrue("HDR types returned from getSupportedHdrOutputTypes should be one of"
                    + Arrays.toString(permissibleHdrTypes.toArray()),
                    permissibleHdrTypes.contains(hdrType));
        }
    }
}
