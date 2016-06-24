package android.uirendering.cts.testclasses;

import android.graphics.*;
import android.test.suitebuilder.annotation.MediumTest;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import org.junit.Test;

@MediumTest
public class ShapeTests extends ActivityTestBase {
    @Test
    public void testDashedOval() {
        createTest()
                .addLayout(R.layout.frame_layout,
                        view -> view.setBackgroundResource(R.drawable.dashed_oval))
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.golden_dashed_oval, new MSSIMComparer(0.99)));
    }
}
