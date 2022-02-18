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

package android.security.cts;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;
import java.lang.reflect.Method;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;

@RunWith(AndroidJUnit4.class)
public class CVE_2020_0105 extends StsExtraBusinessLogicTestCase {
    private WakeLock mScreenLock;

    @Before
    public void setUp() {
        String TAG = "CVE-2020-0105";
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeNotNull(context);
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        assumeNotNull(powerManager);
        mScreenLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        assumeNotNull(mScreenLock);
        mScreenLock.acquire();
        KeyguardManager keyguardManager = (KeyguardManager) context
                .getSystemService(KeyguardManager.class);
        assumeNotNull(keyguardManager);
        KeyguardManager.KeyguardLock keyguardLock
                = keyguardManager.newKeyguardLock("UnlockScreen");
        assumeNotNull(keyguardLock);
        keyguardLock.disableKeyguard();
        keyguardLock.reenableKeyguard();
    }

    @After
    public void tearDown() {
        mScreenLock.release();
    }

    private boolean fakeUnlock() throws Exception {
        int actionToPerform = 35;
        int normalRPC = 0;
        int isShowing = 0;
        int userId = 0;
        Class smClass = Class.forName("android.os.ServiceManager");
        assumeNotNull(smClass);
        Method getService = smClass.getMethod("getService", String.class);
        assumeNotNull(getService);
        IBinder binder = (IBinder) getService.invoke(smClass,
                "android.security.keystore");
        assumeNotNull(binder);
        Parcel data = Parcel.obtain();
        assumeNotNull(data);
        Parcel reply = Parcel.obtain();
        assumeNotNull(reply);
        data.writeInterfaceToken("android.security.keystore.IKeystoreService");
        data.writeInt(isShowing);
        data.writeInt(userId);
        if (binder.transact(actionToPerform, data, reply, normalRPC)) {
            reply.readException();
            if (reply.readInt() != 1) {
                return false;
            }
        }
        return true;
    }

    @AsbSecurityTest(cveBugId = 144285084)
    @Test
    public void testCVE_2020_0105() throws Exception {
        String KEY_ALIAS = "key";
        String KEYSTORE = "AndroidKeyStore";
        boolean containsAlias = false;
        final KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        assumeNotNull(keyStore);
        keyStore.load(null);
        try {
            containsAlias = keyStore.containsAlias(KEY_ALIAS);
        } catch (NullPointerException e) {
            assumeNoException(e);
        }
        if (!containsAlias) {
            final KeyGenParameterSpec keyGenParameterSpec =
                    new KeyGenParameterSpec.Builder(KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT
                                    | KeyProperties.PURPOSE_DECRYPT)
                                            .setBlockModes(
                                                    KeyProperties.BLOCK_MODE_GCM)
                                            .setEncryptionPaddings(
                                                    KeyProperties.ENCRYPTION_PADDING_NONE)
                                            .setUnlockedDeviceRequired(true)
                                            .build();
            assumeNotNull(keyGenParameterSpec);
            KeyGenerator keyGenerator = KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            assumeNotNull(keyGenerator);
            keyGenerator.init(keyGenParameterSpec);
            keyGenerator.generateKey();
        }
        String message =
                "Keyguard-bound keys are accessible even when keyguard is visible. "
                        + "Hence device is vulnerable to b/144285084";
        assertFalse(message, fakeUnlock());
    }
}
