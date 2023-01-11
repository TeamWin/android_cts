/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Mesh
import android.graphics.MeshSpecification
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.uirendering.cts.bitmapverifiers.RectVerifier
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class MeshTest : ActivityTestBase() {

    // /////////////    MeshSpecification Tests    ///////////////

    @Test
    fun setSmallerStride() {
        assertThrows(IllegalArgumentException::class.java) {
            MeshSpecification.make(
                    simpleAttributeList, 4, simpleVaryingList,
                    simpleVertexShader, simpleFragmentShader
            )
        }
    }

    @Test
    fun setInvalidVS() {
        // doesn't return Varyings
        val vs: String = ("main(const Attributes attributes) { " +
                "     Varyings varyings;" +
                "     varyings.position = attributes.position;" +
                "     return varyings;" +
                "}")
        assertThrows(IllegalArgumentException::class.java) {
            MeshSpecification.make(
                    simpleAttributeList, 8, simpleVaryingList, vs,
                    simpleFragmentShader
            )
        }
    }

    @Test
    fun setInvalidFS() {
        val fs: String = ("float2 main(const Varyings varyings) {" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;" +
                "}")

        assertThrows(IllegalArgumentException::class.java) {
            MeshSpecification.make(
                    simpleAttributeList, 8, simpleVaryingList,
                    simpleVertexShader, fs
            )
        }
    }

    @Test
    fun testValidMeshSpecMake() {
        MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
    }

    @Test
    fun testMeshSpecWithColorSpace() {
        MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader,
                ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        )
    }

    @Test
    fun testMeshSpecWithAlphaType() {
        MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader,
                ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
                MeshSpecification.PREMUL
        )
    }

    @Test
    fun testMeshSpecMakeWithUnorderedAttributes() {
        val attList = mutableListOf(
                MeshSpecification.Attribute(
                        MeshSpecification.FLOAT2,
                        12,
                        "position"
                ),
                MeshSpecification.Attribute(
                        MeshSpecification.FLOAT3,
                        0,
                        "test"
                )
        )
        MeshSpecification.make(
                attList, 20, simpleVaryingList, simpleVertexShader,
                simpleFragmentShader
        )
    }

    @Test
    fun testMeshSpecMakeWithBiggerStride() {
        MeshSpecification.make(
                simpleAttributeList, 112, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
    }

    @Test
    fun testMeshSpecMakeWithNonEmptyVaryings() {
        val varyList = mutableListOf(
                MeshSpecification.Varying(MeshSpecification.FLOAT2, "uv")
        )
        MeshSpecification.make(
                simpleAttributeList, 8, varyList, simpleVertexShader,
                simpleFragmentShader
        )
    }

    // /////////////    Mesh Tests    ///////////////

    @Test
    fun setInvalidMode() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(6)
        vertexBuffer.put(0f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.put(50f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.rewind()
        assertThrows(IllegalArgumentException::class.java) {
            Mesh.make(meshSpec, 6, vertexBuffer, 3, Rect(0, 0, 0, 0))
        }
    }

    @Test
    fun setWrongVertexCountSmall() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(6)
        vertexBuffer.put(0f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.put(50f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.rewind()
        assertThrows(IllegalArgumentException::class.java) {
            Mesh.make(meshSpec, Mesh.TRIANGLES, vertexBuffer, 1, Rect(0, 0, 0, 0))
        }
    }

    @Test
    fun setWrongVertexCountBig() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(6)
        vertexBuffer.put(0f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.put(50f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.rewind()
        assertThrows(IllegalArgumentException::class.java) {
            Mesh.make(meshSpec, Mesh.TRIANGLES, vertexBuffer, 100, Rect(0, 0, 0, 0))
        }
    }

    @Test
    fun setBadVertexBuffer() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(6)
        vertexBuffer.put(0f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.put(50f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        assertThrows(IllegalArgumentException::class.java) {
            Mesh.make(meshSpec, Mesh.TRIANGLES, vertexBuffer, 3, Rect(0, 0, 0, 0))
        }
    }

    @Test
    fun testMeshMakeInvalidUniform() {
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(6)
        vertexBuffer.put(0f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.put(50f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.rewind()
        val mesh = Mesh.make(meshSpec, Mesh.TRIANGLES, vertexBuffer, 3, Rect(0, 0, 0, 0))

        assertThrows(IllegalArgumentException::class.java) {
            mesh.setFloatUniform("test", 1f)
        }
    }

    @Test
    fun testValidMeshMake() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(6)
        vertexBuffer.put(0f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.put(50f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.rewind()
        Mesh.make(meshSpec, Mesh.TRIANGLES, vertexBuffer, 3, Rect(0, 0, 0, 0))
    }

    @Test
    fun testValidMeshMakeIndexed() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(6)
        vertexBuffer.put(0f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.put(50f)
        vertexBuffer.put(0f)
        vertexBuffer.put(50f)
        vertexBuffer.rewind()
        val indexBuffer = ShortBuffer.allocate(3)
        indexBuffer.put(0, 0)
        indexBuffer.put(1, 1)
        indexBuffer.put(2, 2)
        indexBuffer.rewind()
        Mesh.makeIndexed(
                meshSpec, Mesh.TRIANGLES, vertexBuffer, 3, indexBuffer,
                Rect(0, 0, 100, 100)
        )
    }

    // /////////////    drawMesh Tests    ///////////////
    // @Test
    fun testSimpleDrawMesh() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
                meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
                Rect(20, 20, 80, 80)
        )

        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testSimpleIndexedDrawMesh() {
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f) // tl
        vertexBuffer.put(20f)

        vertexBuffer.put(80f) // br
        vertexBuffer.put(80f)

        vertexBuffer.put(20f) // bl
        vertexBuffer.put(80f)

        vertexBuffer.put(80f) // tr
        vertexBuffer.put(20f)

        vertexBuffer.put(20f)
        vertexBuffer.put(90f)

        vertexBuffer.put(90f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()
        val indexBuffer = ShortBuffer.allocate(12)
        indexBuffer.put(0, 0)
        indexBuffer.put(1, 1)
        indexBuffer.put(2, 2)

        indexBuffer.put(3, 0)
        indexBuffer.put(4, 1)
        indexBuffer.put(5, 3)

        indexBuffer.put(6, 3)
        indexBuffer.put(7, 1)
        indexBuffer.put(8, 5)

        indexBuffer.put(9, 1)
        indexBuffer.put(10, 3)
        indexBuffer.put(11, 4)
        indexBuffer.rewind()

        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.makeIndexed(
                meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
                indexBuffer, Rect(20, 20, 90, 90)
        )
        val points = Array(3) {
            Point(30, 30)
        }
        points[1] = Point(40, 40)
        points[2] = Point(89, 89)
        val colors = intArrayOf(
                Color.BLUE,
                Color.BLUE,
                Color.WHITE
        )
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(SamplePointVerifier(points, colors))
    }

    @Test
    fun testDrawMeshWithColorUniformInt() {
        val fragmentShader = ("layout(color) uniform float4 color;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
                simpleAttributeList, 8, simpleVaryingList,
                simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
                meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
                Rect(20, 20, 80, 80)
        )

        mesh.setColorUniform("color", Color.GREEN)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithColorUniformObject() {
        val fragmentShader = ("layout(color) uniform float4 color;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setColorUniform("color", Color())
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithColorUniformLong() {
        val fragmentShader = ("layout(color) uniform float4 color;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setColorUniform("color", 0L)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithIntUniform() {
        val fragmentShader = ("uniform int test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setIntUniform("test", 2)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithIntUniformTwo() {
        val fragmentShader = ("uniform int2 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setIntUniform("test", 1, 2)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithIntUniformThree() {
        val fragmentShader = ("uniform int3 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setIntUniform("test", 1, 2, 3)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithIntUniformFour() {
        val fragmentShader = ("uniform int4 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setIntUniform("test", 1, 2, 3, 4)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithIntUniformArray() {
        val fragmentShader = ("uniform int4 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setIntUniform("test", intArrayOf(1, 2, 3, 4))
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithFloatUniform() {
        val fragmentShader = ("uniform float test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setFloatUniform("test", 1f)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithFloatUniformTwo() {
        val fragmentShader = ("uniform float2 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setFloatUniform("test", 1f, 2f)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithFloatUniformThree() {
        val fragmentShader = ("uniform float3 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setFloatUniform("test", 1f, 2f, 3f)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithFloatUniformFour() {
        val fragmentShader = ("uniform float4 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setFloatUniform("test", 1f, 2f, 3f, 4f)
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithFloatUniformArray() {
        val fragmentShader = ("uniform float2 test;" +
                "float2 main(const Varyings varyings, out float4 color) {\n" +
                "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
                "      return varyings.position;\n" +
                "}")
        val meshSpec = MeshSpecification.make(
            simpleAttributeList, 8, simpleVaryingList,
            simpleVertexShader, fragmentShader
        )
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.make(
            meshSpec, Mesh.TRIANGLES, vertexBuffer, 6,
            Rect(20, 20, 80, 80)
        )

        mesh.setFloatUniform("test", floatArrayOf(1f, 2f))
        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    @Test
    fun testDrawMeshWithOutOfOrderAttributes() {
        val attList = mutableListOf(
                MeshSpecification.Attribute(
                        MeshSpecification.FLOAT,
                        8,
                        "offset"
                ),
                MeshSpecification.Attribute(
                        MeshSpecification.FLOAT2,
                        0,
                        "position"
                )
        )
        val meshSpec = MeshSpecification.make(attList, 12, simpleVaryingList,
                simpleVertexShader, simpleFragmentShader)
        val vertexBuffer = FloatBuffer.allocate(12)
        vertexBuffer.put(20f)
        vertexBuffer.put(20f)
        vertexBuffer.put(1f)
        vertexBuffer.put(80f)
        vertexBuffer.put(80f)
        vertexBuffer.put(2f)
        vertexBuffer.put(20f)
        vertexBuffer.put(80f)
        vertexBuffer.put(3f)
        vertexBuffer.put(80f)
        vertexBuffer.put(20f)
        vertexBuffer.put(4f)
        vertexBuffer.rewind()
        val indexBuffer = ShortBuffer.allocate(6)
        indexBuffer.put(0, 0)
        indexBuffer.put(1, 1)
        indexBuffer.put(2, 2)
        indexBuffer.put(3, 0)
        indexBuffer.put(4, 1)
        indexBuffer.put(5, 3)
        indexBuffer.rewind()

        val rect = Rect(20, 20, 80, 80)
        val paint = Paint()
        paint.color = Color.BLUE
        val mesh = Mesh.makeIndexed(
                meshSpec, Mesh.TRIANGLES, vertexBuffer, 4,
                indexBuffer, Rect(20, 20, 80, 80)
        )

        createTest().addCanvasClient({ canvas: Canvas, width: Int, height: Int ->
            canvas.drawMesh(mesh, BlendMode.SRC, paint)
        }, true).runWithVerifier(RectVerifier(Color.WHITE, paint.color, rect))
    }

    // /////////////    Test Values    ///////////////

    val simpleAttributeList: MutableList<MeshSpecification.Attribute> = mutableListOf(
            MeshSpecification.Attribute(
                    MeshSpecification.FLOAT2,
                    0,
                    "position"
            )
    )

    val simpleVaryingList = java.util.ArrayList<MeshSpecification.Varying>()

    val simpleVertexShader = ("Varyings main(const Attributes attributes) { " +
            "     Varyings varyings;" +
            "     varyings.position = attributes.position;" +
            "     return varyings;" +
            "}")

    val simpleFragmentShader = ("float2 main(const Varyings varyings, out float4 color) {\n" +
            "      color = vec4(1.0, 0.0, 0.0, 1.0);" +
            "      return varyings.position;\n" +
            "}")
}
