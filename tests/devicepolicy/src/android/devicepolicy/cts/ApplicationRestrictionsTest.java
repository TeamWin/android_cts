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
package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.expectThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.RemoteUserManager;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.PositivePolicyTest;
import com.android.bedstead.harrier.policies.ApplicationRestrictions;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstanceReference;
import com.android.bedstead.testapp.TestAppProvider;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(BedsteadJUnit4.class)
public final class ApplicationRestrictionsTest {

    private static final String TAG = ApplicationRestrictionsTest.class.getSimpleName();

    private static final String[] TEST_STRINGS = new String[] {
            "<bad/>",
            ">worse!\"£$%^&*()'<",
            "<JSON>\"{ \\\"One\\\": { \\\"OneOne\\\": \\\"11\\\", \\\""
                    + "OneTwo\\\": \\\"12\\\" }, \\\"Two\\\": \\\"2\\\" } <JSON/>\""
    };

    private static final Bundle BUNDLE_0 = createBundle0();
    private static final Bundle BUNDLE_1 = createBundle1();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    private static final TestApis sTestApis = new TestApis();

    private static final TestApp sTestApp =
            sTestAppProvider.query().whereActivities().isNotEmpty().get();

    private static final Context sContext = sTestApis.context().instrumentedContext();

    private static String sTargetPkg;
    private static String sOtherPkg;

    @BeforeClass
    public static void setPackageNames() {
        sTargetPkg = sTestApp.packageName();
        sOtherPkg = sTargetPkg + "placeholder";
        Log.d(TAG, "setPackageNames(): sTargetPkg=" + sTargetPkg + ", sOtherPkg=" + sOtherPkg);
    }

    @Test
    public void testNullComponentThrowsException() {
        DevicePolicyManager dpm = sContext.getSystemService(DevicePolicyManager.class);
        assertWithMessage("local dpm").that(dpm).isNotNull();

        SecurityException exception = expectThrows(SecurityException.class, () -> dpm
                .setApplicationRestrictions(/* admin= */ null, "whatever", /* settings= */ null));

        assertWithMessage("exception message").that(exception.getMessage())
                .containsMatch("Calling identity is not authorized");
    }

    @Test
    @Postsubmit(reason = "New test")
    @PositivePolicyTest(policy = ApplicationRestrictions.class)
    public void testSetApplicationRestrictions() {
        RemoteDevicePolicyManager rDpm = sDeviceState.dpc().devicePolicyManager();

        // Test setting restrictions
        rDpm.setApplicationRestrictions(sDeviceState.dpc().componentName(), sTargetPkg, BUNDLE_0);
        rDpm.setApplicationRestrictions(sDeviceState.dpc().componentName(), sOtherPkg, BUNDLE_1);

        // Retrieve restrictions using DPM and make sure they are what we put in.
        assertBundle0(rDpm.getApplicationRestrictions(
                sDeviceState.dpc().componentName(), sTargetPkg));
        assertBundle1(rDpm.getApplicationRestrictions(
                sDeviceState.dpc().componentName(), sOtherPkg));

        try (TestAppInstanceReference testApp =
                sTestApp.install(sTestApis.users().instrumented())) {
            RemoteUserManager testAppUm = testApp.userManager();
            assertBundle0(testAppUm.getApplicationRestrictions(sTargetPkg));

            // Test overwriting
            rDpm.setApplicationRestrictions(
                    sDeviceState.dpc().componentName(), sTargetPkg, BUNDLE_1);
            assertBundle1(rDpm.getApplicationRestrictions(
                    sDeviceState.dpc().componentName(), sTargetPkg));

            rDpm.setApplicationRestrictions(
                    sDeviceState.dpc().componentName(), sTargetPkg, new Bundle());
            assertEmptyBundle(rDpm.getApplicationRestrictions(
                    sDeviceState.dpc().componentName(), sTargetPkg));
            assertEmptyBundle(testAppUm.getApplicationRestrictions(sTargetPkg));

            rDpm.setApplicationRestrictions(
                    sDeviceState.dpc().componentName(), sOtherPkg, /* settings= */ null);
            assertEmptyBundle(rDpm.getApplicationRestrictions(
                    sDeviceState.dpc().componentName(), sOtherPkg));
        }
    }

