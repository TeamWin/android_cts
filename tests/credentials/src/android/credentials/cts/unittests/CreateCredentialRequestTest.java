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

package android.credentials.cts.unittests;

import static android.credentials.cts.unittests.TestUtils.assertEquals;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.credentials.CreateCredentialRequest;
import android.credentials.Credential;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;


@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CreateCredentialRequestTest {

    @Test
    public void testConstructor_nullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateCredentialRequest(null, Bundle.EMPTY, Bundle.EMPTY, false, false));
    }

    @Test
    public void testConstructor_nullRetrievalData() {
        assertThrows(NullPointerException.class,
                () -> new CreateCredentialRequest(Credential.TYPE_PASSWORD_CREDENTIAL, null,
                        Bundle.EMPTY, false, false));
    }

    @Test
    public void testConstructor_nullQueryData() {
        assertThrows(NullPointerException.class,
                () -> new CreateCredentialRequest(Credential.TYPE_PASSWORD_CREDENTIAL, Bundle.EMPTY,
                        null, false, false));
    }

    @Test
    public void testConstructor() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final Bundle query = new Bundle();
        query.putString("bar", "baz");

        final boolean isSystemProviderRequired = true;
        final boolean alwaysSendAppInfo = true;

        final CreateCredentialRequest req = new CreateCredentialRequest(type, data, query,
                isSystemProviderRequired, alwaysSendAppInfo);

        assertThat(req.getType()).isEqualTo(type);
        assertThat(req.getCredentialData()).isEqualTo(data);
        assertThat(req.getCandidateQueryData()).isEqualTo(query);
        assertThat(req.isSystemProviderRequired()).isEqualTo(isSystemProviderRequired);
        assertThat(req.alwaysSendAppInfoToProvider()).isEqualTo(alwaysSendAppInfo);
    }

    @Test
    public void testWriteToParcel() {
        final Bundle credData = new Bundle();
        credData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("baz", "foo");

        final CreateCredentialRequest req1 = new CreateCredentialRequest(
                Credential.TYPE_PASSWORD_CREDENTIAL, credData, queryData, true, true);

        final CreateCredentialRequest req2 = TestUtils.cloneParcelable(req1);
        assertThat(req2.getType()).isEqualTo(req1.getType());
        assertEquals(req2.getCredentialData(), req1.getCredentialData());
        assertEquals(req2.getCandidateQueryData(), req1.getCandidateQueryData());
        assertThat(req2.isSystemProviderRequired()).isEqualTo((req1.isSystemProviderRequired()));
        assertThat(req2.alwaysSendAppInfoToProvider()).isEqualTo(
                req1.alwaysSendAppInfoToProvider());
    }
}
