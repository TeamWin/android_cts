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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.graphics.fonts.FontRequest;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link android.graphics.fonts.FontRequest}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FontRequestTest {
    private static final String PROVIDER = "com.test.fontprovider";
    private static final String QUERY = "my_family_name";

    @Test
    public void testWriteToParcel() {
        // GIVEN a FontRequest
        FontRequest request = new FontRequest(PROVIDER, QUERY);

        // WHEN we write it to a Parcel
        Parcel dest = Parcel.obtain();
        request.writeToParcel(dest, 0);
        dest.setDataPosition(0);

        // THEN we create from that parcel and get the same values.
        FontRequest result = FontRequest.CREATOR.createFromParcel(dest);
        assertEquals(PROVIDER, result.getProviderAuthority());
        assertEquals(QUERY, result.getQuery());
    }

    @Test
    public void testConstructorWithNullAuthority() {
        try {
            // WHEN we create a request with a null authority
            new FontRequest(null, QUERY);
        } catch (NullPointerException e) {
            // THEN we expect an exception to be raised.
            return;
        }
        fail();
    }

    @Test
    public void testConstructorWithNullQuery() {
        try {
            // WHEN we create a request with a null query
            new FontRequest(PROVIDER, null);
        } catch (NullPointerException e) {
            // THEN we expect an exception to be raised.
            return;
        }
        fail();
    }
}
