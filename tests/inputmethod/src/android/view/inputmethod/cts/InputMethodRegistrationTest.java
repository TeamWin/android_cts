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

package android.view.inputmethod.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.AppModeFull;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot query the installed IMEs")
public class InputMethodRegistrationTest {

    private static final String MOCK_IME_PACKAGE = "com.android.cts.mocklargeresourceime";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
        runShellCommand("ime reset");
    }

    /**
     * We only allow names of a maximum size of
     * {@link InputMethodInfo#COMPONENT_NAME_MAX_LENGTH} characters. If the name of an IME or
     * settingsActivityComponent is greater than that, it will be ignored
     */
    @Test
    public void testIgnoreLargeSettingsActivityComponent() {
        final var imm = mContext.getSystemService(InputMethodManager.class);

        runShellCommand("am wait-for-broadcast-barrier");

        List<InputMethodInfo> imis = imm.getInputMethodList();

        List<InputMethodInfo> filteredImis = imis.stream().filter(
                imi -> imi.getServiceInfo().packageName.equals(MOCK_IME_PACKAGE)
                        && (imi.getServiceInfo().name.length()
                        > InputMethodInfo.COMPONENT_NAME_MAX_LENGTH
                        || imi.getSettingsActivity().length()
                        > InputMethodInfo.COMPONENT_NAME_MAX_LENGTH)).toList();
        assertWithMessage("Number of IMEs that exceed threshold").that(
                filteredImis.size()).isEqualTo(0);
    }

    /**
     * If a package contains more than {@link InputMethodInfo#MAX_IMES_PER_PACKAGE} IMEs, all IMEs
     * up to that  threshold should be loaded(if none is enabled)
     */
    @Test
    public void testLoadIMEsUpToThreshold() {
        final var imm = mContext.getSystemService(InputMethodManager.class);

        runShellCommand("am wait-for-broadcast-barrier");

        List<InputMethodInfo> imis = imm.getInputMethodList();

        List<InputMethodInfo> filteredImis = imis.stream().filter(
                imi -> imi.getServiceInfo().packageName.equals(MOCK_IME_PACKAGE)).toList();
        assertWithMessage("Number of loaded IMEs (overall %s, filtered %s)", imis,
                filteredImis).that(filteredImis.size()).isAtLeast(
                InputMethodInfo.MAX_IMES_PER_PACKAGE);
    }

    /**
     * Ensure that all enabled + {@link InputMethodInfo#MAX_IMES_PER_PACKAGE} are loaded:
     * 1. load all enabled IMEs
     * 2. enable some of them,
     * 3. set enabled status = true of IMEs in the manifest (located before the currently enabled
     * ones),
     * 4. load IMEs again, check that total amount is greater
     */
    @Test
    public void testLoadEnabledIMEsAndMore() throws InterruptedException {

        final var imm = mContext.getSystemService(InputMethodManager.class);

        final int flags = PackageManager.GET_META_DATA
                | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                | PackageManager.MATCH_DISABLED_COMPONENTS;
        final List<ResolveInfo> allPackageImes = mContext.getPackageManager().queryIntentServices(
                new Intent(InputMethod.SERVICE_INTERFACE).setPackage(MOCK_IME_PACKAGE),
                PackageManager.ResolveInfoFlags.of(flags));
        final List<ComponentName> componentNamesToEnable = allPackageImes.subList(2, 4)
                .stream().map(ri -> new ComponentName(ri.serviceInfo.packageName,
                        ri.serviceInfo.name)).toList();

        try {

            CountDownLatch countDownLatch = new CountDownLatch(1);
            BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                    if (packageName.equals(MOCK_IME_PACKAGE)) {
                        countDownLatch.countDown();
                    }
                }
            };
            mContext.registerReceiver(broadcastReceiver,
                    new IntentFilter(Intent.ACTION_PACKAGE_CHANGED));
            // enable last two IMEs and make two IMEs at the beginning available
            enableImes(MOCK_IME_PACKAGE + "/.services.imeservice19",
                    MOCK_IME_PACKAGE + "/.services.imeservice20");
            setComponentEnabledSync(componentNamesToEnable,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

            countDownLatch.await(2, TimeUnit.SECONDS);
            runShellCommand("am wait-for-broadcast-barrier");
            mContext.unregisterReceiver(broadcastReceiver);

            // load all IMEs: the number of enabled IMEs should be more than MAX_IMES_PER_PACKAGE.
            List<InputMethodInfo> imis = imm.getInputMethodList();
            List<InputMethodInfo> filteredImis = imis.stream().filter(
                    imi -> imi.getServiceInfo().packageName.equals(MOCK_IME_PACKAGE)).toList();
            assertWithMessage("Number of loaded IMEs (overall %s, filtered %s)", imis,
                    filteredImis).that(filteredImis.size()).isAtLeast(
                    2 + InputMethodInfo.MAX_IMES_PER_PACKAGE);

        } finally {
            // disable again, for other tests
            setComponentEnabledSync(componentNamesToEnable,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

            runShellCommand("am wait-for-broadcast-barrier");
        }
    }

    private void setComponentEnabledSync(List<ComponentName> componentNames, int enabledState) {
        for (ComponentName componentName : componentNames) {
            runWithShellPermissionIdentity(
                    () -> mContext.getPackageManager().setComponentEnabledSetting(componentName,
                            enabledState, 0));
        }
    }

    private void enableImes(String... ids) {
        for (String id : ids) {
            runShellCommand("ime enable " + id);
        }
    }
}
