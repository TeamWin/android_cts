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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.slice.Slice;
import android.credentials.Credential;
import android.credentials.cts.unittests.TestUtils;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.BeginGetCredentialOption;
import android.service.credentials.CredentialEntry;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CredentialEntryTest {

    private static final BeginGetCredentialOption sCredOption = new BeginGetCredentialOption("id1",
            "type", Bundle.EMPTY);

    private static final Slice sSlice = new Slice.Builder(Uri.parse("foo://bar"), null).addText(
            "some text", null, List.of(Slice.HINT_TITLE)).build();

    @Test
    public void testConstructor_nullOption() {
        assertThrows(NullPointerException.class,
                () -> new CredentialEntry((BeginGetCredentialOption) null, sSlice));
    }

    @Test
    public void testConstructor_nullSlice() {
        assertThrows(NullPointerException.class, () -> new CredentialEntry(sCredOption, null));
    }

    @Test
    public void testConstructor() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final CredentialEntry entry = new CredentialEntry(sCredOption, sSlice);

        assertThat(entry.getBeginGetCredentialOption()).isSameInstanceAs(sCredOption);
        assertThat(entry.getSlice()).isSameInstanceAs(sSlice);
    }

    @Test
    public void testWriteToParcel() {
        final CredentialEntry entry1 = new CredentialEntry(sCredOption, sSlice);

        final CredentialEntry entry2 = TestUtils.cloneParcelable(entry1);
        assertThat(entry2.getBeginGetCredentialOption().getId()).isEqualTo(
                entry1.getBeginGetCredentialOption().getId());
        assertThat(entry2.getBeginGetCredentialOption().getType()).isEqualTo(
                entry1.getBeginGetCredentialOption().getType());

        TestUtils.assertEquals(entry2.getSlice(), entry1.getSlice());
    }
}
