/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.provider.FontsContract;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TypefaceTest {
    // generic family name for monospaced fonts
    private static final String MONO = "monospace";
    private static final String DEFAULT = (String)null;
    private static final String INVALID = "invalid-family-name";

    // list of family names to try when attempting to find a typeface with a given style
    private static final String[] FAMILIES =
            { (String) null, "monospace", "serif", "sans-serif", "cursive", "arial", "times" };

    private Context mContext;

    /**
     * Create a typeface of the given style. If the default font does not support the style,
     * a number of generic families are tried.
     * @return The typeface or null, if no typeface with the given style can be found.
     */
    private static Typeface createTypeface(int style) {
        for (String family : FAMILIES) {
            Typeface tf = Typeface.create(family, style);
            if (tf.getStyle() == style) {
                return tf;
            }
        }
        return null;
    }

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testIsBold() {
        Typeface typeface = createTypeface(Typeface.BOLD);
        if (typeface != null) {
            assertEquals(Typeface.BOLD, typeface.getStyle());
            assertTrue(typeface.isBold());
            assertFalse(typeface.isItalic());
        }

        typeface = createTypeface(Typeface.ITALIC);
        if (typeface != null) {
            assertEquals(Typeface.ITALIC, typeface.getStyle());
            assertFalse(typeface.isBold());
            assertTrue(typeface.isItalic());
        }

        typeface = createTypeface(Typeface.BOLD_ITALIC);
        if (typeface != null) {
            assertEquals(Typeface.BOLD_ITALIC, typeface.getStyle());
            assertTrue(typeface.isBold());
            assertTrue(typeface.isItalic());
        }

        typeface = createTypeface(Typeface.NORMAL);
        if (typeface != null) {
            assertEquals(Typeface.NORMAL, typeface.getStyle());
            assertFalse(typeface.isBold());
            assertFalse(typeface.isItalic());
        }
    }

    @Test
    public void testCreate() {
        Typeface typeface = Typeface.create(DEFAULT, Typeface.NORMAL);
        assertNotNull(typeface);
        typeface = Typeface.create(MONO, Typeface.BOLD);
        assertNotNull(typeface);
        typeface = Typeface.create(INVALID, Typeface.ITALIC);
        assertNotNull(typeface);

        typeface = Typeface.create(typeface, Typeface.NORMAL);
        assertNotNull(typeface);
        typeface = Typeface.create(typeface, Typeface.BOLD);
        assertNotNull(typeface);
    }

    @Test
    public void testDefaultFromStyle() {
        Typeface typeface = Typeface.defaultFromStyle(Typeface.NORMAL);
        assertNotNull(typeface);
        typeface = Typeface.defaultFromStyle(Typeface.BOLD);
        assertNotNull(typeface);
        typeface = Typeface.defaultFromStyle(Typeface.ITALIC);
        assertNotNull(typeface);
        typeface = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC);
        assertNotNull(typeface);
    }

    @Test
    public void testConstants() {
        assertNotNull(Typeface.DEFAULT);
        assertNotNull(Typeface.DEFAULT_BOLD);
        assertNotNull(Typeface.MONOSPACE);
        assertNotNull(Typeface.SANS_SERIF);
        assertNotNull(Typeface.SERIF);
    }

    @Test(expected=NullPointerException.class)
    public void testCreateFromAssetNull() {
        // input abnormal params.
        Typeface.createFromAsset(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testCreateFromAssetNullPath() {
        // input abnormal params.
        Typeface.createFromAsset(mContext.getAssets(), null);
    }

    @Test(expected=RuntimeException.class)
    public void testCreateFromAssetInvalidPath() {
        // input abnormal params.
        Typeface.createFromAsset(mContext.getAssets(), "invalid path");
    }

    @Test
    public void testCreateFromAsset() {
        Typeface typeface = Typeface.createFromAsset(mContext.getAssets(), "samplefont.ttf");
        assertNotNull(typeface);
    }

    @Test(expected=NullPointerException.class)
    public void testCreateFromFileByFileReferenceNull() {
        // input abnormal params.
        Typeface.createFromFile((File) null);
    }

    @Test
    public void testCreateFromFileByFileReference() throws IOException {
        File file = new File(obtainPath());
        Typeface typeface = Typeface.createFromFile(file);
        assertNotNull(typeface);
    }

    @Test(expected=RuntimeException.class)
    public void testCreateFromFileWithInvalidPath() throws IOException {
        File file = new File("/invalid/path");
        Typeface.createFromFile(file);
    }

    @Test(expected=NullPointerException.class)
    public void testCreateFromFileByFileNameNull() throws IOException {
        // input abnormal params.
        Typeface.createFromFile((String) null);
    }

    @Test(expected=RuntimeException.class)
    public void testCreateFromFileByInvalidFileName() throws IOException {
        // input abnormal params.
        Typeface.createFromFile("/invalid/path");
    }

    @Test
    public void testCreateFromFileByFileName() throws IOException {
        Typeface typeface = Typeface.createFromFile(obtainPath());
        assertNotNull(typeface);
    }

    private String obtainPath() throws IOException {
        File dir = mContext.getFilesDir();
        dir.mkdirs();
        File file = new File(dir, "test.jpg");
        if (!file.createNewFile()) {
            if (!file.exists()) {
                fail("Failed to create new File!");
            }
        }
        InputStream is = mContext.getAssets().open("samplefont.ttf");
        FileOutputStream fOutput = new FileOutputStream(file);
        byte[] dataBuffer = new byte[1024];
        int readLength = 0;
        while ((readLength = is.read(dataBuffer)) != -1) {
            fOutput.write(dataBuffer, 0, readLength);
        }
        is.close();
        fOutput.close();
        return (file.getPath());
    }

    @Test
    public void testInvalidCmapFont() {
        Typeface typeface = Typeface.createFromAsset(mContext.getAssets(), "bombfont.ttf");
        assertNotNull(typeface);
        Paint p = new Paint();
        final String testString = "abcde";
        float widthDefaultTypeface = p.measureText(testString);
        p.setTypeface(typeface);
        float widthCustomTypeface = p.measureText(testString);
        assertEquals(widthDefaultTypeface, widthCustomTypeface, 1.0f);
    }

    @Test
    public void testInvalidCmapFont2() {
        Typeface typeface = Typeface.createFromAsset(mContext.getAssets(), "bombfont2.ttf");
        assertNotNull(typeface);
        Paint p = new Paint();
        final String testString = "abcde";
        float widthDefaultTypeface = p.measureText(testString);
        p.setTypeface(typeface);
        float widthCustomTypeface = p.measureText(testString);
        assertEquals(widthDefaultTypeface, widthCustomTypeface, 1.0f);
    }

    @Test
    public void testCreateFromAsset_cachesTypeface() {
        Typeface typeface1 = Typeface.createFromAsset(mContext.getAssets(), "bombfont2.ttf");
        assertNotNull(typeface1);

        Typeface typeface2 = Typeface.createFromAsset(mContext.getAssets(), "bombfont2.ttf");
        assertNotNull(typeface2);
        assertSame("Same font asset should return same Typeface object", typeface1, typeface2);

        Typeface typeface3 = Typeface.createFromAsset(mContext.getAssets(), "bombfont.ttf");
        assertNotNull(typeface3);
        assertNotSame("Different font asset should return different Typeface object",
                typeface2, typeface3);

        Typeface typeface4 = Typeface.createFromAsset(mContext.getAssets(), "samplefont.ttf");
        assertNotNull(typeface4);
        assertNotSame("Different font asset should return different Typeface object",
                typeface2, typeface4);
        assertNotSame("Different font asset should return different Typeface object",
                typeface3, typeface4);
    }

    @Test
    public void testBadFont() {
        Typeface typeface = Typeface.createFromAsset(mContext.getAssets(), "ft45987.ttf");
        assertNotNull(typeface);
    }

    @Test
    public void testTypefaceRequestFailureConstantsAreInSync() {
        // Error codes from the provider are positive numbers and are in sync
        assertEquals(FontsContract.Columns.RESULT_CODE_FONT_NOT_FOUND,
                Typeface.FontRequestCallback.FAIL_REASON_FONT_NOT_FOUND);
        assertEquals(FontsContract.Columns.RESULT_CODE_FONT_UNAVAILABLE,
                Typeface.FontRequestCallback.FAIL_REASON_FONT_UNAVAILABLE);
        assertEquals(FontsContract.Columns.RESULT_CODE_MALFORMED_QUERY,
                Typeface.FontRequestCallback.FAIL_REASON_MALFORMED_QUERY);

        // Internal errors are negative
        assertTrue(0 > Typeface.FontRequestCallback.FAIL_REASON_PROVIDER_NOT_FOUND);
        assertTrue(0 > Typeface.FontRequestCallback.FAIL_REASON_WRONG_CERTIFICATES);
        assertTrue(0 > Typeface.FontRequestCallback.FAIL_REASON_FONT_LOAD_ERROR);
    }

    @Test
    public void testTypefaceBuilder_AssetSource() {
        Typeface.Builder builder = Typeface.Builder.obtain();
        try {
            Typeface typeface1 =
                    builder.setSourceFromAsset(mContext.getAssets(), "samplefont.ttf").build();
            assertNotNull(typeface1);

            builder.reset();
            Typeface typeface2 =
                    builder.setSourceFromAsset(mContext.getAssets(), "samplefont.ttf").build();
            assertNotNull(typeface2);
            assertSame("Same font asset should return same Typeface object", typeface1, typeface2);

            builder.reset();
            Typeface typeface3 =
                    builder.setSourceFromAsset(mContext.getAssets(), "samplefont2.ttf").build();
            assertNotNull(typeface3);
            assertNotSame("Different font asset should return different Typeface object",
                    typeface2, typeface3);

            builder.reset();
            Typeface typeface4 =
                    builder.setSourceFromAsset(mContext.getAssets(), "samplefont3.ttf").build();
            assertNotNull(typeface4);
            assertNotSame("Different font asset should return different Typeface object",
                    typeface2, typeface4);
            assertNotSame("Different font asset should return different Typeface object",
                    typeface3, typeface4);

            builder.reset();
            Typeface typeface5 =
                    builder.setSourceFromAsset(mContext.getAssets(), "samplefont.ttf")
                    .setFontVariationSettings("'wdth' 1.0").build();
            assertNotNull(typeface5);
            assertNotSame("Different font font variation should return different Typeface object",
                    typeface2, typeface5);

            builder.reset();
            Typeface typeface6 =
                    builder.setSourceFromAsset(mContext.getAssets(), "samplefont.ttf")
                    .setFontVariationSettings("'wdth' 2.0").build();
            assertNotNull(typeface6);
            assertNotSame("Different font font variation should return different Typeface object",
                    typeface2, typeface6);
            assertNotSame("Different font font variation should return different Typeface object",
                    typeface5, typeface6);

            // TODO: Add ttc index case. Need TTC file for CTS.
        } finally {
            builder.recycle();
        }
    }

    @Test
    public void testTypefaceBuilder_FileSource() {
        Typeface.Builder builder = Typeface.Builder.obtain();
        try {
            File file = new File(obtainPath());
            Typeface typeface1 = builder.setSourceFromFile(file).build();
            assertNotNull(typeface1);

            builder.reset();
            Typeface typeface2 = builder.setSourceFromFilePath(file.getAbsolutePath()).build();
            assertNotNull(typeface2);

            builder.reset();
            Typeface typeface3 = builder.setSourceFromFile(file)
                    .setFontVariationSettings("'wdth' 1.0")
                    .build();
            assertNotNull(typeface3);
            assertNotSame(typeface1, typeface3);
            assertNotSame(typeface2, typeface3);

            // TODO: Add ttc index case. Need TTC file for CTS.
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            builder.recycle();
        }
    }

    @Test
    public void testTypefaceBuilder_FileSourceFD() {
        Typeface.Builder builder = Typeface.Builder.obtain();
        try (FileInputStream fis = new FileInputStream(obtainPath())) {
            assertNotNull(builder.setSourceFromFile(fis.getFD()).build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            builder.recycle();
        }
    }

    @Test
    public void testTypeface_SupportedCmapEncodingTest() {
        // We support the following combinations of cmap platfrom/endcoding pairs.
        String[] fontPaths = {
            "CmapPlatform0Encoding0.ttf",  // Platform ID == 0, Encoding ID == 0
            "CmapPlatform0Encoding1.ttf",  // Platform ID == 0, Encoding ID == 1
            "CmapPlatform0Encoding2.ttf",  // Platform ID == 0, Encoding ID == 2
            "CmapPlatform0Encoding3.ttf",  // Platform ID == 0, Encoding ID == 3
            "CmapPlatform0Encoding4.ttf",  // Platform ID == 0, Encoding ID == 4
            "CmapPlatform0Encoding6.ttf",  // Platform ID == 0, Encoding ID == 6
            "CmapPlatform3Encoding1.ttf",  // Platform ID == 3, Encoding ID == 1
            "CmapPlatform3Encoding10.ttf",  // Platform ID == 3, Encoding ID == 10
        };

        for (String fontPath : fontPaths) {
            Typeface typeface = Typeface.createFromAsset(mContext.getAssets(), fontPath);
            assertNotNull(typeface);
            Paint p = new Paint();
            final String testString = "a";
            float widthDefaultTypeface = p.measureText(testString);
            p.setTypeface(typeface);
            float widthCustomTypeface = p.measureText(testString);
            // The width of the glyph "a" from above fonts are 2em.
            // So the width should be different from the default one.
            assertNotEquals(widthDefaultTypeface, widthCustomTypeface, 1.0f);
        }
    }
}
