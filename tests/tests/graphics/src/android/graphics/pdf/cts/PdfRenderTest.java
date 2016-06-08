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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRenderer.Page;
import android.os.ParcelFileDescriptor;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test for the {@link PdfRenderer}
 */
@RunWith(AndroidJUnit4.class)
public class PdfRenderTest {
    public static final int A4_WIDTH_PTS = 595;
    public static final int A4_HEIGHT_PTS = 841;
    private static final String LOG_TAG = "PdfRenderTest";
    private static final int A4_PORTRAIT = R.raw.a4_portrait_rgbb;
    private static final int A5_PORTRAIT = R.raw.a5_portrait_rgbb;
    private static final int A5_PORTRAIT_PRINTSCALING_DEFAULT = R.raw.a5_portrait_rgbb_1_6_printscaling_default;
    private static final int A5_PORTRAIT_PRINTSCALING_NONE = R.raw.a5_portrait_rgbb_1_6_printscaling_none;
    private static final int TWO_PAGES = R.raw.two_pages;
    private Context mContext;

    /**
     * Run a runnable and expect and exception of a certain type.
     *
     * @param r             The {@link Invokable} to run
     * @param expectedClass The expected exception type
     */
    private void assertException(@NonNull Invokable r,
            @NonNull Class<? extends Exception> expectedClass) {
        try {
            r.run();
        } catch (Exception e) {
            if (e.getClass().isAssignableFrom(expectedClass)) {
                return;
            } else {
                Log.e(LOG_TAG, "Incorrect exception", e);
                throw new AssertionError("Expected: " + expectedClass.getName() + ", got: "
                        + e.getClass().getName());
            }
        }

        throw new AssertionError("No exception thrown");
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * Create a {@link PdfRenderer} pointing to a file copied from a resource.
     *
     * @param docRes The resource to load
     *
     * @return the renderer
     *
     * @throws IOException If anything went wrong
     */
    private @NonNull PdfRenderer createRenderer(@RawRes int docRes) throws IOException {
        File pdfFile = File.createTempFile("pdf", null, mContext.getCacheDir());

        // Copy resource to file so that we can open it as a ParcelFileDescriptor
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(pdfFile))) {
            try (InputStream is = new BufferedInputStream(
                    mContext.getResources().openRawResource(docRes))) {
                byte buffer[] = new byte[1024];

                while (true) {
                    int numRead = is.read(buffer, 0, buffer.length);

                    if (numRead == -1) {
                        break;
                    }

                    os.write(Arrays.copyOf(buffer, numRead));
                }

                os.flush();
            }
        }

