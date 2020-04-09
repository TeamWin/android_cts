package android.media.tv.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class Utils {
    private final static String TAG = "Utils";

    private Utils() {
    }

    public static boolean hasTvInputFramework(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_TV);
    }

    public static TestRule assumeFeatureRule(final String feature) {
        // Based on com.android.compatibility.common.util.RequiredFeatureRule
        return new TestRule() {
            private final boolean mHasFeature = false;

            @Override
            public Statement apply(Statement base, Description description) {
                return new Statement() {

                    @Override
                    public void evaluate() throws Throwable {
                        if (!mHasFeature) {
                            Log.d(TAG, "skipping "
                                    + description.getClassName() + "#" + description.getMethodName()
                                    + " because device does not have feature '" + feature + "'");
                            Assume.assumeTrue("Device does not have feature '" + feature + "'",
                                    mHasFeature);
                        }
                        base.evaluate();
                    }
                };
            }

            @Override
            public String toString() {
                return "RequiredFeatureRule[" + feature + "]";
            }
        };
    }
}
