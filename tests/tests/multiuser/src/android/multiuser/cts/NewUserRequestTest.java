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
package android.multiuser.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.NewUserRequest;
import org.junit.Test;

public class NewUserRequestTest {

    @Test
    public void testSetName() {
        String name = "testUser";
        NewUserRequest request = new NewUserRequest.Builder().setName(name).build();

        assertThat(request.getName()).isEqualTo(name);
    }

    @Test
    public void testSetNameNull() {
        NewUserRequest request = new NewUserRequest.Builder().setName(null).build();

        assertThat(request.getName()).isNull();
    }

    @Test
    public void testSetAdmin() {
        NewUserRequest request = new NewUserRequest.Builder().setAdmin().build();

        assertThat(request.isAdmin()).isTrue();
    }

    @Test
    public void testBuildThrowsOnNullUserType() {
        assertThrows(IllegalStateException.class,
                () -> new NewUserRequest.Builder().setUserType(null).setAdmin().build());
    }
}
