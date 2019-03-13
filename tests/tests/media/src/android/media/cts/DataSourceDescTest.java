/*
 * Copyright 2019 The Android Open Source Project
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

package android.media.cts;

import android.content.Context;
import android.media.UriDataSourceDesc;
import android.net.Uri;
import android.test.AndroidTestCase;

import java.net.HttpCookie;
import java.util.HashMap;

/**
 * Tests for DataSourceDesc and its subclasses.
 */
public class DataSourceDescTest extends AndroidTestCase {
    public void testUriDataSourceDesc() {
        String file = "file://foo";
        Uri uri = Uri.parse(file);

        HashMap<String, String> headers = new HashMap<>();
        headers.put("header0", "value0");

        String cookieName = "auth_1234567";
        String cookieValue = "0123456789ABCDEF0123456789ABCDEF";
        HttpCookie cookie = new HttpCookie(cookieName, cookieValue);
        cookie.setDomain("www.foo.com");

        java.util.Vector<HttpCookie> cookies = new java.util.Vector<HttpCookie>();
        cookies.add(cookie);

        UriDataSourceDesc uriDsd = new UriDataSourceDesc.Builder()
            .setDataSource(mContext, uri, headers, cookies)
            .build();
        assertEquals(mContext, uriDsd.getContext());
        assertEquals(uri, uriDsd.getUri());
        assertEquals(headers, uriDsd.getHeaders());
        assertEquals(cookies, uriDsd.getCookies());

        UriDataSourceDesc uriDsd2 = new UriDataSourceDesc.Builder(uriDsd)
            .build();
        assertEquals(mContext, uriDsd2.getContext());
        assertEquals(uri, uriDsd2.getUri());
        assertEquals(headers, uriDsd2.getHeaders());
        assertEquals(cookies, uriDsd2.getCookies());
    }

    public void testUriDataSourceDescWithNullArguments() {
        String file = "file://foo";
        Uri uri = Uri.parse(file);

        try {
            UriDataSourceDesc uriDsd = new UriDataSourceDesc.Builder()
                .setDataSource(null, uri)
                .build();
        } catch (IllegalArgumentException e) {
            // Expected
            try {
                UriDataSourceDesc uriDsd2 = new UriDataSourceDesc.Builder()
                    .setDataSource(mContext, null)
                    .build();
            } catch (IllegalArgumentException e2) {
                // Expected
                return;
            }
        }
        fail();
    }
}
