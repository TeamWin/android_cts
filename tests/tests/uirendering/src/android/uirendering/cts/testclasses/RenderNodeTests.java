/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class RenderNodeTests extends ActivityTestBase {

    @Test
    public void testBasicDraw() {
        final Rect rect = new Rect(10, 10, 80, 80);

        final RenderNode renderNode = new RenderNode("Blue rect");
        renderNode.setLeftTopRightBottom(rect.left, rect.top, rect.right, rect.bottom);
        renderNode.setClipToBounds(true);

        {
            Canvas canvas = renderNode.startRecording();
            assertEquals(rect.width(), canvas.getWidth());
            assertEquals(rect.height(), canvas.getHeight());
            assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.BLUE);
            renderNode.endRecording();
        }

        assertTrue(renderNode.hasDisplayList());
        assertTrue(renderNode.hasIdentityMatrix());

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLUE, rect));
    }

    @Test
    public void testTranslationGetSet() {
        final RenderNode renderNode = new RenderNode("translation");

        assertTrue(renderNode.hasIdentityMatrix());

        assertFalse(renderNode.setTranslationX(0.0f));
        assertFalse(renderNode.setTranslationY(0.0f));
        assertFalse(renderNode.setTranslationZ(0.0f));

        assertTrue(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setTranslationX(1.0f));
        assertEquals(1.0f, renderNode.getTranslationX(), 0.0f);
        assertTrue(renderNode.setTranslationY(1.0f));
        assertEquals(1.0f, renderNode.getTranslationY(), 0.0f);
        assertTrue(renderNode.setTranslationZ(1.0f));
        assertEquals(1.0f, renderNode.getTranslationZ(), 0.0f);

        assertFalse(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setTranslationX(0.0f));
        assertTrue(renderNode.setTranslationY(0.0f));
        assertTrue(renderNode.setTranslationZ(0.0f));

        assertTrue(renderNode.hasIdentityMatrix());
    }

    @Test
    public void testAlphaGetSet() {
        final RenderNode renderNode = new RenderNode("alpha");

        assertFalse(renderNode.setAlpha(1.0f));
        assertTrue(renderNode.setAlpha(.5f));
        assertEquals(.5f, renderNode.getAlpha(), 0.0001f);
        assertTrue(renderNode.setAlpha(1.0f));
    }

    @Test
    public void testRotationGetSet() {
        final RenderNode renderNode = new RenderNode("rotation");

        assertFalse(renderNode.setRotationX(0.0f));
        assertFalse(renderNode.setRotationY(0.0f));
        assertFalse(renderNode.setRotation(0.0f));
        assertTrue(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setRotationX(1.0f));
        assertEquals(1.0f, renderNode.getRotationX(), 0.0f);
        assertTrue(renderNode.setRotationY(1.0f));
        assertEquals(1.0f, renderNode.getRotationY(), 0.0f);
        assertTrue(renderNode.setRotation(1.0f));
        assertEquals(1.0f, renderNode.getRotation(), 0.0f);
        assertFalse(renderNode.hasIdentityMatrix());

        assertTrue(renderNode.setRotationX(0.0f));
        assertTrue(renderNode.setRotationY(0.0f));
        assertTrue(renderNode.setRotation(0.0f));
        assertTrue(renderNode.hasIdentityMatrix());
    }

    @Test
    public void testScaleGetSet() {
        final RenderNode renderNode = new RenderNode("scale");

        assertFalse(renderNode.setScaleX(1.0f));
        assertFalse(renderNode.setScaleY(1.0f));

        assertTrue(renderNode.setScaleX(2.0f));
        assertEquals(2.0f, renderNode.getScaleX(), 0.0f);
        assertTrue(renderNode.setScaleY(2.0f));
        assertEquals(2.0f, renderNode.getScaleY(), 0.0f);

        assertTrue(renderNode.setScaleX(1.0f));
        assertTrue(renderNode.setScaleY(1.0f));
    }

    @Test
    public void testStartEndRecordingEmpty() {
        final RenderNode renderNode = new RenderNode(null);
        assertEquals(0, renderNode.getWidth());
        assertEquals(0, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.startRecording();
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(0, canvas.getWidth());
        assertEquals(0, canvas.getHeight());
        renderNode.endRecording();
    }

    @Test
    public void testStartEndRecordingWithBounds() {
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setLeftTopRightBottom(10, 20, 30, 50);
        assertEquals(20, renderNode.getWidth());
        assertEquals(30, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.startRecording();
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(20, canvas.getWidth());
        assertEquals(30, canvas.getHeight());
        renderNode.endRecording();
    }

    @Test
    public void testStartEndRecordingEmptyWithSize() {
        final RenderNode renderNode = new RenderNode(null);
        assertEquals(0, renderNode.getWidth());
        assertEquals(0, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.startRecording(5, 10);
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(5, canvas.getWidth());
        assertEquals(10, canvas.getHeight());
        renderNode.endRecording();
    }

    @Test
    public void testStartEndRecordingWithBoundsWithSize() {
        final RenderNode renderNode = new RenderNode(null);
        renderNode.setLeftTopRightBottom(10, 20, 30, 50);
        assertEquals(20, renderNode.getWidth());
        assertEquals(30, renderNode.getHeight());
        RecordingCanvas canvas = renderNode.startRecording(5, 10);
        assertTrue(canvas.isHardwareAccelerated());
        assertEquals(5, canvas.getWidth());
        assertEquals(10, canvas.getHeight());
        renderNode.endRecording();
    }
}
