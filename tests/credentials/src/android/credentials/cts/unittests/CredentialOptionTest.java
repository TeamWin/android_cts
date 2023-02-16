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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.credentials.Credential;
import android.credentials.CredentialOption;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;


@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CredentialOptionTest {

    @Test
    public void testConstructor_nullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialOption(null, Bundle.EMPTY, Bundle.EMPTY, false));
    }

    @Test
    public void testConstructor_nullRetrievalData() {
        assertThrows(NullPointerException.class,
                () -> new CredentialOption(Credential.TYPE_PASSWORD_CREDENTIAL, null, Bundle.EMPTY,
                        false));
    }

    @Test
    public void testConstructor_nullQueryData() {
        assertThrows(NullPointerException.class,
                () -> new CredentialOption(Credential.TYPE_PASSWORD_CREDENTIAL, Bundle.EMPTY, null,
                        false));
    }

    @Test
    public void testConstructor() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("bar", "baz");

        final boolean isSystemProviderRequired = true;

        final CredentialOption opt = new CredentialOption(type, retrievalData, queryData,
                isSystemProviderRequired);

        assertThat(opt.getType()).isEqualTo(type);
        TestUtils.assertEquals(opt.getCredentialRetrievalData(), retrievalData);
        TestUtils.assertEquals(opt.getCandidateQueryData(), queryData);
        assertThat(opt.isSystemProviderRequired()).isEqualTo(isSystemProviderRequired);
    }

    @Test
    public void testWriteToParcel() {
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("baz", "foo");

        final CredentialOption opt1 = new CredentialOption(Credential.TYPE_PASSWORD_CREDENTIAL,
                retrievalData, queryData, true);

        final CredentialOption opt2 = TestUtils.cloneParcelable(opt1);
        TestUtils.assertEquals(opt2, opt1);
    }
}
