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

package android.telecom.cts;

import android.os.Parcel;
import android.telecom.TransformationInfo;

import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.assertThrows;

/**
 * Unit Tests for TransformationInfo.
 */
public class TransformationInfoTest extends BaseTelecomTestWithMockServices {
    public final TransformationInfo mTransformationInfo =
            new TransformationInfo("650-456-7890", "+1 650-456-7890", "US", "GB", 1);

    public void testGetNewInstanceFromSerializedBundleIsEquivalent() {
        Parcel parcel = Parcel.obtain();
        mTransformationInfo.writeToParcel(parcel, 0);
        TransformationInfo deserializedInfo = TransformationInfo.CREATOR.createFromParcel(parcel);
        assertThat(deserializedInfo).isEqualTo(mTransformationInfo);
    }

    public void testInvalidArgumentsThrows_nullInput() {
        assertThrows(IllegalStateException.class,
                () -> new TransformationInfo(null, null, null, null, 0));
    }

    public void testInvalidArgumentsThrows_emptyInput() {
        assertThrows(IllegalStateException.class,
                () -> new TransformationInfo("", "", "", "", 0));
    }
}
