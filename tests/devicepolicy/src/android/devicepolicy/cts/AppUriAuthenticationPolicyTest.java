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

import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Parcel;
import android.security.AppUriAuthenticationPolicy;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class AppUriAuthenticationPolicyTest {

    private final static String PACKAGE_NAME = "com.android.test";
    private final static Uri URI = Uri.parse("test.com");
    private final static Uri URI2 = Uri.parse("test2.com");
    private final static String ALIAS = "testAlias";
    private final static AppUriAuthenticationPolicy AUTHENTICATION_POLICY =
            new AppUriAuthenticationPolicy.Builder()
                    .addAppAndUriMapping(PACKAGE_NAME, URI, ALIAS)
                    .build();

    @Test
    public void getAppAndUrisMappings_returnsMapping() {
        AppUriAuthenticationPolicy authenticationPolicy =
                new AppUriAuthenticationPolicy.Builder()
                        .addAppAndUriMapping(PACKAGE_NAME, URI, ALIAS)
                        .build();

        Map<String, Map<Uri, String>> appToUris = authenticationPolicy.getAppAndUriMappings();

        assertThat(appToUris.containsKey(PACKAGE_NAME)).isTrue();
        Map<Uri, String> urisToAlias = appToUris.get(PACKAGE_NAME);
        assertThat(urisToAlias.containsKey(URI)).isTrue();
        assertThat(urisToAlias.get(URI)).isEqualTo(ALIAS);
    }

    @Test
    public void getAppAnyUrisMappings_multipleUrisSameAlias_containsBothUris() {
        AppUriAuthenticationPolicy authenticationPolicy =
                new AppUriAuthenticationPolicy.Builder()
                        .addAppAndUriMapping(PACKAGE_NAME, URI, ALIAS)
                        .addAppAndUriMapping(PACKAGE_NAME, URI2, ALIAS)
                        .build();

        Map<String, Map<Uri, String>> appToUris = authenticationPolicy.getAppAndUriMappings();
        Map<Uri, String> urisToAlias = appToUris.get(PACKAGE_NAME);

        assertThat(urisToAlias.containsKey(URI)).isTrue();
        assertThat(urisToAlias.get(URI)).isEqualTo(ALIAS);
        assertThat(urisToAlias.containsKey(URI2)).isTrue();
        assertThat(urisToAlias.get(URI2)).isEqualTo(ALIAS);
    }

    @Test
    public void addAppAndUriMapping_nullUri_throwException() {
        try {
            new AppUriAuthenticationPolicy.Builder().addAppAndUriMapping(
                    PACKAGE_NAME, /* uris= */ null, ALIAS);
            fail("Shall not take null inputs");
        } catch (NullPointerException expected) {
            // Expected behavior, nothing to do.
        }
    }

    @Test
    public void addAppAndUriMapping_nullPackageName_throwException() {
        try {
            new AppUriAuthenticationPolicy.Builder().addAppAndUriMapping(
                    /* packageName= */ null, URI, ALIAS);
            fail("Shall not take null inputs");
        } catch (NullPointerException expected) {
            // Expected behavior, nothing to do.
        }
    }

    @Test
    public void addAppAndUriMapping_nullAlias_throwException() {
        try {
            new AppUriAuthenticationPolicy.Builder().addAppAndUriMapping(PACKAGE_NAME,
                    URI, /* alias= */null);
            fail("Shall not take null inputs");
        } catch (NullPointerException expected) {
            // Expected behavior, nothing to do.
        }
    }

    @Test
    public void AppUriAuthenticationPolicy_parcel() {
        Parcel parcel = null;
        try {
            // Write to parcel
            parcel = Parcel.obtain();
            parcel.writeParcelable(AUTHENTICATION_POLICY, 0);
            parcel.setDataPosition(0);

            // Read from parcel
            AppUriAuthenticationPolicy createdPolicy =
                    parcel.readParcelable(/* classLoader = */null);

            assertThat(createdPolicy).isNotNull();
            assertAuthenticationPoliciesEqual(createdPolicy, AUTHENTICATION_POLICY);
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
    }

    private void assertAuthenticationPoliciesEqual(AppUriAuthenticationPolicy actual,
            AppUriAuthenticationPolicy expected) {
        Iterator<Map.Entry<String, Map<Uri, String>>> actualIter =
                actual.getAppAndUriMappings().entrySet().iterator();
        Iterator<Map.Entry<String, Map<Uri, String>>> expectedIter =
                expected.getAppAndUriMappings().entrySet().iterator();

        assertThat(actual.getAppAndUriMappings().size())
                .isEqualTo(expected.getAppAndUriMappings().size());
        while (actualIter.hasNext()) {
            Map.Entry<String, Map<Uri, String>> actualAppToUri = actualIter.next();
            Map.Entry<String, Map<Uri, String>> expectedAppToUri = expectedIter.next();
            assertThat(actualAppToUri.getKey()).isEqualTo(expectedAppToUri.getKey());
            assertUriToAliasesEqual(actualAppToUri.getValue(), expectedAppToUri.getValue());
        }
    }

    private void assertUriToAliasesEqual(Map<Uri, String> actual, Map<Uri, String> expected) {
        Iterator<Map.Entry<Uri, String>> actualIter = actual.entrySet().iterator();
        Iterator<Map.Entry<Uri, String>> expectedIter = expected.entrySet().iterator();

        assertThat(actual.size()).isEqualTo(expected.size());
        while (actualIter.hasNext()) {
            Map.Entry<Uri, String> actualUriToAlias = actualIter.next();
            Map.Entry<Uri, String> expectedUriToAlias = expectedIter.next();
            assertThat(actualUriToAlias.getKey()).isEqualTo(expectedUriToAlias.getKey());
            assertThat(actualUriToAlias.getValue()).isEqualTo(expectedUriToAlias.getValue());
        }
    }
}
