package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;
import android.text.TextUtils;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test TelephonyRegistryManagerTest APIs.
 */
public class TelephonyRegistryManagerTest {
    private TelephonyRegistryManager mTelephonyRegistryMgr;
    private static final long TIMEOUT_MILLIS = 1000;

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

    @Test
    public void testNotifyCallStateChangedForAllSubscriptions() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Integer, String>> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallStateChanged(int state, String number) {
                queue.offer(Pair.create(state, number));
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        String dummyNumber = "288124";
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallStateChangedForAllSubscriptions(
                        TelephonyManager.CALL_STATE_IDLE, dummyNumber));

        Pair<Integer, String> result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(TelephonyManager.CALL_STATE_IDLE, result.first.longValue());
        // This test doesn't have READ_CALL_LOG, so we expect the second arg to be empty.
        assertTrue(TextUtils.isEmpty(result.second));
    }
}