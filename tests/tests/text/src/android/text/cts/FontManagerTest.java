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

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.FontConfig;
import android.text.FontManager;

import java.io.FileDescriptor;
import java.util.List;

/**
 * Tests {@link FontManager}.
 */
public class FontManagerTest extends AndroidTestCase {

    private FontManager mFontManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mFontManager = (FontManager) getContext().getSystemService(Context.FONT_SERVICE);
    }

    @SmallTest
    public void testGetSystemFontsData() {
        FontConfig config = mFontManager.getSystemFonts();

        assertNotNull(config);
        assertTrue("There should at least be one font family", config.getFamilies().size() > 0);
        for (int i = 0; i < config.getFamilies().size(); ++i) {
            FontConfig.Family family = config.getFamilies().get(i);
            assertTrue("Each font family should have at least one font",
                    family.getFonts().size() > 0);
            for (int j = 0; j < family.getFonts().size(); ++j) {
                FontConfig.Font font = family.getFonts().get(j);
                assertNotNull("FontManager should provide a FileDescriptor for each system font",
                        font.getFd());
            }
        }
    }

    @SmallTest
    public void testFileDescriptorsAreReadOnly() throws Exception {
        FontConfig fc = mFontManager.getSystemFonts();

        List<FontConfig.Family> families = fc.getFamilies();
        for (int i = 0; i < families.size(); ++i) {
            List<FontConfig.Font> fonts = families.get(i).getFonts();
            for (int j = 0; j < fonts.size(); ++j) {
                ParcelFileDescriptor pfd = fonts.get(j).getFd();
                assertNotNull(pfd);
                FileDescriptor fd = pfd.getFileDescriptor();
                long size = Os.lseek(fd, 0, OsConstants.SEEK_END);
                // Read only mapping should success.
                long addr = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_SHARED, fd, 0);
                Os.munmap(addr, size);

                // Mapping with PROT_WRITE should fail with EPERM.
                try {
                    Os.mmap(0, size, OsConstants.PROT_READ | OsConstants.PROT_WRITE,
                            OsConstants.MAP_SHARED, fd, 0);
                    fail();
                } catch (ErrnoException e) {
                    // EPERM should be raised.
                }
            }
        }
    }
}
