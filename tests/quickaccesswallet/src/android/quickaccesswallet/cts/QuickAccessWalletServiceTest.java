/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.quickaccesswallet.cts;


import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.quickaccesswallet.DoNotUseTargetActivityForQuickAccessWalletService;
import android.quickaccesswallet.TestHostApduService;
import android.quickaccesswallet.TestQuickAccessWalletService;
import android.quickaccesswallet.UseTargetActivityForQuickAccessWalletService;
import android.service.quickaccesswallet.QuickAccessWalletClient;
import android.service.quickaccesswallet.QuickAccessWalletService;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.SettingsUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;


/**
 * Tests {@link android.service.quickaccesswallet.QuickAccessWalletService}.
 */
@RunWith(AndroidJUnit4.class)
public class QuickAccessWalletServiceTest {

    private static final String NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component";

    private Context mContext;
    private String mDefaultPaymentApp;

    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();

    @Before
    public void setUp() throws Exception {
        // Save current default payment app
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDefaultPaymentApp = SettingsUtils.get(NFC_PAYMENT_DEFAULT_COMPONENT);
        ComponentName component =
                ComponentName.createRelative(mContext, TestHostApduService.class.getName());
        SettingsUtils.syncSet(mContext, NFC_PAYMENT_DEFAULT_COMPONENT,
                component.flattenToString());
        TestQuickAccessWalletService.resetStaticFields();
    }

    @After
    public void tearDown() {
        // Restore saved default payment app
        SettingsUtils.syncSet(mContext, NFC_PAYMENT_DEFAULT_COMPONENT, mDefaultPaymentApp);

        // Return all services to default state
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        setServiceState(UseTargetActivityForQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        setServiceState(DoNotUseTargetActivityForQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        TestQuickAccessWalletService.resetStaticFields();
    }

    @Test
    public void defaultTestQuickAccessWalletService_noMetadataItem_useTargetActivityIsFalse()
            throws Exception {
        bindToService();

        assertThat(TestQuickAccessWalletService.testGetUseTargetActivityForQuickAccess()).isFalse();
    }

    @Test
    public void testQuickAccessService_metadataSpecifiesToUseTarget_useTargetActivityIsTrue()
            throws Exception {
        setServiceState(UseTargetActivityForQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        bindToService();

        assertThat(UseTargetActivityForQuickAccessWalletService
                .testGetUseTargetActivityForQuickAccess()).isTrue();

    }

    @Test
    public void testQuickAccessService_metadataSpecifiesNotToUseTarget_useTargetActivityIsFalse()
            throws Exception {
        setServiceState(DoNotUseTargetActivityForQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        bindToService();

        assertThat(
                DoNotUseTargetActivityForQuickAccessWalletService
                        .testGetUseTargetActivityForQuickAccess()).isFalse();
    }

    private void bindToService() throws Exception {
        // Creates a QuickAccessWalletClient that binds to the currently active service.
        // This ensures that the value set in TestQuickAccessWallet's
        // sServiceRef is the correct service.
        TestQuickAccessWalletService.setExpectedBindCount(1);
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        client.notifyWalletDismissed();
        TestQuickAccessWalletService.awaitBinding(3, TimeUnit.SECONDS);
    }

    private void setServiceState(
            Class<? extends QuickAccessWalletService> cls, int state) {
        ComponentName componentName = ComponentName.createRelative(mContext, cls.getName());
        mContext.getPackageManager().setComponentEnabledSetting(
                componentName, state, PackageManager.DONT_KILL_APP);
    }
}
