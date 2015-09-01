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

        ll = new LocaleList((Locale) null);
        assertNotNull(ll);
        assertTrue(ll.isEmpty());
        assertEquals(0, ll.size());
        assertNull(ll.getPrimary());
        assertNull(ll.get(1));
        assertNull(ll.get(10));

        ll = new LocaleList((Locale[]) null);
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
        final LocaleList ll = new LocaleList(Locale.US);
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

    public void testEquals() {
        final LocaleList empty = new LocaleList();
        final LocaleList anotherEmpty = new LocaleList();
        LocaleList oneMember = new LocaleList(Locale.US);
        LocaleList sameOneMember = new LocaleList(new Locale("en", "US"));
        LocaleList differentOneMember = new LocaleList(Locale.FRENCH);
        Locale[] la = {Locale.US, Locale.FRENCH};
        LocaleList twoMember = new LocaleList(la);

        assertFalse(empty.equals(null));
        assertFalse(oneMember.equals(null));

        assertFalse(empty.equals(new Object()));

        assertTrue(empty.equals(empty));
        assertTrue(oneMember.equals(oneMember));

        assertFalse(empty.equals(oneMember));
        assertFalse(oneMember.equals(twoMember));

        assertFalse(oneMember.equals(differentOneMember));

        assertTrue(empty.equals(anotherEmpty));
        assertTrue(oneMember.equals(sameOneMember));
    }

    public void testHashCode() {
        final LocaleList empty = new LocaleList();
        final LocaleList anotherEmpty = new LocaleList();
        Locale[] la1 = {Locale.US};
        LocaleList oneMember = new LocaleList(la1);
        LocaleList sameOneMember = new LocaleList(la1);

        assertEquals(empty.hashCode(), anotherEmpty.hashCode());
        assertEquals(oneMember.hashCode(), sameOneMember.hashCode());
    }

    public void testToString() {
        LocaleList ll = new LocaleList();
        assertEquals("[]", ll.toString());

        final Locale[] la1 = {Locale.US};
        ll = new LocaleList(la1);
        assertEquals("["+Locale.US.toString()+"]", ll.toString());

        final Locale[] la2 = {Locale.US, Locale.FRENCH};
        ll = new LocaleList(la2);
        assertEquals("["+Locale.US.toString()+","+Locale.FRENCH.toString()+"]", ll.toString());
    }

    public void testToLanguageTags() {
        LocaleList ll = new LocaleList();
        assertEquals("", ll.toLanguageTags());

        final Locale[] la1 = {Locale.US};
        ll = new LocaleList(la1);
        assertEquals(Locale.US.toLanguageTag(), ll.toLanguageTags());

        final Locale[] la2 = {Locale.US, Locale.FRENCH};
        ll = new LocaleList(la2);
        assertEquals(Locale.US.toLanguageTag()+","+Locale.FRENCH.toLanguageTag(),
                ll.toLanguageTags());
    }

    public void testGetEmptyLocaleList() {
        LocaleList empty = LocaleList.getEmptyLocaleList();
        LocaleList anotherEmpty = LocaleList.getEmptyLocaleList();
        LocaleList constructedEmpty = new LocaleList();

        assertEquals(constructedEmpty, empty);
        assertSame(empty, anotherEmpty);
    }

    public void testForLanguageTags() {
        assertEquals(LocaleList.getEmptyLocaleList(), LocaleList.forLanguageTags(null));
        assertEquals(LocaleList.getEmptyLocaleList(), LocaleList.forLanguageTags(""));

        assertEquals(new LocaleList(Locale.forLanguageTag("en-US")),
                LocaleList.forLanguageTags("en-US"));

        final Locale[] la = {Locale.forLanguageTag("en-PH"), Locale.forLanguageTag("en-US")};
        assertEquals(new LocaleList(la), LocaleList.forLanguageTags("en-PH,en-US"));
    }
}