    // Should be consistent with assertBundle0
    private static Bundle createBundle0() {
        Bundle result = new Bundle();
        // Tests for 6 allowed types: Integer, Boolean, String, String[], Bundle and Parcelable[]
        // Also test for string escaping handling
        result.putBoolean("boolean_0", false);
        result.putBoolean("boolean_1", true);
        result.putInt("integer", 0x7fffffff);
        // If a null is stored, "" will be read back
        result.putString("empty", "");
        result.putString("string", "text");
        result.putStringArray("string[]", TEST_STRINGS);

        // Adding a bundle, which contain 2 nested restrictions - bundle_string and bundle_int
        Bundle bundle = new Bundle();
        bundle.putString("bundle_string", "bundle_string");
        bundle.putInt("bundle_int", 1);
        result.putBundle("bundle", bundle);

        // Adding an array of 2 bundles
        Bundle[] bundleArray = new Bundle[2];
        bundleArray[0] = new Bundle();
        bundleArray[0].putString("bundle_array_string", "bundle_array_string");
        // Put bundle inside bundle
        bundleArray[0].putBundle("bundle_array_bundle", bundle);
        bundleArray[1] = new Bundle();
        bundleArray[1].putString("bundle_array_string2", "bundle_array_string2");
        result.putParcelableArray("bundle_array", bundleArray);
        return result;
    }

    // Should be consistent with createBundle0
    private void assertBundle0(Bundle bundle) {
        assertWithMessage("bundle0 size").that(bundle.size()).isEqualTo(8);
        assertBooleanKey(bundle, "boolean_0", false);
        assertBooleanKey(bundle, "boolean_1", true);
        assertIntKey(bundle, "integer", 0x7fffffff);
        assertStringKey(bundle, "empty", "");
        assertStringKey(bundle, "string", "text");
        assertStringsKey(bundle, "string[]", TEST_STRINGS);

        Bundle childBundle = bundle.getBundle("bundle");
        assertStringKey(childBundle, "bundle_string", "bundle_string");
        assertIntKey(childBundle, "bundle_int", 1);

        Parcelable[] bundleArray = bundle.getParcelableArray("bundle_array");
        assertWithMessage("size of bundle_array").that(bundleArray).hasLength(2);

        // Verifying bundle_array[0]
        Bundle bundle1 = (Bundle) bundleArray[0];
        assertStringKey(bundle1, "bundle_array_string", "bundle_array_string");

        Bundle bundle1ChildBundle = getBundleKey(bundle1, "bundle_array_bundle");

        assertWithMessage("bundle_array_bundle").that(bundle1ChildBundle).isNotNull();
        assertStringKey(bundle1ChildBundle, "bundle_string", "bundle_string");
        assertIntKey(bundle1ChildBundle, "bundle_int", 1);

        // Verifying bundle_array[1]
        Bundle bundle2 = (Bundle) bundleArray[1];
        assertStringKey(bundle2, "bundle_array_string2", "bundle_array_string2");
    }

    // Should be consistent with assertBundle1
    private static Bundle createBundle1() {
        Bundle result = new Bundle();
        result.putInt("placeholder", 1);
        return result;
    }

    // Should be consistent with createBundle1
    private void assertBundle1(Bundle bundle) {
        assertWithMessage("bundle1 size").that(bundle.size()).isEqualTo(1);
        assertIntKey(bundle, "placeholder", 1);
    }

    private void assertEmptyBundle(Bundle bundle) {
        assertWithMessage("empty bundle").that(bundle.isEmpty()).isTrue();
    }

    private void assertBooleanKey(Bundle bundle, String key, boolean expectedValue) {
        boolean value = bundle.getBoolean(key);
        Log.v(TAG, "assertBooleanKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key").that(value).isEqualTo(expectedValue);
    }

    private void assertIntKey(Bundle bundle, String key, int expectedValue) {
        int value = bundle.getInt(key);
        Log.v(TAG, "assertIntKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key").that(value).isEqualTo(expectedValue);
    }

    private void assertStringKey(Bundle bundle, String key, String expectedValue) {
        String value = bundle.getString(key);
        Log.v(TAG, "assertStringKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key").that(value).isEqualTo(expectedValue);
    }

    private void assertStringsKey(Bundle bundle, String key, String[] expectedValue) {
        String[] value = bundle.getStringArray(key);
        Log.v(TAG, "assertStringsKey(): " + key + "="
                + (value == null ? "null" : Arrays.toString(value)));

        assertWithMessage("bundle's '%s' key").that(value).asList()
                .containsExactlyElementsIn(expectedValue).inOrder();
    }

    private Bundle getBundleKey(Bundle bundle, String key) {
        Bundle value = bundle.getBundle(key);
        Log.v(TAG, "getBundleKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key").that(value).isNotNull();
        return value;
    }
}