        return new PdfRenderer(
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY));
    }

    @Test
    public void constructRendererNull() throws Exception {
        assertException(() -> new PdfRenderer(null), NullPointerException.class);
    }

    @Test
    public void constructRendererFromNonPDF() throws Exception {
        // Open jpg as if it was a PDF
        ParcelFileDescriptor fd = mContext.getResources()
                .openRawResourceFd(R.raw.testimage).getParcelFileDescriptor();
        assertException(() -> new PdfRenderer(fd), IOException.class);
    }

    @Test
    public void useRendererAfterClose() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT);
        renderer.close();

        assertException(() -> renderer.close(), IllegalStateException.class);
        assertException(() -> renderer.getPageCount(), IllegalStateException.class);
        assertException(() -> renderer.shouldScaleForPrinting(), IllegalStateException.class);
        assertException(() -> renderer.openPage(0), IllegalStateException.class);
    }

    @Test
    public void usePageAfterClose() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT);
        Page page = renderer.openPage(0);
        page.close();

        // Legacy behavior: The properties are cached, hence they are still available after the page
        //                  is closed
        page.getHeight();
        page.getWidth();
        page.getIndex();
        assertException(() -> page.close(), IllegalStateException.class);
        assertException(() -> page.render(null, null, null, Page.RENDER_MODE_FOR_DISPLAY),
                IllegalStateException.class);

        renderer.close();
    }

    @Test
    public void closeWithOpenPage() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT);
        Page page = renderer.openPage(0);

        assertException(() -> renderer.close(), IllegalStateException.class);

        page.close();
        renderer.close();
    }

    @Test
    public void openTwoPages() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES)) {
            // Cannot open two pages at once
            Page page = renderer.openPage(0);
            assertException(() -> renderer.openPage(1), IllegalStateException.class);

            page.close();
        }
    }

    @Test
    public void testPageCount() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES)) {
            assertEquals(2, renderer.getPageCount());
        }
    }

    @Test
    public void testOpenPage() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES)) {
            assertException(() -> renderer.openPage(-1), IllegalArgumentException.class);
            Page page0 = renderer.openPage(0);
            page0.close();
            Page page1 = renderer.openPage(1);
            page1.close();
            assertException(() -> renderer.openPage(2), IllegalArgumentException.class);
        }
    }

    @Test
    public void testPageSize() throws Exception {
        try (PdfRenderer renderer = createRenderer(A4_PORTRAIT)) {
            try (Page page = renderer.openPage(0)) {
                assertEquals(A4_HEIGHT_PTS, page.getHeight());
                assertEquals(A4_WIDTH_PTS, page.getWidth());
            }
        }
    }

    @Test
    public void testPrintScaleDefault() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16Default() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_DEFAULT)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16None() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_NONE)) {
            assertFalse(renderer.shouldScaleForPrinting());
        }
    }

    /**
     * Render a pdf onto a bitmap <u>while</u> applying the transformation <u>in the</u>
     * PDFRenderer. Hence use PdfRenderer.*'s translation and clipping methods.
     *
     * @param bmWidth        The width of the destination bitmap
     * @param bmHeight       The height of the destination bitmap
     * @param docRes         The resolution of the doc
     * @param clipping       The clipping for the PDF document
     * @param transformation The transformation of the PDF
     * @param renderMode     The render mode to use to render the PDF
     *
     * @return The rendered bitmap
     */
    private @NonNull Bitmap renderWithTransform(int bmWidth, int bmHeight, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode)
            throws IOException {
        try (PdfRenderer renderer = createRenderer(docRes)) {
            try (Page page = renderer.openPage(0)) {
                Bitmap bm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);

                page.render(bm, clipping, transformation, renderMode);

                return bm;
            }
        }
    }

    /**
     * Render a pdf onto a bitmap <u>and then</u> apply then render the resulting bitmap onto
     * another bitmap while applying the transformation. Hence use canvas' translation and clipping
     * methods.
     *
     * @param bmWidth        The width of the destination bitmap
     * @param bmHeight       The height of the destination bitmap
     * @param docRes         The resolution of the doc
     * @param clipping       The clipping for the PDF document
     * @param transformation The transformation of the PDF
     * @param renderMode     The render mode to use to render the PDF
     *
     * @return The rendered bitmap
     */
    private @NonNull Bitmap renderAndThenTransform(int bmWidth, int bmHeight, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode)
            throws IOException {
        try (PdfRenderer renderer = createRenderer(docRes)) {
            try (Page page = renderer.openPage(0)) {
                Bitmap bm = Bitmap.createBitmap(page.getWidth(), page.getHeight(),
                        Bitmap.Config.ARGB_8888);
                page.render(bm, null, null, renderMode);

                if (transformation != null) {
                    Bitmap tmpBm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(tmpBm);
                    canvas.drawBitmap(bm, transformation, null);
                    bm.recycle();
                    bm = tmpBm;
                }

                if (clipping != null) {
                    Bitmap tmpBm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(tmpBm);
                    canvas.drawBitmap(bm, clipping, clipping, null);
                    bm.recycle();
                    bm = tmpBm;
                }

                return bm;
            }
        }
    }

    /**
     * Take 16 color probes in the middle of the 16 segments of the page in the following pattern:
     * <pre>
     * +----+----+----+----+
     * |  0 :  1 :  2 :  3 |
     * +....:....:....:....+
     * |  4 :  5 :  6 :  7 |
     * +....:....:....:....+
     * |  8 :  9 : 10 : 11 |
     * +....:....:....:....+
     * | 12 : 13 : 14 : 15 |
     * +----+----+----+----+
     * </pre>
     *
     * @param bm The bitmap to probe
     *
     * @return The color at the probes
     */
    private @NonNull int[] getColorProbes(@NonNull Bitmap bm) {
        int[] probes = new int[16];

        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                probes[row * 4 + column] = bm.getPixel((int) (bm.getWidth() * (column + 0.5) / 4),
                        (int) (bm.getHeight() * (row + 0.5) / 4));
            }
        }

        return probes;
    }

    /**
     * Implementation for {@link #renderNoTransformationAndComparePointsForScreen} and {@link
     * #renderNoTransformationAndComparePointsForPrint}.
     *
     * @param renderMode The render mode to use
     *
     * @throws Exception If anything was unexpected
     */
    private void renderNoTransformationAndComparePoints(int renderMode) throws Exception {
        Bitmap bm = renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                renderMode);
        int[] probes = getColorProbes(bm);

        // Compare rendering to expected result. This ensures that all other tests in this class do
        // not accidentally all compare empty bitmaps.
        assertEquals(Color.RED, probes[0]);
        assertEquals(Color.RED, probes[1]);
        assertEquals(Color.GREEN, probes[2]);
        assertEquals(Color.GREEN, probes[3]);
        assertEquals(Color.RED, probes[4]);
        assertEquals(Color.RED, probes[5]);
        assertEquals(Color.GREEN, probes[6]);
        assertEquals(Color.GREEN, probes[7]);
        assertEquals(Color.BLUE, probes[8]);
        assertEquals(Color.BLUE, probes[9]);
        assertEquals(Color.BLACK, probes[10]);
        assertEquals(Color.BLACK, probes[11]);
        assertEquals(Color.BLUE, probes[12]);
        assertEquals(Color.BLUE, probes[13]);
        assertEquals(Color.BLACK, probes[14]);
        assertEquals(Color.BLACK, probes[15]);
    }

    @Test
    public void renderNoTransformationAndComparePointsForScreen() throws Exception {
        renderNoTransformationAndComparePoints(Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderNoTransformationAndComparePointsForPrint() throws Exception {
        renderNoTransformationAndComparePoints(Page.RENDER_MODE_FOR_PRINT);
    }

    /**
     * Get the fraction of non-matching pixels of two bitmaps. 1 == no pixels match, 0 == all pixels
     * match.
     *
     * @param a The first bitmap
     * @param b The second bitmap
     *
     * @return The fraction of non-matching pixels.
     */
    private @FloatRange(from = 0, to = 0) float getNonMatching(@NonNull Bitmap a,
            @NonNull Bitmap b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return 1;
        }

        int[] aPx = new int[a.getWidth() * a.getHeight()];
        int[] bPx = new int[b.getWidth() * b.getHeight()];
        a.getPixels(aPx, 0, a.getWidth(), 0, 0, a.getWidth(), a.getHeight());
        b.getPixels(bPx, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());

        int badPixels = 0;
        for (int i = 0; i < a.getWidth() * a.getHeight(); i++) {
            if (aPx[i] != bPx[i]) {
                badPixels++;
            }
        }

        return ((float) badPixels) / (a.getWidth() * a.getHeight());
    }

    /**
     * Render the PDF two times. Once with applying the transformation and clipping in the {@link
     * PdfRenderer}. The other time render the PDF onto a bitmap and then clip and transform that
     * image. The result should be the same beside some minor aliasing.
     *
     * @param width          The width of the resulting bitmap
     * @param height         The height of the resulting bitmap
     * @param docRes         The resource of the PDF document
     * @param clipping       The clipping to apply
     * @param transformation The transformation to apply
     * @param renderMode     The render mode to use
     *
     * @throws IOException
     */
    private void renderAndCompare(int width, int height, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode) throws IOException {
        Bitmap a = renderWithTransform(width, height, docRes, clipping, transformation, renderMode);
        Bitmap b = renderAndThenTransform(width, height, docRes, clipping, transformation,
                renderMode);

        // We allow 1% aliasing error
        float nonMatching = getNonMatching(a, b);
        if (nonMatching == 0) {
            Log.d(LOG_TAG, "bitmaps match");
        } else if (nonMatching > 0.01) {
            fail("bitmaps differ by " + getNonMatching(a, b) + "%. That is too much.");
        } else {
            Log.d(LOG_TAG, "bitmaps differ by " + Math.ceil(nonMatching * 10000) / 100 + "%");
        }
    }

    @Test
    public void renderNoTransformation() throws Exception {
        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderTranslation() throws Exception {
        Matrix transform = new Matrix();
        // Move down and to the right
        transform.postTranslate(A4_WIDTH_PTS / 4f, A4_HEIGHT_PTS / 4f);

        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, transform,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderPositiveScale() throws Exception {
        Matrix transform = new Matrix();
        // Stretch to bottom and to the right
        transform.postScale(4f / 3, 4f / 3);

        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, transform,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderNegativeScale() throws Exception {
        Matrix transform = new Matrix();
        // Reflect both ways
        transform.postScale(-1, -1);
        // Move back into visible area
        transform.postTranslate(A4_WIDTH_PTS, A4_HEIGHT_PTS);

        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, transform,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void render90degreeRotation() throws Exception {
        Matrix transform = new Matrix();
        // Rotate on top left corner
        transform.postRotate(90);

        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A5_PORTRAIT, null, transform,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void render90degreeRotationAndTranslationAndScale() throws Exception {
        Matrix transform = new Matrix();
        // Rotate on top left corner
        transform.postRotate(90);
        // Move to right
        transform.postTranslate(A4_WIDTH_PTS * 5 / 4, A4_HEIGHT_PTS / 4);
        // Scale to 75%
        transform.postScale(0.75f, 0.75f);

        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A5_PORTRAIT, null, transform,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void render45degreeRotationTranslationAndScaleAndClip() throws Exception {
        Matrix transform = new Matrix();
        // Rotate on top left corner
        transform.postRotate(45);
        // Move
        transform.postTranslate(A4_WIDTH_PTS / 4, A4_HEIGHT_PTS / 4);
        // Scale to 75%
        transform.postScale(0.75f, 0.75f);
        // Clip
        Rect clip = new Rect(20, 20, A4_WIDTH_PTS - 20, A4_HEIGHT_PTS - 20);

        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, transform,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderPerspective() throws Exception {
        Matrix transform = new Matrix();

        transform.setValues(new float[] { 1, 1, 1, 1, 1, 1, 1, 1, 1 });

        assertException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, transform,
                        Page.RENDER_MODE_FOR_DISPLAY), IllegalArgumentException.class);
    }

    @Test
    public void renderWithClip() throws Exception {
        Rect clip = new Rect(20, 20, A4_WIDTH_PTS - 20, A4_HEIGHT_PTS - 20);
        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderWithAllClipped() throws Exception {
        Rect clip = new Rect(A4_WIDTH_PTS / 2, A4_HEIGHT_PTS / 2, A4_WIDTH_PTS / 2,
                A4_HEIGHT_PTS / 2);
        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderWithBadLowerCornerOfClip() throws Exception {
        Rect clip = new Rect(0, 0, A4_WIDTH_PTS + 20, A4_HEIGHT_PTS + 20);
        assertException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                        Page.RENDER_MODE_FOR_DISPLAY), IllegalArgumentException.class);
    }

    @Test
    public void renderWithBadUpperCornerOfClip() throws Exception {
        Rect clip = new Rect(-20, -20, A4_WIDTH_PTS, A4_HEIGHT_PTS);
        assertException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                        Page.RENDER_MODE_FOR_DISPLAY), IllegalArgumentException.class);
    }

    @Test
    public void renderTwoModes() throws Exception {
        assertException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                        Page.RENDER_MODE_FOR_DISPLAY | Page.RENDER_MODE_FOR_PRINT),
                IllegalArgumentException.class);
    }

    @Test
    public void renderBadMode() throws Exception {
        assertException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                        1 << 30), IllegalArgumentException.class);
    }

    @Test
    public void renderAllModes() throws Exception {
        assertException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                        -1), IllegalArgumentException.class);
    }

    @Test
    public void renderNoMode() throws Exception {
        assertException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null, 0),
                        IllegalArgumentException.class);
    }

    @Test
    public void renderOnNullBitmap() throws Exception {
        try (PdfRenderer renderer = createRenderer(A4_PORTRAIT)) {
            try (Page page = renderer.openPage(0)) {
                assertException(() -> page.render(null, null, null, Page.RENDER_MODE_FOR_DISPLAY),
                        NullPointerException.class);
            }
        }
    }

    /**
     * A runnable that can throw an exception.
     */
    private interface Invokable {
        void run() throws Exception;
    }
}
