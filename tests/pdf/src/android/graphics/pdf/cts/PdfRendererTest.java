/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.graphics.pdf.cts;

import static android.graphics.pdf.cts.Utils.A4_HEIGHT_PTS;
import static android.graphics.pdf.cts.Utils.A4_PORTRAIT;
import static android.graphics.pdf.cts.Utils.A4_WIDTH_PTS;
import static android.graphics.pdf.cts.Utils.A5_PORTRAIT;
import static android.graphics.pdf.cts.Utils.createRenderer;
import static android.graphics.pdf.cts.Utils.verifyException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRenderer.Page;
import android.graphics.pdf.cts.R;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * All test for {@link PdfRenderer} beside the valid transformation parameter tests of {@link
 * PdfRenderer.Page#render}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PdfRendererTest {
    private static final int A5_PORTRAIT_PRINTSCALING_DEFAULT =
            R.raw.a5_portrait_rgbb_1_6_printscaling_default;
    private static final int A5_PORTRAIT_PRINTSCALING_NONE =
            R.raw.a5_portrait_rgbb_1_6_printscaling_none;
    private static final int TWO_PAGES = R.raw.two_pages;
    private static final int PROTECTED_PDF = R.raw.protected_pdf;

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void constructRendererNull() throws Exception {
        verifyException(() -> new PdfRenderer(null), NullPointerException.class);
    }

    @Test
    public void constructRendererFromNonPDF() throws Exception {
        // Open jpg as if it was a PDF
        verifyException(() -> createRenderer(R.raw.testimage, mContext), IOException.class);
    }

    @Test
    public void constructRendererFromProtectedPDF() throws Exception {
        verifyException(() -> createRenderer(PROTECTED_PDF, mContext), SecurityException.class);
    }

    @Test
    public void rendererRecoversAfterFailure() throws Exception {
        // Create rendered to prevent lib from being unloaded
        PdfRenderer firstRenderer = createRenderer(A4_PORTRAIT, mContext);

        verifyException(() -> createRenderer(PROTECTED_PDF, mContext), SecurityException.class);

        // We can create new renderers after we failed to create one
        PdfRenderer renderer = createRenderer(TWO_PAGES, mContext);
        renderer.close();

        firstRenderer.close();
    }

    @Test
    public void useRendererAfterClose() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        renderer.close();

        verifyException(renderer::close, IllegalStateException.class);
        verifyException(renderer::getPageCount, IllegalStateException.class);
        verifyException(renderer::shouldScaleForPrinting, IllegalStateException.class);
        verifyException(() -> renderer.openPage(0), IllegalStateException.class);
    }

    @Test
    public void usePageAfterClose() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        Page page = renderer.openPage(0);
        page.close();

        // Legacy behavior: The properties are cached, hence they are still available after the page
        //                  is closed
        page.getHeight();
        page.getWidth();
        page.getIndex();
        verifyException(page::close, IllegalStateException.class);

        // Legacy support. An IllegalStateException would be nice by unfortunately the legacy
        // implementation returned NullPointerException
        verifyException(() -> page.render(null, null, null, Page.RENDER_MODE_FOR_DISPLAY),
                NullPointerException.class);

        renderer.close();
    }

    @Test
    public void closeWithOpenPage() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        Page page = renderer.openPage(0);

        verifyException(renderer::close, IllegalStateException.class);

        page.close();
        renderer.close();
    }

    @Test
    public void openTwoPages() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            // Cannot open two pages at once
            Page page = renderer.openPage(0);
            verifyException(() -> renderer.openPage(1), IllegalStateException.class);

            page.close();
        }
    }

    @Test
    public void testPageCount() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            assertEquals(2, renderer.getPageCount());
        }
    }

    @Test
    public void testOpenPage() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            verifyException(() -> renderer.openPage(-1), IllegalArgumentException.class);
            Page page0 = renderer.openPage(0);
            page0.close();
            Page page1 = renderer.openPage(1);
            page1.close();
            verifyException(() -> renderer.openPage(2), IllegalArgumentException.class);
        }
    }

    @Test
    public void testPageSize() throws Exception {
        try (PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
             Page page = renderer.openPage(0)) {
            assertEquals(A4_HEIGHT_PTS, page.getHeight());
            assertEquals(A4_WIDTH_PTS, page.getWidth());
        }
    }

    @Test
    public void testPrintScaleDefault() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT, mContext)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16Default() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_DEFAULT, mContext)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16None() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_NONE, mContext)) {
            assertFalse(renderer.shouldScaleForPrinting());
        }
    }
}
