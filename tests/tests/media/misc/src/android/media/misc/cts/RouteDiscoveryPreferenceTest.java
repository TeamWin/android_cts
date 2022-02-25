/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.misc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertThrows;

import android.media.RouteDiscoveryPreference;
import android.media.cts.NonMediaMainlineTest;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
@NonMediaMainlineTest
public class RouteDiscoveryPreferenceTest {

    private static final String TEST_FEATURE_1 = "TEST_FEATURE_1";
    private static final String TEST_FEATURE_2 = "TEST_FEATURE_2";

    private static final String TEST_PACKAGE_1 = "TEST_PACKAGE_1";
    private static final String TEST_PACKAGE_2 = "TEST_PACKAGE_2";
    private static final String TEST_PACKAGE_3 = "TEST_PACKAGE_3";

    @Test
    public void testBuilderConstructorWithNull() {
        // Tests null preferredFeatures
        assertThrows(NullPointerException.class,
                () -> new RouteDiscoveryPreference.Builder(null, true));

        // Tests null RouteDiscoveryPreference
        assertThrows(NullPointerException.class,
                () -> new RouteDiscoveryPreference.Builder((RouteDiscoveryPreference) null));
    }

    @Test
    public void testBuilderSetPreferredFeaturesWithNull() {
        RouteDiscoveryPreference.Builder builder =
                new RouteDiscoveryPreference.Builder(new ArrayList<>(), true);

        assertThrows(NullPointerException.class, () -> builder.setPreferredFeatures(null));
    }

    @Test
    public void testBuilderSetAllowedPackagesWithNull() {
        RouteDiscoveryPreference.Builder builder =
                new RouteDiscoveryPreference.Builder(new ArrayList<>(), true);

        assertThrows(NullPointerException.class, () -> builder.setAllowedPackages(null));
    }

    @Test
    public void testBuilderSetDeduplicationPackageOrderWithNull() {
        RouteDiscoveryPreference.Builder builder =
                new RouteDiscoveryPreference.Builder(new ArrayList<>(), true);

        assertThrows(NullPointerException.class, () -> builder.setDeduplicationPackageOrder(null));
    }

    @Test
    public void testDefaultValues() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();
        assertEquals(preferredFeatures, preference.getPreferredFeatures());
        assertTrue(preference.shouldPerformActiveScan());

