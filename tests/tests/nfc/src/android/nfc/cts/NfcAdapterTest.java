package android.nfc.cts;

import static android.Manifest.permission.MANAGE_DEFAULT_APPLICATIONS;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.*;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldReader;
import org.mockito.internal.util.reflection.FieldSetter;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(JUnit4.class)
public class NfcAdapterTest {
    @Mock private INfcAdapter mService;
    private INfcAdapter mSavedService;
    private Context mContext;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean supportsHardware() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    @Before
    public void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getContext();
        assumeTrue(supportsHardware());
        // Backup the original service. It is being overridden
        // when creating a mocked adapter.
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        mSavedService = (INfcAdapter) (
            new FieldReader(adapter, adapter.getClass().getDeclaredField("sService")).read());
    }

    @After
    public void tearDown() throws NoSuchFieldException {
        if (!supportsHardware()) return;
        // Restore the original service.
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        FieldSetter.setField(adapter,
                adapter.getClass().getDeclaredField("sService"), mSavedService);
    }

    @Test
    public void testGetDefaultAdapter() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertNotNull(adapter);
    }

    @Test
    public void testAddNfcUnlockHandler() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            adapter.addNfcUnlockHandler(new CtsNfcUnlockHandler(), new String[]{"IsoDep"});
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testDisableWithNoParams() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.disable(anyBoolean())).thenReturn(true);
        boolean result = adapter.disable();
        Assert.assertTrue(result);
    }

    @Test
    public void testDisableWithParam() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.disable(anyBoolean())).thenReturn(true);
        boolean result = adapter.disable(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testDisableForegroundDispatch() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.disableForegroundDispatch(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testDisableReaderMode() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.disableReaderMode(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testEnable() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.enable()).thenReturn(true);
        boolean result = adapter.enable();
        Assert.assertTrue(result);
    }

    @Test
    public void testEnableForegroundDispatch() {
        try {
            NfcAdapter adapter = createMockedInstance();
            Activity activity = createAndResumeActivity();
            Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
            PendingIntent pendingIntent
                = PendingIntent.getActivity(ApplicationProvider.getApplicationContext(),
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            String[][] techLists = new String[][]{new String[]{}};
            doNothing().when(mService).setForegroundDispatch(any(PendingIntent.class),
                any(IntentFilter[].class), any(TechListParcel.class));
            adapter.enableForegroundDispatch(activity, pendingIntent, null, techLists);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testEnableReaderMode() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.enableReaderMode(activity, new CtsReaderCallback(),
                NfcAdapter.FLAG_READER_NFC_A, new Bundle());
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    public void testEnableReaderOption() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.enableReaderOption(anyBoolean())).thenReturn(true);
        boolean result = adapter.enableReaderOption(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testEnableSecureNfc() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.setNfcSecure(anyBoolean())).thenReturn(true);
        boolean result = adapter.enableSecureNfc(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetNfcAntennaInfo() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        NfcAntennaInfo info = new NfcAntennaInfo(0, 0, false,
            new ArrayList<AvailableNfcAntenna>());
        when(mService.getNfcAntennaInfo()).thenReturn(info);
        NfcAntennaInfo result = adapter.getNfcAntennaInfo();
        Assert.assertEquals(info, result);
    }

    @Test
    public void testIgnore() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        Tag tag = new Tag(new byte[]{0x00}, new int[]{}, new Bundle[]{}, 0, 0L, null);
        when(mService.ignore(anyInt(), anyInt(), eq(null))).thenReturn(true);
        boolean result = adapter.ignore(tag, 0, null, null);
        Assert.assertTrue(result);
    }

    @Test
    public void testIsControllerAlwaysOn() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isControllerAlwaysOn()).thenReturn(true);
        boolean result = adapter.isControllerAlwaysOn();
        Assert.assertTrue(result);
    }

    @Test
    public void testIsControllerAlwaysOnSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isControllerAlwaysOnSupported()).thenReturn(true);
        boolean result = adapter.isControllerAlwaysOnSupported();
        Assert.assertTrue(result);
    }

    @Test
    public void testIsEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.getState()).thenReturn(NfcAdapter.STATE_ON);
        boolean result = adapter.isEnabled();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_READER_OPTION)
    public void testIsReaderOptionEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isReaderOptionEnabled()).thenReturn(true);
        boolean result = adapter.isReaderOptionEnabled();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_READER_OPTION)
    public void testIsReaderOptionSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isReaderOptionSupported()).thenReturn(true);
        boolean result = adapter.isReaderOptionSupported();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testAdapterState() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.getState()).thenReturn(NfcAdapter.STATE_ON);
        Assert.assertEquals(adapter.getAdapterState(), NfcAdapter.STATE_ON);
    }

    @Test
    public void testIsSecureNfcEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isNfcSecureEnabled()).thenReturn(true);
        boolean result = adapter.isSecureNfcEnabled();
        Assert.assertTrue(result);
    }

    @Test
    public void testIsSecureNfcSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.deviceSupportsNfcSecure()).thenReturn(true);
        boolean result = adapter.isSecureNfcSupported();
        Assert.assertTrue(result);
    }

    @Test
    public void testRemoveNfcUnlockHandler() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        boolean result = adapter.removeNfcUnlockHandler(new CtsNfcUnlockHandler());
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testResetDiscoveryTechnology() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.resetDiscoveryTechnology(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testSetDiscoveryTechnology() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Activity activity = createAndResumeActivity();
            adapter.setDiscoveryTechnology(activity,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F,
                    NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                    | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_F);
            adapter.resetDiscoveryTechnology(activity);
            adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_DISABLE,
                    NfcAdapter.FLAG_LISTEN_KEEP);
            adapter.resetDiscoveryTechnology(activity);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Exception: " + e);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testSetReaderMode() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        // Verify the API does not crash or throw any exceptions.
        adapter.setReaderMode(true);
        adapter.setReaderMode(false);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testIsObserveModeSupported() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        boolean result = adapter.isObserveModeSupported();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAllowTransaction() {
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            boolean result = adapter.allowTransaction();
            Assert.assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDisallowTransaction() {
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            boolean result = adapter.disallowTransaction();
            Assert.assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAllowTransaction_walletRoleEnabled() throws InterruptedException {
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity(MANAGE_DEFAULT_APPLICATIONS);
            assumeTrue(setDefaultWalletRoleHolder(mContext));
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            boolean result = adapter.allowTransaction();
            Assert.assertTrue(result);
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testDisallowTransaction_walletRoleEnabled() throws InterruptedException {
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity(MANAGE_DEFAULT_APPLICATIONS);
            assumeTrue(setDefaultWalletRoleHolder(mContext));
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            boolean result = adapter.disallowTransaction();
            Assert.assertTrue(result);
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testEnableNfcCharging() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.enableWlc(anyBoolean())).thenReturn(true);
        boolean result = adapter.enableWlc(true);
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testIsNfcChargingEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isWlcEnabled()).thenReturn(true);
        boolean result = adapter.isWlcEnabled();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testTypeAPollingLoopToDefault() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
            ArrayList<Bundle> frames = new ArrayList<Bundle>(6);
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_OFF));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_OFF));
            notifyPollingLoopAndWait(new ArrayList<Bundle>(frames),
                    CtsMyHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testTypeAPollingLoopToForeground() {
        Activity activity = createAndResumeActivity();
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext,
                            CtsMyHostApduService.class)));
            ArrayList<Bundle> frames = new ArrayList<Bundle>(6);
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_OFF));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_OFF));
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testCustomPollingLoopToCustomDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                annotationStringHex));
        ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
        frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForegroundDynamic() {
        ComponentName originalDefault = null;
        Activity activity = createAndResumeActivity();
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            ComponentName ctsMyServiceName = new ComponentName(mContext,
                    CtsMyHostApduService.class);
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsMyServiceName));
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(ctsMyServiceName,
                    annotationStringHex));
            ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
            frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundForegroundConflictPollingLoopToPaymentDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        ComponentName customServiceName = new ComponentName(mContext,
                CustomHostApduService.class);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity, customServiceName));
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex));
            ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
            frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPaymentDynamic() {
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex));
            ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
            frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testCustomPollingLoopToCustom() {
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
        frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForeground() {
        ComponentName originalDefault = null;
        Activity activity = createAndResumeActivity();
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
            frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            setDefaultPaymentService(originalDefault);
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundForegroundConflictPollingLoopToPayment() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CustomHostApduService.class)));
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
            frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPayment() {
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
            frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
        }
    }

    private Bundle createFrame(char type) {
        Bundle frame = new Bundle();
        frame.putChar(HostApduService.POLLING_LOOP_TYPE_KEY, type);
        return frame;
    }

    private Bundle createFrameWithData(char type, byte[] data) {
        Bundle frame = createFrame(type);
        frame.putByteArray(HostApduService.POLLING_LOOP_DATA_KEY, data);
        return frame;
    }

    private ComponentName setDefaultPaymentService(Class serviceClass) {
        ComponentName componentName = setDefaultPaymentService(
                new ComponentName(mContext, serviceClass));
        if (componentName == null) {
            return null;
        }
        return componentName;
    }

    private ComponentName getDefaultPaymentService() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        CharSequence desc = cardEmulation.getDescriptionForPreferredPaymentService();
        if (desc == null) {
            return null;
        } else if (desc.toString().equals(mContext.getResources()
                .getString(R.string.CtsPaymentService))) {
            return new ComponentName(mContext, CtsMyHostApduService.class);
        } else if (desc.toString().equals(mContext.getResources()
                .getString(R.string.CtsCustomPaymentService))) {
            return new ComponentName(mContext, CustomHostApduService.class);
        } else if (desc.toString().equals(mContext.getResources()
                .getString(R.string.CtsBackgroundPaymentService))) {
            return new ComponentName(mContext, BackgroundHostApduService.class);
        }
        return null;
    }

    private ComponentName setDefaultPaymentService(ComponentName serviceName) {
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity();

            ComponentName originalValue = getDefaultPaymentService();
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            Settings.Secure.putString(mContext.getContentResolver(),
                    "nfc_payment_default_component", serviceName.flattenToString());
            int count = 0;
            while (!cardEmulation.isDefaultServiceForCategory(serviceName,
                    CardEmulation.CATEGORY_PAYMENT) && count < 100) {
                synchronized (this) {
                    try {
                        this.wait(10);
                    } catch (InterruptedException ie) {
                    }
                }
            }
            return originalValue;
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    static class PollLoopReceiver extends BroadcastReceiver {
        int mFrameIndex = 0;
        List<Bundle> mFrames;
        String mServiceName;

        PollLoopReceiver(List<Bundle> frames, String serviceName) {
            mFrames = frames;
            mServiceName = serviceName;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            abortBroadcast();
            Assert.assertEquals(mServiceName,
                    intent.getStringExtra(CtsMyHostApduService.SERVICE_NAME_EXTRA));
            ArrayList<Bundle> receivedFrames = intent.getParcelableArrayListExtra(
                    CtsMyHostApduService.POLLING_FRAMES_EXTRA, Bundle.class);
            for (Bundle receivedFrame : receivedFrames) {
                if (mFrameIndex < mFrames.size()) {
                    Assert.assertEquals(
                            mFrames.get(mFrameIndex).getChar(HostApduService.POLLING_LOOP_TYPE_KEY),
                            receivedFrame.getChar(HostApduService.POLLING_LOOP_TYPE_KEY));
                    Assert.assertArrayEquals(
                            mFrames.get(mFrameIndex).getByteArray(
                                    HostApduService.POLLING_LOOP_DATA_KEY),
                            receivedFrame.getByteArray(HostApduService.POLLING_LOOP_DATA_KEY));
                } else {
                    Assert.fail("received more frames than sent: " + receivedFrame);
                }
                mFrameIndex++;
            }
            synchronized (this) {
                if (mFrameIndex >= mFrames.size()) {
                    notifyAll();
                }
            }
        }
    }

    private void notifyPollingLoopAndWait(List<Bundle> frames, String serviceName) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        PollLoopReceiver receiver = new PollLoopReceiver(frames, serviceName);
        IntentFilter filter = new IntentFilter();
        filter.addAction(CtsMyHostApduService.POLLING_LOOP_RECEIVED_ACTION);
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        for (Bundle frame : frames) {
            adapter.notifyPollingLoop(frame);
        }
        synchronized (receiver) {
            try {
                receiver.wait(5000);
            } catch (InterruptedException ie) {
                Assert.assertNull(ie);
            }
        }
        mContext.unregisterReceiver(receiver);
        adapter.notifyHceDeactivated();
        Assert.assertEquals(frames.size(), receiver.mFrameIndex);
    }

    private class CtsReaderCallback implements NfcAdapter.ReaderCallback {
        @Override
        public void onTagDiscovered(Tag tag) {
        }
    }

    private class CtsNfcUnlockHandler implements NfcAdapter.NfcUnlockHandler {
        @Override
        public boolean onUnlockAttempted(Tag tag) {
            return true;
        }
    }

    private Activity createAndResumeActivity() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        return activity;
    }

    private NfcAdapter createMockedInstance() throws NoSuchFieldException {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        FieldSetter.setField(adapter, adapter.getClass().getDeclaredField("sService"), mService);
        return adapter;
    }

    private static boolean setDefaultWalletRoleHolder(Context context)
            throws InterruptedException {
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        roleManager.setDefaultApplication(RoleManager.ROLE_WALLET,
                "android.nfc.cts", 0,
                MoreExecutors.directExecutor(), aBoolean -> {
                    result.set(aBoolean);
                    countDownLatch.countDown();
                });
        countDownLatch.await(2000, TimeUnit.MILLISECONDS);
        return result.get();
    }
}
