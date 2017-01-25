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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
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
}
