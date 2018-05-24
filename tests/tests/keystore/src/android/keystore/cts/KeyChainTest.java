/*
 * Copyright 2013 The Android Open Source Project
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

package android.keystore.cts;

import android.content.pm.PackageManager;
import android.os.Handler;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.test.AndroidTestCase;

import java.util.concurrent.CountDownLatch;

public class KeyChainTest extends AndroidTestCase {
    public void testIsKeyAlgorithmSupported_RequiredAlgorithmsSupported() throws Exception {
        assertFalse("DSA must not be supported", KeyChain.isKeyAlgorithmSupported("DSA"));
        assertTrue("EC must be supported", KeyChain.isKeyAlgorithmSupported("EC"));
        assertTrue("RSA must be supported", KeyChain.isKeyAlgorithmSupported("RSA"));
    }

    public void testNullPrivateKeyArgumentsFail()
            throws KeyChainException, InterruptedException {
        try {
            KeyChain.getPrivateKey(null, null);
            fail("NullPointerException was expected for null arguments to "
                    + "KeyChain.getPrivateKey(Context, String)");
        } catch (NullPointerException expected) {
        }
    }

    public void testNullPrivateKeyAliasArgumentFails()
            throws KeyChainException, InterruptedException {
        try {
            KeyChain.getPrivateKey(getContext(), null);
            fail("NullPointerException was expected with null String argument to "
                        + "KeyChain.getPrivateKey(Context, String).");
        } catch (NullPointerException expected) {
        }
    }

    public void testNullPrivateKeyContextArgumentFails()
            throws KeyChainException, InterruptedException {
        try {
            KeyChain.getPrivateKey(null, "");
            fail("NullPointerException was expected with null Context argument to "
                    + "KeyChain.getPrivateKey(Context, String).");
        } catch (NullPointerException expected) {
        }
    }

    public void testGetPrivateKeyOnMainThreadFails() throws InterruptedException {
        final CountDownLatch waiter = new CountDownLatch(1);
        new Handler(getContext().getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    KeyChain.getPrivateKey(getContext(), "");
                    fail("IllegalStateException was expected for calling "
                            + "KeyChain.getPrivateKey(Context, String) on main thread");
                } catch (IllegalStateException expected) {
                } catch (Exception invalid) {
                    fail("Expected IllegalStateException, received " + invalid);
                } finally {
                    waiter.countDown();
                }
            }
        });
        waiter.await();
    }

    public void testIsBoundKeyAlgorithm_RequiredAlgorithmsSupported() throws Exception {
        // These are not required (until Nougat), but must not throw an exception
        KeyChain.isBoundKeyAlgorithm("RSA");
        KeyChain.isBoundKeyAlgorithm("DSA");
        KeyChain.isBoundKeyAlgorithm("EC");
    }
}
