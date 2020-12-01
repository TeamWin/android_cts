/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.android.eventlib.events.CustomEvent;

/**
 * Activity that will call {@link KeyChain.choosePrivateKeyAlias} to verify the credential
 * management app is able to specify which alias should be used given an app package name and URI.
 * <p>
 * This test Activity is required to call the API {@link KeyChain.choosePrivateKeyAlias}.
 */
public class AppUriAuthenticationActivity extends Activity {

    private static final String TAG = "AppUriAuthenticationActivity";

    private static final long KEYCHAIN_TIMEOUT_MINS = 2;
    private static final String ALIAS = "com.android.test.rsa";
    private final static Uri URI = Uri.parse("https://test.com");

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        String aliasProvidedByCredentialManager = null;
        try {
            aliasProvidedByCredentialManager = new KeyChainAliasFuture(/* activity */ this).get();
        } catch (InterruptedException e) {
            Log.e(TAG, "Unable to get the alias provided by the credential management app");
        }
        CustomEvent.logger(this)
                .setTag("credentialManagementAppAliasFetched")
                .setData(aliasProvidedByCredentialManager)
                .log();
    }

    private static class KeyChainAliasFuture implements KeyChainAliasCallback {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private String mChosenAlias = null;

        @Override
        public void alias(final String chosenAlias) {
            mChosenAlias = chosenAlias;
            mLatch.countDown();
        }

        public KeyChainAliasFuture(Activity activity) {
            KeyChain.choosePrivateKeyAlias(activity, this, /* keyTypes */null,
                    /* issuers */null, URI, /* alias = */null);
        }

        public String get() throws InterruptedException {
            assertWithMessage("Chooser timeout")
                    .that(mLatch.await(KEYCHAIN_TIMEOUT_MINS, TimeUnit.MINUTES))
                    .isTrue();
            return mChosenAlias;
        }
    }
}
