/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.uirendering.cts.bitmapverifiers.RectVerifier
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import android.uirendering.cts.testinfrastructure.CanvasClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class RuntimeShaderTests : ActivityTestBase() {

    @Test(expected = NullPointerException::class)
    fun createWithNullInput() {
        RuntimeShader(Nulls.type<String>())
    }

    @Test(expected = IllegalArgumentException::class)
    fun createWithEmptyInput() {
        RuntimeShader("")
    }

    val bitmapShader = BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    @Test(expected = NullPointerException::class)
    fun setNullUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform(Nulls.type<String>(), 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform("", 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform("invalid", 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidUniformType() {
        val shader = RuntimeShader(simpleShader)
        shader.setFloatUniform("inputInt", 1.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidUniformLength() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setFloatUniform("inputNonColor", 1.0f, 1.0f, 1.0f)
    }

    @Test(expected = NullPointerException::class)
    fun setNullIntUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform(Nulls.type<String>(), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyIntUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidIntUniformName() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("invalid", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidIntUniformType() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("inputFloat", 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidIntUniformLength() {
        val shader = RuntimeShader(simpleShader)
        shader.setIntUniform("inputInt", 1, 2)
    }

    @Test(expected = NullPointerException::class)
    fun setNullColorName() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform(Nulls.type<String>(), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyColorName() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidColorName() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("invalid", 0)
    }

    @Test(expected = NullPointerException::class)
    fun setNullColorValue() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("inputColor", Nulls.type<Color>())
    }

    @Test(expected = IllegalArgumentException::class)
    fun setColorValueNonColorUniform() {
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("inputNonColor", Color.BLUE)
    }

    @Test(expected = NullPointerException::class)
    fun setNullShaderName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader(Nulls.type<String>(), bitmapShader)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setEmptyShaderName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("", bitmapShader)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setInvalidShaderName() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("invalid", bitmapShader)
    }

    @Test(expected = NullPointerException::class)
    fun setNullShaderValue() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("inputShader", Nulls.type<Shader>())
    }

    @Test
    fun testDefaultUniform() {
        val shader = RuntimeShader(simpleShader)
        shader.setInputShader("inputShader", RuntimeShader(simpleRedShader))

        val paint = Paint()
        paint.shader = shader

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLACK, rect))
    }

    @Test
    fun testDefaultColorUniform() {
        val shader = RuntimeShader(simpleColorShader, true)

        val paint = Paint()
        paint.shader = shader

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLACK, rect))
    }

    @Test
    fun testDefaultInputShader() {
        val paint = Paint()
        paint.color = Color.BLUE
        paint.shader = RuntimeShader(mBlackIfInputNotOpaqueShader)

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDefaultInputShaderWithPaintAlpha() {
        val paint = Paint()
        paint.color = Color.argb(0.5f, 0.0f, 0.0f, 1.0f)
        paint.shader = RuntimeShader(mBlackIfInputNotOpaqueShader)
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        // The shader should be evaluated with an opaque paint color and the paint's alpha will be
        // applied after the shader returns but before it is blended into the destination
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testInputShaderWithPaintAlpha() {
        val shader = RuntimeShader(mBlackIfInputNotOpaqueShader)
        shader.setInputShader("inputShader", RuntimeShader(mSemiTransparentBlueShader))

        val paint = Paint()
        paint.color = Color.argb(0.5f, 0.0f, 1.0f, .0f)
        paint.shader = shader
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        // The shader should be evaluated first then the paint's alpha will be applied after the
        // shader returns but before it is blended into the destination
        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLACK, rect))
    }

    @Test
    fun testBasicColorUniform() {
        val color = Color.valueOf(Color.BLUE).convert(ColorSpace.get(ColorSpace.Named.BT2020))
        val shader = RuntimeShader(simpleColorShader)
        shader.setColorUniform("inputColor", color)

        val paint = Paint()
        paint.shader = shader

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLUE, rect))
    }

    @Test
    fun testOpaqueConstructor() {
        val shader = RuntimeShader(simpleColorShader, true)
        Assert.assertTrue(shader.isForceOpaque)

        val color = Color.valueOf(0.0f, 0.0f, 1.0f, 0.5f)
        shader.setFloatUniform("inputNonColor", color.components)
        shader.setIntUniform("useNonColor", 1)

        val paint = Paint()
        paint.shader = shader
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(CanvasClient
                { canvas: Canvas, width: Int, height: Int -> canvas.drawRect(rect, paint) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.BLUE, rect))
    }

    @Test
    fun testDrawThroughPicture() {
        val rect = Rect(10, 10, 80, 80)
        val picture = Picture()
        run {
            val paint = Paint()
            paint.shader = RuntimeShader(simpleRedShader)

            val canvas = picture.beginRecording(TEST_WIDTH, TEST_HEIGHT)
            canvas.clipRect(rect)
            canvas.drawPaint(paint)
            picture.endRecording()
        }
        Assert.assertTrue(picture.requiresHardwareAcceleration())

        createTest().addCanvasClient(CanvasClient
        { canvas: Canvas, width: Int, height: Int -> canvas.drawPicture(picture) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testDrawThroughPictureWithComposeShader() {
        val rect = Rect(10, 10, 80, 80)
        val picture = Picture()
        run {
            val paint = Paint()
            val runtimeShader = RuntimeShader(simpleRedShader)
            paint.shader = ComposeShader(runtimeShader, bitmapShader, BlendMode.DST)

            val canvas = picture.beginRecording(TEST_WIDTH, TEST_HEIGHT)
            canvas.clipRect(rect)
            canvas.drawPaint(paint)
            picture.endRecording()
        }
        Assert.assertTrue(picture.requiresHardwareAcceleration())

        createTest().addCanvasClient(CanvasClient
        { canvas: Canvas, width: Int, height: Int -> canvas.drawPicture(picture) },
                true).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDrawIntoSoftwareCanvas() {
        val paint = Paint()
        paint.shader = RuntimeShader(simpleRedShader)

        val canvas = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        canvas.drawRect(0f, 0f, 10f, 10f, paint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDrawIntoSoftwareCanvasWithComposeShader() {
        val paint = Paint()
        paint.shader = ComposeShader(RuntimeShader(simpleRedShader), bitmapShader, BlendMode.SRC)

        val canvas = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        canvas.drawRect(0f, 0f, 10f, 10f, paint)
    }

    val mSemiTransparentBlueShader = """
        vec4 main(vec2 coord) {
          return vec4(0.0, 0.0, 0.5, 0.5);
        }"""
    val simpleRedShader = """
       vec4 main(vec2 coord) {
          return vec4(1.0, 0.0, 0.0, 1.0);
       }"""
    val simpleColorShader = """
        layout(color) uniform vec4 inputColor;
        uniform vec4 inputNonColor;
        uniform int useNonColor;
       vec4 main(vec2 coord) {
          vec4 outputColor = inputColor;
          if (useNonColor != 0) {
            outputColor = inputNonColor;
          }
          return outputColor;
       }"""
    val simpleShader = """
        uniform shader inputShader;
        uniform float inputFloat;
        uniform int inputInt;
       vec4 main(vec2 coord) {
          float alpha = float(100 - inputInt) / 100.0;
          return vec4(inputShader.eval(coord).rgb * inputFloat, alpha);
       }"""
    val mBlackIfInputNotOpaqueShader = """
        uniform shader inputShader;
        vec4 main(vec2 coord) {
          vec4 color = inputShader.eval(coord);
          float multiplier = 1.0;
          if (color.a != 1.0) {
            multiplier = 0.0;
          }
          return vec4(color.rgb * multiplier, 1.0);
        }"""
}