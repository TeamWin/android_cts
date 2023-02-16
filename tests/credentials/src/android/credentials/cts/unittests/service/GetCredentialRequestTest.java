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

package android.credentials.cts.unittests.service;

import static android.credentials.cts.unittests.TestUtils.createTestBundle;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.credentials.CredentialOption;
import android.credentials.cts.unittests.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.CallingAppInfo;
import android.service.credentials.GetCredentialRequest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class GetCredentialRequestTest {
    private CallingAppInfo mCallingAppInfo;
    private CredentialOption mCredentialOption = new CredentialOption("type", createTestBundle(),
            createTestBundle(), true);

    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final SigningInfo signingInfo = context.getPackageManager().getPackageInfo(
                context.getOpPackageName(), PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES)).signingInfo;
        mCallingAppInfo = new CallingAppInfo(context.getOpPackageName(), signingInfo);
    }

    @Test
    public void testConstructor_nullCallingAppInfo() {
        assertThrows(NullPointerException.class,
                () -> new GetCredentialRequest(null, mCredentialOption));
    }

    @Test
    public void testConstructor_nullCredentialOption() {
        assertThrows(NullPointerException.class,
                () -> new GetCredentialRequest(mCallingAppInfo, null));
    }

    @Test
    public void testConstructor() {
        final GetCredentialRequest request = new GetCredentialRequest(mCallingAppInfo,
                mCredentialOption);
        assertThat(request.getCallingAppInfo()).isSameInstanceAs(mCallingAppInfo);
        assertThat(request.getCredentialOption()).isSameInstanceAs(mCredentialOption);
    }

    @Test
    public void testWriteToParcel() {
        final GetCredentialRequest req1 = new GetCredentialRequest(mCallingAppInfo,
                mCredentialOption);

        final GetCredentialRequest req2 = TestUtils.cloneParcelable(req1);
        TestUtils.assertEquals(req2.getCredentialOption().getCandidateQueryData(),
                req1.getCredentialOption().getCandidateQueryData());
        TestUtils.assertEquals(req2.getCredentialOption().getCredentialRetrievalData(),
                req1.getCredentialOption().getCredentialRetrievalData());
        assertThat(req2.getCredentialOption().getType()).isEqualTo(
                req1.getCredentialOption().getType());

        final SigningInfo signing1 = req1.getCallingAppInfo().getSigningInfo();
        final SigningInfo signing2 = req2.getCallingAppInfo().getSigningInfo();
        assertThat(signing2.getApkContentsSigners()).isEqualTo(signing1.getApkContentsSigners());
        assertThat(signing2.getSigningCertificateHistory()).isEqualTo(
                signing1.getSigningCertificateHistory());
        assertThat(signing2.hasPastSigningCertificates()).isEqualTo(
                signing1.hasPastSigningCertificates());
        assertThat(signing2.hasMultipleSigners()).isEqualTo(signing2.hasMultipleSigners());
    }
}
