/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.nfc.cts;

import static android.nfc.cardemulation.CardEmulation.CATEGORY_OTHER;
import static android.nfc.cardemulation.CardEmulation.CATEGORY_PAYMENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.nfc.Flags;
import android.nfc.cardemulation.AidGroup;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.proto.ProtoOutputStream;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
@RunWith(AndroidJUnit4.class)
public class ApduServiceInfoTest {
    private static final String AID_1 = "00000000000000";
    private static final String AID_2 = "00000000000001";
    private static final String SERVICE_PACKAGE_NAME = "com.nfc.test";
    private static final String SERVICE_NAME = "hce_service";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final ArrayList<AidGroup> mDynamicAidGroups = new ArrayList<>();
    private final ResolveInfo mResolveInfo = new ResolveInfo();

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        assumeTrue(supportsHardware());

        ArrayList<String> aids = new ArrayList<String>();
        aids.add(AID_1);
        aids.add(AID_2);
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        mDynamicAidGroups.add(aidGroup);

        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = SERVICE_PACKAGE_NAME;
        serviceInfo.name = SERVICE_NAME;
        serviceInfo.applicationInfo = new ApplicationInfo();
        mResolveInfo.serviceInfo = serviceInfo;
    }

    @Test
    public void test_Constructor() {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");

        assertEquals(apduServiceInfo.getComponent(),
                new ComponentName(SERVICE_PACKAGE_NAME, SERVICE_NAME));
        assertEquals(apduServiceInfo.getOffHostSecureElement(), "");
        assertEquals(apduServiceInfo.getAids(), mDynamicAidGroups.get(0).getAids());
        assertTrue(apduServiceInfo.getPrefixAids().isEmpty());
        assertTrue(apduServiceInfo.getSubsetAids().isEmpty());
        assertEquals(apduServiceInfo.getDynamicAidGroupForCategory(CATEGORY_PAYMENT).getAids(),
                mDynamicAidGroups.get(0).getAids());
        assertFalse(apduServiceInfo.removeDynamicAidGroupForCategory(CATEGORY_OTHER));
        assertEquals(apduServiceInfo.getAidGroups().get(0).getAids(),
                mDynamicAidGroups.get(0).getAids());
        assertEquals(apduServiceInfo.getCategoryForAid(AID_1), CATEGORY_PAYMENT);
        assertTrue(apduServiceInfo.hasCategory(CATEGORY_PAYMENT));
        assertFalse(apduServiceInfo.isOnHost());
        assertFalse(apduServiceInfo.requiresUnlock());
        assertFalse(apduServiceInfo.requiresScreenOn());
        assertEquals(apduServiceInfo.getDescription(), "");
        assertEquals(apduServiceInfo.getUid(), 0);
        assertEquals(apduServiceInfo.getSettingsActivityName(), "");
    }

    @Test
    public void test_ReadWriteParcel() {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");
        Parcel p = Parcel.obtain();
        apduServiceInfo.writeToParcel(p, 0);
        p.setDataPosition(0);

        ApduServiceInfo newApduServiceInfo = ApduServiceInfo.CREATOR.createFromParcel(p);
        assertEquals(apduServiceInfo.getComponent(), newApduServiceInfo.getComponent());
        assertEquals(apduServiceInfo.getOffHostSecureElement(),
                newApduServiceInfo.getOffHostSecureElement());
        assertEquals(apduServiceInfo.getAids(), newApduServiceInfo.getAids());
        assertEquals(apduServiceInfo.getPrefixAids(), newApduServiceInfo.getPrefixAids());
        assertEquals(apduServiceInfo.getSubsetAids(), newApduServiceInfo.getSubsetAids());
        assertEquals(apduServiceInfo.getDynamicAidGroupForCategory(CATEGORY_PAYMENT).getAids(),
                newApduServiceInfo.getDynamicAidGroupForCategory(CATEGORY_PAYMENT).getAids());
        assertFalse(newApduServiceInfo.removeDynamicAidGroupForCategory(CATEGORY_OTHER));
        assertEquals(apduServiceInfo.getAidGroups().get(0).getAids(),
                newApduServiceInfo.getAidGroups().get(0).getAids());
        assertEquals(apduServiceInfo.getCategoryForAid(AID_1),
                newApduServiceInfo.getCategoryForAid(AID_1));
        assertEquals(apduServiceInfo.hasCategory(CATEGORY_PAYMENT),
                newApduServiceInfo.hasCategory(CATEGORY_PAYMENT));
        assertEquals(apduServiceInfo.isOnHost(), newApduServiceInfo.isOnHost());
        assertEquals(apduServiceInfo.requiresUnlock(), newApduServiceInfo.requiresUnlock());
        assertEquals(apduServiceInfo.requiresScreenOn(), newApduServiceInfo.requiresScreenOn());
        assertEquals(apduServiceInfo.getDescription(), newApduServiceInfo.getDescription());
        assertEquals(apduServiceInfo.getUid(), newApduServiceInfo.getUid());
        assertEquals(apduServiceInfo.getSettingsActivityName(),
                newApduServiceInfo.getSettingsActivityName());
    }

    @Test
    public void test_Equals() throws Exception {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");
        ApduServiceInfo newApduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");

        assertEquals(apduServiceInfo, newApduServiceInfo);
    }

    @Test
    public void test_Dump() throws Exception {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");
        ParcelFileDescriptor pfd = new ParcelFileDescriptor(Parcel.obtain().readFileDescriptor());
        PrintWriter pw = new PrintWriter(new ByteArrayOutputStream());
        apduServiceInfo.dump(pfd, pw, new String[0]);
    }

    @Test
    public void test_DumpDebug() throws Exception {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");
        ProtoOutputStream po = new ProtoOutputStream();
        apduServiceInfo.dumpDebug(po);
    }

    @Test
    public void test_SetDynamicAidGroup() throws Exception {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");

        assertFalse(apduServiceInfo.hasCategory(CATEGORY_OTHER));

        ArrayList<String> aids = new ArrayList<String>();
        aids.add(AID_1);
        aids.add(AID_2);
        AidGroup aidGroup = new AidGroup(aids, CATEGORY_OTHER);
        apduServiceInfo.setDynamicAidGroup(aidGroup);
        assertTrue(apduServiceInfo.hasCategory(CATEGORY_OTHER));
    }

    @Test
    public void test_SetResetOffHostSecureElement() throws Exception {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");

        assertEquals(apduServiceInfo.getOffHostSecureElement(), "");

        apduServiceInfo.setOffHostSecureElement("SIM");
        assertEquals(apduServiceInfo.getOffHostSecureElement(), "SIM");

        apduServiceInfo.resetOffHostSecureElement();
        assertEquals(apduServiceInfo.getOffHostSecureElement(), "");
    }

    @Test
    public void test_LoadLabel() throws Exception {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");

        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        assertEquals(apduServiceInfo.loadLabel(pm), SERVICE_NAME);
        assertNull(apduServiceInfo.loadAppLabel(pm));
        assertNotNull(apduServiceInfo.loadIcon(pm));
        assertNull(apduServiceInfo.loadBanner(pm));
    }

    @Test
    public void test_SetOtherServiceStateSelected() throws Exception {
        ApduServiceInfo apduServiceInfo = new ApduServiceInfo(mResolveInfo, false, "",
                new ArrayList<>(), mDynamicAidGroups, false, 0, 0, "", "", "");

        assertFalse(apduServiceInfo.isOtherServiceEnabled());

        apduServiceInfo.setOtherServiceEnabled(true);
        assertTrue(apduServiceInfo.isOtherServiceEnabled());
    }

}
