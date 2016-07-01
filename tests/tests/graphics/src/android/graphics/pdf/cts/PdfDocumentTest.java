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

import android.content.Context;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import static android.graphics.pdf.cts.Utils.assertException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link PdfDocument}
 */
@RunWith(AndroidJUnit4.class)
public class PdfDocumentTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void getPagesEmptyDocAfterClose() throws Exception {
        PdfDocument doc = new PdfDocument();
        doc.close();
        assertEquals(0, doc.getPages().size());
    }

    @Test
    public void closeClosedDoc() throws Exception {
        PdfDocument doc = new PdfDocument();
        doc.close();

        // legacy behavior, double close does nothing
        doc.close();
    }

    @Test
    public void writeClosedDoc() throws Exception {
        PdfDocument doc = new PdfDocument();
        doc.close();

        OutputStream os = new FileOutputStream(File.createTempFile("tmp", "pdf",
                mContext.getCacheDir()));

        assertException(() -> doc.writeTo(os), IllegalStateException.class);
    }

    @Test
    public void startPageClosedDoc() throws Exception {
        PdfDocument doc = new PdfDocument();
        doc.close();

        assertException(() -> doc.startPage(new PdfDocument.PageInfo.Builder(100, 100, 0).create()),
                IllegalStateException.class);
    }

    @Test
    public void finishPageTwiceDoc() throws Exception {
        PdfDocument doc = new PdfDocument();

        PdfDocument.Page page = doc
                .startPage(new PdfDocument.PageInfo.Builder(100, 100, 0).create());
        doc.finishPage(page);
        assertException(() -> doc.finishPage(page), IllegalStateException.class);

        doc.close();
    }

    @Test
    public void closeWithOpenPage() throws Exception {
        PdfDocument doc = new PdfDocument();

        PdfDocument.Page page = doc
                .startPage(new PdfDocument.PageInfo.Builder(100, 100, 0).create());
        assertException(doc::close, IllegalStateException.class);
        doc.finishPage(page);
        doc.close();
    }

    @Test
    public void writeEmptyDoc() throws Exception {
        PdfDocument doc = new PdfDocument();

        // Legacy behavior. Writing an empty doc does not fail.
        File pdfFile = File.createTempFile("tmp", "pdf", mContext.getCacheDir());
        try (OutputStream os = new FileOutputStream(pdfFile)) {
            doc.writeTo(os);
        }
        doc.close();
    }

    @Test
    public void writeWithOpenPage() throws Exception {
        PdfDocument doc = new PdfDocument();

        PdfDocument.Page page = doc
                .startPage(new PdfDocument.PageInfo.Builder(100, 100, 0).create());

        File pdfFile = File.createTempFile("tmp", "pdf", mContext.getCacheDir());
        try (OutputStream os = new FileOutputStream(pdfFile)) {
            assertException(() -> doc.writeTo(os), IllegalStateException.class);
        }

        doc.finishPage(page);
        doc.close();
    }

    @Test
    public void openTwoPages() throws Exception {
        PdfDocument doc = new PdfDocument();

        PdfDocument.Page page = doc
                .startPage(new PdfDocument.PageInfo.Builder(100, 100, 0).create());
        assertException(() -> doc.startPage(new PdfDocument.PageInfo.Builder(100, 100, 1).create()),
                IllegalStateException.class);

        doc.finishPage(page);
        doc.close();
    }

    @Test
    public void finishPageFromWrongDoc() throws Exception {
        PdfDocument doc1 = new PdfDocument();
        PdfDocument doc2 = new PdfDocument();

        PdfDocument.Page page1 = doc1
                .startPage(new PdfDocument.PageInfo.Builder(100, 100, 0).create());
        assertException(() -> doc2.finishPage(page1), IllegalStateException.class);

        PdfDocument.Page page2 = doc2
                .startPage(new PdfDocument.PageInfo.Builder(100, 100, 0).create());
        assertException(() -> doc1.finishPage(page2), IllegalStateException.class);

        doc1.finishPage(page1);
        doc2.finishPage(page2);
        doc1.close();
        doc2.close();
    }

    @Test
    public void writeTwoPageDocWithSameIndex() throws Exception {
        PdfDocument doc = new PdfDocument();

        PdfDocument.Page page0 = doc
                .startPage(new PdfDocument.PageInfo.Builder(101, 100, 0).create());
        doc.finishPage(page0);
        PdfDocument.Page page1 = doc
                .startPage(new PdfDocument.PageInfo.Builder(201, 200, 0).create());
        doc.finishPage(page1);
        assertEquals(2, doc.getPages().size());

        File pdfFile = File.createTempFile("tmp", "pdf", mContext.getCacheDir());
        try (OutputStream os = new FileOutputStream(pdfFile)) {
            doc.writeTo(os);
        }

        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY)) {
            PdfRenderer renderer = new PdfRenderer(fd);
            assertEquals(2, renderer.getPageCount());
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                assertEquals(0, page.getIndex());
                assertEquals(101, page.getWidth());
                assertEquals(100, page.getHeight());
            }
            try (PdfRenderer.Page page = renderer.openPage(1)) {
                assertEquals(1, page.getIndex());
                assertEquals(201, page.getWidth());
                assertEquals(200, page.getHeight());
            }
        }

        doc.close();
    }

    /**
     * Replacement for non existing <code>{@link PdfDocument.PageInfo}#equals()</code>
     *
     * @param a The first info, can not be null
     * @param b The second info, can not be null
     *
     * @return If a is equal to b
     */
    private boolean pageInfoEquals(@NonNull PdfDocument.PageInfo a,
            @NonNull PdfDocument.PageInfo b) {
        return a.getContentRect().equals(b.getContentRect()) &&
                a.getPageHeight() == b.getPageHeight() && a.getPageWidth() == b.getPageWidth() &&
                a.getPageNumber() == b.getPageNumber();
    }

    @Test
    public void writeTwoPageDoc() throws Exception {
        PdfDocument doc = new PdfDocument();

        assertEquals(0, doc.getPages().size());

        PdfDocument.Page page0 = doc
                .startPage(new PdfDocument.PageInfo.Builder(101, 100, 0).create());

        assertEquals(0, doc.getPages().size());
        doc.finishPage(page0);
        assertEquals(1, doc.getPages().size());
        assertTrue(pageInfoEquals(page0.getInfo(), doc.getPages().get(0)));

        File page1File = File.createTempFile("tmp", "pdf", mContext.getCacheDir());
        try (OutputStream os = new FileOutputStream(page1File)) {
            doc.writeTo(os);
        }

        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(page1File,
                ParcelFileDescriptor.MODE_READ_ONLY)) {
            PdfRenderer renderer = new PdfRenderer(fd);
            assertEquals(1, renderer.getPageCount());
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                assertEquals(0, page.getIndex());
                assertEquals(101, page.getWidth());
                assertEquals(100, page.getHeight());
            }
        }

        PdfDocument.Page page1 = doc
                .startPage(new PdfDocument.PageInfo.Builder(201, 200, 1).create());

        doc.finishPage(page1);
        assertEquals(2, doc.getPages().size());
        assertTrue(pageInfoEquals(page0.getInfo(), doc.getPages().get(0)));
        assertTrue(pageInfoEquals(page1.getInfo(), doc.getPages().get(1)));

        File page2File = File.createTempFile("tmp", "pdf", mContext.getCacheDir());
        try (OutputStream os = new FileOutputStream(page2File)) {
            doc.writeTo(os);
        }

        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(page2File,
                ParcelFileDescriptor.MODE_READ_ONLY)) {
            PdfRenderer renderer = new PdfRenderer(fd);
            assertEquals(2, renderer.getPageCount());
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                assertEquals(0, page.getIndex());
                assertEquals(101, page.getWidth());
                assertEquals(100, page.getHeight());
            }
            try (PdfRenderer.Page page = renderer.openPage(1)) {
                assertEquals(1, page.getIndex());
                assertEquals(201, page.getWidth());
                assertEquals(200, page.getHeight());
            }
        }

        doc.close();
    }

    @Test
    public void writeToNull() throws Exception {
        PdfDocument doc = new PdfDocument();
        assertException(() -> doc.writeTo(null), IllegalArgumentException.class);
        doc.close();
    }

    @Test
    public void startNullPage() throws Exception {
        PdfDocument doc = new PdfDocument();
        assertException(() -> doc.startPage(null), IllegalArgumentException.class);
        doc.close();
    }

    @Test
    public void finishNullPage() throws Exception {
        PdfDocument doc = new PdfDocument();
        assertException(() -> doc.finishPage(null), IllegalArgumentException.class);
        doc.close();
    }

    @Test
    public void zeroWidthPage() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(0, 200, 0),
                IllegalArgumentException.class);
    }

    @Test
    public void negativeWidthPage() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(-1, 200, 0),
                IllegalArgumentException.class);
    }

    @Test
    public void zeroHeightPage() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(100, 0, 0),
                IllegalArgumentException.class);
    }

    @Test
    public void negativeHeightPage() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(100, -1, 0),
                IllegalArgumentException.class);
    }

    @Test
    public void negativePageNumber() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(100, 200, -1),
                IllegalArgumentException.class);
    }

    @Test
    public void contentRectLeftNegative() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(100, 200, 0)
                        .setContentRect(new Rect(-1, 0, 100, 200)),
                IllegalArgumentException.class);
    }

    @Test
    public void contentRectTopNegative() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(100, 200, 0)
                        .setContentRect(new Rect(0, -1, 100, 200)),
                IllegalArgumentException.class);
    }

    @Test
    public void contentRectRightToHigh() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(100, 200, 0)
                        .setContentRect(new Rect(0, 0, 101, 200)),
                IllegalArgumentException.class);
    }

    @Test
    public void contentRectBottomToHigh() throws Exception {
        assertException(() -> new PdfDocument.PageInfo.Builder(100, 200, 0)
                        .setContentRect(new Rect(0, 0, 100, 201)),
                IllegalArgumentException.class);
    }

    @Test
    public void createPageWithFullContentRect() throws Exception {
        PdfDocument doc = new PdfDocument();
        Rect contentRect = new Rect(0, 0, 100, 200);
        PdfDocument.Page page = doc.startPage((new PdfDocument.PageInfo.Builder(100, 200, 0))
                .setContentRect(contentRect).create());
        assertEquals(page.getInfo().getContentRect(), contentRect);
        assertEquals(100, page.getCanvas().getWidth());
        assertEquals(200, page.getCanvas().getHeight());
        doc.finishPage(page);
        doc.close();
    }

    @Test
    public void createPageWithPartialContentRect() throws Exception {
        PdfDocument doc = new PdfDocument();
        Rect contentRect = new Rect(10, 20, 90, 180);
        PdfDocument.Page page = doc.startPage((new PdfDocument.PageInfo.Builder(100, 200, 0))
                .setContentRect(contentRect).create());
        assertEquals(page.getInfo().getContentRect(), contentRect);
        assertEquals(80, page.getCanvas().getWidth());
        assertEquals(160, page.getCanvas().getHeight());
        doc.finishPage(page);
        doc.close();
    }

    @Test
    public void createPageWithEmptyContentRect() throws Exception {
        PdfDocument doc = new PdfDocument();
        Rect contentRect = new Rect(50, 100, 50, 100);
        PdfDocument.Page page = doc.startPage((new PdfDocument.PageInfo.Builder(100, 200, 0))
                .setContentRect(contentRect).create());
        assertEquals(page.getInfo().getContentRect(), contentRect);
        assertEquals(0, page.getCanvas().getWidth());
        assertEquals(0, page.getCanvas().getHeight());
        doc.finishPage(page);
        doc.close();
    }

    @Test
    public void createPageWithInverseContentRect() throws Exception {
        PdfDocument doc = new PdfDocument();

        // A Rect can have a lower right than left and bottom than top. Of course this does not make
        // sense for a content rect. For legacy reasons this is treated as we have a empty content
        // rect.
        Rect contentRect = new Rect(90, 180, 10, 20);
        PdfDocument.Page page = doc.startPage((new PdfDocument.PageInfo.Builder(100, 200, 0))
                .setContentRect(contentRect).create());
        assertEquals(page.getInfo().getContentRect(), contentRect);
        assertEquals(0, page.getCanvas().getWidth());
        assertEquals(0, page.getCanvas().getHeight());
        doc.finishPage(page);

        doc.close();
    }

    @Test
    public void defaultContentRectIsFullRect() throws Exception {
        PdfDocument.PageInfo info =(new PdfDocument.PageInfo.Builder(100, 200, 0)).create();
        assertEquals(info.getContentRect(), new Rect(0, 0, 100, 200));
    }
}
