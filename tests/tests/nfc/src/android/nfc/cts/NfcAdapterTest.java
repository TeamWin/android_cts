package android.nfc.cts;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.*;
import android.nfc.tech.*;
import android.os.Bundle;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

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
    public void testAllowTransaction() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        boolean result = adapter.allowTransaction();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDisallowTransaction() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        boolean result = adapter.disallowTransaction();
        Assert.assertTrue(result);
    }

    private class CtsReaderCallback implements NfcAdapter.ReaderCallback {
        @Override
        public void onTagDiscovered(Tag tag) {}
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
}
