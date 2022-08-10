/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.car.cts;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.CarVersion;
import android.car.annotation.ApiRequirements;
import android.car.test.AbstractExpectableTestCase;
import android.os.Parcel;

import org.junit.Test;

public final class CarVersionTest extends AbstractExpectableTestCase {

    @Test
    public void testTiramisu_0() {
        CarVersion version = CarVersion.VERSION_CODES.TIRAMISU_0;

        assertWithMessage("TIRAMISU_0").that(version).isNotNull();
        expectWithMessage("TIRAMISU_0.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_0.minor").that(version.getMinorVersion())
                .isEqualTo(0);

        CarVersion fromEnum = ApiRequirements.CarVersion.TIRAMISU_0.get();
        assertWithMessage("TIRAMISU_0 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_0 from enum").that(fromEnum).isSameInstanceAs(version);

    }

    @Test
    public void testTiramisu_1() {
        CarVersion version = CarVersion.VERSION_CODES.TIRAMISU_1;

        assertWithMessage("TIRAMISU_1").that(version).isNotNull();
        expectWithMessage("TIRAMISU_1.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_1.minor").that(version.getMinorVersion())
                .isEqualTo(1);

        CarVersion fromEnum = ApiRequirements.CarVersion.TIRAMISU_1.get();
        assertWithMessage("TIRAMISU_1 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_1 from enum").that(fromEnum).isSameInstanceAs(version);
    }

    @Test
    public void testUpsideDownCake_0() {
        CarVersion version = CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;

        assertWithMessage("UPSIDE_DOWN_CAKE_0").that(version).isNotNull();
        expectWithMessage("UPSIDE_DOWN_CAKE_0.major").that(version.getMajorVersion())
                .isEqualTo(UPSIDE_DOWN_CAKE);
        expectWithMessage("UPSIDE_DOWN_CAKE_0.minor").that(version.getMinorVersion())
                .isEqualTo(0);
    }

    @Test
    public void testMarshalling() {
        CarVersion original = CarVersion.forMajorAndMinorVersions(66, 6);
        expectWithMessage("original.describeContents()").that(original.describeContents())
                .isEqualTo(0);
        Parcel parcel =  Parcel.obtain();
        try {
            original.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);

            CarVersion clone = CarVersion.CREATOR.createFromParcel(parcel);

            assertWithMessage("CREATOR.createFromParcel()").that(clone).isNotNull();
            expectWithMessage("clone.major").that(clone.getMajorVersion()).isEqualTo(66);
            expectWithMessage("clone.minor").that(clone.getMinorVersion()).isEqualTo(6);
            expectWithMessage("clone.describeContents()").that(clone.describeContents())
                    .isEqualTo(0);

        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testNewArray() {
        CarVersion[] array = CarVersion.CREATOR.newArray(42);

        assertWithMessage("CREATOR.newArray()").that(array).isNotNull();
        expectWithMessage("CREATOR.newArray()").that(array).hasLength(42);
    }
}
