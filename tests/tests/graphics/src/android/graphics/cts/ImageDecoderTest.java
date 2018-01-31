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
import android.graphics.PostProcessor;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.content.FileProvider;
import android.util.Size;

import com.android.compatibility.common.util.BitmapUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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

    private static final class Record {
        public final int resId;
        public final int width;
        public final int height;
        public final String mimeType;

        public Record(int resId, int width, int height, String mimeType) {
            this.resId    = resId;
            this.width    = width;
            this.height   = height;
            this.mimeType = mimeType;
        }
    }

    private static final Record[] RECORDS = new Record[] {
        new Record(R.drawable.baseline_jpeg, 1280, 960, "image/jpeg"),
        new Record(R.drawable.png_test, 640, 480, "image/png"),
        new Record(R.drawable.gif_test, 320, 240, "image/gif"),
        new Record(R.drawable.bmp_test, 320, 240, "image/bmp"),
        new Record(R.drawable.webp_test, 640, 480, "image/webp"),
        new Record(R.drawable.google_chrome, 256, 256, "image/x-ico"),
        new Record(R.drawable.color_wheel, 128, 128, "image/x-ico"),
    };

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
        resId -> ImageDecoder.createSource(getAsByteBufferWrap(resId)),
        resId -> ImageDecoder.createSource(getAsDirectByteBuffer(resId)),
        resId -> ImageDecoder.createSource(getAsReadOnlyByteBuffer(resId)),
        resId -> ImageDecoder.createSource(getAsFile(resId)),
    };

    private interface UriCreator extends IntFunction<Uri> {};

    @Test
    public void testUris() {
        UriCreator creators[] = new UriCreator[] {
            resId -> getAsResourceUri(resId),
            resId -> getAsFileUri(resId),
            resId -> getAsContentUri(resId),
        };
        for (Record record : RECORDS) {
            int resId = record.resId;
            String name = mRes.getResourceEntryName(resId);
            for (UriCreator f : creators) {
                ImageDecoder.Source src = null;
                Uri uri = f.apply(resId);
                String fullName = name + ": " + uri.toString();
                src = ImageDecoder.createSource(mContentResolver, uri);

                assertNotNull("failed to create Source for " + fullName, src);
                try {
                    Drawable d = ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                        decoder.setOnPartialImageListener((e, source) -> {
                            fail("error for image " + fullName + ":\n" + e);
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                mWidth  = info.getSize().getWidth();
                mHeight = info.getSize().getHeight();
                mMimeType = info.getMimeType();
            }
        };
        Listener l = new Listener();

        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(record.resId);
                assertNotNull(src);
                try {
                    ImageDecoder.decodeDrawable(src, l);
                    assertEquals(record.width,  l.mWidth);
                    assertEquals(record.height, l.mHeight);
                    assertEquals(record.mimeType, l.mMimeType);
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testDecodeDrawable() {
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(record.resId);
                assertNotNull(src);

                try {
                    Drawable drawable = ImageDecoder.decodeDrawable(src);
                    assertNotNull(drawable);
                    assertEquals(record.width,  drawable.getIntrinsicWidth());
                    assertEquals(record.height, drawable.getIntrinsicHeight());
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testDecodeBitmap() {
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(record.resId);
                assertNotNull(src);

                try {
                    Bitmap bm = ImageDecoder.decodeBitmap(src);
                    assertNotNull(bm);
                    assertEquals(record.width, bm.getWidth());
                    assertEquals(record.height, bm.getHeight());
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
        ImageDecoder.Source src = mCreators[0].apply(RECORDS[0].resId);
        try {
            ImageDecoder.decodeBitmap(src, (decoder, info, s) -> decoder.setAllocator(15));
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                decoder.setAllocator(allocator);
                if (doScale) {
                    decoder.setResize(2);
                }
                if (doCrop) {
                    decoder.setCrop(new Rect(1, 1, info.getSize().getWidth()  / 2 - 1,
                                                   info.getSize().getHeight() / 2 - 1));
                }
            }
        };
        Listener l = new Listener();

        int allocators[] = new int[] {
            ImageDecoder.ALLOCATOR_DEFAULT,
            ImageDecoder.ALLOCATOR_SOFTWARE,
            ImageDecoder.ALLOCATOR_SHARED_MEMORY,
            ImageDecoder.ALLOCATOR_HARDWARE,
        };
        boolean trueFalse[] = new boolean[] { true, false };
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                for (int allocator : allocators) {
                    for (boolean doCrop : trueFalse) {
                        for (boolean doScale : trueFalse) {
                            l.doCrop = doCrop;
                            l.doScale = doScale;
                            l.allocator = allocator;
                            ImageDecoder.Source src = f.apply(record.resId);
                            assertNotNull(src);

                            Bitmap bm = null;
                            try {
                               bm = ImageDecoder.decodeBitmap(src, l);
                            } catch (IOException e) {
                                fail("Failed " + getAsResourceUri(record.resId) +
                                        " with exception " + e);
                            }
                            assertNotNull(bm);

                            switch (allocator) {
                                case ImageDecoder.ALLOCATOR_SOFTWARE:
                                // TODO: Once Bitmap provides access to its
                                // SharedMemory, confirm that ALLOCATOR_SHARED_MEMORY
                                // worked.
                                case ImageDecoder.ALLOCATOR_SHARED_MEMORY:
                                    assertNotEquals(Bitmap.Config.HARDWARE, bm.getConfig());

                                    if (!doScale && !doCrop) {
                                        Bitmap reference = BitmapFactory.decodeResource(mRes,
                                                record.resId, null);
                                        assertNotNull(reference);
                                        BitmapUtils.compareBitmaps(bm, reference);
                                    }
                                    break;
                                default:
                                    String name = getAsResourceUri(record.resId).toString();
                                    assertEquals("image " + name + "; allocator: " + allocator,
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
                            (decoder, info, s) -> decoder.setRequireUnpremultiplied(true));
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
    public void testPostProcessor() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
                decoder.setPostProcessor((canvas) -> {
                    canvas.drawColor(Color.BLACK);
                    return PixelFormat.OPAQUE;
                });
            }
        };
        Listener l = new Listener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                for (boolean requireSoftware : trueFalse) {
                    l.requireSoftware = requireSoftware;
                    ImageDecoder.Source src = f.apply(record.resId);
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
    public void testPostProcessorOverridesNinepatch() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
                decoder.setPostProcessor((c) -> PixelFormat.UNKNOWN);
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
    public void testPostProcessorAndMadeOpaque() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
                decoder.setPostProcessor((c) -> PixelFormat.OPAQUE);
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
    public void testPostProcessorAndAddedTransparency() {
        class Listener implements ImageDecoder.OnHeaderDecodedListener {
            public boolean requireSoftware;
            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
                decoder.setPostProcessor((c) -> PixelFormat.TRANSLUCENT);
            }
        };
        Listener l = new Listener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                for (boolean requireSoftware : trueFalse) {
                    l.requireSoftware = requireSoftware;
                    ImageDecoder.Source src = f.apply(record.resId);
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
    public void testPostProcessorTRANSPARENT() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setPostProcessor((c) -> PixelFormat.TRANSPARENT);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostProcessorInvalidReturn() {
        ImageDecoder.Source src = mCreators[0].apply(RECORDS[0].resId);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setPostProcessor((c) -> 42);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testPostProcessorAndUnpremul() {
        ImageDecoder.Source src = mCreators[0].apply(RECORDS[0].resId);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setRequireUnpremultiplied(true);
                decoder.setPostProcessor((c) -> PixelFormat.UNKNOWN);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test
    public void testPostProcessorAndScale() {
        class PostProcessorWithSize implements PostProcessor {
            public int width;
            public int height;
            @Override
            public int onPostProcess(Canvas canvas) {
                assertEquals(this.width,  width);
                assertEquals(this.height, height);
                return PixelFormat.UNKNOWN;
            };
        };
        final PostProcessorWithSize pp = new PostProcessorWithSize();
        for (Record record : RECORDS) {
            pp.width =  record.width  / 2;
            pp.height = record.height / 2;
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(record.resId);
                try {
                    Drawable drawable = ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                        decoder.setResize(pp.width, pp.height);
                        decoder.setPostProcessor(pp);
                    });
                    assertEquals(pp.width,  drawable.getIntrinsicWidth());
                    assertEquals(pp.height, drawable.getIntrinsicHeight());
                } catch (IOException e) {
                    fail("Failed " + getAsResourceUri(record.resId) + " with exception " + e);
                }
            }
        }
    }

    @Test
    public void testGetSampledSize() {
        class SampleListener implements ImageDecoder.OnHeaderDecodedListener {
            public Size dimensions;
            public int sampleSize;
            public boolean useSampleSizeDirectly;

            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                if (useSampleSizeDirectly) {
                    decoder.setResize(sampleSize);
                } else {
                    dimensions = decoder.getSampledSize(sampleSize);
                    decoder.setResize(dimensions.getWidth(), dimensions.getHeight());
                }
            }
        };

        SampleListener l = new SampleListener();

        int[] sampleSizes = new int[] { 1, 2, 3, 4, 6, 8, 16, 32, 64 };
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                for (int j = 0; j < sampleSizes.length; j++) {
                    l.sampleSize = sampleSizes[j];
                    l.useSampleSizeDirectly = false;
                    ImageDecoder.Source src = f.apply(record.resId);

                    try {
                        Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                        assertEquals(l.dimensions.getWidth(),  drawable.getIntrinsicWidth());
                        assertEquals(l.dimensions.getHeight(), drawable.getIntrinsicHeight());

                        l.useSampleSizeDirectly = true;
                        src = f.apply(record.resId);
                        drawable = ImageDecoder.decodeDrawable(src, l);
                        assertEquals(l.dimensions.getWidth(),  drawable.getIntrinsicWidth());
                        assertEquals(l.dimensions.getHeight(), drawable.getIntrinsicHeight());
                    } catch (IOException e) {
                        fail("Failed " + getAsResourceUri(record.resId) + " with exception " + e);
                    }
                }
            }
        }
    }

    @Test
    public void testLargeSampleSize() {
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                ImageDecoder.Source src = f.apply(record.resId);
                try {
                    ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                        Size dimensions = decoder.getSampledSize(info.getSize().getWidth());
                        assertEquals(dimensions.getWidth(), 1);

                        dimensions = decoder.getSampledSize(info.getSize().getWidth() + 5);
                        assertEquals(dimensions.getWidth(), 1);

                        dimensions = decoder.getSampledSize(info.getSize().getWidth() * 2);
                        assertEquals(dimensions.getWidth(), 1);
                    });
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }

    @Test
    public void testOnPartialImage() {
        class PartialImageCallback implements ImageDecoder.OnPartialImageListener {
            public boolean wasCalled;
            public boolean returnDrawable;
            @Override
            public boolean onPartialImage(int error, ImageDecoder.Source src) {
                wasCalled = true;
                assertEquals(ImageDecoder.ERROR_SOURCE_INCOMPLETE, error);
                return returnDrawable;
            }
        };
        final PartialImageCallback callback = new PartialImageCallback();
        boolean abortDecode[] = new boolean[] { true, false };
        for (Record record : RECORDS) {
            byte[] bytes = getAsByteArray(record.resId);
            int truncatedLength = bytes.length / 2;
            if (record.mimeType == "image/x-ico") {
                // FIXME (scroggo): SkIcoCodec currently does not support incomplete images.
                continue;
            }
            for (boolean abort : abortDecode) {
                ImageDecoder.Source src = ImageDecoder.createSource(
                        ByteBuffer.wrap(bytes, 0, truncatedLength));
                callback.wasCalled = false;
                callback.returnDrawable = !abort;
                try {
                    Drawable drawable = ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                        decoder.setOnPartialImageListener(callback);
                    });
                    assertFalse(abort);
                    assertNotNull(drawable);
                    assertEquals(record.width,  drawable.getIntrinsicWidth());
                    assertEquals(record.height, drawable.getIntrinsicHeight());
                } catch (IOException e) {
                    assertTrue(abort);
                }
                assertTrue(callback.wasCalled);
            }

            // null listener behaves as if onPartialImage returned false.
            ImageDecoder.Source src = ImageDecoder.createSource(
                    ByteBuffer.wrap(bytes, 0, truncatedLength));
            try {
                ImageDecoder.decodeDrawable(src);
                fail("Should have thrown an exception!");
            } catch (ImageDecoder.IncompleteException incomplete) {
                // This is the correct behavior.
            } catch (IOException e) {
                fail("Failed with exception " + e);
            }
        }
    }

    @Test
    public void testCorruptException() {
        class PartialImageCallback implements ImageDecoder.OnPartialImageListener {
            public boolean wasCalled = false;
            @Override
            public boolean onPartialImage(int error, ImageDecoder.Source src) {
                wasCalled = true;
                assertEquals(ImageDecoder.ERROR_SOURCE_ERROR, error);
                return true;
            }
        };
        final PartialImageCallback callback = new PartialImageCallback();
        byte[] bytes = getAsByteArray(R.drawable.png_test);
        // The four bytes starting with byte 40,000 represent the CRC. Changing
        // them will cause the decode to fail.
        for (int i = 0; i < 4; ++i) {
            bytes[40000 + i] = 'X';
        }
        ImageDecoder.Source src = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
        callback.wasCalled = false;
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setOnPartialImageListener(callback);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
        assertTrue(callback.wasCalled);
    }

    private static class DummyException extends RuntimeException {};

    @Test
    public void  testPartialImageThrowException() {
        byte[] bytes = getAsByteArray(R.drawable.png_test);
        ImageDecoder.Source src = ImageDecoder.createSource(
                ByteBuffer.wrap(bytes, 0, bytes.length / 2));
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setOnPartialImageListener((e, source) -> {
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
        int allocators[] = new int[] { ImageDecoder.ALLOCATOR_DEFAULT,
                                       ImageDecoder.ALLOCATOR_SOFTWARE,
                                       ImageDecoder.ALLOCATOR_SHARED_MEMORY };
        class HeaderListener implements ImageDecoder.OnHeaderDecodedListener {
            int allocator;
            boolean postProcess;
            @Override
            public void onHeaderDecoded(ImageDecoder decoder,
                                        ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                decoder.setMutable(true);
                decoder.setAllocator(allocator);
                if (postProcess) {
                    decoder.setPostProcessor((c) -> PixelFormat.UNKNOWN);
                }
            }
        };
        HeaderListener l = new HeaderListener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                for (boolean postProcess : trueFalse) {
                    for (int allocator : allocators) {
                        l.allocator = allocator;
                        l.postProcess = postProcess;

                        ImageDecoder.Source src = f.apply(record.resId);
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
        ImageDecoder.Source src = mCreators[0].apply(RECORDS[0].resId);
        try {
            ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                decoder.setMutable(true);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testMutableDrawable() {
        ImageDecoder.Source src = mCreators[0].apply(RECORDS[0].resId);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setMutable(true);
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
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> decoder.getSampledSize(0));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNegativeSampleSize() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> decoder.getSampledSize(-2));
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                decoder.setResize(width, height);
            }
        };
        ResizeListener l = new ResizeListener();

        float[] scales = new float[] { .0625f, .125f, .25f, .5f, .75f, 1.1f, 2.0f };
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                for (int j = 0; j < scales.length; ++j) {
                    l.width  = (int) (scales[j] * record.width);
                    l.height = (int) (scales[j] * record.height);

                    ImageDecoder.Source src = f.apply(record.resId);

                    try {
                        Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                        assertEquals(l.width,  drawable.getIntrinsicWidth());
                        assertEquals(l.height, drawable.getIntrinsicHeight());

                        src = f.apply(record.resId);
                        Bitmap bm = ImageDecoder.decodeBitmap(src, l);
                        assertEquals(l.width,  bm.getWidth());
                        assertEquals(l.height, bm.getHeight());
                    } catch (IOException e) {
                        fail("Failed " + getAsResourceUri(record.resId) + " with exception " + e);
                    }
                }

                try {
                    // Arbitrary square.
                    l.width  = 50;
                    l.height = 50;
                    ImageDecoder.Source src = f.apply(record.resId);
                    Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                    assertEquals(50,  drawable.getIntrinsicWidth());
                    assertEquals(50, drawable.getIntrinsicHeight());

                    // Swap width and height, for different scales.
                    l.height = record.width;
                    l.width  = record.height;
                    src = f.apply(record.resId);
                    drawable = ImageDecoder.decodeDrawable(src, l);
                    assertEquals(record.height, drawable.getIntrinsicWidth());
                    assertEquals(record.width,  drawable.getIntrinsicHeight());
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                decoder.setResize(width, height);
                decoder.setRequireUnpremultiplied(true);
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
            ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                decoder.setResize(info.getSize().getWidth() * 2, info.getSize().getHeight() * 2);
                decoder.setRequireUnpremultiplied(true);
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testResizeUnpremul() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.alpha);
        try {
            ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                // Choose a width and height that cannot be achieved with sampling.
                Size dims = decoder.getSampledSize(2);
                decoder.setResize(dims.getWidth() + 3, dims.getHeight() + 3);
                decoder.setRequireUnpremultiplied(true);
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                int width  = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                if (doScale) {
                    width  /= 2;
                    height /= 2;
                    decoder.setResize(width, height);
                }
                // Crop to the middle:
                int quarterWidth  = width  / 4;
                int quarterHeight = height / 4;
                cropRect = new Rect(quarterWidth, quarterHeight,
                        quarterWidth * 3, quarterHeight * 3);
                decoder.setCrop(cropRect);

                if (requireSoftware) {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
            }
        };
        Listener l = new Listener();
        boolean trueFalse[] = new boolean[] { true, false };
        for (Record record : RECORDS) {
            for (SourceCreator f : mCreators) {
                for (boolean doScale : trueFalse) {
                    l.doScale = doScale;
                    for (boolean requireSoftware : trueFalse) {
                        l.requireSoftware = requireSoftware;
                        ImageDecoder.Source src = f.apply(record.resId);

                        try {
                            Drawable drawable = ImageDecoder.decodeDrawable(src, l);
                            assertEquals(l.cropRect.width(),  drawable.getIntrinsicWidth());
                            assertEquals(l.cropRect.height(), drawable.getIntrinsicHeight());
                        } catch (IOException e) {
                            fail("Failed " + getAsResourceUri(record.resId) +
                                    " with exception " + e);
                        }
                    }
                }
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeZeroX() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) ->
                    decoder.setResize(0, info.getSize().getHeight()));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeZeroY() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) ->
                    decoder.setResize(info.getSize().getWidth(), 0));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeNegativeX() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) ->
                    decoder.setResize(-10, info.getSize().getHeight()));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResizeNegativeY() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) ->
                    decoder.setResize(info.getSize().getWidth(), -10));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testStoreImageDecoder() {
        class CachingCallback implements ImageDecoder.OnHeaderDecodedListener {
            ImageDecoder cachedDecoder;
            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                cachedDecoder = decoder;
            }
        };
        CachingCallback l = new CachingCallback();
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, l);
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
        l.cachedDecoder.getSampledSize(2);
    }

    @Test(expected=IllegalStateException.class)
    public void testDecodeUnpremulDrawable() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) ->
                    decoder.setRequireUnpremultiplied(true));
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropNegativeLeft() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setCrop(new Rect(-1, 0, info.getSize().getWidth(),
                                                info.getSize().getHeight()));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropNegativeTop() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setCrop(new Rect(0, -1, info.getSize().getWidth(),
                                                info.getSize().getHeight()));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropTooWide() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setCrop(new Rect(1, 0, info.getSize().getWidth() + 1,
                                               info.getSize().getHeight()));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropTooTall() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setCrop(new Rect(0, 1, info.getSize().getWidth(),
                                               info.getSize().getHeight() + 1));
            });
        } catch (IOException e) {
            fail("Failed with exception " + e);
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testCropResize() {
        ImageDecoder.Source src = mCreators[0].apply(R.drawable.png_test);
        try {
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setResize(info.getSize().getWidth() / 2, info.getSize().getHeight() / 2);
                decoder.setCrop(new Rect(0, 0, info.getSize().getWidth(),
                                               info.getSize().getHeight()));
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
            Bitmap bm = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                decoder.setAsAlphaMask(true);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
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
            ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                decoder.setAsAlphaMask(true);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE);
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                decoder.setAsAlphaMask(true);
                if (doScale) {
                    decoder.setResize(info.getSize().getWidth() / 2,
                                      info.getSize().getHeight() / 2);
                }
                if (doCrop) {
                    decoder.setCrop(new Rect(0, 0, info.getSize().getWidth() / 4,
                                                   info.getSize().getHeight() / 4));
                }
                if (doPostProcess) {
                    decoder.setPostProcessor((c) -> {
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                decoder.setPreferRamOverQuality(true);
                decoder.setAllocator(allocator);
            }
        };
        Listener l = new Listener();
        int resIds[] = new int[] { R.drawable.png_test, R.raw.basi6a16 };
        // Though png_test is opaque, using HARDWARE will require 8888, so we
        // do not decode to 565. basi6a16 will still downconvert from F16 to
        // 8888.
        boolean hardwareOverrides[] = new boolean[] { true, false };
        int[] allocators = new int[] { ImageDecoder.ALLOCATOR_HARDWARE,
                                       ImageDecoder.ALLOCATOR_SOFTWARE,
                                       ImageDecoder.ALLOCATOR_DEFAULT,
                                       ImageDecoder.ALLOCATOR_SHARED_MEMORY };
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
                if ((allocator == ImageDecoder.ALLOCATOR_HARDWARE ||
                     allocator == ImageDecoder.ALLOCATOR_DEFAULT)
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
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source src) {
                if (preferRamOverQuality) {
                    decoder.setPreferRamOverQuality(true);
                }
                if (doPostProcess) {
                    decoder.setPostProcessor((c) -> {
                        c.drawColor(Color.BLACK);
                        return PixelFormat.TRANSLUCENT;
                    });
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
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

    @Test
    public void testRespectOrientation() {
        // These 8 images test the 8 EXIF orientations. If the orientation is
        // respected, they all have the same dimensions: 100 x 80.
        // They are also identical (after adjusting), so compare them.
        Bitmap reference = null;
        for (int resId : new int[] { R.drawable.orientation_1,
                                     R.drawable.orientation_2,
                                     R.drawable.orientation_3,
                                     R.drawable.orientation_4,
                                     R.drawable.orientation_5,
                                     R.drawable.orientation_6,
                                     R.drawable.orientation_7,
                                     R.drawable.orientation_8,
                                     R.drawable.webp_orientation1,
                                     R.drawable.webp_orientation2,
                                     R.drawable.webp_orientation3,
                                     R.drawable.webp_orientation4,
                                     R.drawable.webp_orientation5,
                                     R.drawable.webp_orientation6,
                                     R.drawable.webp_orientation7,
                                     R.drawable.webp_orientation8,
        }) {
            if (resId == R.drawable.webp_orientation1) {
                // The webp files may not look exactly the same as the jpegs.
                // Recreate the reference.
                reference = null;
            }
            Uri uri = getAsResourceUri(resId);
            ImageDecoder.Source src = ImageDecoder.createSource(mContentResolver, uri);
            try {
                Bitmap bm = ImageDecoder.decodeBitmap(src);
                assertNotNull(bm);
                assertEquals(100, bm.getWidth());
                assertEquals(80,  bm.getHeight());

                if (reference == null) {
                    reference = bm;
                } else {
                    BitmapUtils.compareBitmaps(bm, reference);
                }
            } catch (IOException e) {
                fail("Decoding " + uri.toString() + " yielded " + e);
            }
        }
    }

    @Test(expected=IOException.class)
    public void testZeroLengthByteBuffer() throws IOException {
        Drawable drawable = ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(ByteBuffer.wrap(new byte[10], 0, 0)));
        fail("should not have reached here!");
    }

    @Test
    public void testOffsetByteArray() {
        for (Record record : RECORDS) {
            int offset = 10;
            int extra = 15;
            byte[] array = getAsByteArray(record.resId, offset, extra);
            int length = array.length - extra - offset;
            // Used for SourceCreators that set both a position and an offset.
            int myOffset = 3;
            int myPosition = 7;
            assertEquals(offset, myOffset + myPosition);

            SourceCreator[] creators = new SourceCreator[] {
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
                    Drawable drawable = ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                        decoder.setOnPartialImageListener((error, source) -> false);
                    });
                    assertNotNull(drawable);
                } catch (IOException e) {
                    fail("Failed with exception " + e);
                }
            }
        }
    }
}
