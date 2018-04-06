/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.app.cts;

import android.app.Person;
import android.app.stubs.R;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Parcel;
import android.test.AndroidTestCase;

public class PersonTest extends AndroidTestCase {

    public void testPerson_defaults() {
        Person person = new Person.Builder().build();
        assertFalse(person.isBot());
        assertFalse(person.isImportant());
        assertNull(person.getIcon());
        assertNull(person.getKey());
        assertNull(person.getName());
        assertNull(person.getUri());
    }

    public void testPerson_parcelable() {
        Person person = new Person.Builder()
                .setBot(true)
                .setImportant(true)
                .setIcon(Icon.createWithResource(getContext(), R.drawable.icon_blue))
                .setKey("key")
                .setName("Name")
                .setUri(Uri.fromParts("a", "b", "c").toString())
                .build();

        Parcel parcel = Parcel.obtain();
        person.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Person result = Person.CREATOR.createFromParcel(parcel);

        assertEquals(person.isBot(), result.isBot());
        assertEquals(person.isImportant(), result.isImportant());
        assertEquals(person.getIcon().getResId(), result.getIcon().getResId());
        assertEquals(person.getKey(), result.getKey());
        assertEquals(person.getName(), result.getName());
        assertEquals(person.getUri(), result.getUri());
    }
}
