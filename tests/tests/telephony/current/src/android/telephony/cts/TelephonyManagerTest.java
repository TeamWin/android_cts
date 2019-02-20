/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import static com.google.common.truth.Truth.assertThat;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.AvailableNetworkInfo;
import android.telephony.CellLocation;
import android.telephony.NetworkRegistrationState;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.UiccCardInfo;
import android.telephony.cts.locationaccessingapp.CtsLocationAccessService;
import android.telephony.cts.locationaccessingapp.ICtsLocationAccessControl;
import android.text.TextUtils;
import android.util.Log;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.TestThread;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Build, install and run the tests by running the commands below:
 *  make cts -j64
 *  cts-tradefed run cts -m CtsTelephonyTestCases --test android.telephony.cts.TelephonyManagerTest
 */
@RunWith(AndroidJUnit4.class)
public class TelephonyManagerTest {
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private PackageManager mPackageManager;
    private boolean mOnCellLocationChangedCalled = false;
    private boolean mServiceStateChangedCalled = false;
    private boolean mRadioRebootTriggered = false;
    private boolean mHasRadioPowerOff = false;
    private ServiceState mServiceState;
    private final Object mLock = new Object();
    private static final int TOLERANCE = 1000;
    private PhoneStateListener mListener;
    private static ConnectivityManager mCm;
    private static final String TAG = "TelephonyManagerTest";
    private static final List<Integer> ROAMING_TYPES = Arrays.asList(
            ServiceState.ROAMING_TYPE_DOMESTIC,
            ServiceState.ROAMING_TYPE_INTERNATIONAL,
            ServiceState.ROAMING_TYPE_NOT_ROAMING,
            ServiceState.ROAMING_TYPE_UNKNOWN);
    private static final List<Integer> NETWORK_TYPES = Arrays.asList(
            TelephonyManager.NETWORK_TYPE_UNKNOWN,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_IWLAN,
            TelephonyManager.NETWORK_TYPE_LTE_CA,
            TelephonyManager.NETWORK_TYPE_NR);

    @Before
    public void setUp() throws Exception {
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        mCm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mSubscriptionManager = (SubscriptionManager) getContext()
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mPackageManager = getContext().getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
        if (mListener != null) {
            // unregister the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Test
    public void testListen() throws Throwable {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            // TODO: temp workaround, need to adjust test to for CDMA
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();
                mListener = new PhoneStateListener() {
                    @Override
                    public void onCellLocationChanged(CellLocation location) {
                        if(!mOnCellLocationChangedCalled) {
                            synchronized (mLock) {
                                mOnCellLocationChangedCalled = true;
                                mLock.notify();
                            }
                        }
                    }
                };

                synchronized (mLock) {
                    mLock.notify(); // mListener is ready
                }

                Looper.loop();
            }
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE); // wait for mListener
        }

