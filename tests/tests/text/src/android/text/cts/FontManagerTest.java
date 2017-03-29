/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.text.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.FontConfig;
import android.text.FontManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/**
 * Tests {@link FontManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FontManagerTest {

    private FontManager mFontManager;

    @Before
    public void setUp() throws Exception {
        final Context targetContext = InstrumentationRegistry.getTargetContext();
        mFontManager = (FontManager) targetContext.getSystemService(Context.FONT_SERVICE);
    }

    @Test
    public void testGetSystemFontsData() {
        final FontConfig config = mFontManager.getSystemFonts();

        assertNotNull(config);
        assertTrue("There should at least be one font family", config.getFamilies().length > 0);
        for (final FontConfig.Family family : config.getFamilies()) {
            assertTrue("Each font family should have at least one font",
                    family.getFonts().length > 0);
            for (final FontConfig.Font font : family.getFonts()) {
                assertNotNull("FontManager should provide a URI for each system font",
                        font.getUri());
            }
        }
    }

    @Test
    public void testFilesAreReadOnly() throws Exception {
        final FontConfig fc = mFontManager.getSystemFonts();
        ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();

        for (final FontConfig.Family family : fc.getFamilies()) {
            for (final FontConfig.Font font : family.getFonts()) {
                ParcelFileDescriptor fd = cr.openFileDescriptor(font.getUri(), "r");
                fd.close();

                try (ParcelFileDescriptor fd2 = cr.openFileDescriptor(font.getUri(), "w")) {
                    fail();
                } catch (FileNotFoundException e) {
                    // System font file must not be writable.
                }
            }
        }
    }
}
