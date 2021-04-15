/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.permission.cts;

import static android.Manifest.permission.NFC_SET_CONTROLLER_ALWAYS_ON;

import static org.junit.Assert.fail;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ControllerAlwaysOnListener;
import android.os.Process;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public final class NfcPermissionTest {

    private NfcAdapter mNfcAdapter;

    @Before
    public void setUp() {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(InstrumentationRegistry.getTargetContext());
    }

    /**
     * Verifies that there's only one dedicated app holds the NfcSetControllerAlwaysOnPermission.
     */
    @Test
    public void testNfcSetControllerAlwaysOnPermission() {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();

        List<Integer> specialUids = Arrays.asList(Process.SYSTEM_UID, Process.NFC_UID);

        List<PackageInfo> holding = pm.getPackagesHoldingPermissions(
                new String[] { NFC_SET_CONTROLLER_ALWAYS_ON },
                PackageManager.MATCH_DISABLED_COMPONENTS);

        List<Integer> nonSpecialPackages = holding.stream()
                .map(pi -> {
                    try {
                        return pm.getPackageUid(pi.packageName, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        return Process.INVALID_UID;
                    }
                })
                .filter(uid -> !specialUids.contains(uid))
                .collect(Collectors.toList());

        if (holding.size() > 1) {
            fail("Only one app on the device is allowed to hold the "
                     + "NFC_SET_CONTROLLER_ALWAYS_ON permission.");
        }
    }

    /**
     * Verifies that isControllerAlwaysOnSupported() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    public void testIsControllerAlwaysOnSupported() {
        try {
            mNfcAdapter.isControllerAlwaysOnSupported();
            fail("mNfcAdapter.isControllerAlwaysOnSupported() did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that isControllerAlwaysOn() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    public void testIsControllerAlwaysOn() {
        try {
            mNfcAdapter.isControllerAlwaysOn();
            fail("mNfcAdapter.isControllerAlwaysOn() did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that setControllerAlwaysOn(true) requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    public void testSetControllerAlwaysOnTrue() {
        try {
            mNfcAdapter.setControllerAlwaysOn(true);
            fail("mNfcAdapter.setControllerAlwaysOn(true) did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that setControllerAlwaysOn(false) requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    public void testSetControllerAlwaysOnFalse() {
        try {
            mNfcAdapter.setControllerAlwaysOn(false);
            fail("mNfcAdapter.setControllerAlwaysOn(true) did not throw SecurityException"
                    + " as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    /**
     * Verifies that registerControllerAlwaysOnListener() requires Permission.
     * <p>
     * Requires Permission: {@link android.Manifest.permission#NFC_SET_CONTROLLER_ALWAYS_ON}.
     */
    @Test
    public void testRegisterControllerAlwaysOnListener() {
        try {
            mNfcAdapter.registerControllerAlwaysOnListener(
                    new SynchronousExecutor(), new AlwaysOnStateListener());
            fail("mNfcAdapter.registerControllerAlwaysOnListener did not throw"
                    + "SecurityException as expected");
        } catch (SecurityException se) {
            // Expected Exception
        }
    }

    private class SynchronousExecutor implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }

    private class AlwaysOnStateListener implements ControllerAlwaysOnListener {
        @Override
        public void onControllerAlwaysOnChanged(boolean isEnabled) {
            // Do nothing
        }
    }
}
