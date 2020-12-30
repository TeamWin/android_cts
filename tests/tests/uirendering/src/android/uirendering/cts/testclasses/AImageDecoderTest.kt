/*
 * Copyright 2020 The Android Open Source Project
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

package android.uirendering.cts.testclasses

import androidx.test.InstrumentationRegistry

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.uirendering.cts.bitmapcomparers.MSSIMComparer
import android.uirendering.cts.bitmapverifiers.ColorVerifier
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(JUnitParamsRunner::class)
class AImageDecoderTest {
    init {
        System.loadLibrary("ctsuirendering_jni")
    }

    private val ANDROID_IMAGE_DECODER_SUCCESS = 0
    private val ANDROID_IMAGE_DECODER_INVALID_CONVERSION = -3
    private val ANDROID_IMAGE_DECODER_INVALID_SCALE = -4
    private val ANDROID_IMAGE_DECODER_BAD_PARAMETER = -5
    private val ANDROID_IMAGE_DECODER_FINISHED = -10
    private val ANDROID_IMAGE_DECODER_INVALID_STATE = -11

    private fun getAssets(): AssetManager {
        return InstrumentationRegistry.getTargetContext().getAssets()
    }

    @Test
    fun testNullDecoder() = nTestNullDecoder()

    private enum class Crop {
        Top,    // Crop a section of the image that contains the top
        Left,   // Crop a section of the image that contains the left
        None,
    }

    /**
     * Helper class to decode a scaled, cropped image to compare to AImageDecoder.
     *
     * Includes properties for getting the right scale and crop values to use in
     * AImageDecoder.
     */
    private inner class DecodeAndCropper constructor(
        image: String,
        scale: Float,
        crop: Crop
    ) {
        val bitmap: Bitmap
        var targetWidth: Int = 0
            private set
        var targetHeight: Int = 0
            private set
        val cropRect: Rect?

        init {
            val source = ImageDecoder.createSource(getAssets(), image)
            val tmpBm = ImageDecoder.decodeBitmap(source) {
                decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (scale == 1.0f) {
                        targetWidth = info.size.width
                        targetHeight = info.size.height
                    } else {
                        targetWidth = (info.size.width * scale).toInt()
                        targetHeight = (info.size.height * scale).toInt()
                        decoder.setTargetSize(targetWidth, targetHeight)
                    }
            }
            cropRect = when (crop) {
                Crop.Top -> Rect((targetWidth / 3.0f).toInt(), 0,
                        (targetWidth * 2 / 3.0f).toInt(),
                        (targetHeight / 2.0f).toInt())
                Crop.Left -> Rect(0, (targetHeight / 3.0f).toInt(),
                        (targetWidth / 2.0f).toInt(),
                        (targetHeight * 2 / 3.0f).toInt())
                Crop.None -> null
            }
            if (cropRect == null) {
                bitmap = tmpBm
            } else {
                // Crop using Bitmap, rather than ImageDecoder, because it uses
                // the same code as AImageDecoder for cropping.
                bitmap = Bitmap.createBitmap(tmpBm, cropRect.left, cropRect.top,
                        cropRect.width(), cropRect.height())
                if (bitmap !== tmpBm) {
                    tmpBm.recycle()
                }
            }
        }
    }

    // Create a Bitmap with the same size and colorspace as bitmap.
    private fun makeEmptyBitmap(bitmap: Bitmap) = Bitmap.createBitmap(bitmap.width, bitmap.height,
                bitmap.config, true, bitmap.colorSpace!!)

    private fun setCrop(decoder: Long, rect: Rect): Int = with(rect) {
        nSetCrop(decoder, left, top, right, bottom)
    }

    /**
     * Test that all frames in the image look as expected.
     *
     * @param image Name of the animated image file.
     * @param frameName Template for creating the name of the expected image
     *                  file for the i'th frame.
     * @param numFrames Total number of frames in the animated image.
     * @param scaleFactor The factor by which to scale the image.
     * @param crop The crop setting to use.
     * @param mssimThreshold The minimum MSSIM value to accept as similar. Some
     *                       images do not match exactly, but they've been
     *                       manually verified to look the same.
     */
    private fun decodeAndCropFrames(
        image: String,
        frameName: String,
        numFrames: Int,
        scaleFactor: Float,
        crop: Crop,
        mssimThreshold: Double
    ) {
        val decodeAndCropper = DecodeAndCropper(image, scaleFactor, crop)
        var expectedBm = decodeAndCropper.bitmap

        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        if (scaleFactor != 1.0f) {
            with(decodeAndCropper) {
                assertEquals(nSetTargetSize(decoder, targetWidth, targetHeight),
                        ANDROID_IMAGE_DECODER_SUCCESS)
            }
        }
        with(decodeAndCropper.cropRect) {
            this?.let {
                assertEquals(setCrop(decoder, this), ANDROID_IMAGE_DECODER_SUCCESS)
            }
        }

        val testBm = makeEmptyBitmap(decodeAndCropper.bitmap)

        var i = 0
        while (true) {
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
            val verifier = GoldenImageVerifier(expectedBm, MSSIMComparer(mssimThreshold))
            assertTrue(verifier.verify(testBm), "$image has mismatch in frame $i")
            expectedBm.recycle()

            i++
            when (val result = nAdvanceFrame(decoder)) {
                ANDROID_IMAGE_DECODER_SUCCESS -> {
                    assertTrue(i < numFrames, "Unexpected frame $i in $image")
                    expectedBm = DecodeAndCropper(frameName.format(i), scaleFactor, crop).bitmap
                }
                ANDROID_IMAGE_DECODER_FINISHED -> {
                    assertEquals(i, numFrames, "Expected $numFrames frames in $image; found $i")
                    break
                }
                else -> fail("Unexpected error $result when advancing $image to frame $i")
            }
        }

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun animationsAndFrames() = arrayOf(
        arrayOf<Any>("animated.gif", "animated_%03d.gif", 4),
        arrayOf<Any>("animated_webp.webp", "animated_%03d.gif", 4),
        arrayOf<Any>("required_gif.gif", "required_%03d.png", 7),
        arrayOf<Any>("required_webp.webp", "required_%03d.png", 7),
        arrayOf<Any>("alphabetAnim.gif", "alphabetAnim_%03d.png", 13),
        arrayOf<Any>("blendBG.webp", "blendBG_%03d.png", 7),
        arrayOf<Any>("stoplight.webp", "stoplight_%03d.png", 3)
    )

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFrames(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 1.0f, Crop.None, .955)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesScaleDown(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .5f, Crop.None, .749)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesScaleDown2(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .75f, Crop.None, .749)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesScaleUp(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 2.0f, Crop.None, .875)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTop(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 1.0f, Crop.Top, .934)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTopScaleDown(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .5f, Crop.Top, .749)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTopScaleDown2(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .75f, Crop.Top, .749)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropTopScaleUp(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 3.0f, Crop.Top, .908)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeft(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 1.0f, Crop.Left, .924)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeftScaleDown(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .5f, Crop.Left, .596)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeftScaleDown2(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, .75f, Crop.Left, .596)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeFramesAndCropLeftScaleUp(image: String, frameName: String, numFrames: Int) {
        decodeAndCropFrames(image, frameName, numFrames, 3.0f, Crop.Left, .894)
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testRewind(image: String, unused: String, numFrames: Int) {
        val frame0 = with(ImageDecoder.createSource(getAssets(), image)) {
            ImageDecoder.decodeBitmap(this) {
                decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        // Regardless of the current frame, calling rewind and decoding should
        // look like frame_0.
        for (framesBeforeReset in 0 until numFrames) {
            val asset = nOpenAsset(getAssets(), image)
            val decoder = nCreateFromAsset(asset)
            val testBm = makeEmptyBitmap(frame0)
            for (i in 1..framesBeforeReset) {
                nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
                assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nAdvanceFrame(decoder))
            }

            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nRewind(decoder))
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)

            val verifier = GoldenImageVerifier(frame0, MSSIMComparer(1.0))
            assertTrue(verifier.verify(testBm), "Mismatch in $image after " +
                        "decoding $framesBeforeReset and then rewinding!")

            nDeleteDecoder(decoder)
            nCloseAsset(asset)
        }
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testDecodeReturnsFinishedAtEnd(image: String, unused: String, numFrames: Int) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        for (i in 0 until (numFrames - 1)) {
            assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_SUCCESS)
        }

        assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_FINISHED)

        // Create a Bitmap to decode into and verify that no decoding occurred.
        val width = nGetWidth(decoder)
        val height = nGetHeight(decoder)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true)
        nDecode(decoder, bitmap, ANDROID_IMAGE_DECODER_FINISHED)

        nDeleteDecoder(decoder)
        nCloseAsset(asset)

        // Every pixel should be transparent black, as no decoding happened.
        assertTrue(ColorVerifier(0, 0).verify(bitmap))
        bitmap.recycle()
    }

    @Test
    @Parameters(method = "animationsAndFrames")
    fun testAdvanceReturnsFinishedAtEnd(image: String, unused: String, numFrames: Int) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        for (i in 0 until (numFrames - 1)) {
            assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_SUCCESS)
        }

        for (i in 0..1000) {
            assertEquals(nAdvanceFrame(decoder), ANDROID_IMAGE_DECODER_FINISHED)
        }

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun nonAnimatedAssets() = arrayOf(
        "blue-16bit-prophoto.png", "green-p3.png", "linear-rgba16f.png", "orange-prophotorgb.png",
        "animated_000.gif", "animated_001.gif", "animated_002.gif", "sunset1.jpg"
    )

    @Test
    @Parameters(method = "nonAnimatedAssets")
    fun testAdvanceFrameFailsNonAnimated(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        assertEquals(ANDROID_IMAGE_DECODER_BAD_PARAMETER, nAdvanceFrame(decoder))
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    @Test
    @Parameters(method = "nonAnimatedAssets")
    fun testRewindFailsNonAnimated(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        assertEquals(ANDROID_IMAGE_DECODER_BAD_PARAMETER, nRewind(decoder))
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun imagesAndSetters(): ArrayList<Any> {
        val setters = arrayOf<(Long) -> Int>(
            { decoder -> nSetUnpremultipliedRequired(decoder, true) },
            { decoder ->
                val rect = Rect(0, 0, nGetWidth(decoder) / 2, nGetHeight(decoder) / 2)
                setCrop(decoder, rect)
            },
            { decoder ->
                val ANDROID_BITMAP_FORMAT_RGBA_F16 = 9
                nSetAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGBA_F16)
            },
            { decoder ->
                nSetTargetSize(decoder, nGetWidth(decoder) / 2, nGetHeight(decoder) / 2)
            },
            { decoder ->
                val ADATASPACE_DISPLAY_P3 = 143261696
                nSetDataSpace(decoder, ADATASPACE_DISPLAY_P3)
            }
        )
        val list = ArrayList<Any>()
        for (animations in animationsAndFrames()) {
            for (setter in setters) {
                list.add(arrayOf(animations[0], animations[2], setter))
            }
        }
        return list
    }

    @Test
    @Parameters(method = "imagesAndSetters")
    fun testSettersFailOnLatterFrames(image: String, numFrames: Int, setter: (Long) -> Int) {
        // Verify that the setter succeeds on the first frame.
        with(nOpenAsset(getAssets(), image)) {
            val decoder = nCreateFromAsset(this)
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, setter(decoder))
            nDeleteDecoder(decoder)
            nCloseAsset(this)
        }

        for (framesBeforeSet in 1 until numFrames) {
            val asset = nOpenAsset(getAssets(), image)
            val decoder = nCreateFromAsset(asset)
            for (i in 1..framesBeforeSet) {
                assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nAdvanceFrame(decoder))
            }

            // Not on the first frame, so the setter fails.
            assertEquals(ANDROID_IMAGE_DECODER_INVALID_STATE, setter(decoder))

            // Rewind to the beginning. Now the setter can succeed.
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nRewind(decoder))
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, setter(decoder))

            nDeleteDecoder(decoder)
            nCloseAsset(asset)
        }
    }

    fun unpremulTestFiles() = arrayOf(
        "alphabetAnim.gif", "animated_webp.webp", "stoplight.webp"
    )

    @Test
    @Parameters(method = "unpremulTestFiles")
    fun testUnpremul(image: String) {
        val expectedBm = with(ImageDecoder.createSource(getAssets(), image)) {
            ImageDecoder.decodeBitmap(this) {
                decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setUnpremultipliedRequired(true)
            }
        }

        val testBm = makeEmptyBitmap(expectedBm)

        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetUnpremultipliedRequired(decoder, true))
        nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)

        val verifier = GoldenImageVerifier(expectedBm, MSSIMComparer(1.0))
        assertTrue(verifier.verify(testBm), "$image did not match in unpremul")

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun imagesWithAlpha() = arrayOf(
        "alphabetAnim.gif",
        "animated_webp.webp",
        "animated.gif"
    )

    @Test
    @Parameters(method = "imagesWithAlpha")
    fun testUnpremulThenScaleFailsWithAlpha(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val width = nGetWidth(decoder)
        val height = nGetHeight(decoder)

        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetUnpremultipliedRequired(decoder, true))
        assertEquals(ANDROID_IMAGE_DECODER_INVALID_SCALE,
                nSetTargetSize(decoder, width * 2, height * 2))
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    @Test
    @Parameters(method = "imagesWithAlpha")
    fun testScaleThenUnpremulFailsWithAlpha(image: String) {
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val width = nGetWidth(decoder)
        val height = nGetHeight(decoder)

        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS,
                nSetTargetSize(decoder, width * 2, height * 2))
        assertEquals(ANDROID_IMAGE_DECODER_INVALID_CONVERSION,
                nSetUnpremultipliedRequired(decoder, true))
        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    fun opaquePlusScale(): ArrayList<Any> {
        val opaqueImages = arrayOf("sunset1.jpg", "blendBG.webp", "stoplight.webp")
        val scales = arrayOf(.5f, .75f, 2.0f)
        val list = ArrayList<Any>()
        for (image in opaqueImages) {
            for (scale in scales) {
                list.add(arrayOf(image, scale))
            }
        }
        return list
    }

    @Test
    @Parameters(method = "opaquePlusScale")
    fun testUnpremulPlusScaleOpaque(image: String, scale: Float) {
        val expectedBm = with(ImageDecoder.createSource(getAssets(), image)) {
            ImageDecoder.decodeBitmap(this) {
                decoder, info, _ ->
                    decoder.isUnpremultipliedRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = (info.size.width * scale).toInt()
                    val height = (info.size.height * scale).toInt()
                    decoder.setTargetSize(width, height)
            }
        }
        val verifier = GoldenImageVerifier(expectedBm, MSSIMComparer(1.0))

        // Flipping the order of setting unpremul and scaling results in taking
        // a different code path. Ensure both succeed.
        val ops = listOf(
            { decoder: Long -> nSetUnpremultipliedRequired(decoder, true) },
            { decoder: Long -> nSetTargetSize(decoder, expectedBm.width, expectedBm.height) }
        )

        for (order in setOf(ops, ops.asReversed())) {
            val testBm = makeEmptyBitmap(expectedBm)
            val asset = nOpenAsset(getAssets(), image)
            val decoder = nCreateFromAsset(asset)
            for (op in order) {
                assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, op(decoder))
            }
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
            assertTrue(verifier.verify(testBm))

            nDeleteDecoder(decoder)
            nCloseAsset(asset)
            testBm.recycle()
        }
        expectedBm.recycle()
    }

    @Test
    fun testUnpremulPlusScaleWithFrameWithAlpha() {
        // The first frame of this image is opaque, so unpremul + scale succeeds.
        // But frame 3 has alpha, so decoding it with unpremul + scale fails.
        val image = "blendBG.webp"
        val scale = 2.0f
        val asset = nOpenAsset(getAssets(), image)
        val decoder = nCreateFromAsset(asset)
        val width = (nGetWidth(decoder) * scale).toInt()
        val height = (nGetHeight(decoder) * scale).toInt()

        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetUnpremultipliedRequired(decoder, true))
        assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nSetTargetSize(decoder, width, height))

        val testBm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true)
        for (i in 0 until 3) {
            nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_SUCCESS)
            assertEquals(ANDROID_IMAGE_DECODER_SUCCESS, nAdvanceFrame(decoder))
        }
        nDecode(decoder, testBm, ANDROID_IMAGE_DECODER_INVALID_SCALE)

        nDeleteDecoder(decoder)
        nCloseAsset(asset)
    }

    private external fun nTestNullDecoder()
    private external fun nOpenAsset(assets: AssetManager, name: String): Long
    private external fun nCloseAsset(asset: Long)
    private external fun nCreateFromAsset(asset: Long): Long
    private external fun nGetWidth(decoder: Long): Int
    private external fun nGetHeight(decoder: Long): Int
    private external fun nDeleteDecoder(decoder: Long)
    private external fun nSetTargetSize(decoder: Long, width: Int, height: Int): Int
    private external fun nSetCrop(decoder: Long, left: Int, top: Int, right: Int, bottom: Int): Int
    private external fun nDecode(decoder: Long, dst: Bitmap, expectedResult: Int)
    private external fun nAdvanceFrame(decoder: Long): Int
    private external fun nRewind(decoder: Long): Int
    private external fun nSetUnpremultipliedRequired(decoder: Long, required: Boolean): Int
    private external fun nSetAndroidBitmapFormat(decoder: Long, format: Int): Int
    private external fun nSetDataSpace(decoder: Long, format: Int): Int
}
