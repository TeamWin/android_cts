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

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.FontConfig;
import android.text.FontManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;

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
                assertNotNull("FontManager should provide a FileDescriptor for each system font",
                        font.getFd());
            }
        }
    }

    @Test
    public void testFileDescriptorsAreReadOnly() throws Exception {
        final FontConfig fc = mFontManager.getSystemFonts();

        for (final FontConfig.Family family : fc.getFamilies()) {
            for (final FontConfig.Font font : family.getFonts()) {
                final ParcelFileDescriptor pfd = font.getFd();
                assertNotNull(pfd);
                final FileDescriptor fd = pfd.getFileDescriptor();
                long size = Os.lseek(fd, 0, OsConstants.SEEK_END);
                // Read only mapping should success.
                final long addr = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_SHARED,
                        fd, 0);
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
