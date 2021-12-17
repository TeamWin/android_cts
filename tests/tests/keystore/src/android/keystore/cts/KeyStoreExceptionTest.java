/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.keystore.cts;

import static org.junit.Assert.assertTrue;

import android.security.KeyStoreException;
import android.security.keymaster.KeymasterDefs;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class KeyStoreExceptionTest {
    @Test
    public void testAllKeymasterDefsAreCovered() throws IllegalAccessException {
        ImmutableList<Field> kmErrors = getKeymasterDefsFields();
        assertTrue("Test bug: there should be errors to look up",
                kmErrors.size() > 0);

        for (Field f : kmErrors) {
            assertTrue(String.format("Missing entry for field %s", f.getName()),
                    KeyStoreException.hasFailureInfoForError(f.getInt(null)));
        }
    }

    public static ImmutableList<Field> getKeymasterDefsFields() {
        ImmutableList.Builder<Field> errorFieldsBuilder = new ImmutableList.Builder<>();

        Class kmDefsClass = KeymasterDefs.class;
        for (Field f : kmDefsClass.getDeclaredFields()) {
            if (f.getName().startsWith("KM_ERROR") && f.getType().equals(int.class)) {
                errorFieldsBuilder.add(f);
            }
        }

        return errorFieldsBuilder.build();
    }
}
