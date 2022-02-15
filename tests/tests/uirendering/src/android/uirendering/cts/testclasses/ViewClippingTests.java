package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.AntiAliasingVerifier;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.bitmapverifiers.RegionVerifier;
import android.uirendering.cts.testclasses.view.UnclippedBlueView;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This tests view clipping by modifying properties of blue_padded_layout, and validating
 * the resulting rect of content.
 *
 * Since the layout is blue on a white background, this is always done with a RectVerifier.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewClippingTests extends ActivityTestBase {
    static final Rect FULL_RECT = new Rect(0, 0, 90, 90);
    static final Rect BOUNDS_RECT = new Rect(0, 0, 80, 80);
    static final Rect PADDED_RECT = new Rect(15, 16, 63, 62);
    static final Rect OUTLINE_RECT = new Rect(1, 2, 78, 79);
    static final Rect ANTI_ALIAS_OUTLINE_RECT = new Rect(20, 10, 80, 80);
    static final Rect CLIP_BOUNDS_RECT = new Rect(10, 20, 50, 60);
    static final Rect CONCAVE_OUTLINE_RECT1 = new Rect(0, 0, 10, 90);
    static final Rect CONCAVE_TEST_RECT1 = new Rect(0, 10, 90, 90);
    static final Rect CONCAVE_OUTLINE_RECT2 = new Rect(0, 0, 90, 10);
    static final Rect CONCAVE_TEST_RECT2 = new Rect(10, 0, 90, 90);

    static final ViewInitializer BOUNDS_CLIP_INIT =
            rootView -> ((ViewGroup)rootView).setClipChildren(true);

    static final ViewInitializer PADDING_CLIP_INIT = rootView -> {
        ViewGroup child = (ViewGroup) rootView.findViewById(R.id.child);
        child.setClipToPadding(true);
        child.setWillNotDraw(true);
        child.addView(new UnclippedBlueView(rootView.getContext()));
    };

    static final ViewInitializer OUTLINE_CLIP_INIT = rootView -> {
        View child = rootView.findViewById(R.id.child);
//        ((ViewGroup)(child.getParent())).setBackgroundColor(Color.WHITE);
        child.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(OUTLINE_RECT);
            }
        });
        child.setClipToOutline(true);
    };

    static final ViewInitializer OUTLINE_CLIP_AA_INIT = rootView -> {
        View child = rootView.findViewById(R.id.child);
        ((ViewGroup) (child.getParent())).setBackgroundColor(Color.BLACK);
        child.setOutlineProvider(new ViewOutlineProvider() {
            Path mPath = new Path();
            @Override
            public void getOutline(View view, Outline outline) {
                mPath.rewind();
                // We're using the AA outline rect as a starting point, but shifting one of the
                // vetices slightly to force AA for this not-quite-rectangle
                mPath.moveTo(ANTI_ALIAS_OUTLINE_RECT.left, ANTI_ALIAS_OUTLINE_RECT.top);
                mPath.lineTo(ANTI_ALIAS_OUTLINE_RECT.right, ANTI_ALIAS_OUTLINE_RECT.top);
                mPath.lineTo(ANTI_ALIAS_OUTLINE_RECT.right, ANTI_ALIAS_OUTLINE_RECT.bottom);
                mPath.lineTo(ANTI_ALIAS_OUTLINE_RECT.left + 1, ANTI_ALIAS_OUTLINE_RECT.bottom);
                mPath.close();
                outline.setPath(mPath);
            }
        });
        child.setClipToOutline(true);
    };

    static final ViewInitializer CONCAVE_CLIP_INIT = rootView -> {
        View child = rootView.findViewById(R.id.child);
        ((ViewGroup) (child.getParent())).setBackgroundColor(Color.BLACK);
        child.setOutlineProvider(new ViewOutlineProvider() {
            Path mPath = new Path();
            @Override
            public void getOutline(View view, Outline outline) {
                mPath.rewind();
                mPath.addRect(CONCAVE_OUTLINE_RECT1.left, CONCAVE_OUTLINE_RECT1.top,
                        CONCAVE_OUTLINE_RECT1.right, CONCAVE_OUTLINE_RECT1.bottom,
                        Path.Direction.CW);
                mPath.addRect(CONCAVE_OUTLINE_RECT2.left, CONCAVE_OUTLINE_RECT2.top,
                        CONCAVE_OUTLINE_RECT2.right, CONCAVE_OUTLINE_RECT2.bottom,
                        Path.Direction.CW);
                outline.setPath(mPath);
                assertTrue(outline.canClip());
            }
        });
        child.setClipToOutline(true);
    };

    static final ViewInitializer CLIP_BOUNDS_CLIP_INIT =
            view -> view.setClipBounds(CLIP_BOUNDS_RECT);

    static BitmapVerifier makeClipVerifier(Rect blueBoundsRect) {
        // very high error tolerance, since all these tests care about is clip alignment
        return new RectVerifier(Color.WHITE, Color.BLUE, blueBoundsRect, 75);
    }

    static BitmapVerifier makeConcaveClipVerifier() {
        return new RegionVerifier()
                .addVerifier(CONCAVE_TEST_RECT1, new RectVerifier(Color.BLACK, Color.BLUE,
                        CONCAVE_OUTLINE_RECT1, 75))
                .addVerifier(CONCAVE_TEST_RECT2, new RectVerifier(Color.BLACK, Color.BLUE,
                        CONCAVE_OUTLINE_RECT2, 75));
    }

    static BitmapVerifier makeAAClipVerifier(Rect blueBoundsRect) {
        return new AntiAliasingVerifier(Color.BLACK, Color.BLUE, blueBoundsRect);
    }

    @Test
    public void testSimpleUnclipped() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, null)
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testSimpleBoundsClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, BOUNDS_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(BOUNDS_RECT));
    }

    @Test
    public void testSimpleClipBoundsClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, CLIP_BOUNDS_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(CLIP_BOUNDS_RECT));
    }

    @Test
    public void testSimplePaddingClip() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, PADDING_CLIP_INIT)
                .runWithVerifier(makeClipVerifier(PADDED_RECT));
    }
    // TODO: add tests with clip + scroll, and with interesting combinations of the above

    @Test
    public void testSimpleOutlineClip() {
        // NOTE: Only HW is supported
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_INIT, true)
                .runWithVerifier(makeClipVerifier(OUTLINE_RECT));

        // SW ignores the outline clip
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_INIT, false)
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testAntiAliasedOutlineClip() {
        // NOTE: Only HW is supported
        createTest()
                .addLayout(R.layout.blue_padded_layout, OUTLINE_CLIP_AA_INIT, true)
                .runWithVerifier(makeAAClipVerifier(ANTI_ALIAS_OUTLINE_RECT));
    }

    @Test
    public void testOvalOutlineClip() {
        // As of Android T, Outline clipping is enabled for all shapes.
        createTest()
                .addLayout(R.layout.blue_padded_layout, view -> {
                    view.setOutlineProvider(new ViewOutlineProvider() {
                        Path mPath = new Path();
                        @Override
                        public void getOutline(View view, Outline outline) {
                            mPath.reset();
                            mPath.addOval(0, 0, view.getWidth(), view.getHeight(),
                                    Path.Direction.CW);
                            outline.setPath(mPath);
                            assertTrue(outline.canClip());
                        }
                    });
                    view.setClipToOutline(false); // should do nothing
                })
                .runWithVerifier(makeClipVerifier(FULL_RECT));
    }

    @Test
    public void testConcaveOutlineClip() {
        // As of Q, Outline#setPath (previously called setConvexPath) no longer throws on a concave
        // path, but it does not result in clipping, which is only supported when explicitly calling
        // one of the other setters. (hw no-op's the arbitrary path, and sw doesn't support Outline
        // clipping.)
        // As of T, path clipping is enabled for all Outline shapes.
        createTest()
                .addLayout(R.layout.blue_padded_layout, CONCAVE_CLIP_INIT, true)
                .runWithVerifier(makeConcaveClipVerifier());
    }
}
