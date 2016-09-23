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

package android.uirendering.cts.testclasses;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.support.test.filters.MediumTest;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.Surface;
import android.view.TextureView;

import org.junit.Test;

@MediumTest
public class TextureViewTests extends ActivityTestBase {
    @Test
    public void testConstructDetachedSingleBuffered() {
        testConstructDetached(true);
    }
    @Test
    public void testConstructDetachedMultiBuffered() {
        testConstructDetached(false);
    }

    private void testConstructDetached(boolean singleBuffered) {
        createTest()
                .addLayout(R.layout.textureview, (ViewInitializer) view -> {
                    SurfaceTexture texture = new SurfaceTexture(singleBuffered);
                    Surface producer = new Surface(texture);
                    Canvas canvas = producer.lockCanvas(null);
                    canvas.drawColor(Color.RED);
                    producer.unlockCanvasAndPost(canvas);
                    TextureView textureview = (TextureView) view;
                    textureview.setSurfaceTexture(texture);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.RED));
    }
}
