/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.graphics.fonts.cts;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.graphics.fonts.FontRequest;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Base64;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link android.graphics.fonts.FontRequest}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FontRequestTest {
    private static final String PROVIDER = "com.test.fontprovider.authority";
    private static final String QUERY = "query";
    private static final String PACKAGE = "com.test.fontprovider.package";
    private static final byte[] BYTE_ARRAY =
            Base64.decode("e04fd020ea3a6910a2d808002b30", Base64.DEFAULT);
    private static final List<List<byte[]>> CERTS = Arrays.asList(Arrays.asList(BYTE_ARRAY));

    @Test
    public void testWriteToParcel() {
        // GIVEN a FontRequest created with the long constructor
        FontRequest request = new FontRequest(PROVIDER, PACKAGE, QUERY, CERTS);

        // WHEN we write it to a Parcel
        Parcel dest = Parcel.obtain();
        request.writeToParcel(dest, 0);
        dest.setDataPosition(0);

        // THEN we create from that parcel and get the same values.
        FontRequest result = FontRequest.CREATOR.createFromParcel(dest);
        assertEquals(PROVIDER, result.getProviderAuthority());
        assertEquals(PACKAGE, result.getProviderPackage());
        assertEquals(QUERY, result.getQuery());
        assertEquals(CERTS.size(), result.getCertificates().size());
        List<byte[]> cert = CERTS.get(0);
        List<byte[]> resultCert = result.getCertificates().get(0);
        assertEquals(cert.size(), resultCert.size());
        assertTrue(Arrays.equals(cert.get(0), resultCert.get(0)));
    }

    @Test
    public void testWriteToParcel_shortConstructor() {
        // GIVEN a FontRequest created with the short constructor
        FontRequest request = new FontRequest(PROVIDER, PACKAGE, QUERY);

        // WHEN we write it to a Parcel
        Parcel dest = Parcel.obtain();
        request.writeToParcel(dest, 0);
        dest.setDataPosition(0);

        // THEN we create from that parcel and get the same values.
        FontRequest result = FontRequest.CREATOR.createFromParcel(dest);
        assertEquals(PROVIDER, result.getProviderAuthority());
        assertEquals(PACKAGE, result.getProviderPackage());
        assertEquals(QUERY, result.getQuery());
        assertNotNull(result.getCertificates());
        assertEquals(0, result.getCertificates().size());
    }

    @Test(expected = NullPointerException.class)
    public void testShortConstructor_nullAuthority() {
        new FontRequest(null, PACKAGE, QUERY);
    }

    @Test(expected = NullPointerException.class)
    public void testShortConstructor_nullPackage() {
        new FontRequest(PROVIDER, null, QUERY);
    }

    @Test(expected = NullPointerException.class)
    public void testShortConstructor_nullQuery() {
        new FontRequest(PROVIDER, PACKAGE, null);
    }

    @Test
    public void testShortConstructor_defaults() {
        // WHEN we create a request with the short constructor
        FontRequest request = new FontRequest(PROVIDER, PACKAGE, QUERY);

        // THEN we expect the defaults to be the following values
        assertNotNull(request.getCertificates());
        assertEquals(0, request.getCertificates().size());
    }

    @Test(expected = NullPointerException.class)
    public void testLongConstructor_nullAuthority() {
        new FontRequest(null, PACKAGE, QUERY, CERTS);
    }

    @Test(expected = NullPointerException.class)
    public void testLongConstructor_nullPackage() {
        new FontRequest(PROVIDER, null, QUERY, CERTS);
    }

    @Test(expected = NullPointerException.class)
    public void testLongConstructor_nullQuery() {
        new FontRequest(PROVIDER, PACKAGE, null, CERTS);
    }

    @Test(expected = NullPointerException.class)
    public void testLongConstructor_nullCerts() {
        new FontRequest(PROVIDER, PACKAGE, QUERY, null);
    }
}
