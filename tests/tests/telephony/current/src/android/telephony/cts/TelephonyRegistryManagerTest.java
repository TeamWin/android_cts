package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.CarrierPrivilegesListener;
import android.telephony.TelephonyRegistryManager;
import android.text.TextUtils;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
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
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

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

    /**
     * expect security exception as there is no carrier privilege permission.
     */
    @Test
    public void testNotifyCarrierNetworkChangeWithSubscription() {
        try {
            mTelephonyRegistryMgr.notifyCarrierNetworkChange(
                    SubscriptionManager.getDefaultSubscriptionId(), /*active=*/ true);
            fail("Expected SecurityException for notifyCarrierNetworkChange with subscription");
        } catch (SecurityException expected) {
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
        assertTrue(!TextUtils.isEmpty(result.second));
    }

    @Test
    public void testNotifyCallStateChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Integer, String>> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallStateChanged(int state, String number) {
                queue.offer(Pair.create(state, number));
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm = tm.createForSubscriptionId(SubscriptionManager.getDefaultSubscriptionId());
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        String dummyNumber = "288124";
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallStateChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        TelephonyManager.CALL_STATE_IDLE, dummyNumber));

        Pair<Integer, String> result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(TelephonyManager.CALL_STATE_IDLE, result.first.longValue());
        assertTrue(!TextUtils.isEmpty(result.second));
    }

    @Test
    public void testNotifyServiceStateChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<ServiceState> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onServiceStateChanged(ServiceState ss) {
                queue.offer(ss);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_SERVICE_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        ServiceState dummyState = new ServiceState();
        dummyState.setCdmaSystemAndNetworkId(1234, 5678);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyServiceStateChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        dummyState));

        ServiceState result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(dummyState, result);
    }

    @Test
    public void testNotifySignalStrengthChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<SignalStrength> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength ss) {
                queue.offer(ss);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        SignalStrength testValue = new SignalStrength();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifySignalStrengthChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        SignalStrength result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyMessageWaitingChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onMessageWaitingIndicatorChanged(boolean msgWaitingInd) {
                queue.offer(msgWaitingInd);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        boolean testValue = true;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyMessageWaitingChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        boolean result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyCallForwardingChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallForwardingIndicatorChanged(boolean callForwarding) {
                queue.offer(callForwarding);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        boolean testValue = true;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallForwardingChanged(
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        boolean result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyDataActivityChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onDataActivity(int activity) {
                queue.offer(activity);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_DATA_ACTIVITY);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int testValue = TelephonyManager.DATA_ACTIVITY_DORMANT;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyDataActivityChanged(
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        int result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyCarrierPrivilegesChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<List<String>, int[]>> queue = new LinkedBlockingQueue(2);
        CarrierPrivilegesListener cpl =
                new CarrierPrivilegesListener() {
                    @Override
                    public void onCarrierPrivilegesChanged(
                            List<String> privilegedPackageNames, int[] privilegedUids) {
                        queue.offer(new Pair<>(privilegedPackageNames, privilegedUids));
                    }
                };
        CarrierPrivilegesListener cpl2 =
                (packageNames, uids) -> queue.offer(new Pair<>(packageNames, uids));
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> tm.addCarrierPrivilegesListener(0, context.getMainExecutor(), cpl));
            // Clear the initial result from registering the listener. We can't necessarily
            // guarantee this is empty so don't assert on it other than the fact we got _something_.
            // We restore this at the end of the test.
            Pair<List<String>, int[]> initialState =
                    queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(initialState);

            // Update state
            List<String> privilegedPackageNames =
                    Arrays.asList("com.carrier.package1", "com.carrier.package2");
            int[] privilegedUids = new int[] {12345, 54321};
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm ->
                            trm.notifyCarrierPrivilegesChanged(
                                    0, privilegedPackageNames, privilegedUids));
            Pair<List<String>, int[]> result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(privilegedPackageNames, result.first);
            assertTrue(Arrays.equals(privilegedUids, result.second));

            // Registering cpl2 now immediately gets us the most recent state
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> tm.addCarrierPrivilegesListener(0, context.getMainExecutor(), cpl2));
            result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(privilegedPackageNames, result.first);
            assertTrue(Arrays.equals(privilegedUids, result.second));

            // Removing cpl means it won't get the final callback when we restore the original state
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager, tm -> tm.removeCarrierPrivilegesListener(cpl));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm ->
                            trm.notifyCarrierPrivilegesChanged(
                                    0, initialState.first, initialState.second));
            result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(initialState.first, result.first);
            assertTrue(Arrays.equals(initialState.second, result.second));
            // No further callbacks received
            assertNull(queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> {
                        tm.removeCarrierPrivilegesListener(cpl); // redundant, but still allowed
                        tm.removeCarrierPrivilegesListener(cpl2);
                    });
        }
    }
}