        // Test register
        synchronized (mLock) {
            // .listen generates an onCellLocationChanged event
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_CELL_LOCATION);
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                mOnCellLocationChangedCalled);
        }

        synchronized (mLock) {
            mOnCellLocationChangedCalled = false;
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            assertTrue("Test register, mOnCellLocationChangedCalled should be true.",
                mOnCellLocationChangedCalled);
        }

        // unregister the listener
        mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        Thread.sleep(TOLERANCE);

        // Test unregister
        synchronized (mLock) {
            mOnCellLocationChangedCalled = false;
            // unregister again, to make sure doing so does not call the listener
            mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
            CellLocation.requestLocationUpdate();
            mLock.wait(TOLERANCE);

            assertFalse("Test unregister, mOnCellLocationChangedCalled should be false.",
                mOnCellLocationChangedCalled);
        }
    }

    /**
     * The getter methods here are all related to the information about the telephony.
     * These getters are related to concrete location, phone, service provider company, so
     * it's no need to get details of these information, just make sure they are in right
     * condition(>0 or not null).
     */
    @Test
    public void testTelephonyManager() {
        assertTrue(mTelephonyManager.getNetworkType() >= TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertTrue(mTelephonyManager.getPhoneType() >= TelephonyManager.PHONE_TYPE_NONE);
        assertTrue(mTelephonyManager.getSimState() >= TelephonyManager.SIM_STATE_UNKNOWN);
        assertTrue(mTelephonyManager.getDataActivity() >= TelephonyManager.DATA_ACTIVITY_NONE);
        assertTrue(mTelephonyManager.getDataState() >= TelephonyManager.DATA_DISCONNECTED);
        assertTrue(mTelephonyManager.getCallState() >= TelephonyManager.CALL_STATE_IDLE);

        for (int i = 0; i < mTelephonyManager.getPhoneCount(); ++i) {
            assertTrue(mTelephonyManager.getSimState(i) >= TelephonyManager.SIM_STATE_UNKNOWN);
        }

        // Make sure devices without MMS service won't fail on this
        if (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE) {
            assertFalse(mTelephonyManager.getMmsUserAgent().isEmpty());
            assertFalse(mTelephonyManager.getMmsUAProfUrl().isEmpty());
        }

        // The following methods may return any value depending on the state of the device. Simply
        // call them to make sure they do not throw any exceptions.
        mTelephonyManager.getVoiceMailNumber();
        mTelephonyManager.getSimOperatorName();
        mTelephonyManager.getNetworkCountryIso();
        mTelephonyManager.getCellLocation();
        mTelephonyManager.getSimCarrierId();
        mTelephonyManager.getSimCarrierIdName();
        mTelephonyManager.getSimPreciseCarrierId();
        mTelephonyManager.getSimPreciseCarrierIdName();
        mTelephonyManager.getCarrierIdFromSimMccMnc();
        mTelephonyManager.isDataRoamingEnabled();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSimSerialNumber());
        mTelephonyManager.getSimOperator();
        mTelephonyManager.getSignalStrength();
        mTelephonyManager.getNetworkOperatorName();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSubscriberId());
        mTelephonyManager.getLine1Number();
        mTelephonyManager.getNetworkOperator();
        mTelephonyManager.getSimCountryIso();
        mTelephonyManager.getVoiceMailAlphaTag();
        mTelephonyManager.isNetworkRoaming();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId(mTelephonyManager.getSlotIndex()));
        mTelephonyManager.getDeviceSoftwareVersion();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(mTelephonyManager.getSlotIndex()));
        mTelephonyManager.getPhoneCount();
        mTelephonyManager.getDataEnabled();
        mTelephonyManager.getNetworkSpecifier();
        mTelephonyManager.getNai();
        TelecomManager telecomManager = (TelecomManager) getContext()
                .getSystemService(Context.TELECOM_SERVICE);
        PhoneAccountHandle defaultAccount = telecomManager
                .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        mTelephonyManager.getVoicemailRingtoneUri(defaultAccount);
        mTelephonyManager.isVoicemailVibrationEnabled(defaultAccount);
        mTelephonyManager.getCarrierConfig();
    }

    @Test
    public void testCellLocationFinePermission() {
        withRevokedPermission(() -> {
            try {
                CellLocation cellLocation = (CellLocation) performLocationAccessCommand(
                        CtsLocationAccessService.COMMAND_GET_CELL_LOCATION);
                assertTrue(cellLocation == null || cellLocation.isEmpty());
            } catch (SecurityException e) {
                // expected
            }

            try {
                List cis = (List) performLocationAccessCommand(
                        CtsLocationAccessService.COMMAND_GET_CELL_INFO);
                assertTrue(cis == null || cis.isEmpty());
            } catch (SecurityException e) {
                // expected
            }
        }, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Test
    public void testServiceStateLocationSanitization() {
        withRevokedPermission(() -> {
                    ServiceState ss = (ServiceState) performLocationAccessCommand(
                            CtsLocationAccessService.COMMAND_GET_SERVICE_STATE);
                    assertServiceStateSanitization(ss, true);

                    withRevokedPermission(() -> {
                                ServiceState ss1 = (ServiceState) performLocationAccessCommand(
                                        CtsLocationAccessService.COMMAND_GET_SERVICE_STATE);
                                assertServiceStateSanitization(ss1, false);
                            },
                            Manifest.permission.ACCESS_COARSE_LOCATION);
                },
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Test
    public void testServiceStateListeningWithoutPermissions() {
            withRevokedPermission(() -> {
                    ServiceState ss = (ServiceState) performLocationAccessCommand(
                            CtsLocationAccessService.COMMAND_GET_SERVICE_STATE_FROM_LISTENER);
                    assertServiceStateSanitization(ss, true);

                    withRevokedPermission(() -> {
                                ServiceState ss1 = (ServiceState) performLocationAccessCommand(
                                        CtsLocationAccessService
                                                .COMMAND_GET_SERVICE_STATE_FROM_LISTENER);
                                assertServiceStateSanitization(ss1, false);
                            },
                            Manifest.permission.ACCESS_COARSE_LOCATION);
                },
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Test
    public void testRegistryPermissionsForCellLocation() {
        withRevokedPermission(() -> {
                    CellLocation cellLocation = (CellLocation) performLocationAccessCommand(
                            CtsLocationAccessService.COMMAND_LISTEN_CELL_LOCATION);
                    assertNull(cellLocation);
                },
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Test
    public void testRegistryPermissionsForCellInfo() {
        withRevokedPermission(() -> {
                    CellLocation cellLocation = (CellLocation) performLocationAccessCommand(
                            CtsLocationAccessService.COMMAND_LISTEN_CELL_INFO);
                    assertNull(cellLocation);
                },
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private ICtsLocationAccessControl getLocationAccessAppControl() {
        Intent bindIntent = new Intent(CtsLocationAccessService.CONTROL_ACTION);
        bindIntent.setComponent(new ComponentName(CtsLocationAccessService.class.getPackageName$(),
                CtsLocationAccessService.class.getName()));

        LinkedBlockingQueue<ICtsLocationAccessControl> pipe =
                new LinkedBlockingQueue<>();
        getContext().bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                pipe.offer(ICtsLocationAccessControl.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        }, Context.BIND_AUTO_CREATE);

        try {
            return pipe.poll(TOLERANCE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("interrupted");
        }
        fail("Unable to connect to location access test app");
        return null;
    }

    private Object performLocationAccessCommand(String command) {
        ICtsLocationAccessControl control = getLocationAccessAppControl();
        try {
            List ret = control.performCommand(command);
            return ret.get(0);
        } catch (RemoteException e) {
            fail("Remote exception");
        }
        return null;
    }

    private void withRevokedPermission(Runnable r, String permission) {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().revokeRuntimePermission(
                CtsLocationAccessService.class.getPackageName$(), permission);
        try {
            r.run();
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().grantRuntimePermission(
                    CtsLocationAccessService.class.getPackageName$(), permission);
        }
    }

    private void assertServiceStateSanitization(ServiceState state, boolean sanitizedForFineOnly) {
        if (state == null) return;

        if (state.getNetworkRegistrationStates() != null) {
            for (NetworkRegistrationState nrs : state.getNetworkRegistrationStates()) {
                assertNull(nrs.getCellIdentity());
            }
        }

        if (sanitizedForFineOnly) return;

        assertTrue(TextUtils.isEmpty(state.getDataOperatorAlphaLong()));
        assertTrue(TextUtils.isEmpty(state.getDataOperatorAlphaShort()));
        assertTrue(TextUtils.isEmpty(state.getDataOperatorNumeric()));
        assertTrue(TextUtils.isEmpty(state.getVoiceOperatorAlphaLong()));
        assertTrue(TextUtils.isEmpty(state.getVoiceOperatorAlphaShort()));
        assertTrue(TextUtils.isEmpty(state.getVoiceOperatorNumeric()));
    }

    @Test
    public void testCreateForPhoneAccountHandle() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return;
        }
        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
        PhoneAccountHandle handle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
        TelephonyManager telephonyManager = mTelephonyManager.createForPhoneAccountHandle(handle);
        String globalSubscriberId = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> tm.getSubscriberId());
        String localSubscriberId = ShellIdentityUtils.invokeMethodWithShellPermissions(
                telephonyManager, (tm) -> tm.getSubscriberId());
        assertEquals(globalSubscriberId, localSubscriberId);
    }

    @Test
    public void testCreateForPhoneAccountHandle_InvalidHandle(){
        PhoneAccountHandle handle =
                new PhoneAccountHandle(new ComponentName("com.example.foo", "bar"), "baz");
        assertNull(mTelephonyManager.createForPhoneAccountHandle(handle));
    }

    /**
     * Tests that the phone count returned is valid.
     */
    @Test
    public void testGetPhoneCount() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        int phoneType = mTelephonyManager.getPhoneType();
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_GSM:
            case TelephonyManager.PHONE_TYPE_CDMA:
                assertTrue("Phone count should be > 0", phoneCount > 0);
                break;
            case TelephonyManager.PHONE_TYPE_NONE:
                assertTrue("Phone count should be 0", phoneCount == 0 || phoneCount == 1);
                break;
            default:
                throw new IllegalArgumentException("Did you add a new phone type? " + phoneType);
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI, MEID/ESN, or a valid MAC address
     * if only a WiFi device. At least one of them must be valid.
     */
    @Test
    public void testGetDeviceId() {
        String deviceId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId());
        verifyDeviceId(deviceId);
    }

    /**
     * Tests that the device properly reports either a valid IMEI, MEID/ESN, or a valid MAC address
     * if only a WiFi device. At least one of them must be valid.
     */
    @Test
    public void testGetDeviceIdForSlot() {
        String deviceId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getDeviceId(mTelephonyManager.getSlotIndex()));
        verifyDeviceId(deviceId);
        // Also verify that no exception is thrown for any slot index (including invalid ones)
        for (int i = -1; i <= mTelephonyManager.getPhoneCount(); i++) {
            // The compiler error 'local variables referenced from a lambda expression must be final
            // or effectively final' is reported when using i, so assign it to a final variable.
            final int currI = i;
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getDeviceId(currI));
        }
    }

    private void verifyDeviceId(String deviceId) {
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            // Either IMEI or MEID need to be valid.
            try {
                assertImei(deviceId);
            } catch (AssertionError e) {
                assertMeidEsn(deviceId);
            }
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            assertSerialNumber();
            assertMacAddress(getWifiMacAddress());
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            assertSerialNumber();
            assertMacAddress(getBluetoothMacAddress());
        } else if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_ETHERNET)) {
            assertTrue(mCm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET) != null);
        }
    }

    private static void assertImei(String id) {
        assertFalse("Imei should not be empty or null", TextUtils.isEmpty(id));
        // IMEI may include the check digit
        String imeiPattern = "[0-9]{14,15}";
        String invalidPattern = "[0]{14,15}";
        assertTrue("IMEI " + id + " does not match pattern " + imeiPattern,
                Pattern.matches(imeiPattern, id));
        assertFalse("IMEI " + id + " must not be a zero sequence" + invalidPattern,
                Pattern.matches(invalidPattern, id));
        if (id.length() == 15) {
            // if the ID is 15 digits, the 15th must be a check digit.
            assertImeiCheckDigit(id);
        }
    }

    private static void assertImeiCheckDigit(String deviceId) {
        int expectedCheckDigit = getLuhnCheckDigit(deviceId.substring(0, 14));
        int actualCheckDigit = Character.digit(deviceId.charAt(14), 10);
        assertEquals("Incorrect check digit for " + deviceId, expectedCheckDigit, actualCheckDigit);
    }

    /**
     * Use decimal value (0-9) to index into array to get sum of its digits
     * needed by Lunh check.
     *
     * Example: DOUBLE_DIGIT_SUM[6] = 3 because 6 * 2 = 12 => 1 + 2 = 3
     */
    private static final int[] DOUBLE_DIGIT_SUM = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};

    /**
     * Calculate the check digit by starting from the right, doubling every
     * each digit, summing all the digits including the doubled ones, and
     * finding a number to make the sum divisible by 10.
     *
     * @param deviceId not including the check digit
     * @return the check digit
     */
    private static int getLuhnCheckDigit(String deviceId) {
        int sum = 0;
        int dontDoubleModulus = deviceId.length() % 2;
        for (int i = deviceId.length() - 1; i >= 0; --i) {
            int digit = Character.digit(deviceId.charAt(i), 10);
            if (i % 2 == dontDoubleModulus) {
                sum += digit;
            } else {
                sum += DOUBLE_DIGIT_SUM[digit];
            }
        }
        sum %= 10;
        return sum == 0 ? 0 : 10 - sum;
    }

    private static void assertMeidEsn(String id) {
        // CDMA device IDs may either be a 14-hex-digit MEID or an
        // 8-hex-digit ESN.  If it's an ESN, it may not be a
        // pseudo-ESN.
        assertFalse("Meid ESN should not be empty or null", TextUtils.isEmpty(id));
        if (id.length() == 14) {
            assertMeidFormat(id);
        } else if (id.length() == 8) {
            assertHexadecimalEsnFormat(id);
        } else {
            fail("device id on CDMA must be 14-digit hex MEID or 8-digit hex ESN.");
        }
    }

    private static void assertHexadecimalEsnFormat(String deviceId) {
        String esnPattern = "[0-9a-fA-F]{8}";
        String invalidPattern = "[0]{8}";
        assertTrue("ESN hex device id " + deviceId + " does not match pattern " + esnPattern,
                   Pattern.matches(esnPattern, deviceId));
        assertFalse("ESN hex device id " + deviceId + " must not be a pseudo-ESN",
                    "80".equals(deviceId.substring(0, 2)));
        assertFalse("ESN hex device id " + deviceId + "must not be a zero sequence",
                Pattern.matches(invalidPattern, deviceId));
    }

    private static void assertMeidFormat(String deviceId) {
        // MEID must NOT include the check digit.
        String meidPattern = "[0-9a-fA-F]{14}";
        String invalidPattern = "[0]{14}";
        assertTrue("MEID device id " + deviceId + " does not match pattern "
                + meidPattern, Pattern.matches(meidPattern, deviceId));
        assertFalse("MEID device id " + deviceId + "must not be a zero sequence",
                Pattern.matches(invalidPattern, deviceId));
    }

    private void assertSerialNumber() {
        assertNotNull("Non-telephony devices must have a Build.getSerial() number.",
                Build.getSerial());
        assertTrue("Hardware id must be no longer than 20 characters.",
                Build.getSerial().length() <= 20);
        assertTrue("Hardware id must be alphanumeric.",
                Pattern.matches("[0-9A-Za-z]+", Build.getSerial()));
    }

    private void assertMacAddress(String macAddress) {
        String macPattern = "([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}";
        assertTrue("MAC Address " + macAddress + " does not match pattern " + macPattern,
                Pattern.matches(macPattern, macAddress));
    }

    /** @return mac address which requires the WiFi system to be enabled */
    private String getWifiMacAddress() {
        WifiManager wifiManager = (WifiManager) getContext()
                .getSystemService(Context.WIFI_SERVICE);

        boolean enabled = wifiManager.isWifiEnabled();

        try {
            if (!enabled) {
                wifiManager.setWifiEnabled(true);
            }

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getMacAddress();

        } finally {
            if (!enabled) {
                wifiManager.setWifiEnabled(false);
            }
        }
    }

    private String getBluetoothMacAddress() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return "";
        }

        return adapter.getAddress();
    }

    private static final String ISO_COUNTRY_CODE_PATTERN = "[a-z]{2}";

    @Test
    public void testGetNetworkCountryIso() {
        String countryCode = mTelephonyManager.getNetworkCountryIso();
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            assertTrue("Country code '" + countryCode + "' did not match "
                    + ISO_COUNTRY_CODE_PATTERN,
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
        } else {
            // Non-telephony may still have the property defined if it has a SIM.
        }
    }

    @Test
    public void testGetSimCountryIso() {
        String countryCode = mTelephonyManager.getSimCountryIso();
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            assertTrue("Country code '" + countryCode + "' did not match "
                    + ISO_COUNTRY_CODE_PATTERN,
                    Pattern.matches(ISO_COUNTRY_CODE_PATTERN, countryCode));
        } else {
            // Non-telephony may still have the property defined if it has a SIM.
        }
    }

    @Test
    public void testGetServiceState() throws InterruptedException {
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        synchronized (mLock) {
                            mServiceState = serviceState;
                            mLock.notify();
                        }
                    }
                };
                mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_SERVICE_STATE);
                Looper.loop();
            }
        });

        synchronized (mLock) {
            t.start();
            mLock.wait(TOLERANCE);
        }

        assertEquals(mServiceState, mTelephonyManager.getServiceState());
    }

    @Test
    public void testGetSimLocale() throws InterruptedException {
        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG,"skipping test that requires Telephony");
            return;
        }
        if (SubscriptionManager.getDefaultSubscriptionId()
                == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            fail("Expected SIM inserted");
        }
        String locale = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSimLocale());
        Log.d(TAG, "testGetSimLocale: " + locale);
        assertNotNull(locale);
    }

    /**
     * Tests that the device properly reports either a valid IMEI or null.
     */
    @Test
    public void testGetImei() {
        String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei());

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                assertImei(imei);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid IMEI or null.
     */
    @Test
    public void testGetImeiForSlot() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        for (int i = 0; i < mTelephonyManager.getPhoneCount(); i++) {
            // The compiler error 'local variables referenced from a lambda expression must be final
            // or effectively final' is reported when using i, so assign it to a final variable.
            final int currI = i;
            String imei = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getImei(currI));
            if (!TextUtils.isEmpty(imei)) {
                assertImei(imei);
            }
        }

        // Also verify that no exception is thrown for any slot index (including invalid ones)
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(-1));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getImei(mTelephonyManager.getPhoneCount()));
    }

    /**
     * Verifies that {@link TelephonyManager#getRadioPowerState()} does not throw any exception
     * and returns radio on.
     */
    @Test
    public void testGetRadioPowerState() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        // Also verify that no exception is thrown.
        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
    }

    /**
     * Verifies that {@link TelephonyManager#setCarrierDataEnabled(boolean)} does not throw any
     * exception. TODO enhance later if we have an API to get data enabled state.
     */
    @Test
    public void testSetCarrierDataEnabled() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        // Also verify that no exception is thrown.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setCarrierDataEnabled(false));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.setCarrierDataEnabled(true));
    }

    /**
     * Verifies that {@link TelephonyManager#rebootRadio()} does not throw any exception
     * and final radio state is radio power on.
     */
    @Test
    public void testRebootRadio() throws Throwable {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        assertEquals(mTelephonyManager.getServiceState().getState(), ServiceState.STATE_IN_SERVICE);

        TestThread t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onRadioPowerStateChanged(
                            @TelephonyManager.RadioPowerState int state) {
                        synchronized (mLock) {
                            if (state == TelephonyManager.RADIO_POWER_ON && mHasRadioPowerOff) {
                                mRadioRebootTriggered = true;
                                mLock.notify();
                            } else if (state == TelephonyManager.RADIO_POWER_OFF) {
                                // reboot must go to power off
                                mHasRadioPowerOff = true;
                            }
                        }
                    }
                };
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        (tm) -> tm.listen(mListener,
                                PhoneStateListener.LISTEN_RADIO_POWER_STATE_CHANGED));
                Looper.loop();
            }
        });

        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
        assertThat(mRadioRebootTriggered).isFalse();
        assertThat(mHasRadioPowerOff).isFalse();
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.rebootRadio());
        t.start();
        synchronized (mLock) {
            // reboot takes longer time
            if (!mRadioRebootTriggered) {
                mLock.wait(10000);
            }
        }
        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(
                TelephonyManager.RADIO_POWER_ON);
        assertThat(mRadioRebootTriggered).isTrue();

        // note, other telephony states might not resumes properly at this point. e.g, service state
        // might still in the transition from OOS to In service. Thus we need to wait for in
        // service state before running next tests.
        t = new TestThread(new Runnable() {
            public void run() {
                Looper.prepare();

                mListener = new PhoneStateListener() {
                    @Override
                    public void onServiceStateChanged(ServiceState serviceState) {
                        synchronized (mLock) {
                            if (serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                                mServiceStateChangedCalled = true;
                                mLock.notify();
                            }
                        }
                    }
                };
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                        (tm) -> tm.listen(mListener, PhoneStateListener.LISTEN_SERVICE_STATE));
                Looper.loop();
            }
        });

        synchronized (mLock) {
            t.start();
            if (!mServiceStateChangedCalled) {
                mLock.wait(10000);
            }
        }
        assertThat(mTelephonyManager.getServiceState().getState()).isEqualTo(
                ServiceState.STATE_IN_SERVICE);
    }

    /**
     * Verifies that {@link TelephonyManager#getAidForAppType(int)} does not throw any exception
     * for all supported subscription app type.
     */
    @Test
    public void testGetAidForAppType() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_SIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_CSIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_RUIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_ISIM));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getAidForAppType(TelephonyManager.APPTYPE_USIM));
    }

    /**
     * Verifies that {@link TelephonyManager#getIsimDomain()} does not throw any exception
     */
    @Test
    public void testGetIsimDomain() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getIsimDomain());
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationState#isRoaming()} does not throw any
     * exception.
     */
    @Test
    public void testNetworkRegistrationStateIsRoaming() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        // get NetworkRegistration object
        NetworkRegistrationState nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationState(NetworkRegistrationState.DOMAIN_CS,
                        AccessNetworkConstants.TransportType.WWAN);
        assertThat(nwReg).isNotNull();
        nwReg.isRoaming();
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationState#getRoamingType()} ()} does not throw any
     * exception and returns valid result
     * @see ServiceState.RoamingType
     */
    @Test
    public void testNetworkRegistrationStateGetRoamingType() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        // get NetworkRegistration object for voice
        NetworkRegistrationState nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationState(NetworkRegistrationState.DOMAIN_CS,
                        AccessNetworkConstants.TransportType.WWAN);
        assertNotNull(nwReg);
        assertThat(nwReg.getRoamingType()).isIn(ROAMING_TYPES);

        // getNetworkRegistration object for data
        // get NetworkRegistration object for voice
        nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationState(NetworkRegistrationState.DOMAIN_PS,
                        AccessNetworkConstants.TransportType.WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getRoamingType()).isIn(ROAMING_TYPES);
    }

    /**
     * Basic test to ensure {@link NetworkRegistrationState#getAccessNetworkTechnology()} not
     * throw any exception and returns valid result
     * @see TelephonyManager.NetworkType
     */
    @Test
    public void testNetworkRegistationStateGetAccessNetworkTechnology() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        // get NetworkRegistration object for voice
        NetworkRegistrationState nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationState(NetworkRegistrationState.DOMAIN_CS,
                        AccessNetworkConstants.TransportType.WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getAccessNetworkTechnology()).isIn(NETWORK_TYPES);

        // get NetworkRegistation object for data
        nwReg = mTelephonyManager.getServiceState()
                .getNetworkRegistrationState(NetworkRegistrationState.DOMAIN_PS,
                        AccessNetworkConstants.TransportType.WWAN);
        assertThat(nwReg).isNotNull();
        assertThat(nwReg.getAccessNetworkTechnology()).isIn(NETWORK_TYPES);
    }


    /**
     * Tests that the device properly reports either a valid MEID or null.
     */
    @Test
    public void testGetMeid() {
        String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid());

        if (mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            if (mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                assertMeidEsn(meid);
            }
        }
    }

    /**
     * Tests that the device properly reports either a valid MEID or null.
     */
    @Test
    public void testGetMeidForSlot() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        SubscriptionManager sm = (SubscriptionManager) getContext()
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> subInfos = sm.getActiveSubscriptionInfoList();

        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos) {
                int slotIndex = subInfo.getSimSlotIndex();
                int subId = subInfo.getSubscriptionId();
                TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
                if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                    String meid = ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager,
                            (telephonyManager) -> telephonyManager.getMeid(slotIndex));

                    if (!TextUtils.isEmpty(meid)) {
                        assertMeidEsn(meid);
                    }
                }
            }
        }

        // Also verify that no exception is thrown for any slot index (including invalid ones)
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid(-1));
        ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getMeid(mTelephonyManager.getPhoneCount()));
    }

    /**
     * Tests sendDialerSpecialCode API.
     * Expects a security exception since the caller does not have carrier privileges or is not the
     * current default dialer app.
     */
    @Test
    public void testSendDialerSpecialCode() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return;
        }
        try {
            mTelephonyManager.sendDialerSpecialCode("4636");
            fail("Expected SecurityException. App does not have carrier privileges or is not the "
                    + "default dialer app");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly reports the contents of EF_FPLMN or null
     */
    @Test
    public void testGetForbiddenPlmns() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        String[] plmns = mTelephonyManager.getForbiddenPlmns();

        int phoneType = mTelephonyManager.getPhoneType();
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_GSM:
                assertNotNull("Forbidden PLMNs must be valid or an empty list!", plmns);
            case TelephonyManager.PHONE_TYPE_CDMA:
            case TelephonyManager.PHONE_TYPE_NONE:
                if (plmns == null) {
                    return;
                }
        }

        for(String plmn : plmns) {
            assertTrue(
                    "Invalid Length for PLMN-ID, must be 5 or 6! plmn=" + plmn,
                    plmn.length() >= 5 && plmn.length() <= 6);
            assertTrue(
                    "PLMNs must be strings of digits 0-9! plmn=" + plmn,
                    android.text.TextUtils.isDigitsOnly(plmn));
        }
    }

    /**
     * Verify that TelephonyManager.getCardIdForDefaultEuicc returns a positive value or either
     * UNINITIALIZED_CARD_ID or UNSUPPORTED_CARD_ID.
     */
    @Test
    public void testGetCardIdForDefaultEuicc() {
        int cardId = mTelephonyManager.getCardIdForDefaultEuicc();
        assertTrue("Card ID for default EUICC is not a valid value",
                cardId == TelephonyManager.UNSUPPORTED_CARD_ID
                || cardId == TelephonyManager.UNINITIALIZED_CARD_ID
                || cardId >= 0);
    }

    /**
     * Tests that a SecurityException is thrown when trying to access UiccCardsInfo.
     */
    @Test
    public void testGetUiccCardsInfo() {
        try {
            // Requires READ_PRIVILEGED_PHONE_STATE or carrier privileges
            List<UiccCardInfo> infos = mTelephonyManager.getUiccCardsInfo();
            fail("Expected SecurityException. App does not have carrier privileges");
        } catch (SecurityException e) {
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    /**
     * Tests that the device properly sets the network selection mode to automatic.
     * Expects a security exception since the caller does not have carrier privileges.
     */
    @Test
    public void testSetNetworkSelectionModeAutomatic() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return;
        }
        try {
            mTelephonyManager.setNetworkSelectionModeAutomatic();
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests that the device properly asks the radio to connect to the input network and change
     * selection mode to manual.
     * Expects a security exception since the caller does not have carrier privileges.
     */
    @Test
    public void testSetNetworkSelectionModeManual() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return;
        }
        try {
            mTelephonyManager.setNetworkSelectionModeManual(
                    "" /* operatorNumeric */, false /* persistSelection */);
            fail("Expected SecurityException. App does not have carrier privileges.");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Tests TelephonyManager.getCurrentEmergencyNumberList.
     */
    @Test
    public void testGetCurrentEmergencyNumberList() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        Map<Integer, List<EmergencyNumber>> emergencyNumberList
          = mTelephonyManager.getCurrentEmergencyNumberList();
        // TODO enhance it later
    }

    /**
     * Tests TelephonyManager.isCurrentEmergencyNumber.
     */
    @Test
    public void testIsCurrentEmergencyNumber() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        boolean isEmergencyNumber = mTelephonyManager.isCurrentEmergencyNumber("911");
        // TODO enhance it later
    }

    /**
     * Tests TelephonyManager.isCurrentPotentialEmergencyNumber.
     */
    @Test
    public void testIsCurrentPotentialEmergencyNumber() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        // use shell permission to run system api
        boolean isEmergencyNumber =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                        (tm) -> tm.isCurrentPotentialEmergencyNumber("911"));
        // TODO enhance it later
    }

    /**
     * Tests {@link TelephonyManager#getSupportedRadioAccessFamily()}
     */
    @Test
    public void testGetRadioAccessFamily() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        long raf = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getSupportedRadioAccessFamily());
        assertThat(raf).isNotEqualTo(TelephonyManager.NETWORK_TYPE_BITMASK_UNKNOWN);
    }

    /**
     * Tests {@link TelephonyManager#setPreferredOpportunisticDataSubscription} and
     * {@link TelephonyManager#getPreferredOpportunisticDataSubscription}
     */
    @Test
    public void testPreferredOpportunisticDataSubscription() {
        int randomSubId = 1;
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        int subId =
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getPreferredOpportunisticDataSubscription());
        assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        List<SubscriptionInfo> subscriptionInfoList =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(mSubscriptionManager,
                            (tm) -> tm.getOpportunisticSubscriptions());
        if (subscriptionInfoList == null || subscriptionInfoList.size() == 0) {
            boolean res = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setPreferredOpportunisticDataSubscription(randomSubId));
            assertThat(res).isEqualTo(false);
            subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.getPreferredOpportunisticDataSubscription());
            assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        } else {
            boolean res = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.setPreferredOpportunisticDataSubscription(
                            subscriptionInfoList.get(0).getSubscriptionId()));
            assertThat(res).isEqualTo(true);
            ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getPreferredOpportunisticDataSubscription());
            assertThat(subId).isEqualTo(subscriptionInfoList.get(0).getSubscriptionId());
        }

        boolean result = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.setPreferredOpportunisticDataSubscription(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID));
        assertThat(result).isEqualTo(true);
        subId = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
            (tm) -> tm.getPreferredOpportunisticDataSubscription());
        assertThat(subId).isEqualTo(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    /**
     * Tests {@link TelephonyManager#updateAvailableNetworks}
     */
    @Test
    public void testUpdateAvailableNetworks() {
        int randomSubId = 1;
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        List<SubscriptionInfo> subscriptionInfoList =
            ShellIdentityUtils.invokeMethodWithShellPermissions(mSubscriptionManager,
                (tm) -> tm.getOpportunisticSubscriptions());
        List<String> mccMncs = new ArrayList<String>();
        List<AvailableNetworkInfo> availableNetworkInfos = new ArrayList<AvailableNetworkInfo>();
        if (subscriptionInfoList == null || subscriptionInfoList.size() == 0
                || !mSubscriptionManager.isActiveSubscriptionId(
                        subscriptionInfoList.get(0).getSubscriptionId())) {
            AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(randomSubId,
                    AvailableNetworkInfo.PRIORITY_HIGH, mccMncs);
            availableNetworkInfos.add(availableNetworkInfo);
            boolean res = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                    (tm) -> tm.updateAvailableNetworks(availableNetworkInfos));
            assertThat(res).isEqualTo(false);
        } else {
            AvailableNetworkInfo availableNetworkInfo = new AvailableNetworkInfo(
                    subscriptionInfoList.get(0).getSubscriptionId(),
                    AvailableNetworkInfo.PRIORITY_HIGH, mccMncs);
            availableNetworkInfos.add(availableNetworkInfo);
            boolean res = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.updateAvailableNetworks(availableNetworkInfos));
            assertThat(res).isEqualTo(true);
            availableNetworkInfos.clear();
            res = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.updateAvailableNetworks(availableNetworkInfos));
            assertThat(res).isEqualTo(true);
        }
    }


    public static void waitForMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException while waiting: " + e);
        }
    }
}
