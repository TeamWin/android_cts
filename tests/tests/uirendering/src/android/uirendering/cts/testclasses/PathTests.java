package android.uirendering.cts.testclasses;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.test.suitebuilder.annotation.MediumTest;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;

@MediumTest
public class PathTests extends ActivityTestBase {

    private static final double REGULAR_THRESHOLD = 0.92;

    @Test
    public void testTextPathWithOffset() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.BLACK);
                    paint.setAntiAlias(true);
                    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                    paint.setTextSize(26);
                    Path path = new Path();
                    String text = "Abc";
                    paint.getTextPath(text, 0, text.length(), 0, 0, path);
                    path.offset(0, 50);
                    canvas.drawPath(path, paint);
                })
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.text_path_with_offset, new MSSIMComparer(REGULAR_THRESHOLD)));
    }
}
