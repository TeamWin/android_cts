/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.util.cts;

import android.util.LocaleList;
import android.test.AndroidTestCase;

import java.util.Locale;

public class LocaleListTest extends AndroidTestCase {
    public void testEmptyLocaleList() {
        LocaleList ll = new LocaleList();
        assertNotNull(ll);
        assertTrue(ll.isEmpty());
        assertEquals(0, ll.size());
        assertNull(ll.getPrimary());
        assertNull(ll.get(1));
        assertNull(ll.get(10));

        ll = new LocaleList(null);
        assertNotNull(ll);
        assertTrue(ll.isEmpty());
        assertEquals(0, ll.size());
        assertNull(ll.getPrimary());
        assertNull(ll.get(1));
        assertNull(ll.get(10));

        ll = new LocaleList(new Locale[0]);
        assertNotNull(ll);
        assertTrue(ll.isEmpty());
        assertEquals(0, ll.size());
        assertNull(ll.getPrimary());
        assertNull(ll.get(1));
        assertNull(ll.get(10));
    }

    public void testOneMemberLocaleList() {
        final Locale[] la = {Locale.US};
        final LocaleList ll = new LocaleList(la);
        assertNotNull(ll);
        assertFalse(ll.isEmpty());
        assertEquals(1, ll.size());
        assertEquals(Locale.US, ll.getPrimary());
        assertEquals(Locale.US, ll.get(0));
        assertNull(ll.get(10));
    }

    public void testTwoMemberLocaleList() {
        final Locale enPH = Locale.forLanguageTag("en-PH");
        final Locale[] la = {enPH, Locale.US};
        final LocaleList ll = new LocaleList(la);
        assertNotNull(ll);
        assertFalse(ll.isEmpty());
        assertEquals(2, ll.size());
        assertEquals(enPH, ll.getPrimary());
        assertEquals(enPH, ll.get(0));
        assertEquals(Locale.US, ll.get(1));
        assertNull(ll.get(10));
    }

    public void testNullArguments() {
        final Locale[] la = {Locale.US, null};
        LocaleList ll = null;
        try {
            ll = new LocaleList(la);
            fail("Initializing a LocaleList with an array containing null should throw.");
        } catch (Throwable e) {
            assertEquals(NullPointerException.class, e.getClass());
        }
    }

    public void testRepeatedArguments() {
        final Locale[] la = {Locale.US, Locale.US};
        LocaleList ll = null;
        try {
            ll = new LocaleList(la);
            fail("Initializing a LocaleList with an array containing duplicates should throw.");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }
}
