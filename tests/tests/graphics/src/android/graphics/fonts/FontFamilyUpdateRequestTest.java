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

package android.graphics.fonts;

import static android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC;
import static android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT;
import static android.graphics.fonts.FontStyle.FONT_WEIGHT_NORMAL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.ParcelFileDescriptor;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class FontFamilyUpdateRequestTest {

    @Test
    public void font() {
        String postScriptName = "Test";
        FontStyle style = new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT);
        List<FontVariationAxis> axes = Arrays.asList(
                new FontVariationAxis("wght", 100f),
                new FontVariationAxis("wdth", 100f));
        FontFamilyUpdateRequest.Font font = new FontFamilyUpdateRequest.Font(
                postScriptName, style, axes);
        assertThat(font.getPostScriptName()).isEqualTo(postScriptName);
        assertThat(font.getStyle()).isEqualTo(style);
        assertThat(font.getAxes()).containsExactlyElementsIn(axes).inOrder();

        // Invalid parameters
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font(null, style, axes));
        assertThrows(IllegalArgumentException.class, () ->
                new FontFamilyUpdateRequest.Font("", style, axes));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font(postScriptName, null, axes));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font(postScriptName, style, null));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font(postScriptName, style,
                        Collections.singletonList(null)));
    }

    @Test
    public void fontFamily() {
        String name = "test";
        List<FontFamilyUpdateRequest.Font> fonts = Arrays.asList(
                new FontFamilyUpdateRequest.Font("Test",
                        new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT),
                        Collections.emptyList()),
                new FontFamilyUpdateRequest.Font("Test",
                        new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_ITALIC),
                        Collections.emptyList()));
        FontFamilyUpdateRequest.FontFamily fontFamily = new FontFamilyUpdateRequest.FontFamily(
                name, fonts);
        assertThat(fontFamily.getName()).isEqualTo(name);
        assertThat(fontFamily.getFonts()).containsExactlyElementsIn(fonts).inOrder();

        // Invalid parameters
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.FontFamily(null, fonts));
        assertThrows(IllegalArgumentException.class, () ->
                new FontFamilyUpdateRequest.FontFamily("", fonts));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.FontFamily(name, null));
        assertThrows(IllegalArgumentException.class, () ->
                new FontFamilyUpdateRequest.FontFamily(name, Collections.emptyList()));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.FontFamily(name, Collections.singletonList(null)));
    }

    @Test
    public void fontFamilyUpdateRequest() throws Exception {
        // Roboto-Regular.ttf is always available.
        File robotoFile = new File("/system/fonts/Roboto-Regular.ttf");
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(robotoFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
        byte[] signature = new byte[256];
        FontFileUpdateRequest fontFileUpdateRequest = new FontFileUpdateRequest(pfd, signature);

        List<FontFamilyUpdateRequest.Font> fonts = Arrays.asList(
                new FontFamilyUpdateRequest.Font("Roboto-Regular",
                        new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT),
                        Collections.emptyList()),
                new FontFamilyUpdateRequest.Font("Roboto-Regular",
                        new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_ITALIC),
                        Collections.emptyList()));
        FontFamilyUpdateRequest.FontFamily fontFamily1 = new FontFamilyUpdateRequest.FontFamily(
                "test-roboto1", fonts);
        FontFamilyUpdateRequest.FontFamily fontFamily2 = new FontFamilyUpdateRequest.FontFamily(
                "test-roboto2", fonts);

        FontFamilyUpdateRequest request = new FontFamilyUpdateRequest.Builder()
                .addFontFileUpdateRequest(fontFileUpdateRequest)
                .addFontFamily(fontFamily1)
                .addFontFamily(fontFamily2)
                .build();
        assertThat(request.getFontFileUpdateRequests())
                .containsExactly(fontFileUpdateRequest);
        assertThat(request.getFontFamilies()).containsExactly(fontFamily1, fontFamily2).inOrder();
    }
}
