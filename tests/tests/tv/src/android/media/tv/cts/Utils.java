package android.media.tv.cts;

import android.content.Context;
import android.content.pm.PackageManager;

import org.junit.AssumptionViolatedException;

public class Utils {

    private Utils() {
    }

    /**
     * True if the has feature {@value PackageManager#FEATURE_LIVE_TV}.
     *
     * @throws AssumptionViolatedException if the device has feature
     *                                     {@value PackageManager#FEATURE_LIVE_TV}.
     * @deprecated use {@Link com.android.compatibility.common.util.RequiredFeatureRule}
     */
    @Deprecated
    public static boolean assumeHasTvInputFramework(Context context) {
        boolean hasSystemFeature = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LIVE_TV);
        if (!hasSystemFeature) {
            throw new AssumptionViolatedException(
                    "Device does not have feature '" + PackageManager.FEATURE_LIVE_TV + "'");
        }
        return true;
    }

}
