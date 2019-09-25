package android.telephony.cts;

import static org.junit.Assert.fail;

import android.content.Context;
import android.os.telephony.TelephonyRegistryManager;
import androidx.test.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;

/**
 * Test TelephonyRegistryManagerTest APIs.
 */
public class TelephonyRegistryManagerTest {
    private TelephonyRegistryManager mTelephonyRegistryMgr;

    @Before
    public void setUp() throws Exception {
        mTelephonyRegistryMgr = (TelephonyRegistryManager) InstrumentationRegistry.getContext()
            .getSystemService(Context.TELEPHONY_REGISTRY_SERVICE);
    }

    /**
     * expect security exception as there is no carrier privilege permission.
     */
    @Test
    public void testNotifyCarrierNetworkChange() {
        try {
            mTelephonyRegistryMgr.notifyCarrierNetworkChange(true);
            fail("Expected SecurityException for notifyCarrierNetworkChange");
        } catch (SecurityException ex) {
            /* Expected */
        }
    }
}