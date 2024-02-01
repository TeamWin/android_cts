package android.nfc.cts;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.nfc.Constants;
import android.nfc.Flags;
import android.nfc.INfcCardEmulation;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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

@RunWith(JUnit4.class)
public class CardEmulationTest {
    private NfcAdapter mAdapter;

    private Context mContext;

    private static final ComponentName mService =
        new ComponentName("android.nfc.cts", "android.nfc.cts.CtsMyHostApduService");

    private INfcCardEmulation mOldService;
    @Mock private INfcCardEmulation mEmulation;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    private boolean supportsHardwareForEse() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE);
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        assumeTrue(supportsHardware());
        mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertNotNull(mAdapter);

        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldReader serviceField = new FieldReader(instance,
                instance.getClass().getDeclaredField("sService"));
        mOldService = (INfcCardEmulation) serviceField.read();
    }

    @After
    public void tearDown() throws Exception {
        if (!supportsHardware()) return;
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance,
                instance.getClass().getDeclaredField("sService"), mOldService);
    }

    @Test
    public void getNonNullInstance() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        Assert.assertNotNull(instance);
    }

    @Test
    public void testIsDefaultServiceForCategory() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultServiceForCategory(anyInt(), any(ComponentName.class),
            anyString())).thenReturn(true);
        boolean result = instance.isDefaultServiceForCategory(mService,
            CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testIsDefaultServiceForAid() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        String aid = "00000000000000";
        when(mEmulation.isDefaultServiceForAid(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.isDefaultServiceForAid(mService, aid);
        Assert.assertTrue(result);
    }

    @Test
    public void testCategoryAllowsForegroundPreferenceWithCategoryPayment() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        boolean result
            = instance.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testCategoryAllowsForegroundPrefenceWithCategoryOther() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        boolean result
            = instance.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryPaymentAndPaymentRegistered()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultPaymentRegistered()).thenReturn(true);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_PREFER_DEFAULT, result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryPaymentAndWithoutPaymentRegistered()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultPaymentRegistered()).thenReturn(false);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_ALWAYS_ASK, result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryOther() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_OTHER);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_ASK_IF_CONFLICT, result);
    }

    @Test
    public void testRegisterAidsForService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        when(mEmulation.registerAidGroupForService(anyInt(), any(ComponentName.class),
            any(AidGroup.class))).thenReturn(true);
        boolean result
            = instance.registerAidsForService(mService, CardEmulation.CATEGORY_PAYMENT, aids);
        Assert.assertTrue(result);
    }

    @Test
    public void testUnsetOffHostForService() throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        when(mEmulation.unsetOffHostForService(anyInt(), any(ComponentName.class)))
            .thenReturn(true);
        boolean result = instance.unsetOffHostForService(mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testSetOffHostForService() throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        String offHostSecureElement = "eSE";
        when(mEmulation.setOffHostForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.setOffHostForService(mService, offHostSecureElement);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetAidsForService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        when(mEmulation.getAidGroupForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(aidGroup);
        List<String> result = instance.getAidsForService(mService, CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(aids, result);
    }

    @Test
    public void testRemoveAidsForService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.removeAidGroupForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.removeAidsForService(mService, CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testSetPreferredService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mEmulation.setPreferredService(any(ComponentName.class))).thenReturn(true);
        boolean result = instance.setPreferredService(activity, mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testUnsetPreferredService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mEmulation.unsetPreferredService()).thenReturn(true);
        boolean result = instance.unsetPreferredService(activity);
        Assert.assertTrue(result);
    }

    @Test
    public void testSupportsAidPrefixRegistration() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.supportsAidPrefixRegistration()).thenReturn(true);
        boolean result = instance.supportsAidPrefixRegistration();
        Assert.assertTrue(result);
    }

    @Test
    public void testGetAidsForPreferredPaymentService() throws NoSuchFieldException,
        RemoteException {
        CardEmulation instance = createMockedInstance();
        ArrayList<AidGroup> dynamicAidGroups = new ArrayList<AidGroup>();
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        dynamicAidGroups.add(aidGroup);
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false, "",
            new ArrayList<AidGroup>(), dynamicAidGroups, false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        List<String> result = instance.getAidsForPreferredPaymentService();
        Assert.assertEquals(aids, result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOnHost()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ true,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals("Host", result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOffHostAndNoSecureElement()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ false,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "",
            /* offHost = */ null, "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals("OffHost", result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOffHostAndSecureElement()
        throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        String offHostSecureElement = "OffHost Secure Element";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ false,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "",
            /* offHost = */ offHostSecureElement, "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals(offHostSecureElement, result);
    }

    @Test
    public void testGetDescriptionForPreferredPaymentService() throws NoSuchFieldException,
        RemoteException {
        CardEmulation instance = createMockedInstance();
        String description = "Preferred Payment Service Description";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false,
            /* description */ description, new ArrayList<AidGroup>(), new ArrayList<AidGroup>(),
            false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        CharSequence result = instance.getDescriptionForPreferredPaymentService();
        Assert.assertEquals(description, result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testGetServices() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        String description = "Preferred Payment Service Description";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false,
                /* description */ description, new ArrayList<AidGroup>(), new ArrayList<AidGroup>(),
                false, 0, 0, "", "", "");
        List<ApduServiceInfo> services = List.of(serviceInfo);
        when(mEmulation.getServices(anyInt(), anyString())).thenReturn(services);
        Assert.assertEquals(instance.getServices(CardEmulation.CATEGORY_PAYMENT, 0), services);
    }

    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testGetPreferredPaymentService() {
        final String expectedPaymentService = "foo.bar/foo.bar.baz.Service";
        Settings.Secure.putString(ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT, expectedPaymentService);

        ComponentName paymentService = CardEmulation.getPreferredPaymentService(
                ApplicationProvider.getApplicationContext());

        Assert.assertEquals(paymentService,
                ComponentName.unflattenFromString(expectedPaymentService));
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testTypeAPollingLoopToDefault() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            ArrayList<Bundle> frames = new ArrayList<Bundle>(6);
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_OFF));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(HostApduService.POLLING_LOOP_TYPE_OFF));
            notifyPollingLoopAndWait(new ArrayList<Bundle>(frames),
                    CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testTypeAPollingLoopToForeground() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
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
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustomDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
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
        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForegroundDynamic() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
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
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testBackgroundForegroundConflictPollingLoopToPaymentDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
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
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPaymentDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        ComponentName originalDefault = null;
        try {
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            originalDefault = setDefaultPaymentService(customServiceName);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);

            Assert.assertTrue(cardEmulation.isDefaultServiceForCategory(customServiceName,
                    CardEmulation.CATEGORY_PAYMENT));
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
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustom() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        ArrayList<Bundle> frames = new ArrayList<Bundle>(1);
        frames.add(createFrameWithData(HostApduService.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForeground() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
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
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testBackgroundForegroundConflictPollingLoopToPayment() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
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
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPayment() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
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
            adapter.notifyHceDeactivated();
        }
    }

    private Bundle createFrame(char type) {
        Bundle frame = new Bundle();
        frame.putChar(HostApduService.POLLING_LOOP_TYPE_KEY, type);
        byte gain = 0x08;
        frame.putByte(HostApduService.POLLING_LOOP_GAIN_KEY, gain);
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
        return componentName;
    }

    ComponentName setDefaultPaymentService(ComponentName serviceName) {
        return setDefaultPaymentService(serviceName, mContext);
    }

    static final class SettingsObserver extends ContentObserver {
        boolean mSeenChange = false;

        SettingsObserver(Handler handler) {
            super(handler);
        }
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            mSeenChange = true;
            synchronized (this) {
                this.notify();
            }
        }
    }

    static ComponentName setDefaultPaymentService(ComponentName serviceName, Context context) {
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity();

            ComponentName originalValue = CardEmulation.getPreferredPaymentService(context);
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            SettingsObserver settingsObserver =
                    new SettingsObserver(new Handler(Looper.getMainLooper()));
            context.getContentResolver().registerContentObserverAsUser(
                    Settings.Secure.getUriFor(
                            Constants.SETTINGS_SECURE_NFC_PAYMENT_DEFAULT_COMPONENT),
                    true, settingsObserver, UserHandle.ALL);
            Assume.assumeTrue(cardEmulation.setDefaultServiceForCategory(serviceName,
                    CardEmulation.CATEGORY_PAYMENT));
            int count = 0;
            while (!settingsObserver.mSeenChange
                    && !cardEmulation.isDefaultServiceForCategory(serviceName,
                    CardEmulation.CATEGORY_PAYMENT) && count < 10) {
                synchronized (settingsObserver) {
                    try {
                        settingsObserver.wait(200);
                    } catch (InterruptedException ie) {
                    }
                    count++;
                }
            }
            Assert.assertTrue(count < 10);
            Assume.assumeTrue(serviceName == null
                    ? null == CardEmulation.getPreferredPaymentService(context)
                    : serviceName.equals(cardEmulation.getPreferredPaymentService(context)));
            return originalValue;
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    static PollLoopReceiver sCurrentPollLoopReceiver = null;

    static class PollLoopReceiver  {
        int mFrameIndex = 0;
        List<Bundle> mFrames;
        String mServiceName;
        List<Bundle> mReceivedFrames;
        String mReceivedServiceName;

        PollLoopReceiver(List<Bundle> frames, String serviceName) {
            mFrames = frames;
            mServiceName = serviceName;
        }

        void notifyPollingLoop(String className, List<Bundle> receivedFrames) {
            if (mReceivedFrames == null) {
                mReceivedFrames = receivedFrames;
            } else {
                mReceivedFrames.addAll(receivedFrames);
            }
            mReceivedServiceName = className;
            if (mReceivedFrames.size() < mFrames.size()) {
                return;
            }
            synchronized (this) {
                if (mFrameIndex >= mFrames.size()) {
                    this.notify();
                }
            }
        }

        void test() {
            for (Bundle receivedFrame : mReceivedFrames) {
                if (mFrameIndex < mFrames.size()) {
                    Assert.assertEquals(
                            mFrames.get(mFrameIndex).getChar(HostApduService.POLLING_LOOP_TYPE_KEY),
                            receivedFrame.getChar(HostApduService.POLLING_LOOP_TYPE_KEY));
                    Assert.assertEquals(
                            mFrames.get(mFrameIndex).getByte(HostApduService.POLLING_LOOP_GAIN_KEY),
                            receivedFrame.getByte(HostApduService.POLLING_LOOP_GAIN_KEY));
                    Assert.assertArrayEquals(
                            mFrames.get(mFrameIndex).getByteArray(
                                    HostApduService.POLLING_LOOP_DATA_KEY),
                            receivedFrame.getByteArray(HostApduService.POLLING_LOOP_DATA_KEY));
                } else {
                    Assert.fail("received more frames than sent: " + receivedFrame);
                }
                mFrameIndex++;
            }
            Assert.assertEquals(mServiceName, mReceivedServiceName);
        }
    }

    private void notifyPollingLoopAndWait(List<Bundle> frames, String serviceName) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        sCurrentPollLoopReceiver = new PollLoopReceiver(frames, serviceName);
        for (Bundle frame : frames) {
            adapter.notifyPollingLoop(frame);
        }
        synchronized (sCurrentPollLoopReceiver) {
            try {
                sCurrentPollLoopReceiver.wait(5000);
            } catch (InterruptedException ie) {
                Assert.assertNull(ie);
            }
        }
        sCurrentPollLoopReceiver.test();
        Assert.assertEquals(frames.size(), sCurrentPollLoopReceiver.mFrameIndex);
        sCurrentPollLoopReceiver = null;
        adapter.notifyHceDeactivated();
    }


    private Activity createAndResumeActivity() {
        Intent intent
            = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        return activity;
    }

    private CardEmulation createMockedInstance() throws NoSuchFieldException {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance, instance.getClass().getDeclaredField("sService"), mEmulation);
        return instance;
    }
}
