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

import static android.uirendering.cts.util.MockVsyncHelper.nextFrame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.PerPixelBitmapVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.Tracer;
import android.uirendering.cts.util.BitmapAsserter;
import android.uirendering.cts.util.MockVsyncHelper;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EdgeEffectTests extends ActivityTestBase {

    private static final int WIDTH = 90;
    private static final int HEIGHT = 90;

    @Rule
    public Tracer name = new Tracer();

    private BitmapAsserter mBitmapAsserter = new BitmapAsserter(this.getClass().getSimpleName(),
            name.getMethodName());

    private Context mThemeContext;

    interface EdgeEffectInitializer {
        void initialize(EdgeEffect edgeEffect);
    }

    private Context getContext() {
        return mThemeContext;
    }

    @Before
    public void setUp() {
        final Context targetContext = InstrumentationRegistry.getTargetContext();
        mThemeContext = new ContextThemeWrapper(targetContext,
                android.R.style.Theme_Material_Light);
    }

    private static class EdgeEffectValidator extends PerPixelBitmapVerifier {
        public int matchedColorCount;

        private int mInverseColorMask;
        private int mColorMask;

        public EdgeEffectValidator(int drawColor) {
            mColorMask = drawColor & 0x00FFFFFF;
            mInverseColorMask = ~(drawColor & 0x00FFFFFF);
        }

        @Override
        protected boolean verifyPixel(int x, int y, int observedColor) {
            if ((observedColor & mColorMask) != 0) {
                matchedColorCount++;
            }
            return (observedColor & mInverseColorMask) == 0xFF000000;
        }
    }

    private void assertEdgeEffect(EdgeEffectInitializer initializer) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(WIDTH, HEIGHT);
        edgeEffect.setColor(Color.RED);
        assertEquals(Color.RED, edgeEffect.getColor());
        initializer.initialize(edgeEffect);
        edgeEffect.draw(canvas);

        EdgeEffectValidator verifier = new EdgeEffectValidator(edgeEffect.getColor());
        mBitmapAsserter.assertBitmapIsVerified(bitmap, verifier,
                name.getMethodName(), "EdgeEffect doesn't match expected");
        assertTrue(verifier.matchedColorCount > 0);
    }

    @Test
    public void testOnPull() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testSetSize() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.setSize(70, 70);
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testSetColor() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.setColor(Color.GREEN);
            assertEquals(Color.GREEN, edgeEffect.getColor());
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testSetBlendMode() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.setBlendMode(null);
            assertNull(edgeEffect.getBlendMode());
            edgeEffect.setBlendMode(EdgeEffect.DEFAULT_BLEND_MODE);
            assertEquals(BlendMode.SRC_ATOP, edgeEffect.getBlendMode());
            edgeEffect.onPull(1);
        });
    }

    @Test
    public void testOnPullWithDisplacement() {
        assertEdgeEffect(edgeEffect -> {
            edgeEffect.onPull(1, 0);
        });

        assertEdgeEffect(edgeEffect -> {
            edgeEffect.onPull(1, 1);
        });
    }

    @Test
    public void testIsFinished() {
        EdgeEffect effect = new EdgeEffect(getContext());
        assertTrue(effect.isFinished());
        effect.onPull(0.5f);
        assertFalse(effect.isFinished());
    }

    @Test
    public void testFinish() {
        EdgeEffect effect = new EdgeEffect(getContext());
        effect.onPull(1);
        effect.finish();
        assertTrue(effect.isFinished());

        effect.onAbsorb(1000);
        effect.finish();
        assertFalse(effect.draw(new Canvas()));
    }

    @Test
    public void testGetColor() {
        EdgeEffect effect = new EdgeEffect(getContext());
        effect.setColor(Color.GREEN);
        assertEquals(Color.GREEN, effect.getColor());
    }

    @Test
    public void testGetMaxHeight() {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setSize(200, 200);
        assertTrue(edgeEffect.getMaxHeight() <= 200 * 2 + 1);
        edgeEffect.setSize(200, 0);
        assertEquals(0, edgeEffect.getMaxHeight());
    }

    @Test
    public void testEdgeEffectTypeAccessors() {
        EdgeEffect effect = new EdgeEffect(getContext());

        // It defaults to glow without any attribute set
        assertEquals(EdgeEffect.TYPE_GLOW, effect.getType());
        effect.setType(EdgeEffect.TYPE_STRETCH);
        assertEquals(EdgeEffect.TYPE_STRETCH, effect.getType());
    }

    @Test
    public void testEdgeEffectTypeAttribute() {
        final Context targetContext = InstrumentationRegistry.getTargetContext();
        final Context themeContext =
                new ContextThemeWrapper(targetContext, R.style.StretchEdgeEffect);
        EdgeEffect withWarpEffect = new EdgeEffect(themeContext);
        assertEquals(EdgeEffect.TYPE_STRETCH, withWarpEffect.getType());
    }

    @Test
    public void testCustomViewEdgeEffectAttribute() {
        Context targetContext = InstrumentationRegistry.getTargetContext();
        LayoutInflater layoutInflater = LayoutInflater.from(targetContext);
        View view = layoutInflater.inflate(R.layout.stretch_edge_effect_view, null);
        assertTrue(view instanceof CustomEdgeEffectView);
        CustomEdgeEffectView customEdgeEffectView = (CustomEdgeEffectView) view;
        assertEquals(EdgeEffect.TYPE_STRETCH, customEdgeEffectView.edgeEffect.getType());
    }

    @Test
    public void testDistance() {
        EdgeEffect effect = new EdgeEffect(getContext());

        assertEquals(0f, effect.getDistance(), 0.001f);

        assertEquals(0.1f, effect.onPullDistance(0.1f, 0.5f), 0.001f);

        assertEquals(0.1f, effect.getDistance(), 0.001f);

        assertEquals(-0.05f, effect.onPullDistance(-0.05f, 0.5f), 0.001f);

        assertEquals(0.05f, effect.getDistance(), 0.001f);

        assertEquals(-0.05f, effect.onPullDistance(-0.2f, 0.5f), 0.001f);

        assertEquals(0f, effect.getDistance(), 0.001f);
    }

    // This is only needed temporarily while using the offset RenderEffect substitution.
    private int calculateEffectHeight(float width, float height) {
        final float radiusFactor = 0.6f;
        final float sin = (float) Math.sin(Math.PI / 6);
        final float cos = (float) Math.cos(Math.PI / 6);
        final float r = width * radiusFactor / sin;
        final float y = cos * r;
        final float h = r - y;

        return (int) Math.min(height, h);
    }

    private RenderNode drawEdgeEffect(float rotationDegrees, int distance) {
        int effectWidth = WIDTH - 20;
        int boxHeight = HEIGHT - 20;
        int effectHeight = boxHeight / 2;
        float realEffectHeight = calculateEffectHeight(effectWidth, effectHeight);
        float distanceFraction = distance / realEffectHeight;

        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setType(EdgeEffect.TYPE_STRETCH);
        edgeEffect.setSize(effectWidth, effectHeight);
        edgeEffect.onPullDistance(distanceFraction, 0.5f);

        Paint bluePaint = new Paint();
        bluePaint.setColor(Color.BLUE);
        bluePaint.setStyle(Paint.Style.FILL);

        RenderNode innerNode = new RenderNode("effect");
        innerNode.setPosition(0, 0, effectWidth, boxHeight);
        innerNode.setClipToBounds(false);
        Canvas effectCanvas = innerNode.beginRecording(effectWidth, boxHeight);
        effectCanvas.drawRect(0f, 0f, effectWidth, boxHeight, bluePaint);
        effectCanvas.rotate(rotationDegrees, effectWidth / 2f, boxHeight / 2f);

        edgeEffect.draw(effectCanvas);
        innerNode.endRecording();

        Paint whitePaint = new Paint();
        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(Color.WHITE);

        RenderNode outerNode = new RenderNode("outer");
        outerNode.setPosition(0, 0, WIDTH, HEIGHT);
        Canvas outerCanvas = outerNode.beginRecording(WIDTH, HEIGHT);
        outerCanvas.drawRect(0, 0, WIDTH, HEIGHT, whitePaint);
        outerCanvas.translate(10f, 10f);
        outerCanvas.drawRenderNode(innerNode);
        outerCanvas.translate(-10f, -10f);
        outerNode.endRecording();
        return outerNode;
    }

    @Test
    public void testStretchTop() {
        RenderNode renderNode = drawEdgeEffect(0, 5);

        Rect innerRect = new Rect(10, 15, WIDTH - 10, HEIGHT - 5);

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLUE, innerRect));
    }

    @Test
    public void testStretchRotated() {
        RenderNode renderNode = drawEdgeEffect(180, 5);

        Rect innerRect = new Rect(10, 5, WIDTH - 10, HEIGHT - 15);

        createTest()
                .addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
                    canvas.drawRenderNode(renderNode);
                }, true)
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLUE, innerRect));
    }

    /**
     * When a TYPE_STRETCH is used, a held pull should not retract.
     */
    @Test
    @LargeTest
    public void testStretchPullAndHold() throws Exception {
        EdgeEffect edgeEffect = createEdgeEffectWithPull(EdgeEffect.TYPE_STRETCH);
        assertEquals(0.25f, edgeEffect.getDistance(), 0.001f);

        // We must wait until the EdgeEffect would normally start receding (167 ms)
        sleepAnimationTime(200);

        // Drawing will cause updates of the distance if it is animating
        RenderNode renderNode = new RenderNode(null);
        Canvas canvas = renderNode.beginRecording();
        edgeEffect.draw(canvas);

        // A glow effect would start receding now, so let's be sure it doesn't:
        sleepAnimationTime(200);
        edgeEffect.draw(canvas);

        // It should not be updating now
        assertEquals(0.25f, edgeEffect.getDistance(), 0.001f);

        // Now let's release it and it should start animating
        edgeEffect.onRelease();

        sleepAnimationTime(20);

        // Now that it should be animating, the draw should update the distance
        edgeEffect.draw(canvas);

        assertTrue(edgeEffect.getDistance() < 0.25f);
    }

    /**
     * When a TYPE_GLOW is used, a held pull should retract after the timeout.
     */
    @Test
    @LargeTest
    public void testGlowPullAndHold() throws Exception {
        EdgeEffect edgeEffect = createEdgeEffectWithPull(EdgeEffect.TYPE_GLOW);
        assertEquals(0.25f, edgeEffect.getDistance(), 0.001f);

        // We must wait until the EdgeEffect would normally start receding (167 ms)
        sleepAnimationTime(200);

        // Drawing will cause updates of the distance if it is animating
        RenderNode renderNode = new RenderNode(null);
        Canvas canvas = renderNode.beginRecording();
        edgeEffect.draw(canvas);

        // It should start retracting now:
        sleepAnimationTime(20);
        edgeEffect.draw(canvas);
        assertTrue(edgeEffect.getDistance() < 0.25f);
    }

    /**
     * It should be possible to catch the stretch effect during an animation.
     */
    @Test
    @LargeTest
    public void testCatchStretchDuringAnimation() throws Exception {
        EdgeEffect edgeEffect = createEdgeEffectWithPull(EdgeEffect.TYPE_STRETCH);
        assertEquals(0.25f, edgeEffect.getDistance(), 0.001f);
        edgeEffect.onRelease();

        // Wait some time to be sure it is animating away.
        long startTime = AnimationUtils.currentAnimationTimeMillis();
        sleepAnimationTime(20);

        // Drawing will cause updates of the distance if it is animating
        RenderNode renderNode = new RenderNode(null);
        Canvas canvas = renderNode.beginRecording();
        edgeEffect.draw(canvas);

        // It should have started retracting. Now catch it.
        float consumed = edgeEffect.onPullDistance(0f, 0.5f);
        assertEquals(0f, consumed, 0f);

        float distanceAfterAnimation = edgeEffect.getDistance();
        assertTrue(distanceAfterAnimation < 0.25f);


        sleepAnimationTime(50);

        // There should be no change once it has been caught.
        edgeEffect.draw(canvas);
        assertEquals(distanceAfterAnimation, edgeEffect.getDistance(), 0f);
    }

    /**
     * It should be possible to catch the glow effect during an animation.
     */
    @Test
    @LargeTest
    public void testCatchGlowDuringAnimation() throws Exception {
        EdgeEffect edgeEffect = createEdgeEffectWithPull(EdgeEffect.TYPE_GLOW);
        edgeEffect.onRelease();

        // Wait some time to be sure it is animating away.
        long startTime = AnimationUtils.currentAnimationTimeMillis();
        sleepAnimationTime(20);

        // Drawing will cause updates of the distance if it is animating
        RenderNode renderNode = new RenderNode(null);
        Canvas canvas = renderNode.beginRecording();
        edgeEffect.draw(canvas);

        // It should have started retracting. Now catch it.
        float consumed = edgeEffect.onPullDistance(0f, 0.5f);
        assertEquals(0f, consumed, 0f);

        float distanceAfterAnimation = edgeEffect.getDistance();
        assertTrue(distanceAfterAnimation < 0.25f);


        sleepAnimationTime(50);

        // There should be no change once it has been caught.
        edgeEffect.draw(canvas);
        assertEquals(distanceAfterAnimation, edgeEffect.getDistance(), 0f);
    }

    private EdgeEffect createEdgeEffectWithPull(int edgeEffectType) {
        EdgeEffect edgeEffect = new EdgeEffect(getContext());
        edgeEffect.setType(edgeEffectType);
        edgeEffect.setSize(100, 100);
        edgeEffect.onPullDistance(0.25f, 0.5f);
        return edgeEffect;
    }

    /**
     * This sleeps until the {@link AnimationUtils#currentAnimationTimeMillis()} changes
     * by at least <code>durationMillis</code> milliseconds. This is useful for EdgeEffect because
     * it uses that mechanism to determine the animation duration.
     *
     * @param durationMillis The time to sleep in milliseconds.
     */
    private void sleepAnimationTime(long durationMillis) throws Exception {
        final long startTime = AnimationUtils.currentAnimationTimeMillis();
        long currentTime = startTime;
        final long endTime = startTime + durationMillis;
        do {
            Thread.sleep(endTime - currentTime);
            currentTime = AnimationUtils.currentAnimationTimeMillis();
        } while (currentTime < endTime);
    }

    private interface AlphaVerifier {
        void verify(int oldAlpha, int newAlpha);
    }

    // validates changes to the alpha of draw commands produced by EdgeEffect
    // over the course of an animation
    private void verifyAlpha(EdgeEffectInitializer initializer, AlphaVerifier alphaVerifier) {
        MockVsyncHelper.runOnVsyncThread(() -> {
            Canvas canvas = mock(Canvas.class);
            ArgumentCaptor<Paint> captor = ArgumentCaptor.forClass(Paint.class);
            EdgeEffect edgeEffect = new EdgeEffect(getContext());
            edgeEffect.setSize(200, 200);
            initializer.initialize(edgeEffect);
            edgeEffect.draw(canvas);
            verify(canvas).drawCircle(anyFloat(), anyFloat(), anyFloat(), captor.capture());
            int oldAlpha = captor.getValue().getAlpha();
            for (int i = 0; i < 3; i++) {
                nextFrame();
                canvas = mock(Canvas.class);
                edgeEffect.draw(canvas);
                verify(canvas).drawCircle(anyFloat(), anyFloat(), anyFloat(), captor.capture());
                int newAlpha = captor.getValue().getAlpha();
                alphaVerifier.verify(oldAlpha, newAlpha);
                oldAlpha = newAlpha;
            }
        });
    }

    @Test
    public void testOnAbsorb() {
        verifyAlpha(edgeEffect -> {
            edgeEffect.onAbsorb(10000);
        }, ((oldAlpha, newAlpha) -> {
            assertTrue("Alpha should grow", oldAlpha < newAlpha);
        }));
    }

    @Test
    public void testOnRelease() {
        verifyAlpha(edgeEffect -> {
            edgeEffect.onPull(1);
            edgeEffect.onRelease();
        }, ((oldAlpha, newAlpha) -> {
            assertTrue("Alpha should decrease", oldAlpha > newAlpha);
        }));
    }

    public static class CustomEdgeEffectView extends View {
        public EdgeEffect edgeEffect;

        public CustomEdgeEffectView(Context context) {
            this(context, null);
        }
        public CustomEdgeEffectView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public CustomEdgeEffectView(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public CustomEdgeEffectView(
                Context context,
                AttributeSet attrs,
                int defStyleAttr,
                int defStyleRes
        ) {
            super(context, attrs, defStyleAttr, defStyleRes);
            edgeEffect = new EdgeEffect(context, attrs);
        }
    }
}
