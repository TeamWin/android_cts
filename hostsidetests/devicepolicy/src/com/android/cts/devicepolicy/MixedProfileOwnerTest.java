/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.devicepolicy;

/**
 * Set of tests for pure (non-managed) profile owner use cases that also apply to device owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTest.
 */
public class MixedProfileOwnerTest extends DeviceAndProfileOwnerTest {

    protected static final String CLEAR_PROFILE_OWNER_TEST_CLASS =
            DEVICE_ADMIN_PKG + ".ClearProfileOwnerTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (mHasFeature) {
            mUserId = USER_OWNER;

            installAppAsUser(DEVICE_ADMIN_APK, mUserId);
            setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove profile owner.",
                    runDeviceTests(DEVICE_ADMIN_PKG, CLEAR_PROFILE_OWNER_TEST_CLASS));
        }
        super.tearDown();
    }

    @Override
    public void testPermissionAppUpdate() {
        // TODO Should it be failing?
        /*
01-27 14:11:14 I/BaseDevicePolicyTest: Test com.android.cts.deviceandprofileowner.PermissionsTest#testPermissionUpdate_setAutoDeniedPolicy: FAILURE
01-27 14:11:14 W/BaseDevicePolicyTest: junit.framework.AssertionFailedError
at junit.framework.Assert.fail(Assert.java:48)
at junit.framework.Assert.assertTrue(Assert.java:20)
at junit.framework.Assert.assertNotNull(Assert.java:218)
at junit.framework.Assert.assertNotNull(Assert.java:211)
at com.android.cts.deviceandprofileowner.PermissionsTest$PermissionBroadcastReceiver.waitForBroadcast(PermissionsTest.java:315)
at com.android.cts.deviceandprofileowner.PermissionsTest.assertPermissionRequest(PermissionsTest.java:241)
at com.android.cts.deviceandprofileowner.PermissionsTest.assertPermissionRequest(PermissionsTest.java:229)
at com.android.cts.deviceandprofileowner.PermissionsTest.testPermissionUpdate_setAutoDeniedPolicy(PermissionsTest.java:193)
at java.lang.reflect.Method.invoke(Native Method)
at android.test.InstrumentationTestCase.runMethod(InstrumentationTestCase.java:214)
at android.test.InstrumentationTestCase.runTest(InstrumentationTestCase.java:199)
at junit.framework.TestCase.runBare(TestCase.java:134)
at junit.framework.TestResult$1.protect(TestResult.java:115)
at android.support.test.internal.runner.junit3.AndroidTestResult.runProtected(AndroidTestResult.java:77)
at junit.framework.TestResult.run(TestResult.java:118)
at android.support.test.internal.runner.junit3.AndroidTestResult.run(AndroidTestResult.java:55)
at junit.framework.TestCase.run(TestCase.java:124)
at android.support.test.internal.runner.junit3.NonLeakyTestSuite$NonLeakyTest.run(NonLeakyTestSuite.java:63)
at junit.framework.TestSuite.runTest(TestSuite.java:243)
at junit.framework.TestSuite.run(TestSuite.java:238)
at android.support.test.internal.runner.junit3.DelegatingTestSuite.run(DelegatingTestSuite.java:103)
at android.support.test.internal.runner.junit3.AndroidTestSuite.run(AndroidTestSuite.java:69)
at android.support.test.internal.runner.junit3.JUnit38ClassRunner.run(JUnit38ClassRunner.java:90)
at org.junit.runners.Suite.runChild(Suite.java:128)
at org.junit.runners.Suite.runChild(Suite.java:27)
at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
at org.junit.runner.JUnitCore.run(JUnitCore.java:115)
at android.support.test.internal.runner.TestExecutor.execute(TestExecutor.java:54)
at android.support.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java:240)
at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:1870)
         */
    }

    @Override
    public void testPermissionPrompts() {
        // TODO Should it be failing?
        /*
01-27 14:15:54 I/BaseDevicePolicyTest: Test com.android.cts.deviceandprofileowner.PermissionsTest#testPermissionPrompts: FAILURE
01-27 14:15:54 W/BaseDevicePolicyTest: junit.framework.AssertionFailedError: Couldn't find button with resource id: permission_deny_button
at junit.framework.Assert.fail(Assert.java:50)
at junit.framework.Assert.assertTrue(Assert.java:20)
at junit.framework.Assert.assertNotNull(Assert.java:218)
at com.android.cts.deviceandprofileowner.PermissionsTest.pressPermissionPromptButton(PermissionsTest.java:298)
at com.android.cts.deviceandprofileowner.PermissionsTest.assertPermissionRequest(PermissionsTest.java:240)
at com.android.cts.deviceandprofileowner.PermissionsTest.testPermissionPrompts(PermissionsTest.java:177)
at java.lang.reflect.Method.invoke(Native Method)
at android.test.InstrumentationTestCase.runMethod(InstrumentationTestCase.java:214)
at android.test.InstrumentationTestCase.runTest(InstrumentationTestCase.java:199)
at junit.framework.TestCase.runBare(TestCase.java:134)
at junit.framework.TestResult$1.protect(TestResult.java:115)
at android.support.test.internal.runner.junit3.AndroidTestResult.runProtected(AndroidTestResult.java:77)
at junit.framework.TestResult.run(TestResult.java:118)
at android.support.test.internal.runner.junit3.AndroidTestResult.run(AndroidTestResult.java:55)
at junit.framework.TestCase.run(TestCase.java:124)
at android.support.test.internal.runner.junit3.NonLeakyTestSuite$NonLeakyTest.run(NonLeakyTestSuite.java:63)
at junit.framework.TestSuite.runTest(TestSuite.java:243)
at junit.framework.TestSuite.run(TestSuite.java:238)
at android.support.test.internal.runner.junit3.DelegatingTestSuite.run(DelegatingTestSuite.java:103)
at android.support.test.internal.runner.junit3.AndroidTestSuite.run(AndroidTestSuite.java:69)
at android.support.test.internal.runner.junit3.JUnit38ClassRunner.run(JUnit38ClassRunner.java:90)
at org.junit.runners.Suite.runChild(Suite.java:128)
at org.junit.runners.Suite.runChild(Suite.java:27)
at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
at org.junit.runner.JUnitCore.run(JUnitCore.java:115)
at android.support.test.internal.runner.TestExecutor.execute(TestExecutor.java:54)
at android.support.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java:240)
at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:1870)
         */
    }
}
