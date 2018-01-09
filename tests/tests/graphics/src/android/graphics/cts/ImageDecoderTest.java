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

package android.graphics.cts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PostProcess;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.content.FileProvider;

import com.android.compatibility.common.util.BitmapUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ArrayIndexOutOfBoundsException;
import java.lang.NullPointerException;
import java.lang.RuntimeException;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImageDecoderTest {
    private Resources mRes;
    private ContentResolver mContentResolver;

    private static final int[] RES_IDS = new int[] {
        R.drawable.baseline_jpeg, R.drawable.png_test, R.drawable.gif_test,
        R.drawable.bmp_test, R.drawable.webp_test, R.drawable.google_chrome,
        R.drawable.color_wheel
    };

    // The width and height of the above images.
    private static final int WIDTHS[] = new int[] { 1280, 640, 320, 320, 640, 256, 128 };
    private static final int HEIGHTS[] = new int[] { 960, 480, 240, 240, 480, 256, 128 };

    // mimeTypes of the above images.
    private static final String[] MIME_TYPES = new String[] { "image/jpeg", "image/png",
            "image/gif", "image/bmp", "image/webp", "image/x-ico",
            "image/x-ico" };

    // offset is how many bytes to offset the beginning of the image.
    // extra is how many bytes to append at the end.
    private byte[] getAsByteArray(int resId, int offset, int extra) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeToStream(output, resId, offset, extra);
        return output.toByteArray();
    }

    private void writeToStream(OutputStream output, int resId, int offset, int extra) {
        InputStream input = mRes.openRawResource(resId);
        byte[] buffer = new byte[4096];
        int bytesRead;
        try {
            for (int i = 0; i < offset; ++i) {
                output.write(0);
            }

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            for (int i = 0; i < extra; ++i) {
                output.write(0);
            }

            input.close();
        } catch (IOException e) {
            fail();
        }
    }

    private byte[] getAsByteArray(int resId) {
        return getAsByteArray(resId, 0, 0);
    }

    private ByteBuffer getAsByteBufferWrap(int resId) {
        byte[] buffer = getAsByteArray(resId);
        return ByteBuffer.wrap(buffer);
    }

    private ByteBuffer getAsDirectByteBuffer(int resId) {
        byte[] buffer = getAsByteArray(resId);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(buffer.length);
        byteBuffer.put(buffer);
        byteBuffer.position(0);
        return byteBuffer;
    }

    private ByteBuffer getAsReadOnlyByteBuffer(int resId) {
        return getAsByteBufferWrap(resId).asReadOnlyBuffer();
    }

    private File getAsFile(int resId) {
        File file = null;
        try {
            Context context = InstrumentationRegistry.getTargetContext();
            File dir = new File(context.getFilesDir(), "images");
            dir.mkdirs();
            file = new File(dir, "test_file" + resId);
            if (!file.createNewFile()) {
                if (file.exists()) {
                    return file;
                }
                fail("Failed to create new File!");
            }

            FileOutputStream output = new FileOutputStream(file);
            writeToStream(output, resId, 0, 0);
            output.close();

        } catch (IOException e) {
            fail("Failed with exception " + e);
            return null;
        }
        return file;
    }

    private Uri getAsFileUri(int resId) {
        return Uri.fromFile(getAsFile(resId));
    }

    private Uri getAsContentUri(int resId) {
        Context context = InstrumentationRegistry.getTargetContext();
        return FileProvider.getUriForFile(context,
                "android.graphics.cts.fileprovider", getAsFile(resId));
    }

    private Uri getAsResourceUri(int resId) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(mRes.getResourcePackageName(resId))
                .appendPath(mRes.getResourceTypeName(resId))
                .appendPath(mRes.getResourceEntryName(resId))
                .build();
    }

    private interface SourceCreator extends IntFunction<ImageDecoder.Source> {};

    private SourceCreator mCreators[] = new SourceCreator[] {
        resId -> ImageDecoder.createSource(getAsByteArray(resId)),
        resId -> ImageDecoder.createSource(mRes, resId),
        resId -> ImageDecoder.createSource(getAsByteBufferWrap(resId)),
        resId -> ImageDecoder.createSource(getAsDirectByteBuffer(resId)),
        resId -> ImageDecoder.createSource(getAsReadOnlyByteBuffer(resId)),
    };

    private interface UriCreator extends IntFunction<Uri> {};

    @Test
    public void testUris() {
        UriCreator creators[] = new UriCreator[] {
            resId -> getAsResourceUri(resId),
            resId -> getAsFileUri(resId),
            resId -> getAsContentUri(resId),
        };
        for (int resId : RES_IDS) {
            String name = mRes.getResourceEntryName(resId);
            for (UriCreator f : creators) {
                ImageDecoder.Source src = null;
                Uri uri = f.apply(resId);
                String fullName = name + ": " + uri.toString();
                src = ImageDecoder.createSource(mContentResolver, uri);

                assertNotNull("failed to create Source for " + fullName, src);
                try {
                    Drawable d = ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                        decoder.setOnPartialImageListener(e -> {
                            fail("exception for image " + fullName + ":\n" + e);
                            return false;
                        });
                    });
                    assertNotNull("failed to create drawable for " + fullName, d);
                } catch (IOException e) {
                    fail("exception for image " + fullName + ":\n" + e);
                }
            }
        }
    }

    @Before
    public void setup() {
        mRes = InstrumentationRegistry.getTargetContext().getResources();
        mContentResolver = InstrumentationRegistry.getTargetContext().getContentResolver();
    }

    @Test
    public void testInfo() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public int mWidth;
            public int mHeight;
            public String mMimeType;

            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                mWidth  = info.width;
                mHeight = info.height;
                mMimeType = info.getMimeType();
            }
        };
        Listener l = new Listener();

        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(RES_IDS[i]);
                assertNotNull(src);
                try {
                    ImageDecoder.decodeDrawable(src, l);
                    assertEquals(WIDTHS[i],  l.mWidth);
                    assertEquals(HEIGHTS[i], l.mHeight);
                    assertEquals(MIME_TYPES[i], l.mMimeType);
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testDecodeDrawable() {
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(RES_IDS[i]);
                assertNotNull(src);

                try {
                    Drawable drawable = ImageDecoder.decodeDrawable(src);
                    assertNotNull(drawable);
                    assertEquals(WIDTHS[i],  drawable.getIntrinsicWidth());
                    assertEquals(HEIGHTS[i], drawable.getIntrinsicHeight());
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testDecodeBitmap() {
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(RES_IDS[i]);
                assertNotNull(src);

                try {
                    Bitmap bm = ImageDecoder.decodeBitmap(src);
                    assertNotNull(bm);
                    assertEquals(WIDTHS[i], bm.getWidth());
                    assertEquals(HEIGHTS[i], bm.getHeight());
                    assertFalse(bm.isMutable());
                    // FIXME: This may change for small resources, etc.
                    assertEquals(Bitmap.Config.HARDWARE, bm.getConfig());
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetBogusAllocator() {
        ImageDecoder.Source src = mCreators[0].apply(RES_IDS[0]);
        try {
            ImageDecoder.decodeBitmap(src, (info, decoder) -> decoder.setAllocator(15));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test
    public void testSetAllocatorDecodeBitmap() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public int allocator;
            public boolean doCrop;
            public boolean doScale;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                decoder.setAllocator(allocator);
                if (doScale) {
                    decoder.resize(2);
                }
                if (doCrop) {
                    decoder.crop(new Rect(1, 1, info.width / 2 - 1, info.height / 2 - 1));
                }
            }
        };
        Listener l = new Listener();

        int allocators[] = new int[] {
            ImageDecoder.DEFAULT_ALLOCATOR,
            ImageDecoder.SOFTWARE_ALLOCATOR,
            ImageDecoder.SHARED_MEMORY_ALLOCATOR,
            ImageDecoder.HARDWARE_ALLOCATOR,
        };
        boolean trueFalse[] = new boolean[] { true, false };
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                for (int allocator : allocators) {
                    for (boolean doCrop : trueFalse) {
                        for (boolean doScale : trueFalse) {
                            l.doCrop = doCrop;
                            l.doScale = doScale;
                            l.allocator = allocator;
                            ImageDecoder.Source src = f.apply(RES_IDS[i]);
                            assertNotNull(src);

                            Bitmap bm = null;
                            try {
                               bm = ImageDecoder.decodeBitmap(src, l);
                            } catch (IOException e) {
                                fail("Failed with exception " + e);
                            }
                            assertNotNull(bm);

                            switch (allocator) {
                                case ImageDecoder.SOFTWARE_ALLOCATOR:
                                // TODO: Once Bitmap provides access to its
                                // SharedMemory, confirm that SHARED_MEMORY_ALLOCATOR
                                // worked.
                                case ImageDecoder.SHARED_MEMORY_ALLOCATOR:
                                    assertNotEquals(Bitmap.Config.HARDWARE, bm.getConfig());

                                    if (!doScale && !doCrop) {
                                        Bitmap reference = BitmapFactory.decodeResource(mRes,
                                                RES_IDS[i], null);
                                        assertNotNull(reference);
                                        BitmapUtils.compareBitmaps(bm, reference);
                                    }
                                    break;
                                default:
                                    assertEquals("image " + i + "; allocator: " + allocator,
                                                 Bitmap.Config.HARDWARE, bm.getConfig());
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testUnpremul() {
        int[] resIds = new int[] { R.drawable.png_test, R.drawable.alpha };
        boolean[] hasAlpha = new boolean[] { false,     true };
        for (int i = 0; i < resIds.length; ++i) {
            for (SourceCreator f : mCreators) {
                // Normal decode
                ImageDecoder.Source src = f.apply(resIds[i]);
                assertNotNull(src);

                try {
                    Bitmap normal = ImageDecoder.decodeBitmap(src);
                    assertNotNull(normal);
                    assertEquals(normal.hasAlpha(), hasAlpha[i]);
                    assertEquals(normal.isPremultiplied(), hasAlpha[i]);

                    // Require unpremul
                    src = f.apply(resIds[i]);
                    assertNotNull(src);

                    Bitmap unpremul = ImageDecoder.decodeBitmap(src,
                            (info, decoder) -> decoder.requireUnpremultiplied());
                    assertNotNull(unpremul);
                    assertEquals(unpremul.hasAlpha(), hasAlpha[i]);
                    assertFalse(unpremul.isPremultiplied());
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testPostProcess() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.SOFTWARE_ALLOCATOR);
                }
                decoder.setPostProcess((canvas, width, height) -> {
                    canvas.drawColor(Color.BLACK);
                    return PixelFormat.OPAQUE;
                });
            }
        };
        Listener l = new Listener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (int i = 0; i < RES_IDS.length; i++) {
            for (SourceCreator f : mCreators) {
                for (boolean requireSoftware : trueFalse) {
                    l.requireSoftware = requireSoftware;
                    ImageDecoder.Source src = f.apply(RES_IDS[i]);
                    assertNotNull(src);

                    Bitmap bitmap = null;
                    try {
                        bitmap = ImageDecoder.decodeBitmap(src, l);
                    } catch (IOException e) {
                        fail("Failed with exception " + e);
                    }
                    assertNotNull(bitmap);
                    assertFalse(bitmap.isMutable());
                    if (requireSoftware) {
                        assertNotEquals(Bitmap.Config.HARDWARE, bitmap.getConfig());
                        for (int x = 0; x < bitmap.getWidth(); ++x) {
                            for (int y = 0; y < bitmap.getHeight(); ++y) {
                                int color = bitmap.getPixel(x, y);
                                assertEquals("pixel at (" + x + ", " + y + ") does not match!",
                                        color, Color.BLACK);
                            }
                        }
                    } else {
                        assertEquals(bitmap.getConfig(), Bitmap.Config.HARDWARE);
                    }
                }
            }
        }
    }

    @Test
    public void testPostProcessOverridesNinepatch() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.SOFTWARE_ALLOCATOR);
                }
                decoder.setPostProcess((c, w, h) -> PixelFormat.UNKNOWN);
            }
        };
        Listener l = new Listener();
        int resIds[] = new int[] { R.drawable.ninepatch_0,
                                   R.drawable.ninepatch_1 };
        boolean trueFalse[] = new boolean[] { true, false };
        for (int resId : resIds) {
            for (SourceCreator f : mCreators) {
                for (boolean requireSoftware : trueFalse) {
                    l.requireSoftware = requireSoftware;
                    ImageDecoder.Source src = f.apply(resId);
                    try {
                        Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                        assertFalse(drawable instanceof NinePatchDrawable);

                        src = f.apply(resId);
                        Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                        assertNull(bm.getNinePatchChunk());
                    } catch (IOException e) {
                        fail("Failed with exception " + e);
                    }
                }
            }
        }
    }

    @Test
    public void testPostProcessAndMadeOpaque() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.SOFTWARE_ALLOCATOR);
                }
                decoder.setPostProcess((c, w, h) -> PixelFormat.OPAQUE);
            }
        };
        Listener l = new Listener();
        boolean trueFalse[] = new boolean[] { true, false };
        int resIds[] = new int[] { R.drawable.alpha, R.drawable.google_logo_2 };
        for (int resId : resIds) {
            for (SourceCreator f : mCreators) {
                for (boolean requireSoftware : trueFalse) {
                    l.requireSoftware = requireSoftware;
                    ImageDecoder.Source src = f.apply(resId);
                    try {
                        Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                        assertFalse(bm.hasAlpha());
                        assertFalse(bm.isPremultiplied());
                    } catch (IOException e) {
                        fail("Failed with exception " + e);
                    }
                }
            }
        }
    }

    @Test
    public void testPostProcessAndAddedTransparency() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.SOFTWARE_ALLOCATOR);
                }
                decoder.setPostProcess((c, w, h) -> PixelFormat.TRANSLUCENT);
            }
        };
        Listener l = new Listener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                for (boolean requireSoftware : trueFalse) {
                    l.requireSoftware = requireSoftware;
                    ImageDecoder.Source src = f.apply(RES_IDS[i]);
                    try {
                        Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                        assertTrue(bm.hasAlpha());
                        assertTrue(bm.isPremultiplied());
                    } catch (IOException e) {
                        fail("Failed with exception " + e);
                    }
                }
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostProcessTRANSPARENT() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.setPostProcess((c, w, h) -> PixelFormat.TRANSPARENT);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostProcessInvalidReturn() {
        ImageDecoder.Source src = mCreators[0].apply(RES_IDS[0]);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.setPostProcess((c, w, h) -> 42);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testPostProcessAndUnpremul() {
        ImageDecoder.Source src = mCreators[0].apply(RES_IDS[0]);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.requireUnpremultiplied();
                decoder.setPostProcess((c, w, h) -> PixelFormat.UNKNOWN);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test
    public void testPostProcessAndScale() {
        class PostProcessWithSize implements PostProcess {
            public int width;
            public int height;
            @Override
            public int postProcess(Canvas canvas, int width, int height) {
                assertEquals(this.width,  width);
                assertEquals(this.height, height);
                return PixelFormat.UNKNOWN;
            };
        };
        final PostProcessWithSize pp = new PostProcessWithSize();
        for (int i = 0; i < RES_IDS.length; ++i) {
            pp.width =  WIDTHS[i]  / 2;
            pp.height = HEIGHTS[i] / 2;
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(RES_IDS[i]);
                try {
                    Drawable drawable = ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                        decoder.resize(pp.width, pp.height);
                        decoder.setPostProcess(pp);
                    });
                    assertEquals(pp.width,  drawable.getIntrinsicWidth());
                    assertEquals(pp.height, drawable.getIntrinsicHeight());
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testGetSampledSize() {
        class SampleListener implements ImageDecoder.OnHeaderDecodedListener {
            public Point dimensions;
            public int sampleSize;
            public boolean useSampleSizeDirectly;

            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                if (useSampleSizeDirectly) {
                    decoder.resize(sampleSize);
                } else {
                    dimensions = decoder.getSampledSize(sampleSize);
                    decoder.resize(dimensions.x, dimensions.y);
                }
            }
        };

        SampleListener l = new SampleListener();

        int[] sampleSizes = new int[] { 1, 2, 3, 4, 6, 8, 16, 32, 64 };
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                for (int j = 0; j < sampleSizes.length; j++) {
                    l.sampleSize = sampleSizes[j];
                    l.useSampleSizeDirectly = false;
                    ImageDecoder.Source src = f.apply(RES_IDS[i]);

                    try {
                        Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                        assertEquals(l.dimensions.x, drawable.getIntrinsicWidth());
                        assertEquals(l.dimensions.y, drawable.getIntrinsicHeight());

                        l.useSampleSizeDirectly = true;
                        src = f.apply(RES_IDS[i]);
                        drawable = ImageDecoder.decodeDrawable(src, l);
                        assertEquals(l.dimensions.x, drawable.getIntrinsicWidth());
                        assertEquals(l.dimensions.y, drawable.getIntrinsicHeight());
                    } catch (IOException e) {
                        fail("Failed with exception " + e);
                    }
                }
            }
        }
    }

    @Test
    public void testLargeSampleSize() {
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(RES_IDS[i]);
                try {
                    ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                        Point dimensions = decoder.getSampledSize(info.width);
                        assertEquals(dimensions.x, 1);

                        dimensions = decoder.getSampledSize(info.width + 5);
                        assertEquals(dimensions.x, 1);

                        dimensions = decoder.getSampledSize(info.width * 2);
                        assertEquals(dimensions.x, 1);
                    });
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testOnPartialImage() {
        class ExceptionCallback implements ImageDecoder.OnPartialImageListener {
            public boolean caughtException;
            public boolean returnDrawable;
            @Override
            public boolean onPartialImage(IOException e) {
                caughtException = true;
                assertTrue(e instanceof ImageDecoder.IncompleteException);
                return returnDrawable;
            }
        };
        final ExceptionCallback exceptionListener = new ExceptionCallback();
        boolean abortDecode[] = new boolean[] { true, false };
        for (int i = 0; i < RES_IDS.length; ++i) {
            byte[] bytes = getAsByteArray(RES_IDS[i]);
            int truncatedLength = bytes.length / 2;
            if (i == 5 || i == 6) {
                // FIXME (scroggo): SkIcoCodec currently does not support incomplete images.
                continue;
            }
            for (boolean abort : abortDecode) {
                ImageDecoder.Source src = ImageDecoder.createSource(bytes, 0, truncatedLength);
                exceptionListener.caughtException = false;
                exceptionListener.returnDrawable = !abort;
                try {
                    Drawable drawable = ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                        decoder.setOnPartialImageListener(exceptionListener);
                    });
                    assertFalse(abort);
                    assertNotNull(drawable);
                    assertEquals(WIDTHS[i],  drawable.getIntrinsicWidth());
                    assertEquals(HEIGHTS[i], drawable.getIntrinsicHeight());
                } catch (IOException e) {
                    assertTrue(abort);
                }
                assertTrue(exceptionListener.caughtException);
            }

            // null listener behaves as if OnPartialImage returned true.
            ImageDecoder.Source src = ImageDecoder.createSource(bytes, 0, truncatedLength);
            try {
                Drawable drawable = ImageDecoder.decodeDrawable(src);
                assertNotNull(drawable);
                assertEquals(WIDTHS[i],  drawable.getIntrinsicWidth());
                assertEquals(HEIGHTS[i], drawable.getIntrinsicHeight());
            } catch (IOException e) {
                fail("Failed with exception " + e);
            }
        }
    }

    @Test
    public void testCorruptException() {
        class ExceptionCallback implements ImageDecoder.OnPartialImageListener {
            public boolean caughtException = false;
            @Override
            public boolean onPartialImage(IOException e) {
                caughtException = true;
                assertTrue(e instanceof ImageDecoder.CorruptException);
                return true;
            }
        };
        final ExceptionCallback exceptionListener = new ExceptionCallback();
        byte[] bytes = getAsByteArray(R.drawable.png_test);
        // The four bytes starting with byte 40,000 represent the CRC. Changing
        // them will cause the decode to fail.
        for (int i = 0; i < 4; ++i) {
            bytes[40000 + i] = 'X';
        }
        ImageDecoder.Source src = ImageDecoder.createSource(bytes);
        exceptionListener.caughtException = false;
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.setOnPartialImageListener(exceptionListener);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
        assertTrue(exceptionListener.caughtException);
    }

    private static class DummyException extends RuntimeException {};

    @Test
    public void  testPartialImageThrowException() {
        byte[] bytes = getAsByteArray(R.drawable.png_test);
        ImageDecoder.Source src = ImageDecoder.createSource(bytes, 0, bytes.length / 2);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.setOnPartialImageListener(e -> {
                    throw new DummyException();
                });
            });
            fail("Should have thrown an exception");
        } catch (DummyException dummy) {
            // This is correct.
        } catch (Throwable t) {
            fail("Should have thrown DummyException - threw " + t + " instead");
        }
    }

    @Test
    public void testMutable() {
        int allocators[] = new int[] { ImageDecoder.DEFAULT_ALLOCATOR,
                                       ImageDecoder.SOFTWARE_ALLOCATOR,
                                       ImageDecoder.SHARED_MEMORY_ALLOCATOR };
        class HeaderListener implements ImageDecoder.OnHeaderDecodedListener {
            int allocator;
            boolean postProcess;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info,
                                        ImageDecoder decoder) {
                decoder.setMutable();
                decoder.setAllocator(allocator);
                if (postProcess) {
                    decoder.setPostProcess((c, w, h) -> PixelFormat.UNKNOWN);
                }
            }
        };
        HeaderListener l = new HeaderListener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (int resId : RES_IDS) {
            for (SourceCreator f : mCreators) {
                for (boolean postProcess : trueFalse) {
                    for (int allocator : allocators) {
                        l.allocator = allocator;
                        l.postProcess = postProcess;

                        ImageDecoder.Source src = f.apply(resId);
                        try {
                            Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                            assertTrue(bm.isMutable());
                            assertNotEquals(Bitmap.Config.HARDWARE, bm.getConfig());
                        } catch (IOException e) {
                            fail("Failed with exception " + e);
                        }
                    }
                }
            }
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testMutableHardware() {
        ImageDecoder.Source src = mCreators[0].apply(RES_IDS[0]);
        try {
            ImageDecoder.decodeBitmap(src, (info, decoder) -> {
                decoder.setMutable();
                decoder.setAllocator(ImageDecoder.HARDWARE_ALLOCATOR);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testMutableDrawable() {
        ImageDecoder.Source src = mCreators[0].apply(RES_IDS[0]);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.setMutable();
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    private interface EmptyByteBufferCreator {
        public ByteBuffer apply();
    };

    @Test
    public void testEmptyByteBuffer() {
        class Direct implements EmptyByteBufferCreator {
            @Override
            public ByteBuffer apply() {
                return ByteBuffer.allocateDirect(0);
            }
        };
        class Wrap implements EmptyByteBufferCreator {
            @Override
            public ByteBuffer apply() {
                byte[] bytes = new byte[0];
                return ByteBuffer.wrap(bytes);
            }
        };
        class ReadOnly implements EmptyByteBufferCreator {
            @Override
            public ByteBuffer apply() {
                byte[] bytes = new byte[0];
                return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
            }
        };
        EmptyByteBufferCreator creators[] = new EmptyByteBufferCreator[] {
            new Direct(), new Wrap(), new ReadOnly() };
        for (EmptyByteBufferCreator creator : creators) {
            try {
                ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(creator.apply()));
                fail("This should have thrown an exception");
            } catch (IOException e) {
                // This is correct.
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testZeroSampleSize() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> decoder.getSampledSize(0));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNegativeSampleSize() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> decoder.getSampledSize(-2));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test
    public void testResize() {
        class ResizeListener implements ImageDecoder.OnHeaderDecodedListener {
            public int width;
            public int height;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                decoder.resize(width, height);
            }
        };
        ResizeListener l = new ResizeListener();

        float[] scales = new float[] { .0625f, .125f, .25f, .5f, .75f, 1.1f, 2.0f };
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                for (int j = 0; j < scales.length; ++j) {
                    l.width  = (int) (scales[j] * WIDTHS[i]);
                    l.height = (int) (scales[j] * HEIGHTS[i]);

                    ImageDecoder.Source src = f.apply(RES_IDS[i]);

                    try {
                        Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                        assertEquals(l.width,  drawable.getIntrinsicWidth());
                        assertEquals(l.height, drawable.getIntrinsicHeight());

                        src = f.apply(RES_IDS[i]);
                        Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                        assertEquals(l.width,  bm.getWidth());
                        assertEquals(l.height, bm.getHeight());
                    } catch (IOException e) {
                        fail("Failed with exception " + e);
                    }
                }

                try {
                    // Arbitrary square.
                    l.width  = 50;
                    l.height = 50;
                    ImageDecoder.Source src = f.apply(RES_IDS[i]);
                    Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                    assertEquals(50,  drawable.getIntrinsicWidth());
                    assertEquals(50, drawable.getIntrinsicHeight());

                    // Swap width and height, for different scales.
                    l.height = WIDTHS[i];
                    l.width  = HEIGHTS[i];
                    src = f.apply(RES_IDS[i]);
                    drawable = ImageDecoder.decodeDrawable(src, l);
                    assertEquals(HEIGHTS[i], drawable.getIntrinsicWidth());
                    assertEquals(WIDTHS[i],  drawable.getIntrinsicHeight());
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testResizeWebp() {
        // libwebp supports unpremultiplied for downscaled output
        class ResizeListener implements ImageDecoder.OnHeaderDecodedListener {
            public int width;
            public int height;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                decoder.resize(width, height);
                decoder.requireUnpremultiplied();
            }
        };
        ResizeListener l = new ResizeListener();

        float[] scales = new float[] { .0625f, .125f, .25f, .5f, .75f };
        for (SourceCreator f : mCreators) {
            for (int j = 0; j < scales.length; ++j) {
                l.width  = (int) (scales[j] * 240);
                l.height = (int) (scales[j] *  87);

                ImageDecoder.Source src = f.apply(R.drawable.google_logo_2);
                try {
                    Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                    assertEquals(l.width,  bm.getWidth());
                    assertEquals(l.height, bm.getHeight());
                    assertTrue(bm.hasAlpha());
                    assertFalse(bm.isPremultiplied());
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testResizeWebpLarger() {
        // libwebp does not upscale, so there is no way to get unpremul.
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.google_logo_2);
        try {
            ImageDecoder.decodeBitmap(src, (info, decoder) -> {
                decoder.resize(info.width * 2, info.height * 2);
                decoder.requireUnpremultiplied();
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testResizeUnpremul() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.alpha);
        try {
            ImageDecoder.decodeBitmap(src, (info, decoder) -> {
                // Choose a width and height that cannot be achieved with sampling.
                Point dims = decoder.getSampledSize(2);
                decoder.resize(dims.x + 3, dims.y + 3);
                decoder.requireUnpremultiplied();
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test
    public void testCrop() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean doScale;
            public boolean requireSoftware;
            public Rect cropRect;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                int width  = info.width;
                int height = info.height;
                if (doScale) {
                    width  /= 2;
                    height /= 2;
                    decoder.resize(width, height);
                }
                // Crop to the middle:
                int quarterWidth  = width  / 4;
                int quarterHeight = height / 4;
                cropRect = new Rect(quarterWidth, quarterHeight,
                        quarterWidth * 3, quarterHeight * 3);
                decoder.crop(cropRect);

                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.SOFTWARE_ALLOCATOR);
                }
            }
        };
        Listener l = new Listener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (int i = 0; i < RES_IDS.length; ++i) {
            for (SourceCreator f : mCreators) {
                for (boolean doScale : trueFalse) {
                    l.doScale = doScale;
                    for (boolean requireSoftware : trueFalse) {
                        l.requireSoftware = requireSoftware;
                        ImageDecoder.Source src = f.apply(RES_IDS[i]);

                        try {
                            Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                            assertEquals(l.cropRect.width(),  drawable.getIntrinsicWidth());
                            assertEquals(l.cropRect.height(), drawable.getIntrinsicHeight());
                        } catch (IOException e) {
                            fail("Failed with exception " + e);
                        }
                    }
                }
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeZeroX() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> decoder.resize(0, info.height));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeZeroY() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> decoder.resize(info.width, 0));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeNegativeX() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> decoder.resize(-10, info.height));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeNegativeY() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> decoder.resize(info.width, -10));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testStoreImageDecoder() {
        class CachingCallback implements ImageDecoder.OnHeaderDecodedListener {
            ImageDecoder cachedDecoder;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                cachedDecoder = decoder;
            }
        };
        CachingCallback l = new CachingCallback();
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, l);
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
        l.cachedDecoder.getSampledSize(2);
    }

    @Test(expected=IllegalStateException.class)
    public void testDecodeUnpremulDrawable() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> decoder.requireUnpremultiplied());
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropNegativeLeft() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.crop(new Rect(-1, 0, info.width, info.height));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropNegativeTop() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.crop(new Rect(0, -1, info.width, info.height));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropTooWide() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.crop(new Rect(1, 0, info.width + 1, info.height));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropTooTall() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.crop(new Rect(0, 1, info.width, info.height + 1));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropResize() {
        ImageDecoder.Source src = ImageDecoder.createSource(mRes, R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.resize(info.width / 2, info.height / 2);
                decoder.crop(new Rect(0, 0, info.width, info.height));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test
    public void testAlphaMaskNonGray() {
        // It is safe to call setAsAlphaMask on a non-gray image.
        SourceCreator f = mCreators[0];
        ImageDecoder.Source src = f.apply(R.drawable.png_test);
        assertNotNull(src);
        try {
            Bitmap bm = ImageDecoder.decodeBitmap(src, (info, decoder) -> {
                decoder.setAsAlphaMask();
                decoder.setAllocator(ImageDecoder.SOFTWARE_ALLOCATOR);
            });
            assertNotNull(bm);
            assertNotEquals(Bitmap.Config.ALPHA_8, bm.getConfig());
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }

    }

    @Test(expected=IllegalStateException.class)
    public void testAlphaMaskPlusHardware() {
        SourceCreator f = mCreators[0];
        ImageDecoder.Source src = f.apply(R.drawable.png_test);
        assertNotNull(src);
        try {
            ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                decoder.setAsAlphaMask();
                decoder.setAllocator(ImageDecoder.HARDWARE_ALLOCATOR);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test
    public void testAlphaMask() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            boolean doCrop;
            boolean doScale;
            boolean doPostProcess;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                decoder.setAsAlphaMask();
                if (doScale) {
                    decoder.resize(info.width / 2, info.height / 2);
                }
                if (doCrop) {
                    decoder.crop(new Rect(0, 0, info.width / 4, info.height / 4));
                }
                if (doPostProcess) {
                    decoder.setPostProcess((c, w, h) -> {
                        c.drawColor(Color.BLACK);
                        return PixelFormat.UNKNOWN;
                    });
                }
            }
        };
        Listener l = new Listener();
        // Both of these are encoded as single channel gray images.
        int resIds[] = new int[] { R.drawable.grayscale_png, R.drawable.grayscale_jpg };
        boolean trueFalse[] = new boolean[] { true, false };
        SourceCreator f = mCreators[0];
        for (int resId : resIds) {
            // By default, this will decode to HARDWARE
            ImageDecoder.Source src = f.apply(resId);
            try {
                Bitmap bm  = ImageDecoder.decodeBitmap(src);
                assertEquals(Bitmap.Config.HARDWARE, bm.getConfig());
            } catch (IOException e) {
                fail("Failed with exception " + e);
            }

            // Now set alpha mask, which is incompatible with HARDWARE
            for (boolean doCrop : trueFalse) {
                for (boolean doScale : trueFalse) {
                    for (boolean doPostProcess : trueFalse) {
                        l.doCrop = doCrop;
                        l.doScale = doScale;
                        l.doPostProcess = doPostProcess;
                        src = f.apply(resId);
                        try {
                            Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                            assertNotEquals(Bitmap.Config.HARDWARE, bm.getConfig());
                        } catch (IOException e) {
                            fail("Failed with exception " + e);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testPreferRamOverQualityPlusHardware() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            int allocator;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                decoder.setPreferRamOverQuality();
                decoder.setAllocator(allocator);
            }
        };
        Listener l = new Listener();
        int resIds[] = new int[] { R.drawable.png_test, R.raw.basi6a16 };
        // Though png_test is opaque, using HARDWARE will require 8888, so we
        // do not decode to 565. basi6a16 will still downconvert from F16 to
        // 8888.
        boolean hardwareOverrides[] = new boolean[] { true, false };
        int[] allocators = new int[] { ImageDecoder.HARDWARE_ALLOCATOR,
                                       ImageDecoder.SOFTWARE_ALLOCATOR,
                                       ImageDecoder.DEFAULT_ALLOCATOR,
                                       ImageDecoder.SHARED_MEMORY_ALLOCATOR };
        SourceCreator f = mCreators[0];
        for (int i = 0; i < resIds.length; ++i) {
            Bitmap normal = null;
            try {
                normal = ImageDecoder.decodeBitmap(f.apply(resIds[i]));
            } catch (IOException e) {
                fail("Failed with exception " + e);
            }
            assertNotNull(normal);
            int normalByteCount = normal.getAllocationByteCount();
            for (int allocator : allocators) {
                l.allocator = allocator;
                Bitmap test = null;
                try {
                   test = ImageDecoder.decodeBitmap(f.apply(resIds[i]), l);
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
                assertNotNull(test);
                int byteCount = test.getAllocationByteCount();
                if ((allocator == ImageDecoder.HARDWARE_ALLOCATOR ||
                     allocator == ImageDecoder.DEFAULT_ALLOCATOR)
                    && hardwareOverrides[i]) {
                    assertEquals(normalByteCount, byteCount);
                } else {
                    assertTrue(byteCount < normalByteCount);
                }
            }
        }
    }

    @Test
    public void testPreferRamOverQuality() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            boolean doPostProcess;
            boolean preferRamOverQuality;
            @Override
            public void onHeaderDecoded(ImageDecoder.ImageInfo info, ImageDecoder decoder) {
                if (preferRamOverQuality) {
                    decoder.setPreferRamOverQuality();
                }
                if (doPostProcess) {
                    decoder.setPostProcess((c, w, h) -> {
                        c.drawColor(Color.BLACK);
                        return PixelFormat.TRANSLUCENT;
                    });
                }
                decoder.setAllocator(ImageDecoder.SOFTWARE_ALLOCATOR);
            }
        };
        Listener l = new Listener();
        // All of these images are opaque, so they can save RAM with
        // setPreferRamOverQuality.
        int resIds[] = new int[] { R.drawable.png_test, R.drawable.baseline_jpeg,
                                   // If this were stored in drawable/, it would
                                   // be converted from 16-bit to 8. FIXME: Is
                                   // behavior still desirable now that we have
                                   // F16?
                                   R.raw.basi6a16 };
        // An opaque image can be converted to 565, but postProcess will promote
        // to 8888 in case alpha is added. The third image defaults to F16, so
        // even with postProcess it will only be promoted to 8888.
        boolean postProcessCancels[] = new boolean[] { true, true, false };
        boolean trueFalse[] = new boolean[] { true, false };
        SourceCreator f = mCreators[0];
        for (int i = 0; i < resIds.length; ++i) {
            int resId = resIds[i];
            l.doPostProcess = false;
            l.preferRamOverQuality = false;
            Bitmap normal = null;
            try {
                normal = ImageDecoder.decodeBitmap(f.apply(resId), l);
            } catch (IOException e) {
                fail("Failed with exception " + e);
            }
            int normalByteCount = normal.getAllocationByteCount();
            for (boolean doPostProcess : trueFalse) {
                l.doPostProcess = doPostProcess;
                l.preferRamOverQuality = true;
                Bitmap saveRamOverQuality = null;
                try {
                    saveRamOverQuality = ImageDecoder.decodeBitmap(f.apply(resId), l);
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
                int saveByteCount = saveRamOverQuality.getAllocationByteCount();
                if (doPostProcess && postProcessCancels[i]) {
                    // Promoted to normal.
                    assertEquals(normalByteCount, saveByteCount);
                } else {
                    assertTrue(saveByteCount < normalByteCount);
                }
            }
        }
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testArrayOutOfBounds() {
        byte[] array = new byte[10];
        ImageDecoder.createSource(array, 1, 10);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testOffsetOutOfBounds() {
        byte[] array = new byte[10];
        ImageDecoder.createSource(array, 10, 0);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testLengthOutOfBounds() {
        byte[] array = new byte[10];
        ImageDecoder.createSource(array, 0, 11);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testNegativeLength() {
        byte[] array = new byte[10];
        ImageDecoder.createSource(array, 0, -1);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testNegativeOffset() {
        byte[] array = new byte[10];
        ImageDecoder.createSource(array, -1, 10);
    }

    @Test(expected=NullPointerException.class)
    public void testNullByteArray() {
        ImageDecoder.createSource(null, 0, 0);
    }

    @Test(expected=IOException.class)
    public void testZeroLengthByteArray() throws IOException {
        Drawable drawable = ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(new byte[10], 0, 0));
        fail("should not have reached here!");
    }

    @Test
    public void testOffsetByteArray() {
        for (int resId : RES_IDS) {
            int offset = 10;
            int extra = 15;
            byte[] array = getAsByteArray(resId, offset, extra);
            int length = array.length - extra - offset;
            // Used for SourceCreators that set both a position and an offset.
            int myOffset = 3;
            int myPosition = 7;
            assertEquals(offset, myOffset + myPosition);

            SourceCreator[] creators = new SourceCreator[] {
                unused -> ImageDecoder.createSource(array, offset, length),
                // Internally, this gives the buffer a position, but not an offset.
                unused -> ImageDecoder.createSource(ByteBuffer.wrap(array, offset, length)),
                unused -> {
                    // Same, but make it readOnly to ensure that we test the
                    // ByteBufferSource rather than the ByteArraySource.
                    ByteBuffer buf = ByteBuffer.wrap(array, offset, length);
                    return ImageDecoder.createSource(buf.asReadOnlyBuffer());
                },
                unused -> {
                    // slice() to give the buffer an offset.
                    ByteBuffer buf = ByteBuffer.wrap(array, 0, array.length - extra);
                    buf.position(offset);
                    return ImageDecoder.createSource(buf.slice());
                },
                unused -> {
                    // Same, but make it readOnly to ensure that we test the
                    // ByteBufferSource rather than the ByteArraySource.
                    ByteBuffer buf = ByteBuffer.wrap(array, 0, array.length - extra);
                    buf.position(offset);
                    return ImageDecoder.createSource(buf.slice().asReadOnlyBuffer());
                },
                unused -> {
                    // Use both a position and an offset.
                    ByteBuffer buf = ByteBuffer.wrap(array, myOffset,
                            array.length - extra - myOffset);
                    buf = buf.slice();
                    buf.position(myPosition);
                    return ImageDecoder.createSource(buf);
                },
                unused -> {
                    // Same, as readOnly.
                    ByteBuffer buf = ByteBuffer.wrap(array, myOffset,
                            array.length - extra - myOffset);
                    buf = buf.slice();
                    buf.position(myPosition);
                    return ImageDecoder.createSource(buf.asReadOnlyBuffer());
                },
                unused -> {
                    // Direct ByteBuffer with a position.
                    ByteBuffer buf = ByteBuffer.allocateDirect(array.length);
                    buf.put(array);
                    buf.position(offset);
                    return ImageDecoder.createSource(buf);
                },
                unused -> {
                    // Sliced direct ByteBuffer, for an offset.
                    ByteBuffer buf = ByteBuffer.allocateDirect(array.length);
                    buf.put(array);
                    buf.position(offset);
                    return ImageDecoder.createSource(buf.slice());
                },
                unused -> {
                    // Direct ByteBuffer with position and offset.
                    ByteBuffer buf = ByteBuffer.allocateDirect(array.length);
                    buf.put(array);
                    buf.position(myOffset);
                    buf = buf.slice();
                    buf.position(myPosition);
                    return ImageDecoder.createSource(buf);
                },
            };
            for (SourceCreator f : creators) {
                ImageDecoder.Source src = f.apply(0);
                try {
                    Drawable drawable = ImageDecoder.decodeDrawable(src, (info, decoder) -> {
                        decoder.setOnPartialImageListener(exception -> false);
                    });
                    assertNotNull(drawable);
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }
}
