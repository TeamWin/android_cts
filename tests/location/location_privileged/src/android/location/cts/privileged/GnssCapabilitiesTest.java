package android.location.cts.privileged;

import static org.junit.Assert.assertEquals;

import android.location.GnssCapabilities;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests fundamental functionality of {@link GnssCapabilities}. This includes writing and reading
 * from parcel, and verifying setters.
 */
@RunWith(AndroidJUnit4.class)
public class GnssCapabilitiesTest {

    @Test
    public void testGetValues() {
        GnssCapabilities gnssCapabilities = getTestGnssCapabilities();
        verifyTestValues(gnssCapabilities);
    }

    @Test
    public void testWriteToParcel() {
        GnssCapabilities gnssCapabilities = getTestGnssCapabilities();
        Parcel parcel = Parcel.obtain();
        gnssCapabilities.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssCapabilities newGnssCapabilities = GnssCapabilities.CREATOR.createFromParcel(parcel);
        verifyTestValues(newGnssCapabilities);
        parcel.recycle();
    }

    private static GnssCapabilities getTestGnssCapabilities() {
        GnssCapabilities.Builder builder = new GnssCapabilities.Builder();
        builder.setHasMeasurementCorrectionsForDriving(true);
        return builder.build();
    }

    private static void verifyTestValues(GnssCapabilities gnssCapabilities) {
        assertEquals(true, gnssCapabilities.hasMeasurementCorrectionsForDriving());
    }
}