        assertTrue(preference.getAllowedPackages().isEmpty());
        assertTrue(preference.getDeduplicationPackageOrder().isEmpty());
        assertFalse(preference.shouldRemoveDuplicates());
        assertEquals(0, preference.describeContents());
    }

    @Test
    public void testGetters() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);
        List<String> allowedPackages = List.of(TEST_PACKAGE_1, TEST_PACKAGE_2, TEST_PACKAGE_3);
        List<String> packageOrder = List.of(TEST_PACKAGE_3, TEST_PACKAGE_1, TEST_PACKAGE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();
        assertEquals(preferredFeatures, preference.getPreferredFeatures());
        assertTrue(preference.shouldPerformActiveScan());
        assertEquals(allowedPackages, preference.getAllowedPackages());
        assertTrue(preference.shouldRemoveDuplicates());
        assertEquals(packageOrder, preference.getDeduplicationPackageOrder());
        assertEquals(0, preference.describeContents());
    }

    @Test
    public void testBuilderSetPreferredFeatures() {
        final List<String> features = new ArrayList<>();
        features.add(TEST_FEATURE_1);
        features.add(TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, true /* isActiveScan */).build();

        final List<String> newFeatures = new ArrayList<>();
        newFeatures.add(TEST_FEATURE_1);

        // Using copy constructor, we only change the setPreferredFeatures.
        RouteDiscoveryPreference newPreference = new RouteDiscoveryPreference.Builder(preference)
                .setPreferredFeatures(newFeatures)
                .build();

        assertEquals(newFeatures, newPreference.getPreferredFeatures());
        assertTrue(newPreference.shouldPerformActiveScan());
    }

    @Test
    public void testBuilderSetActiveScan() {
        final List<String> features = new ArrayList<>();
        features.add(TEST_FEATURE_1);
        features.add(TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, true /* isActiveScan */).build();

        // Using copy constructor, we only change the activeScan to 'false'.
        RouteDiscoveryPreference newPreference = new RouteDiscoveryPreference.Builder(preference)
                .setShouldPerformActiveScan(false)
                .build();

        assertEquals(features, newPreference.getPreferredFeatures());
        assertFalse(newPreference.shouldPerformActiveScan());
    }

    @Test
    public void testEqualsCreatedWithSameArguments() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);
        List<String> allowedPackages = List.of(TEST_PACKAGE_1, TEST_PACKAGE_2, TEST_PACKAGE_3);
        List<String> packageOrder = List.of(TEST_PACKAGE_3, TEST_PACKAGE_1, TEST_PACKAGE_2);

        RouteDiscoveryPreference preference1 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();

        RouteDiscoveryPreference preference2 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();

        assertEquals(preference1, preference2);
    }

    @Test
    public void testEqualsCreatedWithBuilderCopyConstructor() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);
        List<String> allowedPackages = List.of(TEST_PACKAGE_1, TEST_PACKAGE_2, TEST_PACKAGE_3);
        List<String> packageOrder = List.of(TEST_PACKAGE_3, TEST_PACKAGE_1, TEST_PACKAGE_2);

        RouteDiscoveryPreference preference1 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();

        RouteDiscoveryPreference preference2 =
                new RouteDiscoveryPreference.Builder(preference1).build();

        assertEquals(preference1, preference2);
    }

    @Test
    public void testEqualsReturnFalse() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);
        List<String> allowedPackages = List.of(TEST_PACKAGE_1, TEST_PACKAGE_2, TEST_PACKAGE_3);
        List<String> packageOrder = List.of(TEST_PACKAGE_3, TEST_PACKAGE_1, TEST_PACKAGE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();

        RouteDiscoveryPreference preferenceWithDifferentFeatures =
                new RouteDiscoveryPreference.Builder(new ArrayList<>(), true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();
        assertNotEquals(preference, preferenceWithDifferentFeatures);

        RouteDiscoveryPreference preferenceWithDifferentActiveScan =
                new RouteDiscoveryPreference.Builder(preferredFeatures, false /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();
        assertNotEquals(preference, preferenceWithDifferentActiveScan);

        RouteDiscoveryPreference preferenceWithDifferentAllowedPackages =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(List.of())
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();
        assertNotEquals(preference, preferenceWithDifferentAllowedPackages);

        RouteDiscoveryPreference preferenceWithDifferentPackageOrder1 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(List.of())
                        .build();
        assertNotEquals(preference, preferenceWithDifferentPackageOrder1);

        RouteDiscoveryPreference preferenceWithDifferentPackageOrder2 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        // same size but different order
                        .setDeduplicationPackageOrder(allowedPackages)
                        .build();
        assertNotEquals(preference, preferenceWithDifferentPackageOrder2);
    }

    @Test
    public void testEqualsReturnFalseWithCopyConstructor() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);
        List<String> allowedPackages = List.of(TEST_PACKAGE_1, TEST_PACKAGE_2, TEST_PACKAGE_3);
        List<String> packageOrder = List.of(TEST_PACKAGE_3, TEST_PACKAGE_1, TEST_PACKAGE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();

        final List<String> newFeatures = new ArrayList<>();
        newFeatures.add(TEST_FEATURE_1);
        RouteDiscoveryPreference preferenceWithDifferentFeatures =
                new RouteDiscoveryPreference.Builder(preference)
                        .setPreferredFeatures(newFeatures)
                        .build();
        assertNotEquals(preference, preferenceWithDifferentFeatures);

        RouteDiscoveryPreference preferenceWithDifferentActiveScan =
                new RouteDiscoveryPreference.Builder(preference)
                        .setShouldPerformActiveScan(false)
                        .build();
        assertNotEquals(preference, preferenceWithDifferentActiveScan);

        RouteDiscoveryPreference preferenceWithDifferentAllowedPackages =
                new RouteDiscoveryPreference.Builder(preference)
                        .setAllowedPackages(List.of())
                        .build();
        assertNotEquals(preference, preferenceWithDifferentAllowedPackages);

        RouteDiscoveryPreference preferenceWithDifferentPackageOrder1 =
                new RouteDiscoveryPreference.Builder(preference)
                        .setDeduplicationPackageOrder(List.of())
                        .build();
        assertNotEquals(preference, preferenceWithDifferentPackageOrder1);

        RouteDiscoveryPreference preferenceWithDifferentPackageOrder2 =
                new RouteDiscoveryPreference.Builder(preference)
                        // same size but different order
                        .setDeduplicationPackageOrder(allowedPackages)
                        .build();
        assertNotEquals(preference, preferenceWithDifferentPackageOrder2);
    }

    @Test
    public void testParcelingAndUnParceling() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);
        List<String> allowedPackages = List.of(TEST_PACKAGE_1, TEST_PACKAGE_2, TEST_PACKAGE_3);
        List<String> packageOrder = List.of(TEST_PACKAGE_3, TEST_PACKAGE_1, TEST_PACKAGE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .setAllowedPackages(allowedPackages)
                        .setDeduplicationPackageOrder(packageOrder)
                        .build();

        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(preference, 0);
        parcel.setDataPosition(0);

        RouteDiscoveryPreference preferenceFromParcel = parcel.readParcelable(null);
        assertEquals(preference, preferenceFromParcel);
        parcel.recycle();

        // In order to mark writeToParcel as tested, we let's just call it directly.
        Parcel dummyParcel = Parcel.obtain();
        preference.writeToParcel(dummyParcel, 0);
        dummyParcel.recycle();
    }
}
